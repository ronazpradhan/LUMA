package com.stash.data.download.lyrics

/**
 * Indirection that lets `:data:download` enqueue a post-download lyrics
 * fetch without taking a compile-time dependency on `:data:lyrics`.
 *
 * `:data:lyrics` already depends on `:data:download` (it consumes the
 * shared [com.stash.data.download.backfill.BackfillVersionTracker]). A
 * direct `:data:download` → `:data:lyrics` dependency would be cyclic, so
 * the download module talks to lyrics through this single-method seam
 * and the concrete binding lives in `:app`'s Hilt graph
 * (`com.stash.app.di.LyricsFetchTriggerModule`).
 *
 * The production implementation enqueues a unique `LyricsFetchWorker`
 * keyed by `LyricsFetchWorker.UNIQUE_PREFIX_POST_DOWNLOAD + trackId`
 * with `ExistingWorkPolicy.KEEP` — duplicate enqueues for the same
 * trackId (e.g. a re-download) collapse to a single fetch.
 *
 * The id is the Room `tracks.id` Long, NOT a string identifier — match
 * the storage type end-to-end so callers don't have to stringify.
 */
interface LyricsFetchTrigger {
    /**
     * Enqueue a post-download lyrics fetch for [trackId]. Idempotent —
     * safe to call repeatedly; WorkManager's unique-work policy collapses
     * duplicates. Failure is silent (the production binding wraps the
     * WorkManager call; if it throws, the download flow must not surface
     * the error since the file is already on disk).
     */
    fun enqueueFor(trackId: Long)
}
