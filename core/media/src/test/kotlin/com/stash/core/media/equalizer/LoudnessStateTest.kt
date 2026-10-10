package com.stash.core.media.equalizer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LoudnessStateTest {
    @Test fun computeGain_nullLufs_returnsZero() {
        assertThat(computeGain(trackLufs = null, trackPeakDbfs = null)).isEqualTo(0f)
    }

    @Test fun computeGain_nanLufs_returnsZero() {
        assertThat(computeGain(trackLufs = Float.NaN, trackPeakDbfs = null)).isEqualTo(0f)
    }

    @Test fun computeGain_quietTrack_boostsByDifference() {
        // target = -14, track = -20  →  +6 dB
        // Peak at -10 dBFS leaves peakRoom = +9 dB, so the +6 result isn't peak-capped.
        assertThat(computeGain(-20f, -10f)).isWithin(0.001f).of(6f)
    }

    @Test fun computeGain_loudTrack_attenuatesByDifference() {
        // target = -14, track = -8  →  -6 dB
        assertThat(computeGain(-8f, -1f)).isWithin(0.001f).of(-6f)
    }

    @Test fun computeGain_clampsToPositive12dbMax() {
        // track = -40, raw would be +26 dB, capped at +12
        // Peak at -20 dBFS leaves peakRoom = +19 dB, so the +12 cap (not peakRoom) binds.
        assertThat(computeGain(-40f, -20f)).isWithin(0.001f).of(12f)
    }

    @Test fun computeGain_clampsToNegative15dbMin() {
        // track = +5, raw would be -19 dB, capped at -15
        assertThat(computeGain(+5f, 0f)).isWithin(0.001f).of(-15f)
    }

    @Test fun computeGain_peakAwareCapPreventsClip() {
        // track = -20 (raw +6 dB), but peak is -0.5 dBFS so peakRoom = -0.5
        // Result: min(+6, -0.5) = -0.5
        assertThat(computeGain(-20f, -0.5f)).isWithin(0.001f).of(-0.5f)
    }

    @Test fun computeGain_nullPeak_skipsPeakCap() {
        // No peak data → use full computed gain
        assertThat(computeGain(-20f, null)).isWithin(0.001f).of(6f)
    }
}
