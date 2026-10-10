package com.stash.core.common

/**
 * Minimal mockable clock abstraction. Modules that stamp wall-clock
 * timestamps (e.g. v0.9.36 lyrics fetch -> `tracks.lyrics_fetched_at`)
 * depend on this rather than calling [System.currentTimeMillis] directly
 * so tests can pin time and verify exact-stamp semantics.
 *
 * Wire [SystemClock] as the production binding in the consuming module's
 * Hilt module. Keep this surface deliberately tiny — only [now] is needed
 * today; richer time concerns belong in a richer abstraction.
 */
interface Clock {
    /** Epoch-millis, equivalent to [System.currentTimeMillis]. */
    fun now(): Long
}

/** Production [Clock] backed by the system wall clock. */
class SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}
