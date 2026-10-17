package com.luma.core.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The AMOLED contract: every color a full-screen ground can resolve to must
 * be pure #000000, or OLED pixels stay lit and the whole point is lost.
 * Guards against someone later "warming up" a surface token or forgetting
 * that M3 components fall back to the surfaceContainer family / tonal
 * elevation (surfaceTint) rather than plain `surface`.
 */
class AmoledSchemeTest {

    @Test
    fun `all canvas-level surfaces are pure black`() {
        val s = LumaAmoledColorScheme
        assertThat(s.background).isEqualTo(Color.Black)
        assertThat(s.surface).isEqualTo(Color.Black)
        assertThat(s.surfaceDim).isEqualTo(Color.Black)
        assertThat(s.surfaceContainerLowest).isEqualTo(Color.Black)
        assertThat(s.surfaceContainerLow).isEqualTo(Color.Black)
        assertThat(s.surfaceContainer).isEqualTo(Color.Black)
    }

    @Test
    fun `tonal elevation cannot tint surfaces away from black`() {
        // surfaceColorAtElevation lerps surface toward surfaceTint; with a
        // black tint the lerp is a no-op at every elevation.
        assertThat(LumaAmoledColorScheme.surfaceTint).isEqualTo(Color.Black)
    }

    @Test
    fun `raised tiers keep a visible lift for hierarchy`() {
        // Deliberately NOT black: chips/thumbnails must stay distinguishable.
        assertThat(LumaAmoledColorScheme.surfaceVariant).isNotEqualTo(Color.Black)
        assertThat(LumaAmoledColorScheme.surfaceContainerHigh).isNotEqualTo(Color.Black)
        assertThat(LumaAmoledColorScheme.surfaceContainerHighest).isNotEqualTo(Color.Black)
    }

    @Test
    fun `non-surface identity is inherited from the dark scheme`() {
        val amoled = LumaAmoledColorScheme
        val dark = LumaDarkColorScheme
        assertThat(amoled.primary).isEqualTo(dark.primary)
        assertThat(amoled.onBackground).isEqualTo(dark.onBackground)
        assertThat(amoled.onSurface).isEqualTo(dark.onSurface)
        assertThat(amoled.outline).isEqualTo(dark.outline)
    }
}
