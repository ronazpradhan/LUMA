package com.stash.data.download.backfill

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.stash.core.common.AppVersionProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Once-per-version enqueue gate for backfill workers, keyed by caller.
 *
 * Persists the highest `versionCode` for which a backfill identified by
 * [key] has been enqueued. [shouldRunForCurrentVersion] returns `true`
 * exactly once per new `versionCode` per key:
 *  - First install / first launch of any version → returns `true`.
 *  - After [markEnqueuedForCurrentVersion] at the same version →
 *    returns `false`, so re-installing the same binary doesn't re-fire
 *    the worker.
 *  - After a version bump (new `versionCode` > stored value) → returns
 *    `true` again, giving us a clean upgrade lever for any future
 *    re-tagging pass.
 *
 * The [key] parameter lets multiple independent backfills share the
 * single `metadata_backfill_state.preferences_pb` DataStore file under
 * disjoint preference keys (e.g. metadata backfill at
 * `"backfill_enqueued_for_version"`, lyrics backfill at
 * `"lyrics_backfill_enqueued_for_version"`). Callers MUST keep their
 * key constants stable across releases — changing a key string would
 * re-fire that backfill on every existing install.
 *
 * Shares the `metadata_backfill_state.preferences_pb` DataStore file
 * with [MetadataBackfillState] (disjoint keys); all classes resolve to
 * the same `DataStore<Preferences>` instance through the
 * module-internal [backfillDataStore] extension delegate, which keeps
 * DataStore's per-file singleton invariant intact.
 */
@Singleton
class BackfillVersionTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appVersion: AppVersionProvider,
) {
    private val store: DataStore<Preferences> get() = context.backfillDataStore

    suspend fun shouldRunForCurrentVersion(key: String): Boolean {
        val stored = store.data.first()[intPreferencesKey(key)] ?: -1
        return stored < appVersion.versionCode
    }

    suspend fun markEnqueuedForCurrentVersion(key: String) {
        store.edit { it[intPreferencesKey(key)] = appVersion.versionCode }
    }
}
