package com.stash.core.data.di

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.stash.core.data.audio.LoudnessProgressStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// File-scope extension for the LoudnessProgressStore DataStore. Per-concern
// naming so the backfill progress counters never collide with other features'
// Preferences, and so a corrupt write during process death only resets the
// loudness backfill progress — not unrelated state.
private val Context.loudnessProgressDataStore by preferencesDataStore(
    name = "loudness_progress_v1",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Hilt module providing audio-pipeline-related stores defined in `:core:data`.
 *
 * Mirrors the per-concern DataStore pattern used by [MediaModule.provideEqStore]
 * — each store gets its own [Context.preferencesDataStore] delegate so a
 * corrupt file in one feature can't take down another.
 */
@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

    @Provides
    @Singleton
    fun provideLoudnessProgressStore(
        @ApplicationContext context: Context,
    ): LoudnessProgressStore = LoudnessProgressStore(context.loudnessProgressDataStore)
}
