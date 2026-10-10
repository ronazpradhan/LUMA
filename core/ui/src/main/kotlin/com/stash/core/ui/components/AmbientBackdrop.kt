package com.stash.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * LUMA: the soft glow every screen sits on — a few large, faint washes of the
 * logo's lavender and indigo over the theme background. It is what the glass
 * cards and the frosted tab bar have to show through; over a flat colour,
 * glass just reads as a pale box. Opaque (it paints the background too), so
 * screens sliding over each other never show through one another.
 */
@Composable
fun Modifier.ambientBackdrop(): Modifier {
    val base = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val dark = base.luminance() < 0.5f
    val lavender = Color(0xFFC9BEFF)
    val indigo = Color(0xFF5B5BD6)
    val a = if (dark) 0.22f else 0.30f
    return this
        .background(base)
        .drawBehind {
            val w = size.width
            val h = size.height
            fun glow(color: Color, alpha: Float, cx: Float, cy: Float, r: Float) =
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = r,
                    ),
                )
            glow(primary, a, w * 0.05f, h * 0.02f, w * 0.95f)
            glow(lavender, a * 0.9f, w * 1.0f, h * 0.38f, w * 0.85f)
            glow(indigo, a * 0.75f, w * 0.15f, h * 0.78f, w * 0.9f)
        }
}
