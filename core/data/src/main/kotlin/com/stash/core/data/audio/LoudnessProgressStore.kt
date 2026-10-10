package com.stash.core.data.audio

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists loudness-backfill progress across process restarts.
 *
 * The backfill worker measures LUFS for tracks that don't yet have a stored
 * value. To keep the EqualizerScreen progress card accurate even after a
 * crash / cold start, the worker writes its remaining-work counter,
 * total-at-start, and last-completed-at timestamp here on every batch flush.
 *
 * Keys are namespaced with the `loudness_backfill_` prefix so this store can
 * safely share the app-wide Preferences DataStore with other features.
 *
 * Snapshot semantics:
 *  - `remaining` — tracks still needing measurement; clamped at 0
 *  - `total`     — tracks needing measurement when the current backfill run began
 *  - `lastCompletedAt` — wall-clock millis of the most recent batch flush, 0 if never run
 */
@Singleton
class LoudnessProgressStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    data class Snapshot(
        val remaining: Int = 0,
        val total: Int = 0,
        val lastCompletedAt: Long = 0L,
    )

    val flow: Flow<Snapshot> = dataStore.data.map { p ->
        Snapshot(
            remaining = p[KEY_REMAINING] ?: 0,
            total = p[KEY_TOTAL] ?: 0,
            lastCompletedAt = p[KEY_LAST] ?: 0L,
        )
    }

    suspend fun setTotal(total: Int) {
        dataStore.edit { it[KEY_TOTAL] = total }
    }

    suspend fun setRemaining(remaining: Int) {
        dataStore.edit { it[KEY_REMAINING] = remaining }
    }

    /**
     * Atomically subtract [completed] from the remaining counter (clamped at 0)
     * and stamp the last-completed-at timestamp.
     */
    suspend fun recordBatchComplete(completed: Int, at: Long) {
        dataStore.edit { p ->
            val rem = (p[KEY_REMAINING] ?: 0) - completed
            p[KEY_REMAINING] = rem.coerceAtLeast(0)
            p[KEY_LAST] = at
        }
    }

    private companion object {
        val KEY_REMAINING = intPreferencesKey("loudness_backfill_remaining")
        val KEY_TOTAL = intPreferencesKey("loudness_backfill_total")
        val KEY_LAST = longPreferencesKey("loudness_backfill_last_completed_at")
    }
}
