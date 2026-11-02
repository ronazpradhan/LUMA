package com.luma.core.data.share

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.luma.core.data.db.LumaDatabase
import com.luma.core.data.db.entity.DownloadQueueEntity
import com.luma.core.data.db.entity.PlaylistEntity
import com.luma.core.data.db.entity.PlaylistTrackCrossRef
import com.luma.core.data.db.entity.SharedMixEntity
import com.luma.core.data.mapper.toEntity
import com.luma.core.data.repository.MusicRepository
import com.luma.core.model.MusicSource
import com.luma.core.model.PlaylistType
import com.luma.core.model.Track
import com.luma.core.model.share.SharedTrack
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SharedMixRepositoryFollowerTest {
    private lateinit var db: LumaDatabase
    private lateinit var server: MockWebServer
    private lateinit var music: MusicRepository
    private lateinit var repo: SharedMixRepository

    private fun doc(version: Int, vararg titles: String, name: String = "Ambient") = SharedMixDocument(
        id = "Kx7Qa2pL", version = version, name = name, sharedBy = "Rawn",
        tracks = titles.map { SharedTrack(it, "Artist") },
    )
    private fun docJson(d: SharedMixDocument) = ShareJson.encodeToString(SharedMixDocument.serializer(), d)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LumaDatabase::class.java)
            .allowMainThreadQueries().build()
        server = MockWebServer().also { it.start() }
        music = mockk(relaxed = true)
        coEvery { music.ensureTrackPersisted(any()) } coAnswers {
            val t = firstArg<Track>()
            db.trackDao().findByCanonicalIdentity(t.title.lowercase(), t.artist.lowercase())?.id
                ?: db.trackDao().insert(t.toEntity().copy(canonicalTitle = t.title.lowercase(), canonicalArtist = t.artist.lowercase()))
        }
        // Real row, as MusicRepositoryImpl.createPlaylist makes it (a relaxed mock would return 0 → FK failure).
        coEvery { music.createPlaylist(any()) } coAnswers {
            db.playlistDao().insert(
                PlaylistEntity(name = firstArg(), source = MusicSource.BOTH, sourceId = "custom_${System.nanoTime()}",
                    type = PlaylistType.CUSTOM, syncEnabled = true),
            )
        }
        repo = SharedMixRepository(db, db.sharedMixDao(), db.playlistDao(), db.trackDao(), music, ApplicationProvider.getApplicationContext())
    }
    @After fun tearDown() { db.close(); server.shutdown() }

    private suspend fun titles(playlistId: Long) = db.playlistDao().getTracksForPlaylist(playlistId).map { it.title }

    @Test fun `download off cancels the waiting songs, keeps downloaded ones, and spares a kept playlist's`() = runBlocking {
        val id = repo.saveCopy(doc(1, "Waiting", "Downloaded", "Also kept"))
        val ids = db.playlistDao().getTracksForPlaylist(id).associate { it.title to it.id }
        repo.setDownload(id, true)
        // What the on queued (music is a mock): discovery rows, as queueDownloadsForPlaylist writes them.
        for (t in listOf("Waiting", "Also kept")) db.downloadQueueDao().insert(DownloadQueueEntity(trackId = ids[t]!!, syncId = null))
        db.openHelper.writableDatabase.execSQL("UPDATE tracks SET is_downloaded = 1, file_path = '/x.flac' WHERE id = ${ids["Downloaded"]}")
        val kept = db.playlistDao().insert(
            PlaylistEntity(name = "Mine", source = MusicSource.BOTH, sourceId = "custom_k", type = PlaylistType.CUSTOM, keepOffline = true),
        )
        db.playlistDao().insertCrossRef(PlaylistTrackCrossRef(playlistId = kept, trackId = ids["Also kept"]!!, position = 0))

        repo.setDownload(id, false)

        assertThat(db.playlistDao().getById(id)!!.syncEnabled).isFalse()
        assertThat(db.downloadQueueDao().getByTrackId(ids["Waiting"]!!)).isNull()
        assertThat(db.downloadQueueDao().getByTrackId(ids["Also kept"]!!)).isNotNull() // the kept playlist still wants it
        val downloaded = db.trackDao().getById(ids["Downloaded"]!!)!!
        assertThat(downloaded.isDownloaded).isTrue()
        assertThat(downloaded.filePath).isEqualTo("/x.flac")
        coVerify(exactly = 0) { music.removeDownload(any()) }
        coVerify(exactly = 0) { music.removeDownloadsForPlaylist(any()) }
    }

    @Test fun `an off right after an on still cancels what the on queues`() = runBlocking {
        val id = repo.saveCopy(doc(1, "One"))
        val track = db.playlistDao().getTracksForPlaylist(id).single().id
        val queueing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { music.queueDownloadsForPlaylist(id, false) } coAnswers {
            queueing.complete(Unit)
            release.await() // still checking each song when the off lands
            db.downloadQueueDao().insert(DownloadQueueEntity(trackId = track, syncId = null))
            1
        }
        val on = launch { repo.setDownload(id, true) }
        queueing.await()
        val off = launch { repo.setDownload(id, false) }
        withTimeoutOrNull(500) { off.join() } // an off that didn't wait would be done here, before the on's row exists
        release.complete(Unit)
        joinAll(on, off)
        assertThat(db.downloadQueueDao().getByTrackId(track)).isNull()
        assertThat(db.playlistDao().getById(id)!!.syncEnabled).isFalse()
    }

    @Test fun `save a copy is an ordinary editable playlist with no shared row`() = runBlocking {
        val id = repo.saveCopy(doc(1, "One", "Two"))
        val p = db.playlistDao().getById(id)!!
        assertThat(p.sourceId).startsWith("custom_")
        assertThat(p.syncEnabled).isFalse()
        assertThat(titles(id)).containsExactly("One", "Two").inOrder()
        assertThat(db.sharedMixDao().forPlaylist(id)).isNull()
    }

    @Test fun `unfollow deletes only the tracks nothing else claims`() = runBlocking {
        coEvery { music.removePlaylist(any()) } coAnswers { db.playlistDao().delete(db.playlistDao().getById(firstArg<com.luma.core.model.Playlist>().id)!!) }
        val id = repo.saveCopy(doc(1, "Loose", "Liked", "Other", "Downloaded", "Heard"))
        val ids = db.playlistDao().getTracksForPlaylist(id).associate { it.title to it.id }
        val sql = db.openHelper.writableDatabase
        sql.execSQL("UPDATE tracks SET stash_liked_at = 1 WHERE id = ${ids["Liked"]}")
        sql.execSQL("UPDATE tracks SET is_downloaded = 1, file_path = '/x.flac' WHERE id = ${ids["Downloaded"]}")
        val other = db.playlistDao().insert(PlaylistEntity(name = "Mine", source = MusicSource.BOTH, sourceId = "custom_o", type = PlaylistType.CUSTOM))
        db.playlistDao().insertCrossRef(com.luma.core.data.db.entity.PlaylistTrackCrossRef(playlistId = other, trackId = ids["Other"]!!, position = 0))
        db.listeningEventDao().insert(com.luma.core.data.db.entity.ListeningEventEntity(trackId = ids["Heard"]!!, startedAt = 1L))

        repo.unfollow(id)

        assertThat(db.playlistDao().getById(id)).isNull()
        assertThat(db.trackDao().getById(ids["Loose"]!!)).isNull()
        for (kept in listOf("Liked", "Other", "Downloaded", "Heard")) assertThat(db.trackDao().getById(ids[kept]!!)).isNotNull()
    }


}
