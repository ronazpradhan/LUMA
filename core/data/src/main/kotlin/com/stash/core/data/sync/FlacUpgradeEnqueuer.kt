package com.stash.core.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.stash.core.data.db.dao.FlacUpgradeQueueDao
import com.stash.core.data.sync.workers.FlacUpgradeWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Seeds the flac_upgrade_queue and enqueues the unique batch worker.
 * KEEP policy: a confirm while a batch is live replaces the row set
 * (startBatch is clear-and-insert) without spawning a second worker.
 * Wi-Fi behavior follows the sync wifiOnly preference (spec §3).
 */
@Singleton
class FlacUpgradeEnqueuer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queueDao: FlacUpgradeQueueDao,
    private val syncPreferencesManager: SyncPreferencesManager,
) {
    suspend fun startBatch(trackIds: List<Long>) {
        if (trackIds.isEmpty()) return
        queueDao.startBatch(trackIds)
        val wifiOnly = syncPreferencesManager.preferences.first().wifiOnly
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<FlacUpgradeWorker>()
            .setConstraints(constraints)
            .addTag("flac_upgrade")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            FlacUpgradeWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
