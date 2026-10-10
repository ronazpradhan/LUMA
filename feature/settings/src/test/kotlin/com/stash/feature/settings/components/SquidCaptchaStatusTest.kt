package com.stash.feature.settings.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [squidCaptchaStatus] — the pure mapping function that
 * collapses (current cookie, last-known-bad cookie) into the tri-state
 * displayed by `LosslessRoutingStatus`.
 *
 * The bug this guards: pre-fix, the UI used `cookie.isNotEmpty()` as a
 * proxy for "active", which lied after server-side expiry — the
 * "solve captcha →" link disappeared even when the cookie was stale.
 */
class SquidCaptchaStatusTest {

    @Test fun `empty cookie → NotConfigured regardless of bad cookie`() {
        assertEquals(SquidCaptchaStatus.NotConfigured, squidCaptchaStatus("", null))
        assertEquals(SquidCaptchaStatus.NotConfigured, squidCaptchaStatus("", "anything"))
    }

    @Test fun `non-empty cookie + no known-bad → Active`() {
        assertEquals(SquidCaptchaStatus.Active, squidCaptchaStatus("fresh-cookie", null))
    }

    @Test fun `non-empty cookie + different known-bad → Active`() {
        // User pasted a new cookie; the previous bad value is stale.
        assertEquals(
            SquidCaptchaStatus.Active,
            squidCaptchaStatus(current = "fresh-cookie", lastKnownBad = "stale-cookie"),
        )
    }

    @Test fun `non-empty cookie + matching known-bad → Expired`() {
        // The current cookie was the one that just got 403'd.
        assertEquals(
            SquidCaptchaStatus.Expired,
            squidCaptchaStatus(current = "expired-cookie", lastKnownBad = "expired-cookie"),
        )
    }
}
