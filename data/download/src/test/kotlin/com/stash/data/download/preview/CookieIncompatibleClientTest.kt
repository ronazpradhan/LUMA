package com.stash.data.download.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * yt-dlp responds to `--cookies` + a cookie-incompatible client by skipping the
 * client, not by dropping the cookies:
 *
 *     WARNING: [youtube] Skipping client "android_vr" since it does not support cookies
 *
 * So sending both silently disabled the android_vr fast path. Measured on-device
 * 2026-07-29: 3.5s burned being refused, then 11.6s on the default multi-client
 * path — ~15s for every YouTube extraction, for as long as the two features have
 * coexisted. The failure is invisible without reading yt-dlp's stderr, which is
 * why it lasted, and why the rule gets a test of its own.
 */
class CookieIncompatibleClientTest {

    @Test fun `android_vr never gets a cookie file`() {
        assertThat(PreviewUrlExtractor.supportsCookies("android_vr")).isFalse()
    }

    /** The default client set is where cookies earn their keep (age-gated, private). */
    @Test fun `default client still gets cookies`() {
        assertThat(PreviewUrlExtractor.supportsCookies(null)).isTrue()
    }

    @Test fun `other pinned clients are unaffected`() {
        assertThat(PreviewUrlExtractor.supportsCookies("web")).isTrue()
        assertThat(PreviewUrlExtractor.supportsCookies("ios")).isTrue()
    }
}
