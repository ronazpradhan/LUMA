package com.stash.core.media.equalizer

import kotlinx.serialization.Serializable

/**
 * In-memory loudness state held by [LoudnessController].
 *
 * `currentTrackGainDb` is the gain to apply *right now* (after the smoothing
 * ramp has run to completion); `currentTargetGainDb` is what we're ramping
 * toward when a track transition fires.
 */
@Serializable
data class LoudnessState(
    val enabled: Boolean = true,
    val targetLufs: Float = -14f,
    val currentTrackGainDb: Float = 0f,
    val currentTargetGainDb: Float = 0f,
)

/**
 * Compute the per-track gain in dB to reach [target] LUFS, with two safety belts:
 *
 *  1. Hard caps at −15 / +12 dB so a podcast at −40 LUFS doesn't get +26 dB boost.
 *  2. Peak-aware cap so the gain never lifts the track's peak above −1 dBFS; the
 *     [SoftClipLimiterProcessor] catches residual peaks from EQ/Bass stages.
 *
 * Returns 0 dB (bypass) when [trackLufs] is null or NaN (un-measured or
 * measurement-failed track).
 */
fun computeGain(
    trackLufs: Float?,
    trackPeakDbfs: Float?,
    target: Float = -14f,
): Float {
    if (trackLufs == null || trackLufs.isNaN()) return 0f
    val raw = target - trackLufs
    val capped = raw.coerceIn(-15f, +12f)
    val peakRoom = if (trackPeakDbfs != null) (-1f) - trackPeakDbfs else Float.MAX_VALUE
    return minOf(capped, peakRoom)
}
