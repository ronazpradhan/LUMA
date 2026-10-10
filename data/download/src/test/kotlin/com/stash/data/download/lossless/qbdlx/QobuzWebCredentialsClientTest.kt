package com.stash.data.download.lossless.qbdlx

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.Test

class QobuzWebCredentialsClientTest {
    private val client = QobuzWebCredentialsClient(OkHttpClient())

    @Test fun `extracts the app_id and app_secret pair from a bundle`() {
        // Shape lifted from the live open.qobuz.com main.js (2026-08-01).
        val js = """...,production:{maxim:1,app_id:"712109809",app_secret:"589be88e4538daea11f509d29e4a23b1",bag:2},..."""
        val creds = client.extractCreds(js)
        assertThat(creds).isNotNull()
        assertThat(creds!!.appId).isEqualTo("712109809")
        assertThat(creds.appSecret).isEqualTo("589be88e4538daea11f509d29e4a23b1")
    }

    @Test fun `returns null when the bundle has no creds`() {
        assertThat(client.extractCreds("just some unrelated javascript;")).isNull()
    }

    @Test fun `ignores a malformed pair (wrong lengths)`() {
        // 8-digit id / short secret must not match the 9-digit / 32-hex shape.
        assertThat(client.extractCreds("""app_id:"12345678",app_secret:"deadbeef"""")).isNull()
    }
}
