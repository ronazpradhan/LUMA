package com.stash.core.data.audio

import com.google.common.truth.Truth.assertThat
import com.stash.core.data.db.dao.TrackDao
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import java.io.File

/**
 * Every downloaded track is fully decoded by ffmpeg to measure EBU R128
 * loudness. On a phone that is a real CPU bill, and it lands at the worst
 * possible moment: downloads only run in OFFLINE mode, so the users who
 * chose offline listening are the ones paying it, unbounded, on battery.
 * A multi-gigabyte sync means hundreds of full decodes.
 *
 * `LoudnessBackfillWorker` already does exactly this work under
 * charging + device-idle + battery-not-low. So off power, a download leaves
 * the row unmeasured and lets that worker have it — the measurement is not
 * skipped, it is moved to where it is free.
 *
 * The critical detail is that a deferred track must stay ELIGIBLE for the
 * worker: its query is `loudness_measured_at IS NULL`, so writing the
 * failure sentinel here would retire the track instead of deferring it,
 * and it would never be level-matched at all.
 */
class LoudnessMeasurerDeferralTest {

    private class FakeCharging(private val charging: Boolean) :
        ChargingMonitor(mockk(relaxed = true)) {
        override fun isCharging(): Boolean = charging
    }

    private class SignallingBridge(private val output: String = "") : FFmpegBridge {
        val invoked = CompletableDeferred<Unit>()
        @Volatile var calls = 0

        override suspend fun runWithStderrCapture(args: List<String>): String {
            calls++
            invoked.complete(Unit)
            return output
        }
    }

    private val createdTempFiles = mutableListOf<File>()

    @After
    fun cleanup() {
        createdTempFiles.forEach { it.delete() }
        createdTempFiles.clear()
    }

    private fun tempFile(): File =
        File.createTempFile("loudness", ".tmp").also { createdTempFiles += it }

    @Test
    fun `on battery ffmpeg never runs and the row is left for the backfill worker`() {
        val bridge = SignallingBridge()
        val trackDao = mockk<TrackDao>(relaxed = true)
        val measurer = LoudnessMeasurer(bridge, trackDao, FakeCharging(charging = false))

        measurer.measureAndPersistInBackground(trackId = 7L, file = tempFile())

        // The guard is synchronous — nothing is even launched — so this is a
        // deterministic assertion, not a race with a background coroutine.
        assertThat(bridge.calls).isEqualTo(0)
        // And the row keeps loudness_measured_at NULL. markLoudnessFailed
        // would stamp it and drop it out of tracksNeedingLoudness forever.
        coVerify(exactly = 0) { trackDao.markLoudnessFailed(any(), any()) }
        coVerify(exactly = 0) { trackDao.updateLoudness(any(), any(), any(), any()) }
    }

    @Test
    fun `on power the measurement still runs at download time`() {
        val bridge = SignallingBridge(NORMAL_SUMMARY)
        val trackDao = mockk<TrackDao>(relaxed = true)
        val measurer = LoudnessMeasurer(bridge, trackDao, FakeCharging(charging = true))

        measurer.measureAndPersistInBackground(trackId = 7L, file = tempFile())

        // Fire-and-forget on an IO scope by design, so await the signal
        // rather than sleeping.
        runBlocking { withTimeout(5_000) { bridge.invoked.await() } }
        assertThat(bridge.calls).isEqualTo(1)
    }

    private companion object {
        /** Minimal ebur128 Summary block — enough for the parser. */
        val NORMAL_SUMMARY = """
            [Parsed_ebur128_0 @ 0x0] Summary:

              Integrated loudness:
                I:         -9.6 LUFS
                Threshold: -20.2 LUFS

              True peak:
                Peak:      -0.5 dBFS
        """.trimIndent()
    }
}
