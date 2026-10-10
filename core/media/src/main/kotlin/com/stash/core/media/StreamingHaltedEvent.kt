package com.stash.core.media

/**
 * Emitted by [PlayerRepository] when consecutive stream errors hit
 * the cascade-guard threshold. UI surfaces a Snackbar; the player
 * itself is already paused by the time this fires.
 */
data class StreamingHaltedEvent(
    val failingTitle: String?,
    val consecutiveErrorCount: Int,
)
