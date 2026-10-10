package com.stash.data.download.lossless.squid

import android.util.Base64
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Pure-HTTP solver for squid.wtf's captcha gate. Replaces the
 * [HeadlessSquidCaptchaSolver] WebView approach with three round-trips:
 *
 *  1. GET /api/altcha/challenge?ts=<now> -> JSON challenge
 *  2. Local PoW solve via [AltchaSolver]
 *  3. POST /api/altcha/verify -> server replies with Set-Cookie
 *
 * Returns the `captcha_verified_at` cookie value (epoch-millis string)
 * on success, null on any failure. Caller persists via
 * `LosslessSourcePreferences.setCaptchaCookieValue`.
 *
 * Reuses the shared OkHttpClient. Does NOT add the existing
 * [SquidWtfCaptchaInterceptor] — that interceptor would attach a stale
 * Cookie header to challenge/verify, which the server may react badly
 * to. We want a clean unauthenticated round-trip.
 *
 * Payload shape (counter / derivedKey / time-as-float, nested challenge
 * + solution) confirmed via live HAR capture against the deployed
 * verify endpoint. ~500ms per solve vs. 5-10s for WebView, plus no
 * battery-saver fragility.
 */
@Singleton
class NativeSquidCaptchaSolver @Inject constructor(
    private val sharedClient: OkHttpClient,
) {

    suspend fun solve(): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch challenge — keep the raw response body so we can
            //    round-trip its `parameters` block byte-for-byte into the
            //    verify payload. The server's `signature` is an HMAC over
            //    a canonical serialization we don't control.
            val challengeRaw = fetchChallengeRaw() ?: return@withContext null
            val challenge = runCatching {
                JSON.decodeFromString(ChallengeResponse.serializer(), challengeRaw)
            }.getOrElse {
                Log.w(TAG, "challenge parse failed", it)
                return@withContext null
            }
            Log.d(
                TAG,
                "challenge fetched cost=${challenge.parameters.cost} " +
                    "keyLength=${challenge.parameters.keyLength}",
            )

            // 2. Solve PoW. Time it externally so the verify payload's
            //    `time` field reflects the actual local wall-clock spent
            //    on the solve (some servers gate on this for scoring).
            val startMs = System.nanoTime()
            val params = ChallengeParameters(
                algorithm = challenge.parameters.algorithm,
                cost = challenge.parameters.cost,
                expiresAt = challenge.parameters.expiresAt,
                keyLength = challenge.parameters.keyLength,
                keyPrefix = challenge.parameters.keyPrefix,
                nonce = challenge.parameters.nonce,
                salt = challenge.parameters.salt,
            )
            val solution = AltchaSolver.solve(params)
            val tookMs = (System.nanoTime() - startMs) / 1_000_000.0
            Log.d(TAG, "solved counter=${solution.counter} tookMs=$tookMs")

            // 3. POST verify and harvest cookie.
            verify(challenge, solution, tookMs)
        } catch (e: Exception) {
            Log.w(TAG, "solve failed", e)
            null
        }
    }

    private fun fetchChallengeRaw(): String? {
        val url = "$BASE_URL/altcha/challenge?ts=${System.currentTimeMillis()}"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .header("Origin", ORIGIN)
            .header("Accept", "application/json")
            .get()
            .build()
        sharedClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "challenge HTTP ${resp.code}")
                return null
            }
            return resp.body?.string()
        }
    }

    private fun verify(challenge: ChallengeResponse, solution: Solution, tookMs: Double): String? {
        // Build the payload as a COMPACT JSON string with hand-written
        // key order. Cannot use a serialization-library round-trip for
        // the inner `parameters` object because the server's signature
        // is HMAC over a canonical serialization we don't own — any
        // reordering / re-encoding breaks verification. Hand-writing
        // minimal-whitespace JSON in the captured key order keeps the
        // payload stable.
        val p = challenge.parameters
        val solutionJson =
            """{"counter":${solution.counter},""" +
                """"derivedKey":"${solution.derivedKey}",""" +
                """"time":$tookMs}"""
        val challengeJson =
            """{"parameters":{""" +
                """"algorithm":"${p.algorithm}",""" +
                """"cost":${p.cost},""" +
                """"expiresAt":${p.expiresAt},""" +
                """"keyLength":${p.keyLength},""" +
                """"keyPrefix":"${p.keyPrefix}",""" +
                """"nonce":"${p.nonce}",""" +
                """"salt":"${p.salt}"""" +
                """},"signature":"${challenge.signature}"}"""
        val payloadJson = """{"challenge":$challengeJson,"solution":$solutionJson}"""
        val payloadB64 = Base64.encodeToString(
            payloadJson.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP,
        )
        val body = """{"payload":"$payloadB64"}""".toRequestBody(JSON_MEDIA_TYPE)

        val req = Request.Builder()
            .url("$BASE_URL/altcha/verify")
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .header("Origin", ORIGIN)
            .header("Accept", "application/json, text/plain, */*")
            .post(body)
            .build()

        sharedClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "verify HTTP ${resp.code}: ${resp.body?.string()?.take(200)}")
                return null
            }
            // Extract captcha_verified_at value from Set-Cookie header(s).
            val cookies = resp.headers("Set-Cookie")
            for (cookie in cookies) {
                val match = COOKIE_REGEX.find(cookie)
                if (match != null) {
                    val value = match.groupValues[1]
                    Log.d(TAG, "verify success cookie length=${value.length}")
                    return value
                }
            }
            Log.w(TAG, "verify 200 but no captcha_verified_at Set-Cookie found")
            return null
        }
    }

    @Serializable
    internal data class ChallengeResponse(
        val parameters: WireParameters,
        val signature: String,
    )

    @Serializable
    internal data class WireParameters(
        val algorithm: String,
        val cost: Int,
        val expiresAt: Long,
        val keyLength: Int,
        val keyPrefix: String,
        val nonce: String,
        val salt: String,
    )

    private companion object {
        const val TAG = "NativeSquidSolver"
        const val BASE_URL = "https://qobuz.squid.wtf/api"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"
        const val REFERER = "https://qobuz.squid.wtf/"
        const val ORIGIN = "https://qobuz.squid.wtf"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val COOKIE_REGEX = Regex("""captcha_verified_at=([^;\s]+)""")
        val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
