package com.stash.data.download.lossless.squid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the Kotlin port of squid.wtf's per-iteration-truncating
 * SHA-256 PoW against a vector captured from a live HAR against the
 * production `/api/altcha/verify` endpoint.
 *
 * NOTE: This algorithm intentionally does NOT match altcha-lib v2's
 * published test vectors — squid.wtf's deployed widget diverges from
 * the public spec by truncating the digest to keyLength bytes every
 * iteration. See KDoc on [AltchaSolver].
 *
 * If the captured vector below fails, the server will reject our
 * verify payload — the algorithm is bit-exact by design.
 */
class AltchaSolverTest {

    /**
     * Builds the password buffer the deriveKey function expects:
     * `nonce_bytes || uint32_BE(counter)`.
     */
    private fun password(nonceHex: String, counter: Int): ByteArray {
        val nonce = hexToBytes(nonceHex)
        return nonce + byteArrayOf(
            (counter ushr 24).toByte(),
            (counter ushr 16).toByte(),
            (counter ushr 8).toByte(),
            counter.toByte(),
        )
    }

    /**
     * Captured from a successful live HAR against
     * `qobuz.squid.wtf/api/altcha/verify` — these exact bytes produced
     * a 200 OK + `Set-Cookie: captcha_verified_at=...` response. If
     * this test fails, the deployed algorithm has changed and the
     * NativeSquidCaptchaSolver will start getting rejected.
     */
    @Test fun `iter-trunc keyLength=16 cost=1000 matches squidwtf HAR capture`() {
        val derived = AltchaSolver.deriveSha256(
            salt = hexToBytes("e65ed142765c347ee6796a236764127f"),
            password = password("b0eb239700bc6339bd9a709a9c053dfc", 14),
            cost = 1000,
            keyLength = 16,
        )
        assertEquals("007f879634bb579611f8bee2d0d24812", bytesToHex(derived))
    }

    /**
     * Single-iteration test: with cost=1 the algorithm reduces to
     * `SHA-256(salt || password)[0 ..< keyLength]`, which IS a prefix
     * of the full 32-byte digest. (For cost > 1 this no longer holds —
     * each round's truncation cascades.)
     */
    @Test fun `keyLength truncates the single-round digest`() {
        val nonce = "b0eb239700bc6339bd9a709a9c053dfc"
        val salt = "e65ed142765c347ee6796a236764127f"
        val full = AltchaSolver.deriveSha256(
            salt = hexToBytes(salt),
            password = password(nonce, 14),
            cost = 1,
            keyLength = 32,
        )
        val truncated = AltchaSolver.deriveSha256(
            salt = hexToBytes(salt),
            password = password(nonce, 14),
            cost = 1,
            keyLength = 16,
        )
        assertEquals(16, truncated.size)
        // Single-round truncation = take the first keyLength bytes of
        // the full digest.
        assertEquals(bytesToHex(full.copyOf(16)), bytesToHex(truncated))
    }

    /**
     * End-to-end: feed the solver a challenge whose answer we control.
     * Expectation: it finds *some* counter whose first byte is 0x00.
     * The contract is "derived key starts with prefix", not "specific
     * counter value" — that's a function of the (changed) algorithm.
     */
    @Test fun `solve finds a counter producing the required prefix`() {
        val params = ChallengeParameters(
            algorithm = "SHA-256",
            cost = 1000,
            expiresAt = 1_777_678_486L,
            keyLength = 16,
            keyPrefix = "00",  // 1 byte == 0x00 → ~1/256 hit rate
            nonce = "fdde129879f8c9ad085f97ce5911ea9b",
            salt = "3aa2d75295816724c70792dac4ba9f4f",
        )
        val solution = AltchaSolver.solve(params)

        // Re-derive at the returned counter; first byte of derived key
        // must be 0x00 to match the prefix.
        val derived = AltchaSolver.deriveSha256(
            salt = hexToBytes(params.salt),
            password = password(params.nonce, solution.counter),
            cost = params.cost,
            keyLength = params.keyLength,
        )
        assertEquals(0.toByte(), derived[0])
        assertEquals(bytesToHex(derived), solution.derivedKey)
        assertTrue("counter should be reasonable, got ${solution.counter}", solution.counter < 5000)
    }

    @Test fun `solve respects a longer prefix`() {
        // Two-byte prefix (16 bits) → ~1/65536 hit rate; still fine
        // with the maxCounter cap.
        val params = ChallengeParameters(
            algorithm = "SHA-256",
            cost = 100,
            expiresAt = 0,
            keyLength = 16,
            keyPrefix = "00aa",  // first 2 bytes must be 0x00, 0xAA — extremely rare
            nonce = "fdde129879f8c9ad085f97ce5911ea9b",
            salt = "3aa2d75295816724c70792dac4ba9f4f",
        )
        // We don't expect this to find a solution quickly, but if it
        // does, the derived key must begin with the prefix bytes.
        val solution = runCatching { AltchaSolver.solve(params, maxCounter = 200_000) }
            .getOrNull() ?: return  // acceptable to give up; cap is the safety net
        val derived = AltchaSolver.deriveSha256(
            salt = hexToBytes(params.salt),
            password = password(params.nonce, solution.counter),
            cost = params.cost,
            keyLength = params.keyLength,
        )
        assertEquals(0x00.toByte(), derived[0])
        assertEquals(0xAA.toByte(), derived[1])
    }

    @Test fun `hex round-trip is stable`() {
        val original = "fdde129879f8c9ad085f97ce5911ea9b"
        assertEquals(original, bytesToHex(hexToBytes(original)))
    }
}
