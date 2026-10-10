package com.stash.data.download.backfill

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore for the metadata-backfill pipeline.
 *
 * Top-level extension — the property delegate enforces a single
 * [DataStore] instance per file path per process. Shared with
 * [BackfillVersionTracker] (Task 10); both classes resolve to the same
 * `DataStore<Preferences>` via this delegate, which is what lets them
 * coexist on `metadata_backfill_state.preferences_pb` without tripping
 * DataStore's "multiple DataStores active for the same file" guard.
 *
 * `internal` so [BackfillVersionTracker] (same module) can import the
 * delegate and resolve the same singleton; outside the module, callers
 * interact via the [MetadataBackfillState] / [BackfillVersionTracker]
 * APIs rather than touching the DataStore directly.
 */
internal val Context.backfillDataStore by preferencesDataStore(name = "metadata_backfill_state")

/**
 * Persists the observable state of the metadata-backfill worker so the
 * Home re-tagging banner (Task 13) can render progress without polling
 * the worker directly.
 *
 *  - [State.IDLE] — no backfill in flight, banner hidden.
 *  - [State.RUNNING] — worker is iterating; banner shows progress.
 *  - [State.FINISHED] — worker drained the queue; banner shows the
 *    completion summary until the user dismisses it
 *    ([markFinishedAcknowledged]).
 *
 * [BackfillSnapshot.safSkipped] counts content:// rows the worker
 * couldn't operate on in place (typically external-storage downloads on
 * Android 11+); it's surfaced in the completion summary so users know
 * why the "done" count is lower than the "total" they saw mid-run.
 */
@Singleton
class MetadataBackfillState @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store: DataStore<Preferences> get() = context.backfillDataStore

    val snapshot: Flow<BackfillSnapshot> = store.data.map { prefs ->
        BackfillSnapshot(
            state = State.valueOf(prefs[KEY_STATE] ?: State.IDLE.name),
            processed = prefs[KEY_PROCESSED] ?: 0,
            total = prefs[KEY_TOTAL] ?: 0,
            safSkipped = prefs[KEY_SAF_SKIPPED] ?: 0,
            finishedAt = prefs[KEY_FINISHED_AT],
        )
    }

    suspend fun markStarted(total: Int) {
        store.edit {
            it[KEY_STATE] = State.RUNNING.name
            it[KEY_TOTAL] = total
            it[KEY_PROCESSED] = 0
            it[KEY_SAF_SKIPPED] = 0
            it.remove(KEY_FINISHED_AT)
        }
    }

    suspend fun publishProgress(processed: Int, total: Int) {
        store.edit {
            it[KEY_PROCESSED] = processed
            it[KEY_TOTAL] = total
        }
    }

    suspend fun incrementSafSkipped() {
        store.edit {
            it[KEY_SAF_SKIPPED] = (it[KEY_SAF_SKIPPED] ?: 0) + 1
        }
    }

    suspend fun markFinished() {
        store.edit {
            it[KEY_STATE] = State.FINISHED.name
            it[KEY_FINISHED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun markFinishedAcknowledged() {
        store.edit {
            it[KEY_STATE] = State.IDLE.name
        }
    }

    enum class State { IDLE, RUNNING, FINISHED }

    data class BackfillSnapshot(
        val state: State,
        val processed: Int,
        val total: Int,
        val safSkipped: Int,
        val finishedAt: Long?,
    )

    companion object {
        private val KEY_STATE = stringPreferencesKey("state")
        private val KEY_PROCESSED = intPreferencesKey("processed")
        private val KEY_TOTAL = intPreferencesKey("total")
        private val KEY_SAF_SKIPPED = intPreferencesKey("saf_skipped")
        private val KEY_FINISHED_AT = longPreferencesKey("finished_at")
    }
}
