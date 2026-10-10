package com.stash.core.data.sync.workers

import android.util.Log
import com.stash.core.auth.TokenManager
import com.stash.core.auth.model.AuthState
import com.stash.core.data.db.dao.ListeningEventDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.mapper.toDomain
import com.stash.core.data.prefs.LikePreferences
import com.stash.core.data.social.Destination
import com.stash.core.data.social.LikeDestinationDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * v0.9.13: Auto-saves tracks to Spotify Liked Songs after the user
 * has played them on N distinct days within the last 30 (where N is
 * [LikePreferences.autoSaveThreshold], default 3, slider 1-10).
 * Off by default.
 *
 * Architecture mirrors `LastFmScrobbler` — collects a Flow on the DAO
 * that emits when new completions land. ListeningRecorder writes
 * `completed_at` on insert, which bumps the Flow's MAX(completed_at)
 * value, which retriggers the drain.
 *
 * Forward-only: once `track.spotifySavedAt` is non-null we never
 * re-fire. Idempotent at the Spotify API level too.
 *
 * Volume is low — most tracks never cross the threshold. No queue
 * table; the inline path's failure recovery is organic (next
 * completion of the same track re-evaluates the threshold and
 * re-fires).
 */
@Singleton
class AutoSaveScrobbler @Inject constructor(
    private val listeningEventDao: ListeningEventDao,
    private val trackDao: TrackDao,
    private val likePreferences: LikePreferences,
    private val likeDispatcher: LikeDestinationDispatcher,
    private val tokenManager: TokenManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            // Wake on every new completion. distinctUntilChanged so a
            // stale read doesn't re-fire spuriously.
            listeningEventDao.observeMostRecentCompletion()
                .filterNotNull()
                .distinctUntilChanged()
                .collect { latestCompletedAt ->
                    runCatching { onNewCompletion(latestCompletedAt) }
                        .onFailure { e ->
                            if (e is CancellationException) throw e
                            Log.w(TAG, "drain failed: ${e.message}", e)
                        }
                }
        }
    }

    private suspend fun onNewCompletion(completedAtMs: Long) {
        if (!likePreferences.autoSaveEnabledNow()) return
        if (tokenManager.spotifyAuthState.value !is AuthState.Connected) return

        // Find the row with this completed_at (the trigger gives us a
        // timestamp, not an event id — query by exact timestamp).
        val event = listeningEventDao.findByCompletedAt(completedAtMs) ?: return
        val trackEntity = trackDao.getById(event.trackId) ?: return
        val track = trackEntity.toDomain()
        if (track.spotifyUri == null) return
        if (track.spotifySavedAt != null) return

        val threshold = likePreferences.autoSaveThresholdNow()
        val sinceMs = System.currentTimeMillis() - LOOKBACK_WINDOW_MS
        val distinctDays = listeningEventDao.distinctDaysCompletedFor(track.id, sinceMs)
        if (distinctDays < threshold) {
            Log.d(TAG, "track ${track.id}: $distinctDays/$threshold days, skipping")
            return
        }

        Log.i(TAG, "track ${track.id}: threshold met ($distinctDays days), firing SPOTIFY save")
        val results = likeDispatcher.like(track, setOf(Destination.SPOTIFY))
        val ok = results[Destination.SPOTIFY]?.isSuccess == true
        if (!ok) {
            Log.w(TAG, "track ${track.id}: auto-save failed; will retry on next completion")
        }
        // Dispatcher updates trackDao.markSpotifySaved on success — no-op here.
    }

    companion object {
        private const val TAG = "AutoSaveScrobbler"
        /** Distinct-days threshold operates on plays from the last 30 days (UTC). */
        private const val LOOKBACK_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
    }
}
