package com.stash.app.di

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.stash.data.download.lyrics.LyricsFetchTrigger
import com.stash.data.lyrics.worker.LyricsFetchWorker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the production [LyricsFetchTrigger] used by the download
 * pipeline to a [LyricsFetchWorker] enqueue. Lives in `:app` (rather
 * than in `:data:download` or `:data:lyrics`) because it's the first
 * module that depends on both — `:data:download` doesn't see
 * [LyricsFetchWorker] (cyclic with `:data:lyrics`), and `:data:lyrics`
 * doesn't expose the [LyricsFetchTrigger] interface.
 *
 * ## WorkManager
 * No `@Provides WorkManager` exists in this project's Hilt graph — every
 * call site uses `WorkManager.getInstance(context)`. We follow that same
 * pattern here so this module doesn't drag in a one-off binding the rest
 * of the codebase would have to mirror.
 *
 * ## Unique-work semantics
 * `ExistingWorkPolicy.KEEP` + the
 * `LyricsFetchWorker.UNIQUE_PREFIX_POST_DOWNLOAD + trackId` key collapses
 * duplicate enqueues for the same track. A re-download won't kick off a
 * second fetch while the first is still pending; the priority on-open
 * path (`UNIQUE_PREFIX_PRIORITY` + REPLACE, wired in Task 12) targets a
 * disjoint unique-work key so the two don't interfere.
 *
 * ## Constraints
 * Only [NetworkType.CONNECTED] — the lyrics sources are all HTTP, no
 * payload is heavy enough to warrant unmetered/charging. Non-expedited
 * because post-download is background polish; the priority-on-open path
 * is the one that uses expedited work.
 */
@Module
@InstallIn(SingletonComponent::class)
object LyricsFetchTriggerModule {

    @Provides
    @Singleton
    fun provideLyricsFetchTrigger(
        @ApplicationContext context: Context,
    ): LyricsFetchTrigger = object : LyricsFetchTrigger {
        override fun enqueueFor(trackId: Long) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${LyricsFetchWorker.UNIQUE_PREFIX_POST_DOWNLOAD}$trackId",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<LyricsFetchWorker>()
                    .setInputData(workDataOf(LyricsFetchWorker.KEY_TRACK_ID to trackId))
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .build(),
            )
        }
    }
}
