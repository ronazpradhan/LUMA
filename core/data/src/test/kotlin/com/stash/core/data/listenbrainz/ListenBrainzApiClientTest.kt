package com.stash.core.data.listenbrainz

import com.google.common.truth.Truth.assertThat
import com.stash.core.data.listen.Listen
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Wire-level tests, deliberately not against a mocked client.
 *
 * The two mistakes most likely here are both accepted silently by ListenBrainz
 * rather than rejected, so no amount of "it returned success" proves correctness:
 *
 *  - `listened_at` in millis instead of seconds files the listen tens of thousands
 *    of years in the future.
 *  - a multi-item payload sent as `listen_type: single` is refused, and a
 *    single-item payload sent as `import` is not what the API documents.
 *
 * Robolectric is needed only because the client logs through `android.util.Log`.
 * `runBlocking` rather than `runTest`: real sockets against MockWebServer want a
 * real clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ListenBrainzApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: ListenBrainzApiClient

    private val startedAtMs = 1_785_365_937_000L
    private val expectedSeconds = 1_785_365_937L

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        client = ListenBrainzApiClient(OkHttpClient()).apply {
            baseUrl = server.url("/").toString().removeSuffix("/")
        }
    }

    @After fun tearDown() { server.shutdown() }

    private fun listen(title: String = "Weird Fishes") = Listen(
        eventId = 1L,
        artist = "Radiohead",
        title = title,
        album = "In Rainbows",
        durationMs = 318_000L,
        startedAtMs = startedAtMs,
    )

    @Test fun `a single listen submits seconds not millis`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))

        val result = client.submitListens("tok", listOf(listen()))

        assertThat(result).isEqualTo(ListenBrainzApiClient.Response.Accepted)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        val payload = body.getJSONArray("payload").getJSONObject(0)
        assertThat(payload.getLong("listened_at")).isEqualTo(expectedSeconds)
    }

    @Test fun `one listen is single and several are import`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        client.submitListens("tok", listOf(listen()))
        assertThat(JSONObject(server.takeRequest().body.readUtf8()).getString("listen_type"))
            .isEqualTo("single")

        server.enqueue(MockResponse().setResponseCode(200))
        client.submitListens("tok", listOf(listen("A"), listen("B")))
        assertThat(JSONObject(server.takeRequest().body.readUtf8()).getString("listen_type"))
            .isEqualTo("import")
    }

    @Test fun `metadata carries artist title release and duration`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        client.submitListens("tok", listOf(listen()))

        val meta = JSONObject(server.takeRequest().body.readUtf8())
            .getJSONArray("payload").getJSONObject(0)
            .getJSONObject("track_metadata")
        assertThat(meta.getString("artist_name")).isEqualTo("Radiohead")
        assertThat(meta.getString("track_name")).isEqualTo("Weird Fishes")
        assertThat(meta.getString("release_name")).isEqualTo("In Rainbows")
        assertThat(meta.getJSONObject("additional_info").getLong("duration_ms"))
            .isEqualTo(318_000L)
    }

    @Test fun `the token is sent as a Token authorization header`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        client.submitListens("  my-token  ", listOf(listen()))

        assertThat(server.takeRequest().getHeader("Authorization")).isEqualTo("Token my-token")
    }

    /** A now-playing listen has not finished, so it must carry no timestamp. */
    @Test fun `playing now omits listened_at`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        client.submitPlayingNow("tok", listen())

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertThat(body.getString("listen_type")).isEqualTo("playing_now")
        assertThat(body.getJSONArray("payload").getJSONObject(0).has("listened_at")).isFalse()
    }

    @Test fun `4xx is refused and 5xx is unavailable`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("bad token"))
        val refused = client.submitListens("tok", listOf(listen()))
        assertThat(refused).isInstanceOf(ListenBrainzApiClient.Response.Refused::class.java)
        assertThat((refused as ListenBrainzApiClient.Response.Refused).code).isEqualTo(401)

        server.enqueue(MockResponse().setResponseCode(503))
        assertThat(client.submitListens("tok", listOf(listen())))
            .isInstanceOf(ListenBrainzApiClient.Response.Unavailable::class.java)
    }

    /** A dead socket must read as unavailable, never as a rejected payload. */
    @Test fun `transport failure is unavailable not refused`() = runBlocking {
        server.shutdown()

        assertThat(client.submitListens("tok", listOf(listen())))
            .isInstanceOf(ListenBrainzApiClient.Response.Unavailable::class.java)
    }

    @Test fun `an empty batch is accepted without a request`() = runBlocking {
        assertThat(client.submitListens("tok", emptyList()))
            .isEqualTo(ListenBrainzApiClient.Response.Accepted)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test fun `validateToken accepts a token the service says is valid`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"code":200,"message":"Token valid.","valid":true}"""),
        )
        assertThat(client.validateToken("good")).isTrue()
    }

    /**
     * The bug this test exists for: /1/validate-token answers HTTP **200** with
     * `valid:false` for a bad token — it reports on the token rather than rejecting
     * the request. An implementation that checks only the status code accepts any
     * string, which is what shipped until an on-device test connected successfully
     * with "not-a-real-token-12345". The previous version of this test asserted
     * 401 -> false, a response this endpoint does not give, so it passed while the
     * real path was broken.
     */
    @Test fun `validateToken rejects a bad token despite HTTP 200`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"code":200,"message":"Token invalid.","valid":false}"""),
        )
        assertThat(client.validateToken("not-a-real-token-12345")).isFalse()
    }

    /** Fail closed: an unreadable body must not count as a valid token. */
    @Test fun `validateToken rejects an unparseable body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        assertThat(client.validateToken("weird")).isFalse()
    }
}
