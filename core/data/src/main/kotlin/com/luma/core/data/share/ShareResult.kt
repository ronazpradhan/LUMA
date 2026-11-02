package com.luma.core.data.share

/** The outcome of a call to a share host. LUMA has none (see ShareConfig.API_BASE_URLS), so every call is Failed. */
sealed interface ShareResult<out T> {
    data class Ok<T>(val value: T) : ShareResult<T>
    data object NotFound : ShareResult<Nothing>
    data object Gone : ShareResult<Nothing>
    data object Forbidden : ShareResult<Nothing>
    /** 400/413/other 4xx: the server will never accept this request as sent, so don't retry it. */
    data class Rejected(val code: Int) : ShareResult<Nothing>
    /** Network failure, 429 or 5xx: worth retrying later. */
    data class Failed(val message: String?) : ShareResult<Nothing> {
        companion object {
            /** The [message] for a 429, so a screen can say "wait a minute" rather than "couldn't reach". */
            const val RATE_LIMITED = "HTTP 429"
        }
    }
}
