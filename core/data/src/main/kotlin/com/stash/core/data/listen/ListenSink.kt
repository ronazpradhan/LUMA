package com.stash.core.data.listen

/**
 * One listen, normalised for submission. Sinks translate this into whatever shape
 * their service wants — Last.fm form fields, a ListenBrainz JSON payload — so the
 * drain loop never has to know which destination it is feeding.
 */
data class Listen(
    /** `listening_events.id` — the drain loop's handle for marking state. */
    val eventId: Long,
    val artist: String,
    val title: String,
    val album: String?,
    val durationMs: Long,
    /** When playback started, epoch millis. Services want seconds; convert at the edge. */
    val startedAtMs: Long,
)

/** Outcome of submitting a batch. The distinction between the two failures matters. */
sealed interface SinkResult {

    data object Success : SinkResult

    /**
     * The destination refused this payload — malformed metadata, an unmappable
     * recording, a bad token (4xx). Resubmitting the same batch unchanged will not
     * help, so the drain loop splits the batch to isolate the offending listen
     * rather than letting one bad row poison its neighbours until they all exhaust
     * their retries.
     */
    data class Rejected(val message: String?) : SinkResult

    /**
     * Outage, timeout, or 5xx. Nothing is wrong with the listens, so they keep
     * their attempt count and wait for the next trigger. A destination being down
     * for a week must never cost the user a week of history.
     */
    data class Transient(val message: String?) : SinkResult
}

/**
 * A destination for finished listens.
 *
 * Queueing, retry, batching, and per-destination state all live in
 * [ListenSinkDrainer] and `listen_submissions`; an implementation only has to know
 * how to talk to its service. That split is why adding ListenBrainz costs a small
 * class instead of the column-plus-three-queries-plus-migration that Last.fm and
 * YouTube history each needed.
 */
interface ListenSink {

    /** Stable id, also the `listen_submissions.target` value. Never change it. */
    val id: String

    /** How many listens this destination accepts per request. */
    val maxBatchSize: Int get() = 50

    /** False when the user hasn't connected this destination — drains skip it. */
    suspend fun isEnabled(): Boolean

    /**
     * Only listens started at or after this instant are eligible.
     *
     * This is the guard against a newly-connected destination inheriting the
     * user's entire history and submitting it as one flood. Implementations return
     * the moment the user connected; deliberate backfill stays a separate,
     * explicit action.
     */
    suspend fun listeningSinceMs(): Long

    suspend fun submit(batch: List<Listen>): SinkResult

    /**
     * Best-effort "currently playing" ping. No retry and no queue — by the time a
     * retry landed the user would be on another track. Default no-op for
     * destinations that have no such concept.
     */
    suspend fun nowPlaying(listen: Listen) {}
}
