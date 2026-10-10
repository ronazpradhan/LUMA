// StashRenderersFactoryTest.kt
package com.stash.core.media.equalizer

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

/**
 * Verifies the audio-processor chain order produced by [StashRenderersFactory].
 *
 * Media3's [androidx.media3.exoplayer.audio.DefaultAudioSink] wraps the
 * processor array internally with no public accessor, so the factory exposes an
 * `internal` test-only function that returns the same array used by
 * `buildAudioSink`. Both call sites build the array via the same private
 * helper, guaranteeing the test verifies the production wiring.
 */
class StashRenderersFactoryTest {
  private val context = mockk<Context>(relaxed = true)
  private val eqController = mockk<EqController>(relaxed = true)
  private val loudnessController = mockk<LoudnessController>(relaxed = true)

  @Test fun `chain order is preamp, eq, bass, loudness, limiter`() {
    val factory = StashRenderersFactory(context, eqController, loudnessController)
    val processors = factory.audioProcessorsForTest()
    assertThat(processors.map { it::class.simpleName }).containsExactly(
      "PreampProcessor",
      "EqProcessor",
      "BassShelfProcessor",
      "LoudnessGainProcessor",
      "SoftClipLimiterProcessor",
    ).inOrder()
  }
}
