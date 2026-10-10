package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.DownloadQueueEntity
import com.stash.core.data.db.entity.SyncHistoryEntity
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.model.DownloadStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v0.9.17: Room-backed tests for the deferred-set queries added to
 * [DownloadQueueDao] for the FLAC-only mode "lossless not yet available"
 * holding state ([DownloadStatus.WAITING_FOR_LOSSLESS]).
 *
 * Verifies:
 *  - [DownloadQueueDao.waitingForLosslessCount] reactively counts deferred rows.
 *  - [DownloadQueueDao.requeueWaitingForLossless] flips every deferred row to
 *    PENDING and leaves other statuses alone.
 *  - [DownloadQueueDao.deleteOrphanedQueueEntries] now also evicts deferred
 *    rows whose track has no sync-enabled playlist parent (extension of the
 *    existing PENDING/FAILED orphan sweep).
 */
@RunWith(RobolectricTestRunner::class)
class DownloadQueueDaoDeferredTest {

    private lateinit var db: StashDatabase
    private lateinit var dao: DownloadQueueDao

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.downloadQueueDao()
        // Seed parent tracks for FK satisfaction. Three is enough — the
        // tests reuse trackIds 1..3 across scenarios.
        val trackDao = db.trackDao()
        trackDao.insert(track(id = 1L))
        trackDao.insert(track(id = 2L))
        trackDao.insert(track(id = 3L))
        // These fixtures are SYNC-created rows (sync_id set). The orphan sweeps
        // only touch that partition — a manual sync_id NULL row is a download the
        // user explicitly asked for and is spared. These tests are about status
        // coverage (WAITING_FOR_LOSSLESS joining the sweep), not partitions.
        db.syncHistoryDao().insert(SyncHistoryEntity(id = 1L))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `waitingForLosslessCount returns count of WAITING_FOR_LOSSLESS rows`() = runTest {
        dao.insert(entry(trackId = 1L, status = DownloadStatus.WAITING_FOR_LOSSLESS))
        dao.insert(entry(trackId = 2L, status = DownloadStatus.PENDING))
        dao.insert(entry(trackId = 3L, status = DownloadStatus.WAITING_FOR_LOSSLESS))
        assertEquals(2, dao.waitingForLosslessCount().first())
    }

    @Test
    fun `requeueWaitingForLossless flips all WAITING rows to PENDING`() = runTest {
        dao.insert(entry(trackId = 1L, status = DownloadStatus.WAITING_FOR_LOSSLESS))
        dao.insert(entry(trackId = 2L, status = DownloadStatus.WAITING_FOR_LOSSLESS))
        dao.insert(entry(trackId = 3L, status = DownloadStatus.COMPLETED))
        val flipped = dao.requeueWaitingForLossless()
        assertEquals(2, flipped)
        assertEquals(2, dao.getByStatus(DownloadStatus.PENDING).first().size)
        assertEquals(0, dao.waitingForLosslessCount().first())
    }

    @Test
    fun `deleteOrphanedQueueEntries also evicts WAITING_FOR_LOSSLESS orphans`() = runTest {
        // Track exists, but is in NO sync-enabled playlist → orphan.
        dao.insert(entry(trackId = 1L, status = DownloadStatus.WAITING_FOR_LOSSLESS))
        val deleted = dao.deleteOrphanedQueueEntries()
        assertEquals(1, deleted)
        assertEquals(0, dao.waitingForLosslessCount().first())
    }

    private fun entry(trackId: Long, status: DownloadStatus) = DownloadQueueEntity(
        trackId = trackId,
        syncId = 1L,
        status = status,
        searchQuery = "test query",
    )

    private fun track(id: Long) = TrackEntity(
        id = id,
        title = "Track $id",
        artist = "Artist $id",
        canonicalTitle = "track $id",
        canonicalArtist = "artist $id",
    )
}
