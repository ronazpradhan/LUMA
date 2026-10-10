package com.stash.feature.settings.di

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.stash.feature.settings.equalizer.LoudnessFirstRunStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// File-scope extension for the LoudnessFirstRunStore DataStore. Per-concern
// naming so the first-run-Snackbar flag never collides with other features'
// Preferences, and so a corrupt write during process death only resets the
// notice-shown flag (worst case: the Snackbar re-appears once).
private val Context.loudnessFirstRunDataStore by preferencesDataStore(
    name = "loudness_first_run_v1",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Hilt module providing settings-scoped stores. Mirrors the per-concern
 * DataStore pattern used by `MediaModule.provideEqStore` — each store gets
 * its own [Context.preferencesDataStore] delegate so a corrupt file in one
 * feature can't take down another.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideLoudnessFirstRunStore(
        @ApplicationContext context: Context,
    ): LoudnessFirstRunStore = LoudnessFirstRunStore(context.loudnessFirstRunDataStore)
}
