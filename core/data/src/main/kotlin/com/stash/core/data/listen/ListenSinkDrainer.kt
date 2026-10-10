package com.stash.core.data.listen

import android.util.Log
import com.stash.core.data.db.dao.ListenSubmissionDao
import com.stash.core.data.db.dao.TrackDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drains the pending-listen queue for one [ListenSink].
 *
 * This is the machinery every scrobble destination used to reimplement.
 * `LastFmScrobbler` and `YouTubeHistoryScrobbler` each carry their own copy of
 * "load pending, resolve the track, submit, mark done, leave failures for the next
 * trigger" — the second one's KDoc says outright that it is structurally parallel
 * to the first. Extracting it means ListenBrainz adds a sink, not a fourth copy.
 *
 * Deliberately not a coroutine owner: no scope, no `start()`, nothing collecting a
 * Flow. Callers decide when to drain (app start, a new listen, a manual sync),
 * which keeps this class synchronous to reason about and trivial to test.
 */
@Singleton
class ListenSinkDrainer internal constructor(
    private val submissionDao: ListenSubmissionDao,
    private val trackDao: TrackDao,
    private val clock: () -> Long,
) {
    /**
     * Hilt entry point. The clock stays off this signature deliberately: giving it
     * a default value would generate two JVM constructors and Dagger would try to
     * provide `Function0<Long>`, which nothing binds. Same reasoning as
     * [com.stash.core.data.social.LikeCoordinator]'s scope/minGap pair.
     */
    @Inject
    constructor(
        submissionDao: ListenSubmissionDao,
        trackDao: TrackDao,
    ) : this(submissionDao, trackDao, System::currentTimeMillis)


    data class DrainReport(
        val sinkId: String,
        val submitted: Int = 0,
        val failed: Int = 0,
        val skipped: Boolean = false,
    )

    /**
     * Drains one pass for [sink]. Returns what happened so the Settings UI can say
     * something specific instead of "done".
     */
    suspend fun drain(sink: ListenSink): DrainReport {
        if (!runCatching { sink.isEnabled() }.getOrDefault(false)) {
            return DrainReport(sink.id, skipped = true)
        }

        val sinceMs = runCatching { sink.listeningSinceMs() }.getOrElse {
            Log.w(TAG, "${sink.id}: could not read listening-since cutoff", it)
            return DrainReport(sink.id, skipped = true)
        }

        val events = runCatching {
            submissionDao.pendingFor(sink.id, sinceMs = sinceMs, limit = sink.maxBatchSize)
        }.getOrElse {
            Log.w(TAG, "${sink.id}: failed to load pending listens", it)
            return DrainReport(sink.id, skipped = true)
        }
        if (events.isEmpty()) return DrainReport(sink.id)

        val batch = mutableListOf<Listen>()
        for (event in events) {
            val track = runCatching { trackDao.getById(event.trackId) }.getOrNull()
            if (track == null) {
                // The track was deleted between playing and submitting. Nothing
                // will ever make this row submittable, so retire it rather than
                // retrying a dead reference on every trigger — the same call
                // LastFmScrobbler makes.
                runCatching { submissionDao.markSent(event.id, sink.id, clock()) }
                continue
            }
            batch += Listen(
                eventId = event.id,
                artist = track.artist,
                title = track.title,
                album = track.album.takeIf { it.isNotBlank() },
                durationMs = track.durationMs,
                startedAtMs = event.startedAt,
            )
        }
        if (batch.isEmpty()) return DrainReport(sink.id)

        return when (val result = submitCatching(sink, batch)) {
            is SinkResult.Success -> {
                batch.forEach { submissionDao.markSent(it.eventId, sink.id, clock()) }
                DrainReport(sink.id, submitted = batch.size)
            }

            is SinkResult.Transient -> {
                // Untouched attempt counts: an outage must not consume retries, or
                // a service being down long enough would silently discard history.
                Log.i(TAG, "${sink.id}: transient failure, ${batch.size} listens held (${result.message})")
                DrainReport(sink.id)
            }

            is SinkResult.Rejected -> {
                if (batch.size == 1) {
                    submissionDao.markFailed(batch.single().eventId, sink.id, clock())
                    Log.w(TAG, "${sink.id}: listen rejected (${result.message})")
                    DrainReport(sink.id, failed = 1)
                } else {
                    // One malformed listen would otherwise burn every retry in the
                    // batch alongside it. Resubmit individually so the offender is
                    // the only row that pays.
                    Log.i(TAG, "${sink.id}: batch rejected, isolating ${batch.size} listens individually")
                    splitAndSubmit(sink, batch)
                }
            }
        }
    }

    private suspend fun splitAndSubmit(sink: ListenSink, batch: List<Listen>): DrainReport {
        var submitted = 0
        var failed = 0
        for (listen in batch) {
            when (submitCatching(sink, listOf(listen))) {
                is SinkResult.Success -> {
                    submissionDao.markSent(listen.eventId, sink.id, clock())
                    submitted++
                }
                is SinkResult.Rejected -> {
                    submissionDao.markFailed(listen.eventId, sink.id, clock())
                    failed++
                }
                is SinkResult.Transient -> Unit // held, attempts untouched
            }
        }
        return DrainReport(sink.id, submitted = submitted, failed = failed)
    }

    /**
     * A sink that throws is treated as a transient failure, never a rejection: an
     * unexpected exception is no evidence the listens are bad, and mistaking it for
     * one would burn their retries.
     */
    private suspend fun submitCatching(sink: ListenSink, batch: List<Listen>): SinkResult =
        runCatching { sink.submit(batch) }
            .getOrElse { t ->
                if (t is kotlinx.coroutines.CancellationException) throw t
                SinkResult.Transient(t.message)
            }

    private companion object {
        private const val TAG = "ListenSinkDrainer"
    }
}
