package com.stash.core.data.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Emits a track id every time that track's `youtube_id` is REPLACED (not
 * first-set) — i.e. an existing platform identity is swapped for a
 * different one. Resync approval, wrong-match swap, and OMV→ATV
 * canonicalization all go through [emitIdentityChanged].
 *
 * Exists so `:core:media` (PlayerRepositoryImpl) can evict any
 * [com.stash.core.media.streaming.StreamUrlCache] entry keyed on the
 * OLD identity without `:core:data` call sites needing a direct
 * dependency on `:core:media` (which would be circular — StreamUrlCache
 * lives in `:core:media`, which already depends on `:core:data`).
 * Mirrors `MusicRepository.trackDeletions`, same reasoning.
 */
@Singleton
class TrackIdentityEvents @Inject constructor() {
    /**
     * Buffered so the common single-change case never blocks, and **never dropping**.
     *
     * This began as `extraBufferCapacity = 8, DROP_OLDEST`, which is right for a
     * progress or UI signal where the newest value supersedes the last, and wrong
     * for cache invalidation, where every event is load-bearing. A dropped event
     * leaves that track's stale StreamUrl in place — the exact bug this class exists
     * to fix, reappearing intermittently instead of consistently.
     *
     * The emitters are the bulk paths (YtLibraryCanonicalizer sweeping OMV→ATV
     * across a library, batched resync approvals), so a small dropping buffer would
     * fail during precisely the operations that change the most identities.
     */
    private val _changes = MutableSharedFlow<Long>(
        extraBufferCapacity = 512,
    )
    val changes: SharedFlow<Long> = _changes.asSharedFlow()

    /**
     * Suspending on purpose. Every call site is already inside a coroutine
     * (`ensureYoutubeId`, `performSwap`, `canonicalize`, and the ViewModel's
     * `viewModelScope.launch`), so real backpressure is available: a slow consumer
     * makes the emitter wait rather than silently discarding an invalidation.
     *
     * `tryEmit` was the earlier fix and is the weaker one — it cannot suspend, so on
     * a full buffer it returns false and the event is gone. Sized buffers only make
     * that rarer; suspending removes it.
     */
    suspend fun emitIdentityChanged(trackId: Long) {
        _changes.emit(trackId)
    }

}
