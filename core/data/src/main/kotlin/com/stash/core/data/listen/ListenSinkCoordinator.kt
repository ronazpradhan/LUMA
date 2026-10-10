package com.stash.core.data.listen

import android.util.Log
import com.stash.core.data.listenbrainz.ListenBrainzSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the registered [ListenSink] list and decides when they drain.
 *
 * Drains are **event-driven, not Flow-driven**, unlike `LastFmScrobbler.start()`
 * which collects a `combine(session, pendingCount)`. Per-target pending work
 * depends on a suspend-resolved cutoff ([ListenSink.listeningSinceMs]), so a
 * reactive count would need a Flow parameterised by a value only obtainable inside
 * a coroutine — awkward, and it buys nothing here. Two triggers cover it: app
 * start, and each newly recorded listen.
 *
 * A [Mutex] serialises drains so a burst of finished tracks cannot run overlapping
 * passes and submit the same listen twice.
 */
@Singleton
class ListenSinkCoordinator @Inject constructor(
    private val drainer: ListenSinkDrainer,
    listenBrainzSink: ListenBrainzSink,
) {
    /**
     * Every destination on the generic path. Last.fm and YouTube history are
     * deliberately absent: they still run their own scrobblers against their own
     * boolean columns. Migrating them across is a separate change, made once this
     * path has proven itself rather than as part of introducing it.
     */
    private val sinks: List<ListenSink> = listOf(listenBrainzSink)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val drainLock = Mutex()

    /** Call once from Application.onCreate. Safe when nothing is connected. */
    fun start() {
        scope.launch { drainAll() }
    }

    /** Fire-and-forget drain after a listen is recorded. Never blocks the caller. */
    fun onListenRecorded() {
        scope.launch { drainAll() }
    }

    /** Drains every enabled sink once. Returns per-sink reports for the UI. */
    suspend fun drainAll(): List<ListenSinkDrainer.DrainReport> = drainLock.withLock {
        sinks.map { sink ->
            runCatching { drainer.drain(sink) }
                .getOrElse {
                    Log.w(TAG, "drain crashed for ${sink.id}", it)
                    ListenSinkDrainer.DrainReport(sink.id, skipped = true)
                }
        }
    }

    /**
     * Best-effort now-playing fan-out. Fire-and-forget: by the time a retry landed
     * the user would be on a different track.
     */
    fun notifyNowPlaying(listen: Listen) {
        scope.launch {
            sinks.forEach { sink ->
                runCatching {
                    if (sink.isEnabled()) sink.nowPlaying(listen)
                }.onFailure { Log.d(TAG, "now-playing failed for ${sink.id}: ${it.message}") }
            }
        }
    }

    private companion object {
        private const val TAG = "ListenSinkCoordinator"
    }
}
