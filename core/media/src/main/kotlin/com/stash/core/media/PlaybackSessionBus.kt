package com.stash.core.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Same-process handshake between [service.StashPlaybackService] and
 * [PlayerRepositoryImpl] for the service's idle self-stop.
 *
 * The service flips [sessionAlive] true in `onCreate` and false just before
 * it stops itself (and again in `onDestroy`, idempotently, for every other
 * death path). The repository reacts: on `false` it releases its
 * MediaController — the binding that would otherwise keep a stopped service
 * alive forever — and on `true` it reconnects, which is what keeps the
 * repository's state observation working for playback the APP didn't start
 * (media button, Bluetooth, Android Auto). The previous design had no
 * "reconnect" signal and was reverted: a released controller left the repo
 * deaf and silently dropped listening history.
 *
 * A [StateFlow] rather than events on purpose: a late subscriber (the repo
 * is constructed lazily) still sees the current answer, and duplicate
 * emissions collapse.
 */
@Singleton
class PlaybackSessionBus @Inject constructor() {

    private val _sessionAlive = MutableStateFlow(false)

    /** Whether the playback service is currently created and usable. */
    val sessionAlive: StateFlow<Boolean> = _sessionAlive.asStateFlow()

    fun onServiceCreated() {
        _sessionAlive.value = true
    }

    fun onServiceStopping() {
        _sessionAlive.value = false
    }
}
