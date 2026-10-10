package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.DiscoveryQueueEntity
import com.stash.core.data.db.entity.StashMixRecipeEntity
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.model.MusicSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [DiscoveryQueueDao.deleteLibraryHitDoneRows] — the one-time
 * PR 7 cleanup that removes pre-PR-6 era "library-hit" discovery DONE
 * rows from `discovery_queue`. Pre-PR-6, StashDiscoveryWorker.handle()
 * canonical-deduped Last.fm candidates against the library and linked
 * existing library tracks instead of creating stubs — those rows now
 * surface as "discovery survivors" in mixes despite being library
 * content.
 *
 * Heuristic: discovery stubs have track.source = YOUTUBE; library
 * tracks have other sources (SPOTIFY, LOCAL, BOTH).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class DiscoveryQueueDaoLibraryHitCleanupTest {

    private lateinit var db: StashDatabase
    private lateinit var dao: DiscoveryQueueDao
    private lateinit var trackDao: TrackDao
    private var recipeId: Long = 0L

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = db.discoveryQueueDao()
        trackDao = db.trackDao()
        recipeId = db.stashMixRecipeDao().insert(
            StashMixRecipeEntity(name = "Test Recipe", isBuiltin = false)
        )
    }

    @After fun tearDown() { db.close() }

    @Test fun `deletes DONE rows whose track has non-YOUTUBE source`() = runTest {
        // Library-sourced tracks
        trackDao.insert(track(id = 1L, source = MusicSource.SPOTIFY))
        trackDao.insert(track(id = 2L, source = MusicSource.LOCAL))
        trackDao.insert(track(id = 3L, source = MusicSource.BOTH))
        // Discovery stub
        trackDao.insert(track(id = 4L, source = MusicSource.YOUTUBE))

        dao.insertIfNew(doneRow(trackId = 1L))
        dao.insertIfNew(doneRow(trackId = 2L))
        dao.insertIfNew(doneRow(trackId = 3L))
        dao.insertIfNew(doneRow(trackId = 4L))

        val deleted = dao.deleteLibraryHitDoneRows()

        assertEquals(3, deleted)
        val remaining = dao.getDoneTrackIdsForRecipe(recipeId, limit = 99)
        assertEquals(listOf(4L), remaining)
    }

    @Test fun `does not delete PENDING or FAILED rows even if track is library-sourced`() = runTest {
        trackDao.insert(track(id = 1L, source = MusicSource.SPOTIFY))

        dao.insertIfNew(pendingRow(trackId = 1L))

        val deleted = dao.deleteLibraryHitDoneRows()

        assertEquals(0, deleted)
    }

    @Test fun `is idempotent — second call deletes nothing`() = runTest {
        trackDao.insert(track(id = 1L, source = MusicSource.SPOTIFY))
        dao.insertIfNew(doneRow(trackId = 1L))

        assertEquals(1, dao.deleteLibraryHitDoneRows())
        assertEquals(0, dao.deleteLibraryHitDoneRows())
    }

    @Test fun `preserves YOUTUBE-sourced DONE rows`() = runTest {
        trackDao.insert(track(id = 1L, source = MusicSource.YOUTUBE))
        trackDao.insert(track(id = 2L, source = MusicSource.YOUTUBE))
        dao.insertIfNew(doneRow(trackId = 1L))
        dao.insertIfNew(doneRow(trackId = 2L))

        val deleted = dao.deleteLibraryHitDoneRows()

        assertEquals(0, deleted)
        val remaining = dao.getDoneTrackIdsForRecipe(recipeId, limit = 99).toSet()
        assertEquals(setOf(1L, 2L), remaining)
    }

    @Test fun `empty discovery_queue returns 0`() = runTest {
        val deleted = dao.deleteLibraryHitDoneRows()
        assertTrue("expected 0, got $deleted", deleted == 0)
    }

    private fun track(id: Long, source: MusicSource) = TrackEntity(
        id = id,
        title = "Title $id",
        artist = "Artist $id",
        canonicalTitle = "title $id",
        canonicalArtist = "artist $id",
        isDownloaded = true,
        source = source,
    )

    private fun doneRow(trackId: Long) = DiscoveryQueueEntity(
        recipeId = recipeId,
        artist = "Artist",
        title = "Title $trackId",
        seedArtist = "Seed",
        status = DiscoveryQueueEntity.STATUS_DONE,
        trackId = trackId,
        queuedAt = 0L,
        completedAt = 1000L,
        errorMessage = null,
    )

    private fun pendingRow(trackId: Long) = DiscoveryQueueEntity(
        recipeId = recipeId,
        artist = "Artist",
        title = "Title $trackId",
        seedArtist = "Seed",
        status = DiscoveryQueueEntity.STATUS_PENDING,
        trackId = trackId,
        queuedAt = 0L,
        completedAt = null,
        errorMessage = null,
    )
}
