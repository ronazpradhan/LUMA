package com.stash.core.media

/**
 * Identifies a bulk-playback action currently in-flight for a Library
 * Detail screen (Liked Songs, Playlist, Album, Artist).
 *
 * Library Detail ViewModels expose a `bulkPlayInFlight: StateFlow<BulkPlayAction?>`
 * parallel to the per-row `tappedTrackId` flow. The per-row flow drives the
 * spinner on the first-to-play track row; this enum drives a separate loading
 * indicator on the Play All / Shuffle All buttons themselves so the user gets
 * visual feedback even when the first track row is off-screen (common after a
 * shuffle).
 *
 * The flag is set immediately before `PlayerRepository.setQueue` and cleared
 * in a `finally` block using `MutableStateFlow.compareAndSet(ACTION, null)`,
 * which is race-free across cross-action taps (e.g. Play All then Shuffle All
 * before the first resolve finishes).
 */
enum class BulkPlayAction {
    PLAY_ALL,
    SHUFFLE_ALL,
}
