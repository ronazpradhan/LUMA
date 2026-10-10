package com.stash.core.data.listenbrainz

import com.google.common.truth.Truth.assertThat
import com.stash.core.data.listen.Listen
import com.stash.core.data.listen.SinkResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * How the sink maps ListenBrainz responses onto the drain loop's retry semantics.
 * Getting this mapping wrong is silent data loss: anything classified Rejected
 * consumes an attempt and is discarded at the cap.
 */
class ListenBrainzSinkTest {

    private val api = mockk<ListenBrainzApiClient>()
    private val prefs = mockk<ListenBrainzPreference>()

    private val sink = ListenBrainzSink(api, prefs)

    private val listen = Listen(
        eventId = 1L,
        artist = "Liz Phair",
        title = "Glory",
        album = "Exile in Guyville",
        durationMs = 90_000L,
        startedAtMs = 1_785_365_937_000L,
    )

    private suspend fun withToken() {
        coEvery { prefs.tokenNow() } returns "tok"
    }

    @Test fun `accepted maps to success`() = runTest {
        withToken()
        coEvery { api.submitListens(any(), any()) } returns ListenBrainzApiClient.Response.Accepted

        assertThat(sink.submit(listOf(listen))).isEqualTo(SinkResult.Success)
    }

    /**
     * The case that made this test file exist. A token that passes
     * /1/validate-token still gets 401 on submit when the MetaBrainz account has
     * no verified email — observed on device 2026-07-30. That is an account
     * problem, not a listen problem: the listens are valid and will submit once
     * the user verifies. Classifying it Rejected would consume an attempt each
     * time and discard them at the cap, for something entirely fixable.
     */
    @Test fun `401 is transient so the listens are held not discarded`() = runTest {
        withToken()
        coEvery { api.submitListens(any(), any()) } returns
            ListenBrainzApiClient.Response.Refused(401, "unverified email")

        assertThat(sink.submit(listOf(listen))).isInstanceOf(SinkResult.Transient::class.java)
    }

    /** A malformed payload IS futile to retry unchanged, so it stays a rejection. */
    @Test fun `other 4xx stays a rejection`() = runTest {
        withToken()
        coEvery { api.submitListens(any(), any()) } returns
            ListenBrainzApiClient.Response.Refused(400, "bad payload")

        assertThat(sink.submit(listOf(listen))).isInstanceOf(SinkResult.Rejected::class.java)
    }

    @Test fun `service outage is transient`() = runTest {
        withToken()
        coEvery { api.submitListens(any(), any()) } returns
            ListenBrainzApiClient.Response.Unavailable("503")

        assertThat(sink.submit(listOf(listen))).isInstanceOf(SinkResult.Transient::class.java)
    }

    /** No token is a temporary state too — never spend retries on it. */
    @Test fun `a missing token holds the listens`() = runTest {
        coEvery { prefs.tokenNow() } returns null

        assertThat(sink.submit(listOf(listen))).isInstanceOf(SinkResult.Transient::class.java)
    }

    /**
     * Fail closed on an unknown cutoff: submitting from epoch zero would push the
     * user's entire recorded history in one flood.
     */
    @Test fun `an unknown connect cutoff submits nothing`() = runTest {
        coEvery { prefs.connectedAtNow() } returns null

        assertThat(sink.listeningSinceMs()).isEqualTo(Long.MAX_VALUE)
    }
}
