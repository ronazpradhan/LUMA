package com.luma.data.download.di

import com.luma.core.data.prefs.QualityPreference
import com.luma.core.data.sync.TrackDownloader
import com.luma.data.download.TrackDownloaderImpl
import com.luma.data.download.prefs.QualityPreferencesManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the download layer.
 *
 * Binds abstraction interfaces defined in `:core:data` to their concrete
 * implementations in `:data:download`, preventing circular module dependencies
 * while allowing feature modules and sync workers to use the download pipeline.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadModule {

    /**
     * Provides [TrackDownloaderImpl] wherever [TrackDownloader] is injected.
     */
    @Binds
    @Singleton
    abstract fun bindTrackDownloader(impl: TrackDownloaderImpl): TrackDownloader

    /**
     * Provides [QualityPreferencesManager] wherever [QualityPreference] is injected.
     */
    @Binds
    @Singleton
    abstract fun bindQualityPreference(impl: QualityPreferencesManager): QualityPreference
}
