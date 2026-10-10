package com.stash.data.download.lossless.qbdlx

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class QobuzLoginClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: QobuzLoginClient
    private val creds = QobuzWebCreds(appId = "712109809", appSecret = "589be88e4538daea11f509d29e4a23b1")

    @Before fun setUp() {
        server = MockWebServer(); server.start()
        // Fake web-creds scrape so login classification is tested without a network.
        client = QobuzLoginClient(OkHttpClient(), QobuzWebCredentialsClient(OkHttpClient())).also {
            it.baseUrl = server.url("/").toString().trimEnd('/')
            it.fetchCreds = { creds }
        }
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun `paid account yields Success with the scraped signing pair`() = runTest {
        server.enqueue(MockResponse().setBody("""{"user_auth_token":"UAT123","user":{"credential":{"parameters":{"lossless_streaming":true}}}}"""))
        val r = client.login("me@example.com", "pw")
        assertThat(r).isInstanceOf(QobuzLoginResult.Success::class.java)
        val ok = r as QobuzLoginResult.Success
        assertThat(ok.token).isEqualTo("UAT123")
        assertThat(ok.appId).isEqualTo("712109809")
        assertThat(ok.appSecret).isEqualTo("589be88e4538daea11f509d29e4a23b1")
        // password is MD5-hashed on the wire, never sent in the clear
        val sent = server.takeRequest().path!!
        assertThat(sent).doesNotContain("password=pw")
        assertThat(sent).contains("app_id=712109809")
    }

    @Test fun `wrong password is InvalidCredentials`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"status":"error","message":"Invalid username/email and password combination"}"""))
        assertThat(client.login("me@example.com", "bad")).isEqualTo(QobuzLoginResult.InvalidCredentials)
    }

    @Test fun `free account with null parameters is FreeAccount`() = runTest {
        server.enqueue(MockResponse().setBody("""{"user_auth_token":"UAT","user":{"credential":{"parameters":null}}}"""))
        assertThat(client.login("free@example.com", "pw")).isEqualTo(QobuzLoginResult.FreeAccount)
    }

    @Test fun `free account with empty parameters is FreeAccount`() = runTest {
        server.enqueue(MockResponse().setBody("""{"user_auth_token":"UAT","user":{"credential":{"parameters":{}}}}"""))
        assertThat(client.login("free@example.com", "pw")).isEqualTo(QobuzLoginResult.FreeAccount)
    }

    @Test fun `a 200 with no token is Error`() = runTest {
        server.enqueue(MockResponse().setBody("""{"user":{"credential":{"parameters":{"x":1}}}}"""))
        assertThat(client.login("me@example.com", "pw")).isEqualTo(QobuzLoginResult.Error)
    }

    @Test fun `a 5xx is Error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        assertThat(client.login("me@example.com", "pw")).isEqualTo(QobuzLoginResult.Error)
    }

    @Test fun `a failed web-creds scrape is Error before any login call`() = runTest {
        val noCreds = QobuzLoginClient(OkHttpClient(), QobuzWebCredentialsClient(OkHttpClient())).also {
            it.baseUrl = server.url("/").toString().trimEnd('/')
            it.fetchCreds = { null }
        }
        assertThat(noCreds.login("me@example.com", "pw")).isEqualTo(QobuzLoginResult.Error)
        assertThat(server.requestCount).isEqualTo(0) // never hit login
    }
}
