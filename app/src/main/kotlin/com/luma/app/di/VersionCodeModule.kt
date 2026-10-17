package com.luma.app.di

import com.luma.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the app's [BuildConfig.VERSION_CODE] as a function to the `core/data` layer.
 * This indirection allows [com.luma.core.data.youtube.YouTubeHistoryScrobbler] and
 * other components to access the version code without depending on BuildConfig directly.
 */
@Module
@InstallIn(SingletonComponent::class)
object VersionCodeModule {

    @Provides
    @Singleton
    fun provideVersionCodeProvider(): () -> Int = { BuildConfig.VERSION_CODE }
}
