package com.luma.core.data.di

import com.luma.core.data.audio.FFmpegBridge
import com.luma.core.data.audio.FFmpegBridgeImpl
import com.luma.core.data.prefs.AutoplayRadioPreference
import com.luma.core.data.prefs.AutoplayRadioPreferencesManager
import com.luma.core.data.prefs.CrossfadePreference
import com.luma.core.data.prefs.CrossfadePreferencesManager
import com.luma.core.data.prefs.StoragePreference
import com.luma.core.data.prefs.StoragePreferencesManager
import com.luma.core.data.prefs.ThemePreference
import com.luma.core.data.prefs.ThemePreferencesManager
import com.luma.core.data.repository.MusicRepository
import com.luma.core.data.repository.MusicRepositoryImpl
import com.luma.core.data.youtube.OkHttpPingSubmitter
import com.luma.core.data.youtube.PingSubmitter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds abstractions defined in `:core:data` to their
 * concrete implementations in the same module.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMusicRepository(impl: MusicRepositoryImpl): MusicRepository

    @Binds
    @Singleton
    abstract fun bindThemePreference(impl: ThemePreferencesManager): ThemePreference

    @Binds
    @Singleton
    abstract fun bindStoragePreference(impl: StoragePreferencesManager): StoragePreference

    @Binds
    @Singleton
    abstract fun bindCrossfadePreference(impl: CrossfadePreferencesManager): CrossfadePreference

    @Binds
    @Singleton
    abstract fun bindAutoplayRadioPreference(impl: AutoplayRadioPreferencesManager): AutoplayRadioPreference

    @Binds
    @Singleton
    abstract fun bindPingSubmitter(impl: OkHttpPingSubmitter): PingSubmitter

    @Binds
    @Singleton
    abstract fun bindFFmpegBridge(impl: FFmpegBridgeImpl): FFmpegBridge
}
