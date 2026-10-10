package com.stash.core.media.preview.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.stash.core.media.preview.TrackKeyCacheKeyFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * Hilt-provides the ExoPlayer SimpleCache used by [SearchPreviewMediaSource]
 * and [SearchDownloadCoordinator] to share streamed bytes between preview
 * and download.
 *
 * Cache lives at `<filesDir>/preview_cache/` — app-private, cleared on
 * uninstall. 200MB LRU cap. Holds 2-6 FLAC previews; older entries evict
 * on use.
 *
 * HTTP source is `DefaultHttpDataSource.Factory` (no OkHttp connection
 * pool / cookie features needed for stateless Qobuz signed CDN URLs;
 * avoids adding the separate `media3-datasource-okhttp` artifact).
 */
@Module
@InstallIn(SingletonComponent::class)
object PreviewCacheModule {

    private const val MAX_CACHE_BYTES = 200L * 1024 * 1024

    @Provides @Singleton
    fun provideCacheDir(@ApplicationContext ctx: Context): File =
        File(ctx.filesDir, "preview_cache").also { it.mkdirs() }

    @Provides @Singleton
    fun provideEvictor(): CacheEvictor =
        LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)

    @Provides @Singleton
    fun provideDatabaseProvider(@ApplicationContext ctx: Context): DatabaseProvider =
        StandaloneDatabaseProvider(ctx)

    @Provides @Singleton
    fun provideSimpleCache(
        cacheDir: File,
        evictor: CacheEvictor,
        databaseProvider: DatabaseProvider,
    ): SimpleCache = SimpleCache(cacheDir, evictor, databaseProvider)

    @Provides @Singleton
    fun provideHttpDataSourceFactory(): HttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setUserAgent("Stash/0.9.12")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

    /**
     * Binds [TrackKeyCacheKeyFactory] as the graph-wide [CacheKeyFactory].
     *
     * [SearchDownloadCoordinator] lives in `:data:download`, which cannot
     * depend on `:core:media` (circular). It injects [CacheKeyFactory] (the
     * media3 interface) so Hilt resolves the concrete type here, at the
     * `:core:media` layer, which owns the factory class.
     */
    @Provides @Singleton
    fun provideCacheKeyFactory(impl: TrackKeyCacheKeyFactory): CacheKeyFactory = impl
}
