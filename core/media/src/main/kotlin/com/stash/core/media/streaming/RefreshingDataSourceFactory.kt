package com.stash.core.media.streaming

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.stash.core.data.db.dao.TrackDao
import kotlinx.coroutines.runBlocking

/**
 * HTTP response codes that signal a stale signed-URL and trigger a
 * re-resolve. 403 is what the Qobuz/Akamai CDN returns past `etsp`; 410
 * is the spec-compliant "gone" code some upstreams emit. Other non-2xx
 * codes (404, 5xx) propagate as fatal so the player surfaces them.
 */
private val REFRESH_TRIGGERS = setOf(403, 410)

/**
 * Per-track [DataSource] wrapper that transparently re-resolves the
 * upstream signed URL when the CDN returns 403/410 mid-stream. This is
 * the seam that makes pause-and-resume-hours-later work: if the user
 * pauses a streamed track and resumes past `etsp`, the next HTTP read
 * here catches the 403, hits Kennyy for a fresh URL, and re-opens at
 * the same byte offset.
 *
 * Kotlin delegation (`DataSource by inner`) forwards every member to
 * the wrapped HTTP source except [open], which we override to install
 * the refresh logic.
 *
 * `setPosition(N)` on [DataSpec.Builder] is the byte offset (used by
 * Media3 for HTTP `Range:` requests, not the time offset). The retry
 * preserves it implicitly via `spec.buildUpon().setUri(...)` so the
 * fresh URL resumes at exactly the byte we failed on.
 *
 * **Construction:** This class is NOT Hilt-injected — it's instantiated
 * per-track inside a `StreamingMediaSourceFactory` (Task 7) which IS
 * a Hilt singleton and threads through the injected `resolver` /
 * `cache` / `trackDao` here.
 *
 * **Re-entrance / runBlocking safety:** Media3 calls [open] from its
 * own loader thread (never main), so [runBlocking] is safe here. We
 * use **one** [runBlocking] for both the DAO lookup and the resolver
 * call — splitting it into two could re-enter Room's connection pool
 * or Kennyy's [com.stash.data.download.lossless.AggregatorRateLimiter]
 * and deadlock.
 */
@OptIn(UnstableApi::class)
class RefreshingDataSource(
    private val inner: HttpDataSource,
    private val resolver: StreamSourceRegistry,
    private val cache: StreamUrlCache,
    private val trackId: Long,
    private val trackDao: TrackDao,
) : DataSource by inner {

    override fun open(spec: DataSpec): Long {
        return try {
            inner.open(spec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode !in REFRESH_TRIGGERS) throw e
            // Single coroutine scope for both lookups — avoids re-entrance
            // hazards from two independent runBlocking calls sharing the
            // loader thread with Room's connection pool + Kennyy's
            // rate-limiter.
            val fresh = runBlocking {
                val track = trackDao.getById(trackId) ?: return@runBlocking null
                resolver.resolve(track)
            } ?: throw e
            cache.put(trackId, fresh)
            val newSpec = spec.buildUpon().setUri(Uri.parse(fresh.url)).build()
            inner.open(newSpec)
        }
    }
}

/**
 * Per-track factory for [RefreshingDataSource]. Constructed inside
 * `StreamingMediaSourceFactory.create(trackId)` (Task 7) which captures
 * the trackId in this factory closure so every [DataSource] that
 * Media3 spawns for the same track shares the same refresh policy.
 *
 * Stateless beyond the captured trackId — the cache / resolver / DAO
 * references come from the Hilt-injected singleton above.
 */
@OptIn(UnstableApi::class)
class RefreshingDataSourceFactory(
    private val innerFactory: HttpDataSource.Factory,
    private val resolver: StreamSourceRegistry,
    private val cache: StreamUrlCache,
    private val trackDao: TrackDao,
    private val trackId: Long,
) : DataSource.Factory {

    override fun createDataSource(): DataSource = RefreshingDataSource(
        inner = innerFactory.createDataSource(),
        resolver = resolver,
        cache = cache,
        trackId = trackId,
        trackDao = trackDao,
    )
}
