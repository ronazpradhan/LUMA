package com.luma.core.data.share

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.luma.core.data.db.LumaDatabase
import com.luma.core.data.db.entity.PlaylistEntity
import com.luma.core.data.db.entity.PlaylistTrackCrossRef
import com.luma.core.data.db.entity.SharedMixEntity
import com.luma.core.data.db.entity.TrackEntity
import com.luma.core.data.repository.MusicRepository
import com.luma.core.model.MusicSource
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
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
class SharedMixRepositoryOwnerTest {
    private lateinit var db: LumaDatabase
    private lateinit var server: MockWebServer
    private lateinit var repo: SharedMixRepository
    private var playlistId = 0L

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LumaDatabase::class.java)
            .allowMainThreadQueries().build()
        server = MockWebServer().also { it.start() }
        repo = SharedMixRepository(db, db.sharedMixDao(), db.playlistDao(), db.trackDao(), mockk<MusicRepository>(relaxed = true), ApplicationProvider.getApplicationContext())
        playlistId = db.playlistDao().insert(PlaylistEntity(name = "Ambient", source = MusicSource.BOTH, sourceId = "custom_1"))
        val t1 = db.trackDao().insert(TrackEntity(title = "One", artist = "A", isrc = "I1", albumArtUrl = "https://i.scdn.co/image/1", source = MusicSource.SPOTIFY))
        val t2 = db.trackDao().insert(TrackEntity(title = "Two", artist = "B", source = MusicSource.YOUTUBE, youtubeId = "y2", albumArtUrl = "https://evil.example/2.jpg"))
        db.playlistDao().insertCrossRef(PlaylistTrackCrossRef(playlistId, t1, position = 0))
        db.playlistDao().insertCrossRef(PlaylistTrackCrossRef(playlistId, t2, position = 1))
    }
    @After fun tearDown() { db.close(); server.shutdown() }

    @Test fun `buildDocument withArt keeps each song's allowed art link, and the default leaves art out`() = runBlocking {
        assertThat(repo.buildDocument(playlistId, "Ambient", null).tracks.map { it.artUrl }).containsExactly(null, null)
        assertThat(repo.buildDocument(playlistId, "Ambient", null, withArt = true).tracks.map { it.artUrl })
            .containsExactly("https://i.scdn.co/image/1", null).inOrder()
    }

    @Test fun `buildDocument cuts the shared-by name to 40 without splitting an emoji`() = runBlocking {
        assertThat(repo.buildDocument(playlistId, "Ambient", sharedBy = "n".repeat(39) + "🎧").sharedBy).isEqualTo("n".repeat(39))
    }

    @Test fun `a link carries only the playlist's own web cover, never song covers or a gallery photo`() = runBlocking {
        db.playlistDao().updateArtUrl(playlistId, "https://i.scdn.co/image/own")
        assertThat(repo.buildDocument(playlistId, "Ambient", null).covers).containsExactly("https://i.scdn.co/image/own")
        db.playlistDao().updateArtUrl(playlistId, "file:///data/user/0/com.luma.music/files/covers/7.jpg")
        assertThat(repo.buildDocument(playlistId, "Ambient", null).covers).isEmpty()
        db.playlistDao().updateArtUrl(playlistId, null)
        assertThat(repo.buildDocument(playlistId, "Ambient", null).covers).isEmpty()
        Unit
    }
}
