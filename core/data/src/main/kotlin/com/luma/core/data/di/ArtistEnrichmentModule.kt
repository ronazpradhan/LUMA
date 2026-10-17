package com.luma.core.data.di

import com.luma.core.data.cache.ArtistAboutEnricher
import com.luma.core.data.cache.RealArtistAboutEnricher
import com.luma.core.data.musicbrainz.MusicBrainzClient
import com.luma.core.data.musicbrainz.MusicBrainzClientImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ArtistEnrichmentModule {
    @Binds abstract fun bindMusicBrainzClient(impl: MusicBrainzClientImpl): MusicBrainzClient
    @Binds abstract fun bindArtistAboutEnricher(impl: RealArtistAboutEnricher): ArtistAboutEnricher
}
