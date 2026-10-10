package com.stash.feature.settings.equalizer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists whether the one-time "loudness normalization is on" first-run
 * Snackbar has been shown.
 *
 * Loudness is on by default, but the user has no way to know that without a
 * visible nudge. We show the Snackbar exactly once — the first time the
 * EqualizerScreen is opened — and persist a single boolean flag here so the
 * notice never re-appears after dismissal (or after the user first toggles
 * the loudness switch, which counts as having seen and acknowledged the
 * feature).
 *
 * Keys are namespaced with the `loudness_first_run_` prefix so this store
 * can safely share the app-wide Preferences DataStore with other features.
 */
@Singleton
class LoudnessFirstRunStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val noticeShownFlow: Flow<Boolean> = dataStore.data.map { it[KEY] ?: false }

    suspend fun markShown() {
        dataStore.edit { it[KEY] = true }
    }

    private companion object {
        val KEY = booleanPreferencesKey("loudness_first_run_notice_shown")
    }
}
