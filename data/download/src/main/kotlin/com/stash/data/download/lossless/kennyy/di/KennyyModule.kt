package com.stash.data.download.lossless.kennyy.di

import com.stash.data.download.lossless.LosslessSource
import com.stash.data.download.lossless.kennyy.KennyySource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Hilt wiring for the Qobuz-via-kennyy.com.br lossless source.
 *
 * Binds [KennyySource] into the `Set<LosslessSource>` multibinding so
 * [com.stash.data.download.lossless.LosslessSourceRegistry] picks it up
 * alongside [com.stash.data.download.lossless.qobuz.QobuzSource].
 *
 * kennyy.com.br is a sibling Qobuz-DL proxy to qobuz.squid.wtf —
 * same Next.js codebase, different operator, no captcha gate. Registering
 * both sources in the multibinding means the resolver can fall through
 * from squid.wtf to kennyy.com.br (or vice-versa per priority order)
 * when one operator's upstream account is rate-limited or region-locked.
 *
 * No credentials wiring — kennyy.com.br is a public proxy; the operator
 * holds the upstream Qobuz subscription and end users supply nothing.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class KennyyModule {

    @Binds
    @IntoSet
    abstract fun bindKennyyAsLosslessSource(impl: KennyySource): LosslessSource
}
