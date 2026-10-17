package com.luma.app.cast

import com.luma.core.media.cast.CastConnection
import com.luma.core.media.cast.CastDevice
import com.luma.core.media.cast.CastDevices
import com.luma.core.media.cast.CastRemote
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * LUMA: the foss flavor ships without the Google Cast SDK (it needs Google
 * Play Services, which F-Droid/IzzyOnDroid don't allow). Casting is always
 * [CastConnection.Unavailable], which hides the cast button.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CastModule {
    @Binds
    abstract fun bindCastDevices(impl: NoCastDevices): CastDevices
}

@Singleton
class NoCastDevices @Inject constructor() : CastDevices {
    override val connection: StateFlow<CastConnection> = MutableStateFlow(CastConnection.Unavailable)
    override val devices: StateFlow<List<CastDevice>> = MutableStateFlow(emptyList())
    override val remote: StateFlow<CastRemote?> = MutableStateFlow(null)

    override fun startScan() = Unit
    override fun stopScan() = Unit
    override fun connect(deviceId: String) = Unit
    override fun disconnect() = Unit
}
