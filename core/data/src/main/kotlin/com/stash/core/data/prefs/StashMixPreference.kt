package com.stash.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Dedicated DataStore for the Stash Mix opt-out toggle (issues #56, #57). */
private val Context.stashMixDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "stash_mix_preference",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * User opt-out for the Stash Mix (beta) feature. When `false`:
 *
 *  - [com.stash.core.data.sync.workers.StashMixRefreshWorker],
 *    [com.stash.core.data.sync.workers.StashDiscoveryWorker],
 *    [com.stash.core.data.sync.workers.DiscoveryDownloadWorker],
 *    [com.stash.core.data.sync.workers.TagEnrichmentWorker], and
 *    [com.stash.core.data.sync.workers.TrackInfoEnrichmentWorker] do not
 *    get scheduled at app start.
 *  - The three built-in mix playlists (Daily Discover, Deep Cuts, First
 *    Listen) are marked `is_active = 0` so they disappear from Home and
 *    Library without being hard-deleted (re-enabling the toggle restores
 *    them with their existing track lists).
 *  - Previously-downloaded discovery tracks remain on disk. The user can
 *    delete them manually via the existing Library UI if they want to
 *    reclaim space.
 *
 * Default is `true` — preserves current behavior for the install base
 * that wants the feature. The toggle is an explicit opt-out for users
 * who don't want auto-generated downloads.
 */
@Singleton
class StashMixPreference @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val enabledKey = booleanPreferencesKey("stash_mixes_enabled")

    val enabled: Flow<Boolean> = context.stashMixDataStore.data.map { prefs ->
        prefs[enabledKey] ?: true
    }

    suspend fun current(): Boolean = enabled.first()

    suspend fun setEnabled(value: Boolean) {
        context.stashMixDataStore.edit { it[enabledKey] = value }
    }
}
