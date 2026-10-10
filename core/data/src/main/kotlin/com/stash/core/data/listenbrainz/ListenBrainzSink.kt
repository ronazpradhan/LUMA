package com.stash.core.data.listenbrainz

import android.util.Log
import com.stash.core.data.listen.Listen
import com.stash.core.data.listen.ListenSink
import com.stash.core.data.listen.SinkResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ListenBrainz as a [ListenSink].
 *
 * The whole class is this short because queueing, retry, batch-splitting and
 * per-destination state all live in
 * [com.stash.core.data.listen.ListenSinkDrainer] and `listen_submissions`. That
 * was the point of extracting them: Last.fm and YouTube history each needed a
 * boolean column, three DAO queries, a schema migration and their own drain loop,
 * and this needed none of those.
 */
@Singleton
class ListenBrainzSink @Inject constructor(
    private val api: ListenBrainzApiClient,
    private val preference: ListenBrainzPreference,
) : ListenSink {

    override val id: String = TARGET_ID

    /**
     * ListenBrainz accepts far larger `import` payloads, but a music app submits a
     * handful of listens per drain in practice, and a smaller batch means a
     * rejected one is cheaper to split and isolate.
     */
    override val maxBatchSize: Int = 50

    override suspend fun isEnabled(): Boolean = preference.tokenNow() != null

    /**
     * Only listens from the moment the user connected.
     *
     * Returning [Long.MAX_VALUE] when no timestamp is stored is a deliberate
     * fail-closed: an unknown cutoff must submit nothing rather than default to
     * zero and flood ListenBrainz with the user's entire history.
     */
    override suspend fun listeningSinceMs(): Long =
        preference.connectedAtNow() ?: Long.MAX_VALUE

    override suspend fun submit(batch: List<Listen>): SinkResult {
        val token = preference.tokenNow()
            ?: return SinkResult.Transient("no token")

        return when (val response = api.submitListens(token, batch)) {
            is ListenBrainzApiClient.Response.Accepted -> SinkResult.Success

            // 401 is an ACCOUNT problem, not a listen problem, so it must not burn
            // retries — the listens are perfectly good and will submit once the
            // user fixes things. Treating it as a rejection would mark them FAILED
            // and silently discard them at the attempt cap, which is the same
            // "an outage costs history" failure the Transient/Rejected split exists
            // to prevent.
            //
            // Seen in the wild on 2026-07-30 with a token that passed
            // /1/validate-token: ListenBrainz answers 401 with "your MetaBrainz
            // account does not have a verified email address". Nothing about the
            // payload is wrong, and nothing about retrying is futile — the user
            // just has to verify their email.
            is ListenBrainzApiClient.Response.Refused -> {
                if (response.code == 401) {
                    Log.w(
                        TAG,
                        "401 from ListenBrainz — account or token problem, holding " +
                            "listens: ${response.message}",
                    )
                    SinkResult.Transient("HTTP 401: ${response.message}")
                } else {
                    SinkResult.Rejected("HTTP ${response.code}: ${response.message}")
                }
            }

            is ListenBrainzApiClient.Response.Unavailable ->
                SinkResult.Transient(response.message)
        }
    }

    override suspend fun nowPlaying(listen: Listen) {
        if (!preference.nowPlayingEnabledNow()) return
        val token = preference.tokenNow() ?: return
        // Best-effort by definition: a retry would land after the user moved on.
        runCatching { api.submitPlayingNow(token, listen) }
            .onFailure { Log.d(TAG, "now-playing ping failed: ${it.message}") }
    }

    companion object {
        /** Also the `listen_submissions.target` value. Never change it. */
        const val TARGET_ID = "listenbrainz"
        private const val TAG = "ListenBrainzSink"
    }
}
