package com.luma.core.data.di

import android.content.Context
import androidx.room.Room
import com.luma.core.data.db.LumaDatabase
import com.luma.core.data.db.dao.ArtistProfileCacheDao
import com.luma.core.data.db.dao.DownloadQueueDao
import com.luma.core.data.db.dao.PlaylistDao
import com.luma.core.data.db.dao.RemoteSnapshotDao
import com.luma.core.data.db.dao.SourceAccountDao
import com.luma.core.data.db.dao.SyncHistoryDao
import com.luma.core.data.db.dao.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the Room [LumaDatabase] singleton and all DAO
 * instances derived from it.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LumaDatabase {
        return Room.databaseBuilder(
            context,
            LumaDatabase::class.java,
            LumaDatabase.DATABASE_NAME,
        )
            // Single source of truth for the chain — DatabaseBackupManager's
            // merge-import opens a second Room instance over a staged backup
            // file with this same array, so an older backup can be rolled
            // forward to the live schema before rows are copied out of it.
            .addMigrations(*LumaDatabase.ALL_MIGRATIONS)
            // No fallbackToDestructiveMigration() — if a migration is missing,
            // the app will crash on startup instead of silently wiping the
            // user's entire library. This forces us to write proper migrations
            // for every schema change. The crash is preferable to data loss.
            .build()
    }

    @Provides
    fun provideTrackDao(db: LumaDatabase): TrackDao = db.trackDao()

    @Provides
    fun providePlaylistDao(db: LumaDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideSyncHistoryDao(db: LumaDatabase): SyncHistoryDao = db.syncHistoryDao()

    @Provides
    fun provideDownloadQueueDao(db: LumaDatabase): DownloadQueueDao = db.downloadQueueDao()

    @Provides
    fun provideSourceAccountDao(db: LumaDatabase): SourceAccountDao = db.sourceAccountDao()

    @Provides
    fun provideRemoteSnapshotDao(db: LumaDatabase): RemoteSnapshotDao = db.remoteSnapshotDao()

    @Provides
    fun provideArtistProfileCacheDao(db: LumaDatabase): ArtistProfileCacheDao =
        db.artistProfileCacheDao()

    @Provides
    fun provideArtistImageDao(db: LumaDatabase): com.luma.core.data.db.dao.ArtistImageDao =
        db.artistImageDao()

    @Provides
    fun provideListeningEventDao(db: LumaDatabase): com.luma.core.data.db.dao.ListeningEventDao =
        db.listeningEventDao()

    @Provides
    fun provideTrackTagDao(db: LumaDatabase): com.luma.core.data.db.dao.TrackTagDao =
        db.trackTagDao()

    @Provides
    fun provideLumaMixRecipeDao(db: LumaDatabase): com.luma.core.data.db.dao.LumaMixRecipeDao =
        db.lumaMixRecipeDao()

    @Provides
    fun provideDiscoveryQueueDao(db: LumaDatabase): com.luma.core.data.db.dao.DiscoveryQueueDao =
        db.discoveryQueueDao()

    @Provides
    fun provideTrackBlocklistDao(db: LumaDatabase): com.luma.core.data.db.dao.TrackBlocklistDao =
        db.trackBlocklistDao()

    @Provides
    fun provideTrackSkipEventDao(db: LumaDatabase): com.luma.core.data.db.dao.TrackSkipEventDao =
        db.trackSkipEventDao()

    @Provides
    fun provideLyricsDao(db: LumaDatabase): com.luma.core.data.db.dao.LyricsDao =
        db.lyricsDao()

    @Provides
    fun provideLastFmCacheDao(db: LumaDatabase): com.luma.core.data.db.dao.LastFmCacheDao =
        db.lastFmCacheDao()

    @Provides
    fun provideSpotifyResolutionDao(db: LumaDatabase): com.luma.core.data.db.dao.SpotifyResolutionDao =
        db.spotifyResolutionDao()

    @Provides
    fun provideFlacUpgradeQueueDao(db: LumaDatabase): com.luma.core.data.db.dao.FlacUpgradeQueueDao =
        db.flacUpgradeQueueDao()

    @Provides
    fun provideListenSubmissionDao(db: LumaDatabase): com.luma.core.data.db.dao.ListenSubmissionDao =
        db.listenSubmissionDao()

    @Provides
    fun provideSyncUndoDao(db: LumaDatabase): com.luma.core.data.db.dao.SyncUndoDao =
        db.syncUndoDao()

    @Provides
    fun provideSharedMixDao(db: LumaDatabase): com.luma.core.data.db.dao.SharedMixDao = db.sharedMixDao()
}
