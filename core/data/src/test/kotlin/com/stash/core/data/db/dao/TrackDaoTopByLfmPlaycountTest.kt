package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.db.StashDatabase
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
class TrackDaoTopByLfmPlaycountTest {

    private lateinit var db: StashDatabase
    private lateinit var dao: TrackDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = db.trackDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun `returns top downloaded tracks by lastfm playcount descending`() = runTest {
        dao.insert(track(id = 1L, artist = "A", title = "Top", lfmCount = 1000))
        dao.insert(track(id = 2L, artist = "B", title = "Mid", lfmCount = 100))
        dao.insert(track(id = 3L, artist = "C", title = "Low", lfmCount = 10))

        val result = dao.getTopTracksByLfmPlaycount(limit = 10)

        assertEquals(
            listOf(
                TrackArtistTitle("A", "Top"),
                TrackArtistTitle("B", "Mid"),
                TrackArtistTitle("C", "Low"),
            ),
            result,
        )
    }

    @Test fun `excludes tracks with null or zero lastfm playcount`() = runTest {
        dao.insert(track(id = 1L, artist = "A", title = "Counted", lfmCount = 5))
        dao.insert(track(id = 2L, artist = "B", title = "Zero", lfmCount = 0))
        dao.insert(track(id = 3L, artist = "C", title = "Null", lfmCount = null))

        val result = dao.getTopTracksByLfmPlaycount(limit = 10)

        assertEquals(listOf(TrackArtistTitle("A", "Counted")), result)
    }

    @Test fun `excludes non-downloaded tracks`() = runTest {
        dao.insert(track(id = 1L, artist = "A", title = "Downloaded", lfmCount = 100, isDownloaded = true))
        dao.insert(track(id = 2L, artist = "B", title = "Stub", lfmCount = 200, isDownloaded = false))

        val result = dao.getTopTracksByLfmPlaycount(limit = 10)

        assertEquals(listOf(TrackArtistTitle("A", "Downloaded")), result)
    }

    @Test fun `respects limit`() = runTest {
        for (i in 1..5) dao.insert(track(id = i.toLong(), artist = "A$i", title = "T$i", lfmCount = i * 10))

        val result = dao.getTopTracksByLfmPlaycount(limit = 3)

        assertEquals(3, result.size)
        // Highest 3 (lfmCount 50, 40, 30) — order desc
        assertEquals(listOf("A5", "A4", "A3"), result.map { it.artist })
    }

    @Test fun `empty library returns empty`() = runTest {
        val result = dao.getTopTracksByLfmPlaycount(limit = 10)
        assertTrue(result.isEmpty())
    }

    private fun track(
        id: Long,
        artist: String,
        title: String,
        lfmCount: Int?,
        isDownloaded: Boolean = true,
    ) = TrackEntity(
        id = id,
        title = title,
        artist = artist,
        canonicalTitle = title.lowercase(),
        canonicalArtist = artist.lowercase(),
        isDownloaded = isDownloaded,
        lastfmUserPlaycount = lfmCount,
    )
}
