package com.stash.core.data.audio

import android.util.Log
import com.stash.core.data.db.dao.TrackDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Measures integrated loudness (LUFS) and true peak (dBFS) of an audio file
 * using ffmpeg's `ebur128` filter.
 *
 * ## Why this class exists
 *
 * EBU R128 loudness is the only standardised way to compare playback level
 * across tracks of mixed mastering era / genre. We measure once per track at
 * download/import time, persist the LUFS value, and apply per-track makeup
 * gain at playback (see [LoudnessGainProcessor]). This wrapper isolates the
 * ffmpeg invocation behind a coroutine-suspending API so the backfill worker
 * and the import pipeline can treat measurement as just another I/O call.
 *
 * ## ffmpeg invocation
 *
 * Args mirror the canonical "measure-only" recipe from the EBU R128 docs:
 *
 *   `-nostats -hide_banner -i <file> -af ebur128=peak=true -f null -`
 *
 * - `peak=true` enables the true-peak limiter on the filter side so the
 *   `Peak:` line appears in the Summary block (it's omitted by default).
 * - `-f null -` discards the decoded audio after analysis — we only care
 *   about the stderr Summary block, not the decoded samples.
 * - `-nostats -hide_banner` keeps stderr tight so the parser regexes don't
 *   have to wade through frame=…  progress lines and the long banner.
 *
 * ## Output parsing
 *
 * `ebur128` emits a single Summary block on stderr at end-of-stream. The
 * parser regexes pull `I:` (integrated loudness in LUFS) and `Peak:` (true
 * peak in dBFS). `-inf LUFS` is treated as a failure — it usually means the
 * track was shorter than the integration window (~3s) or all-silence, and
 * a Float infinity would otherwise propagate into [LoudnessGainProcessor]
 * with disastrous results.
 *
 * ## Concurrency
 *
 * The internal [mutex] serialises calls so we never have two ffmpeg
 * processes contending for CPU and decoder buffers simultaneously. The
 * backfill worker measures one track at a time anyway, but the mutex is
 * cheap insurance against parallel import paths slipping past in the
 * future.
 */
@Singleton
class LoudnessMeasurer @Inject constructor(
    private val bridge: FFmpegBridge,
    private val trackDao: TrackDao,
    private val chargingMonitor: ChargingMonitor,
) {
    // NOT a constructor parameter — Hilt doesn't honour Kotlin default values,
    // so injecting CoroutineDispatcher would require a project-wide @Qualifier
    // binding the codebase doesn't currently have. Keep the dispatcher as a
    // private property so production always uses Dispatchers.IO; tests that
    // need a different dispatcher can construct via a future overload.
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
    private val mutex = Mutex()

    // Application-scope coroutine context for fire-and-forget background
    // measurements triggered by [measureAndPersistInBackground]. A
    // SupervisorJob so one failed measurement doesn't cancel siblings, and
    // Dispatchers.IO so we're already off the main thread when the launch
    // lands. This scope is intentionally NOT cancelled at the end of any
    // single suspending caller's coroutine — the whole point is to outlive
    // the download flow that triggered the measurement.
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Measures [file] and returns the parsed LUFS/peak, or [Result.Failed]
     * with a human-readable reason if ffmpeg crashes or the Summary block
     * is unparseable / missing / contains `-inf`.
     *
     * Never throws — even an underlying I/O failure inside the bridge is
     * wrapped into a [Result.Failed]. Callers persist the failure sentinel
     * (`loudnessLufs = NULL`, `loudnessMeasuredAt = now()`) so the backfill
     * worker doesn't retry the same broken file forever.
     */
    suspend fun measure(file: File): Result = mutex.withLock {
        withContext(dispatcher) {
            val args = listOf(
                "-nostats", "-hide_banner",
                "-i", file.absolutePath,
                "-af", "ebur128=peak=true",
                "-f", "null", "-",
            )
            val stderr = runCatching { bridge.runWithStderrCapture(args) }
                .getOrElse {
                    return@withContext Result.Failed(
                        "ffmpeg invocation failed: ${it.message}",
                    )
                }
            parseSummary(stderr)
        }
    }

    /**
     * Parses ffmpeg's stderr Summary block. Visible-for-tests so the
     * fixture suite can hammer the parser directly without round-tripping
     * through the suspending [measure] API.
     */
    internal fun parseSummary(stderr: String): Result {
        if (!stderr.contains("Summary:")) {
            return Result.Failed("no summary block")
        }
        // Parse ONLY the text after "Summary:". ebur128 also prints a
        // progress line per 100ms of audio ABOVE it, and the first frames
        // always read `I: -70.0 LUFS` (the gating floor) — a first-match
        // scan over the whole stream stored exactly -70.0 for every track
        // ever measured on device. The true-peak regex only ever matched
        // inside the Summary (progress lines say TPK:, not Peak:), which is
        // why peaks were always right while loudness was always wrong.
        val summary = stderr.substringAfter("Summary:")
        val lufsMatch = LUFS_REGEX.find(summary)
            ?: return Result.Failed("could not parse integrated loudness")
        val lufsRaw = lufsMatch.groupValues[1]
        // Explicitly reject -inf / inf BEFORE toFloatOrNull, because Kotlin's
        // parser happily turns "-inf" into Float.NEGATIVE_INFINITY which would
        // then sail through the isInfinite check below if we let it — keeping
        // the textual check first makes the failure path explicit in logs.
        if (lufsRaw.equals("-inf", ignoreCase = true) ||
            lufsRaw.equals("inf", ignoreCase = true)
        ) {
            return Result.Failed("integrated loudness is -inf (track too short / silent)")
        }
        val lufs = lufsRaw.toFloatOrNull()
            ?: return Result.Failed("could not parse integrated loudness number: $lufsRaw")
        if (lufs.isInfinite() || lufs.isNaN()) {
            return Result.Failed("integrated loudness is inf/NaN (track too short?)")
        }
        val peakMatch = PEAK_REGEX.find(summary)
            ?: return Result.Failed("could not parse true peak")
        val peak = peakMatch.groupValues[1].toFloatOrNull()
            ?: return Result.Failed("could not parse true peak number")
        return Result.Success(lufs = lufs, truePeakDbfs = peak)
    }

    /**
     * Fire-and-forget background measurement triggered from the download
     * path. Returns immediately; the actual ffmpeg invocation + DB write
     * happens on [backgroundScope] some time after the caller returns.
     *
     * Why fire-and-forget: measuring a single FLAC track via ffmpeg's
     * ebur128 takes ~25–50 s on a mid-range phone (the decode dominates).
     * Running it synchronously inside [TrackFinalizer.finalizeFile]
     * serialised every album download behind the per-track measurement
     * — a 25-track album took ~12 minutes of additional dead time. By
     * detaching it onto a dedicated scope the download flow returns as
     * soon as the file is on disk, and loudness data trickles in over
     * the next few minutes in the background.
     *
     * The [mutex] in [measure] still serialises concurrent measurements
     * so we don't burn N CPUs simultaneously. Failures are non-fatal:
     * the failure path writes a NULL [Failed] row via [markLoudnessFailed]
     * so the daily backfill worker won't pick it up again immediately.
     */
    fun measureAndPersistInBackground(trackId: Long, file: File) {
        // Off power, hand this to LoudnessBackfillWorker instead of doing it
        // now. A full ffmpeg decode per downloaded track is the single most
        // expensive thing the download pipeline does, and downloads run in
        // OFFLINE mode — so the users who picked offline listening are
        // exactly the ones paying for it, on battery, hundreds of tracks at
        // a time during a sync.
        //
        // The worker already measures precisely these rows (its query is
        // `loudness_measured_at IS NULL`) and already waits for
        // charging + device-idle + battery-not-low, so deferring costs no
        // measurement — it just moves the CPU to where it is free. Leave the
        // row untouched: stamping it (even as failed) would drop it out of
        // that query and it would never be level-matched at all.
        if (!chargingMonitor.isCharging()) {
            Log.i(TAG, "not charging — deferring loudness for track $trackId to the backfill worker")
            return
        }
        backgroundScope.launch {
            when (val r = measure(file)) {
                is Result.Success -> runCatching {
                    trackDao.updateLoudness(
                        id = trackId,
                        lufs = r.lufs,
                        peak = r.truePeakDbfs,
                        now = System.currentTimeMillis(),
                    )
                }.onFailure { e ->
                    Log.w(TAG, "updateLoudness failed for track $trackId: ${e.message}")
                }
                is Result.Failed -> {
                    Log.w(
                        TAG,
                        "background loudness measurement failed for ${file.absolutePath}: ${r.reason}",
                    )
                    runCatching {
                        trackDao.markLoudnessFailed(
                            id = trackId,
                            now = System.currentTimeMillis(),
                        )
                    }.onFailure { e ->
                        Log.w(TAG, "markLoudnessFailed failed for track $trackId: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Result of a single [measure] call. [Failed.reason] is intended for
     * logging — surface to the user as "measurement failed" without the
     * raw text.
     */
    sealed class Result {
        data class Success(val lufs: Float, val truePeakDbfs: Float) : Result()
        data class Failed(val reason: String) : Result()
    }

    private companion object {
        private const val TAG = "LoudnessMeasurer"

        // `\bI:` would not match because `:` is non-word, but the preceding
        // whitespace gives us a reliable anchor. Allow `-inf` as a sentinel
        // value so the parser can flag it as a specific failure mode.
        val LUFS_REGEX = Regex("""\bI:\s+(-?[\d.]+|-?inf)\s+LUFS""")
        val PEAK_REGEX = Regex("""\bPeak:\s+(-?[\d.]+)\s+dBFS""")
    }
}
