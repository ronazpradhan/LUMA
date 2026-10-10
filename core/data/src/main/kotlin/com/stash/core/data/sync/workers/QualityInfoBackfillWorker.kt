package com.stash.core.data.sync.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stash.core.data.audio.AudioDurationExtractor
import com.stash.core.data.db.dao.TrackDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * One-shot batch worker that fills in `bits_per_sample` + `sample_rate_hz`
 * for downloaded lossless tracks where those columns are still NULL.
 *
 * Triggered automatically once on first launch of v0.9.11 (gated by a
 * SharedPreferences flag in StashApplication.onCreate) and manually from
 * the Settings → Library Health → "Refresh quality info" row.
 *
 * Processes up to [BATCH_LIMIT] rows per run; if more remain it
 * re-enqueues itself so the user's library drains over a few background
 * runs rather than one long-blocking job. The DAO predicate
 * (`bits_per_sample IS NULL OR sample_rate_hz IS NULL`) is idempotent —
 * re-runs after everything is populated cost a single empty SELECT.
 *
 * Skipped silently when the file is missing on disk (legacy storage
 * relocations) or when MediaExtractor + STREAMINFO both fail to
 * produce values (corrupt or unsupported files). Such rows simply stay
 * NULL — the badge falls back to plain "FLAC" rendering.
 */
@HiltWorker
class QualityInfoBackfillWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val trackDao: TrackDao,
    private val audioExtractor: AudioDurationExtractor,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val candidates = trackDao.getMissingQualityInfo()
        if (candidates.isEmpty()) {
            Log.i(TAG, "QualityInfoBackfill: nothing to do")
            return Result.success()
        }
        Log.i(TAG, "QualityInfoBackfill: ${candidates.size} tracks to probe")
        var updated = 0
        for (track in candidates) {
            val filePath = track.filePath ?: continue
            val meta = audioExtractor.extract(filePath) ?: continue
            if (meta.sampleRateHz != null || meta.bitsPerSample != null) {
                runCatching {
                    trackDao.updateQualityInfo(
                        trackId = track.id,
                        sampleRateHz = meta.sampleRateHz,
                        bitsPerSample = meta.bitsPerSample,
                    )
                    updated++
                }.onFailure { e ->
                    Log.w(TAG, "updateQualityInfo failed for trackId=${track.id}", e)
                }
            }
        }
        Log.i(TAG, "QualityInfoBackfill complete: updated $updated/${candidates.size}")
        // Re-enqueue if we hit the batch limit — there are likely more
        // candidates remaining. The DAO predicate filters out anything
        // we just wrote, so the worker eventually drains to empty.
        if (candidates.size >= BATCH_LIMIT) {
            WorkManager.getInstance(applicationContext).enqueue(
                OneTimeWorkRequestBuilder<QualityInfoBackfillWorker>().build()
            )
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "QualityInfoBackfill"
        const val BATCH_LIMIT = 500
    }
}
