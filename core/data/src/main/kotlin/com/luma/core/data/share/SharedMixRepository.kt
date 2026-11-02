package com.luma.core.data.share

import android.content.Context
import androidx.room.withTransaction
import com.luma.core.data.db.LumaDatabase
import com.luma.core.data.db.dao.PlaylistDao
import com.luma.core.data.db.dao.SharedMixDao
import com.luma.core.data.db.dao.TrackDao
import com.luma.core.data.db.entity.SharedMixEntity
import com.luma.core.data.mapper.toDomain
import com.luma.core.data.repository.MusicRepository
import com.luma.core.model.Track
import com.luma.core.model.share.ShareConfig
import com.luma.core.model.share.SharedTrack
import com.luma.core.model.share.toSharedTrack
import com.luma.core.model.share.toSharedTrackWithArt
import com.luma.core.model.share.toTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Clips a descriptor to the Worker's limits (worker src/validate.js), so one odd row can't fail a whole mix or post. */
internal fun SharedTrack.withinLimits() = copy(
    title = title.cut(500),
    artist = artist.cut(500),
    album = album?.cut(500),
    isrc = isrc?.takeIf { it.length <= 20 },
    spotifyId = spotifyId?.takeIf { it.length <= 40 },
    youtubeId = youtubeId?.takeIf { it.length <= 20 },
    artUrl = artUrl?.takeIf { it.length <= 1000 },
)

/** [take] that never ends on half an emoji: UTF-8 would send the lone half as "?". */
internal fun String.cut(n: Int) = take(n).let { if (it.isNotEmpty() && it.length == n && it.last().isHighSurrogate()) it.dropLast(1) else it }

/**
 * LUMA: shared playlists without a share server. Links are built on the phone
 * ([com.luma.core.model.share.ShareLinks.playlistUrl]); a received playlist is played or
 * saved as a copy. Mixes followed in older versions stay as playlists you can remove.
 */
@Singleton
class SharedMixRepository @Inject constructor(
    private val database: LumaDatabase,
    private val sharedMixDao: SharedMixDao,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val musicRepository: MusicRepository,
    @ApplicationContext private val context: Context,
) {
    fun observe(playlistId: Long): Flow<SharedMixEntity?> = sharedMixDao.observeForPlaylist(playlistId)

    /** Playlists that are an active follow, i.e. read-only in the Library (spec §6). */
    fun observeActiveFollowedIds(): Flow<List<Long>> = sharedMixDao.observeActiveFollowedIds()
    suspend fun forPlaylist(playlistId: Long): SharedMixEntity? = sharedMixDao.forPlaylist(playlistId)

    /**
     * Build the document from the playlist's live members, in order (removed ones excluded).
     * Every field is clipped to the Worker's limits (worker src/validate.js) so one odd library
     * row can never make the whole mix unpublishable.
     * [withArt] keeps each song's cover link (Community posts; a shared mix never sends it).
     */
    suspend fun buildDocument(playlistId: Long, name: String, sharedBy: String?, withArt: Boolean = false): SharedMixDocument {
        // Untagged local files are kept: toSharedTrack() gives them placeholder title/artist.
        val tracks = playlistDao.getTracksForPlaylist(playlistId).map { it.toDomain() }
        return SharedMixDocument(
            name = name.trim().cut(100),
            sharedBy = sharedBy?.trim()?.cut(40)?.ifBlank { null },
            covers = linkCovers(playlistId, tracks),
            tracks = tracks.take(MAX_TRACKS).map { (if (withArt) it.toSharedTrackWithArt() else it.toSharedTrack()).withinLimits() },
        )
    }

    /**
     * The playlist's picture for a link: its own cover when that's a web image, else nothing, and
     * whoever opens the link builds the mosaic from the songs' YouTube ids. Cover addresses are long
     * and don't compress, so carrying four of them more than doubled a short playlist's link. A photo
     * picked from the gallery is a local file, which a link can't carry without a server.
     */
    private suspend fun linkCovers(playlistId: Long, @Suppress("UNUSED_PARAMETER") tracks: List<Track>): List<String> =
        listOfNotNull(playlistDao.getById(playlistId)?.artUrl?.takeIf { it.length <= 500 && ShareConfig.isAllowedCover(it) })

    /** Persist every descriptor (deduped against the library), keeping document order and dropping repeats. */
    suspend fun persistTracks(doc: SharedMixDocument): List<Long> =
        doc.tracks.map { musicRepository.ensureTrackPersisted(it.toTrack()) }.distinct()

    suspend fun tracksFor(doc: SharedMixDocument): List<Track> =
        persistTracks(doc).mapNotNull { trackDao.getById(it)?.toDomain() }

    /** Spec §6 Save a copy: an ordinary editable playlist, no link back. */
    suspend fun saveCopy(doc: SharedMixDocument): Long {
        val ids = persistTracks(doc)
        val playlistId = musicRepository.createPlaylist(doc.name)
        // Streams until the user switches downloads on (spec §6); set before any track joins.
        playlistDao.setSyncEnabled(playlistId, false)
        playlistDao.replaceMixMembership(playlistId, ids, doc.name, Instant.now())
        return playlistId
    }

    /**
     * Remove a followed mix and the tracks it brought in, unless something else claims them
     * (a like, a download, another playlist, listening history; see [TrackDao.deleteUnclaimedTracks]).
     */
    suspend fun unfollow(playlistId: Long) {
        val playlist = playlistDao.getById(playlistId)?.toDomain() ?: return
        val memberIds = playlistDao.getTracksForPlaylist(playlistId).map { it.id }
        sharedMixDao.delete(playlistId)
        musicRepository.removePlaylist(playlist)
        trackDao.deleteUnclaimedTracks(memberIds)
    }

    /**
     * "Download this mix" is the playlist's sync_enabled (spec §6). On also starts downloading now.
     * Off cancels the songs still waiting, exactly as a playlist's Download button does
     * ([com.luma.core.data.db.dao.DownloadQueueDao.cancelWaitingForPlaylist]): a song downloading
     * now finishes, downloaded songs stay, and a song another kept playlist or followed mix still
     * wants keeps its row. The flag lands at once; the queue work runs one change at a time, so an
     * off right after an on waits for the on's songs to be queued, then cancels them.
     *
     * ponytail: a follow update's background queueing ([applyUpdate]) doesn't take [downloadLock],
     * so an off landing mid-update can let that update's new songs through. Take the lock there too,
     * re-reading sync_enabled inside it, if that ever shows up.
     */
    suspend fun setDownload(playlistId: Long, on: Boolean) {
        playlistDao.setSyncEnabled(playlistId, on)
        downloadLock.withLock {
            if (on) musicRepository.queueDownloadsForPlaylist(playlistId)
            else database.downloadQueueDao().cancelWaitingForPlaylist(playlistId)
        }
    }

    private val downloadLock = Mutex()

    /** The one-time "stopped sharing" message (spec §6), or null. Clears the flag. */
    suspend fun consumeRemovedNotice(playlistId: Long): String? {
        val row = sharedMixDao.forPlaylist(playlistId)?.takeIf { it.noticePending } ?: return null
        sharedMixDao.clearNotice(playlistId)
        return "${row.sharedBy ?: "The owner"} stopped sharing this mix. You keep your copy."
    }

    companion object {
        const val MAX_TRACKS = 2000
    }
}
