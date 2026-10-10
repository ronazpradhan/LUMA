package com.stash.core.data.audio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for [LoudnessMeasurer] — the ffmpeg ebur128 wrapper.
 *
 * Fixtures live in `src/test/resources/ffmpeg_output/`. Each fixture is a
 * verbatim (or close-to-verbatim) slice of an ffmpeg stderr stream, so the
 * parser is exercised against text shaped exactly like the binary emits in
 * production.
 *
 * The bridge is faked so no native ffmpeg is invoked during the JVM test run.
 */
class LoudnessMeasurerTest {

    private val fakeBridge = FakeFFmpegBridge()
    private val fakeTrackDao = io.mockk.mockk<com.stash.core.data.db.dao.TrackDao>(relaxed = true)
    // These tests exercise measure() directly, which is charging-agnostic —
    // only the download-time measureAndPersistInBackground defers off power
    // (see LoudnessMeasurerDeferralTest). Charging = true keeps them honest
    // either way.
    private val alwaysCharging = io.mockk.mockk<ChargingMonitor> {
        io.mockk.every { isCharging() } returns true
    }
    private val measurer = LoudnessMeasurer(fakeBridge, fakeTrackDao, alwaysCharging)

    private val createdTempFiles = mutableListOf<File>()

    @After
    fun cleanupTempFiles() {
        createdTempFiles.forEach { it.delete() }
        createdTempFiles.clear()
    }

    @Test
    fun parsesNormalOutput() {
        fakeBridge.nextOutput = resource("ffmpeg_output/normal.txt")

        val r = runBlocking { measurer.measure(tempFile("dummy.mp3")) }

        assertTrue(
            "expected Success, got $r",
            r is LoudnessMeasurer.Result.Success,
        )
        r as LoudnessMeasurer.Result.Success
        assertEquals(-14.2f, r.lufs, 0.01f)
        assertEquals(-0.3f, r.truePeakDbfs, 0.01f)
    }

    @Test
    fun parsesTheSummaryNotTheFirstProgressLine() {
        // Production stderr contains a per-frame progress line for every 100ms
        // of audio BEFORE the Summary block, and the first frames always read
        // `I: -70.0 LUFS` (the gating floor — nothing integrated yet). The
        // parser must anchor to the Summary. This is not hypothetical: every
        // one of the 857 measured tracks on the reference device had stored
        // exactly -70.0, because the old first-match parse always won on
        // frame line one. The Summary-only `normal.txt` fixture could never
        // catch it.
        fakeBridge.nextOutput = resource("ffmpeg_output/full_run_with_frame_lines.txt")

        val r = runBlocking { measurer.measure(tempFile("dummy.flac")) }

        assertTrue("expected Success, got $r", r is LoudnessMeasurer.Result.Success)
        r as LoudnessMeasurer.Result.Success
        assertEquals(-9.8f, r.lufs, 0.01f)
        assertEquals(0.3f, r.truePeakDbfs, 0.01f)
    }

    @Test
    fun shortClip_returnsFailed() {
        fakeBridge.nextOutput = resource("ffmpeg_output/short_clip.txt")

        val r = runBlocking { measurer.measure(tempFile("dummy.mp3")) }

        assertTrue(
            "expected Failed (inf LUFS), got $r",
            r is LoudnessMeasurer.Result.Failed,
        )
    }

    @Test
    fun noSummary_returnsFailed() {
        fakeBridge.nextOutput = resource("ffmpeg_output/no_summary.txt")

        val r = runBlocking { measurer.measure(tempFile("dummy.mp3")) }

        assertTrue(
            "expected Failed (no Summary block), got $r",
            r is LoudnessMeasurer.Result.Failed,
        )
    }

    @Test
    fun concurrentCalls_serializeBehindMutex() = runTest {
        // Force the fake to pause mid-call so a second invocation can race in
        // and observe the in-flight state. A correctly-mutex'd measurer must
        // serialize: at no point may both calls be inside the bridge.
        val pausingBridge = PausingFakeFFmpegBridge(
            output = resource("ffmpeg_output/normal.txt"),
        )
        val serialMeasurer = LoudnessMeasurer(pausingBridge, fakeTrackDao, alwaysCharging)
        val file = tempFile("dummy.mp3")

        val first = async { serialMeasurer.measure(file) }

        // Wait until the first call is inside the bridge.
        pausingBridge.entered.await()
        assertEquals(1, pausingBridge.inFlight)

        // Launch the second call. It must NOT enter the bridge while the first
        // is still parked — verify by spinning briefly and checking inFlight.
        val second = async { serialMeasurer.measure(file) }
        repeat(20) {
            delay(5)
            assertTrue(
                "second call entered the bridge while first was in flight (inFlight=${pausingBridge.inFlight})",
                pausingBridge.inFlight <= 1,
            )
        }
        assertEquals(1, pausingBridge.callsStarted)

        // Release the first call; second should now proceed.
        pausingBridge.release.complete(Unit)

        val r1 = first.await()
        val r2 = second.await()
        assertTrue(r1 is LoudnessMeasurer.Result.Success)
        assertTrue(r2 is LoudnessMeasurer.Result.Success)
        assertEquals(2, pausingBridge.callsStarted)
        assertEquals(0, pausingBridge.inFlight)
    }

    // ---- helpers ----

    private fun resource(path: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "missing fixture: $path"
        }.bufferedReader().readText()

    private fun tempFile(name: String): File {
        val f = File.createTempFile("loudness-test-", "-$name")
        createdTempFiles.add(f)
        return f
    }
}

/**
 * Simple [FFmpegBridge] fake — returns [nextOutput] on each call and records
 * the args it was invoked with for after-the-fact assertions.
 */
private class FakeFFmpegBridge : FFmpegBridge {
    var nextOutput: String = ""
    val callLog = mutableListOf<List<String>>()

    override suspend fun runWithStderrCapture(args: List<String>): String {
        callLog.add(args)
        return nextOutput
    }
}

/**
 * [FFmpegBridge] fake that suspends mid-call so the test can observe whether
 * a second `measure()` invocation enters before the first has finished.
 *
 * - [entered] completes once the bridge has been entered (first call only).
 * - [release] gates the bridge's return.
 * - [inFlight] is the count of bridge invocations currently between entry and
 *   exit (must stay ≤ 1 for a correctly-serialized measurer).
 * - [callsStarted] increments on entry.
 */
private class PausingFakeFFmpegBridge(
    private val output: String,
) : FFmpegBridge {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()

    @Volatile var inFlight: Int = 0
        private set
    @Volatile var callsStarted: Int = 0
        private set

    override suspend fun runWithStderrCapture(args: List<String>): String {
        synchronized(this) {
            inFlight += 1
            callsStarted += 1
        }
        if (!entered.isCompleted) entered.complete(Unit)
        // First call waits for the release; subsequent calls do not need to
        // (the test only needs to prove the second call was blocked while the
        // first was in flight).
        if (callsStarted == 1) release.await()
        synchronized(this) { inFlight -= 1 }
        return output
    }
}
