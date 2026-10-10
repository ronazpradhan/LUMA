package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.ListeningEventEntity
import com.stash.core.data.db.entity.TrackEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ListeningEventDaoTopTracksTest {

    private lateinit var db: StashDatabase
    private lateinit var dao: ListeningEventDao
    private lateinit var trackDao: TrackDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = db.listeningEventDao()
        trackDao = db.trackDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun `returns top tracks by play count descending within window`() = runTest {
        trackDao.insert(track(id = 1L, artist = "Tame Impala", title = "Borderline"))
        trackDao.insert(track(id = 2L, artist = "Tame Impala", title = "The Less I Know"))
        trackDao.insert(track(id = 3L, artist = "Kanye West", title = "Runaway"))

        val now = System.currentTimeMillis()
        // Track 1: 5 plays
        repeat(5) { dao.insert(listeningEvent(trackId = 1L, startedAt = now - it * 1000L)) }
        // Track 2: 2 plays
        repeat(2) { dao.insert(listeningEvent(trackId = 2L, startedAt = now - it * 1000L)) }
        // Track 3: 1 play
        dao.insert(listeningEvent(trackId = 3L, startedAt = now))

        val result = dao.getTopTracksByLocalPlays(sinceEpochMs = 0L, limit = 10)

        assertEquals(
            listOf(
                TrackArtistTitle("Tame Impala", "Borderline"),
                TrackArtistTitle("Tame Impala", "The Less I Know"),
                TrackArtistTitle("Kanye West", "Runaway"),
            ),
            result,
        )
    }

    @Test fun `excludes plays outside the time window`() = runTest {
        trackDao.insert(track(id = 1L, artist = "Old Artist", title = "Old Song"))

        val now = System.currentTimeMillis()
        val tooOld = now - 365L * 24 * 60 * 60 * 1000
        repeat(10) { dao.insert(listeningEvent(trackId = 1L, startedAt = tooOld)) }

        val result = dao.getTopTracksByLocalPlays(
            sinceEpochMs = now - 30L * 24 * 60 * 60 * 1000,  // last 30 days
            limit = 10,
        )

        assertTrue("expected empty, got $result", result.isEmpty())
    }

    @Test fun `respects limit`() = runTest {
        for (i in 1..5) {
            trackDao.insert(track(id = i.toLong(), artist = "Artist $i", title = "Title $i"))
            dao.insert(listeningEvent(trackId = i.toLong(), startedAt = System.currentTimeMillis()))
        }

        val result = dao.getTopTracksByLocalPlays(sinceEpochMs = 0L, limit = 3)

        assertEquals(3, result.size)
    }

    @Test fun `empty listening events returns empty`() = runTest {
        val result = dao.getTopTracksByLocalPlays(sinceEpochMs = 0L, limit = 10)
        assertTrue(result.isEmpty())
    }

    private fun track(id: Long, artist: String, title: String) = TrackEntity(
        id = id,
        title = title,
        artist = artist,
        canonicalTitle = title.lowercase(),
        canonicalArtist = artist.lowercase(),
        isDownloaded = true,
    )

    private fun listeningEvent(trackId: Long, startedAt: Long) = ListeningEventEntity(
        trackId = trackId,
        startedAt = startedAt,
    )
}
