package com.stash.data.download.backfill

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auto-enqueues [MetadataBackfillWorker] exactly once per `versionCode`.
 *
 * Called from [com.stash.app.StashApplication.onCreate] on a background
 * coroutine. Idempotent: if the version has already been enqueued, the
 * gate short-circuits before WorkManager is touched.
 *
 * ## WorkManager DI shape
 * WorkManager is not Hilt-injectable in this project — every existing
 * usage in `StashApplication` resolves it from `Context` via
 * `WorkManager.getInstance(...)`. We follow that pattern here by
 * injecting [Context] and resolving the singleton on demand.
 *
 * ## Crash-resilience ordering
 * The check/enqueue/mark sequence is intentional:
 *  1. `shouldRunForCurrentVersion()` returns true on the first launch
 *     after each version bump.
 *  2. `enqueueUniqueWork(KEEP)` is idempotent — if a previous launch
 *     already enqueued and the process died before marking, this is a
 *     no-op (and the marked-as-enqueued state is set immediately after).
 *  3. `markEnqueuedForCurrentVersion()` flips the gate so subsequent
 *     launches at the same version skip the enqueue path.
 *
 * If the process dies between step 1 and step 2, the next launch retries
 * cleanly. If it dies between step 2 and step 3, the next launch's
 * enqueue is absorbed by [ExistingWorkPolicy.KEEP].
 *
 * ## Constraints
 * Only [NetworkType.CONNECTED] is required — `ffmpeg -c copy` is a
 * mux-only operation (no transcode), so charging isn't necessary even
 * for large libraries. Expedited with non-expedited fallback so the
 * worker starts immediately when WorkManager's foreground-quota allows,
 * but doesn't block on it.
 */
@Singleton
class MetadataBackfillScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val versionTracker: BackfillVersionTracker,
) {
    suspend fun scheduleIfNeeded() {
        if (!versionTracker.shouldRunForCurrentVersion(METADATA_BACKFILL_KEY)) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            MetadataBackfillWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<MetadataBackfillWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build(),
        )
        versionTracker.markEnqueuedForCurrentVersion(METADATA_BACKFILL_KEY)
    }

    private companion object {
        /**
         * Stable preference-key string for the metadata-embedding backfill.
         *
         * MUST remain `"backfill_enqueued_for_version"` — this is the exact
         * key v0.9.35 wrote when it first shipped. Renaming it would cause
         * every existing user who already ran the metadata backfill to
         * re-trigger it on first launch of v0.9.36.
         */
        const val METADATA_BACKFILL_KEY = "backfill_enqueued_for_version"
    }
}
