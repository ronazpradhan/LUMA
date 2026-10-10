package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.PlaylistEntity
import com.stash.core.data.db.entity.PlaylistTrackCrossRef
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.model.MusicSource
import com.stash.core.model.PlaylistType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Audit (REPLACE→ABORT): [TrackEntity] and [PlaylistEntity] are cascade
 * PARENTS (tracks → playlist_tracks/lyrics/listening_events/…; playlists →
 * playlist_tracks). `@Insert(onConflict = REPLACE)` is DELETE-then-INSERT, so
 * a natural-key conflict (track spotify_uri/youtube_id, playlist source_id)
 * used to DELETE the existing parent row and CASCADE-wipe all its children —
 * silent data loss.
 *
 * With ABORT the conflicting insert throws instead, leaving the existing row
 * and its children untouched; callers dedup-first so this only fires on a real
 * bug, now loudly rather than by eating the user's data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ReplaceAbortNoWipeTest {

    private lateinit var db: StashDatabase
    private lateinit var trackDao: TrackDao
    private lateinit var playlistDao: PlaylistDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        trackDao = db.trackDao()
        playlistDao = db.playlistDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun `re-inserting a track with a conflicting youtube_id aborts without wiping its memberships`() = runTest {
        val trackId = trackDao.insert(track(id = 1L, youtubeId = "yt-1"))
        val playlistId = playlistDao.insert(mix("Mix", "stash_mix_1"))
        playlistDao.insertCrossRef(PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackId, position = 0))

        // A fresh (id = 0) insert whose youtube_id collides with track 1.
        val result = runCatching {
            trackDao.insert(track(id = 0L, youtubeId = "yt-1"))
        }

        assertTrue("the conflicting insert must abort, not silently REPLACE", result.isFailure)
        // The original track AND its cross-ref survive — no DELETE, no cascade.
        assertNotNull("original track must still exist", trackDao.getById(1L))
        assertEquals(
            "the track's playlist membership must survive the aborted insert",
            listOf(1L),
            playlistDao.getOrderedTrackIdsForPlaylist(playlistId),
        )
    }

    @Test fun `re-inserting a playlist with a conflicting source_id aborts without wiping its membership`() = runTest {
        val trackId = trackDao.insert(track(id = 1L, youtubeId = "yt-1"))
        val playlistId = playlistDao.insert(mix("Mix", "stash_mix_1"))
        playlistDao.insertCrossRef(PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackId, position = 0))

        val result = runCatching {
            playlistDao.insert(mix("Mix Renamed", "stash_mix_1"))
        }

        assertTrue("the conflicting insert must abort, not silently REPLACE", result.isFailure)
        assertNotNull("original playlist must still exist", playlistDao.getById(playlistId))
        assertEquals(
            "the playlist's membership must survive the aborted insert",
            listOf(1L),
            playlistDao.getOrderedTrackIdsForPlaylist(playlistId),
        )
    }

    private fun mix(name: String, sourceId: String) = PlaylistEntity(
        name = name,
        source = MusicSource.BOTH,
        sourceId = sourceId,
        type = PlaylistType.STASH_MIX,
        syncEnabled = true,
        isActive = true,
    )

    private fun track(id: Long, youtubeId: String) = TrackEntity(
        id = id,
        title = "Track $id",
        artist = "Artist $id",
        youtubeId = youtubeId,
        canonicalTitle = "track $id",
        canonicalArtist = "artist $id",
        isDownloaded = true,
        isStreamable = false,
    )
}
