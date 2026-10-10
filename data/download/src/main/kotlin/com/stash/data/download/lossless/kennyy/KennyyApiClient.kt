package com.stash.data.download.lossless.kennyy

import com.stash.data.download.lossless.qobuz.QobuzApiException
import com.stash.data.download.lossless.qobuz.QobuzDownloadData
import com.stash.data.download.lossless.qobuz.QobuzQuality
import com.stash.data.download.lossless.qobuz.QobuzSearchData
import com.stash.data.download.lossless.qobuz.SquidWtfEnvelope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * HTTP client for the public `qobuz.kennyy.com.br/api` proxy.
 *
 * kennyy.com.br is a near-clone of qobuz.squid.wtf — same Qobuz-DL
 * Next.js codebase, same `/api/get-music` and `/api/download-music`
 * endpoints, same `{success, data}` JSON envelope, different operator.
 * Critically: **no captcha gate** — the download-music endpoint is
 * openly accessible with no cookie requirement.
 *
 * Because the JSON response shape is identical to squid.wtf, all
 * [QobuzModels] types are reused verbatim (cross-package import).
 * [QobuzApiException] is also reused for the same reason — no new
 * exception types.
 *
 * This client deliberately omits any captcha interceptor: kennyy.com.br
 * has no ALTCHA challenge and no cookie-gating logic.
 */
@Singleton
class KennyyApiClient @Inject constructor(
    sharedClient: OkHttpClient,
) {

    /**
     * Derived OkHttp client that re-uses the shared connection pool +
     * dispatcher + TLS config. Adds a User-Agent header to all requests
     * to identify Stash traffic on the operator's logs.
     *
     * `internal var` rather than a `val` so tests can replace it with a
     * MockWebServer-bound client without needing to reconstruct the full
     * OkHttpClient stack.
     */
    internal var httpClient: OkHttpClient =
        sharedClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .build()

    /**
     * Test seam: tests assign a MockWebServer URL before calling any
     * endpoint. Production paths leave this on [DEFAULT_BASE_URL].
     */
    internal var baseUrl: String = DEFAULT_BASE_URL

    /** Test seam — override for parsing strictness checks. */
    internal var json: Json = DEFAULT_JSON

    /**
     * Search the kennyy.com.br-proxied Qobuz catalog. [query] can be
     * free text (`"Radiohead Karma Police"`) or an ISRC (Qobuz's best
     * index key). Stash passes ISRC when available, free text otherwise.
     *
     * The server hardcodes `limit=10`; paginate via [offset].
     */
    suspend fun search(
        query: String,
        offset: Int = 0,
    ): QobuzSearchData = withContext(Dispatchers.IO) {
        val url = "$baseUrl/get-music".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("offset", offset.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        executeAndParseEnvelope(request)
    }

    /**
     * Resolve a Qobuz track id to a signed CDN download URL via
     * kennyy.com.br's `download-music` route. The returned URL is a
     * direct pre-signed Akamai link — fetch with a plain GET, no auth,
     * no cookies. Lifetime is short (minutes-to-hours); don't cache it
     * across the full download lifecycle.
     *
     * @param trackId Qobuz canonical track id from search results.
     * @param quality One of [QobuzQuality]. Default FLAC 24/192;
     *   server picks highest-available <= requested.
     */
    suspend fun getFileUrl(
        trackId: Long,
        quality: Int = QobuzQuality.FLAC_HIRES_192,
    ): QobuzDownloadData = withContext(Dispatchers.IO) {
        val url = "$baseUrl/download-music".toHttpUrl().newBuilder()
            .addQueryParameter("track_id", trackId.toString())
            .addQueryParameter("quality", quality.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        executeAndParseEnvelope(request)
    }

    // ── Internals ───────────────────────────────────────────────────────

    /**
     * Execute, decode the `{success, data, error}` envelope, and unwrap
     * to [T]. Throws [QobuzApiException] on:
     *  - HTTP non-2xx
     *  - `success: false` (kennyy.com.br validation error)
     *  - HTTP 200 with `data: null` (upstream weirdness)
     */
    private inline fun <reified T> executeAndParseEnvelope(request: Request): T {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val parsedMessage = runCatching {
                    json.decodeFromString<SquidWtfEnvelope<T>>(body).error
                }.getOrNull()
                throw QobuzApiException(
                    status = response.code,
                    message = parsedMessage ?: response.message.ifBlank { "HTTP ${response.code}" },
                )
            }

            val envelope = runCatching { json.decodeFromString<SquidWtfEnvelope<T>>(body) }
                .getOrElse { e ->
                    throw QobuzApiException(
                        status = response.code,
                        message = "malformed JSON: ${e.message}",
                    )
                }

            if (!envelope.success || envelope.data == null) {
                throw QobuzApiException(
                    status = response.code,
                    message = envelope.error ?: "empty data with success=${envelope.success}",
                )
            }
            return envelope.data
        }
    }

    companion object {
        /** Base URL for the kennyy.com.br Qobuz proxy (no captcha required). */
        const val DEFAULT_BASE_URL: String = "https://qobuz.kennyy.com.br/api"

        /**
         * User-Agent header sent on every request so the operator can
         * identify Stash traffic in their server logs.
         */
        const val USER_AGENT: String = "Stash-Android/1.0 (+https://github.com/rawnaldclark/Stash)"

        /**
         * Tolerant Json instance — kennyy.com.br passes Qobuz responses
         * through, and Qobuz includes many fields we don't model.
         * Without `ignoreUnknownKeys` deserialisation would fail on
         * every call.
         */
        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
}
