package com.stash.core.data.sync.workers

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.stash.core.data.audio.LoudnessMeasurer
import com.stash.core.data.audio.LoudnessProgressStore
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.entity.TrackEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * MockK tests for [LoudnessBackfillWorker] — the periodic worker that
 * drains rows where `loudness_measured_at IS NULL` in batches of 20.
 *
 * Mirrors [DiscoveryDownloadWorkerTest]'s constructor-injection +
 * mocked WorkerParameters pattern. The `isStopped` flag is package-private
 * on CoroutineWorker so we use a thin subclass override to make it
 * controllable in the cancellation test.
 */
class LoudnessBackfillWorkerTest {

    private val appContext: Context = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val measurer: LoudnessMeasurer = mockk()
    private val progressStore: LoudnessProgressStore = mockk(relaxed = true)

    // Track-scoped temp files so each test gets a real on-disk path the
    // worker's File.exists() check will accept. Cleaned up in @After.
    private val tempFiles = mutableListOf<File>()

    @After fun cleanup() {
        tempFiles.forEach { runCatching { it.delete() } }
        tempFiles.clear()
    }

    private fun tempAudioFile(): File {
        val f = Files.createTempFile("loudness-test-", ".flac").toFile()
        tempFiles += f
        return f
    }

    private fun stubTrack(id: Long, filePath: String?) = TrackEntity(
        id = id,
        title = "Track $id",
        artist = "Artist $id",
        filePath = filePath,
    )

    private fun newWorker(stoppedAfter: Int = Int.MAX_VALUE) =
        TestableLoudnessBackfillWorker(
            appContext, workerParams,
            trackDao, measurer, progressStore,
            stoppedAfter,
        )

    @Test fun `empty queue returns success with KEY_DONE true and zeroes remaining`() = runTest {
        coEvery { trackDao.tracksNeedingLoudness(any()) } returns emptyList()

        val result = newWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val done = (result as ListenableWorker.Result.Success).outputData
            .getBoolean(LoudnessBackfillWorker.KEY_DONE, false)
        assertTrue("expected KEY_DONE=true on empty queue", done)
        coVerify(exactly = 1) { progressStore.setRemaining(0) }
        coVerify(exactly = 0) { measurer.measure(any()) }
        coVerify(exactly = 0) { trackDao.updateLoudness(any(), any(), any(), any()) }
    }

    @Test fun `batch of three all-success writes three updateLoudness rows and records progress`() = runTest {
        val tracks = (1L..3L).map { stubTrack(it, tempAudioFile().absolutePath) }
        coEvery { trackDao.tracksNeedingLoudness(any()) } returns tracks
        coEvery { measurer.measure(any()) } returns LoudnessMeasurer.Result.Success(
            lufs = -12.5f, truePeakDbfs = -0.8f,
        )

        val result = newWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 3) {
            trackDao.updateLoudness(
                id = any(),
                lufs = -12.5f,
                peak = -0.8f,
                now = any(),
            )
        }
        coVerify(exactly = 0) { trackDao.markLoudnessFailed(any(), any()) }
        coVerify(exactly = 1) {
            progressStore.recordBatchComplete(completed = 3, at = any())
        }
    }

    @Test fun `failed measurement falls through to markLoudnessFailed without aborting batch`() = runTest {
        val ok1 = stubTrack(1L, tempAudioFile().absolutePath)
        val bad = stubTrack(2L, tempAudioFile().absolutePath)
        val ok2 = stubTrack(3L, tempAudioFile().absolutePath)
        coEvery { trackDao.tracksNeedingLoudness(any()) } returns listOf(ok1, bad, ok2)
        // Order-sensitive responses — measurer.measure() in the order tracks are iterated.
        coEvery { measurer.measure(File(ok1.filePath!!)) } returns LoudnessMeasurer.Result.Success(-14f, -1f)
        coEvery { measurer.measure(File(bad.filePath!!)) } returns LoudnessMeasurer.Result.Failed("bad")
        coEvery { measurer.measure(File(ok2.filePath!!)) } returns LoudnessMeasurer.Result.Success(-10f, -2f)

        newWorker().doWork()

        coVerify(exactly = 2) {
            trackDao.updateLoudness(id = any(), lufs = any(), peak = any(), now = any())
        }
        coVerify(exactly = 1) { trackDao.markLoudnessFailed(id = 2L, now = any()) }
        coVerify(exactly = 1) {
            progressStore.recordBatchComplete(completed = 3, at = any())
        }
    }

    @Test fun `isStopped after first track halts the batch with only one update written`() = runTest {
        val t1 = stubTrack(1L, tempAudioFile().absolutePath)
        val t2 = stubTrack(2L, tempAudioFile().absolutePath)
        val t3 = stubTrack(3L, tempAudioFile().absolutePath)
        coEvery { trackDao.tracksNeedingLoudness(any()) } returns listOf(t1, t2, t3)
        coEvery { measurer.measure(any()) } returns LoudnessMeasurer.Result.Success(-14f, -1f)

        // stoppedAfter=1 → isStopped flips to true after the first track's
        // measure() completes, so the loop break fires before track 2.
        newWorker(stoppedAfter = 1).doWork()

        coVerify(exactly = 1) {
            trackDao.updateLoudness(id = any(), lufs = any(), peak = any(), now = any())
        }
        coVerify(exactly = 0) { trackDao.markLoudnessFailed(any(), any()) }
        // Mid-batch cancellation still records what completed so the
        // progress card stays in sync — but only counts the one that
        // actually ran.
        coVerify(exactly = 1) {
            progressStore.recordBatchComplete(completed = 1, at = any())
        }
    }

    /**
     * Thin subclass that lets the test flip the cancellation predicate
     * deterministically after a given number of measurements. The real
     * `isStopped` is a `final` accessor on [androidx.work.ListenableWorker]
     * set by WorkManager when it cancels the worker — there's no public
     * setter and no override hook, so the production worker routes its
     * cancellation check through [LoudnessBackfillWorker.shouldStop],
     * which we override here.
     */
    private class TestableLoudnessBackfillWorker(
        ctx: Context,
        params: WorkerParameters,
        trackDao: TrackDao,
        measurer: LoudnessMeasurer,
        progressStore: LoudnessProgressStore,
        private val stoppedAfter: Int,
    ) : LoudnessBackfillWorker(ctx, params, trackDao, measurer, progressStore) {
        private var measurementsObserved = 0

        // Counter is bumped from the worker via this hook so the subclass
        // can flip the simulated stopped flag between tracks. The base
        // worker calls onTrackProcessed() at the bottom of each iteration.
        override fun onTrackProcessed() {
            measurementsObserved++
        }

        override fun shouldStop(): Boolean = measurementsObserved >= stoppedAfter
    }
}
