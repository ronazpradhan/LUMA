package com.stash.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.stash.core.ui.theme.StashTheme

/**
 * A frosted-glass style card using a semi-transparent background with a subtle border.
 *
 * The glass effect comes from the translucent [glassBackground] color layered over
 * the dark app background. A true backdrop blur is not used because Android's
 * RenderEffect.createBlurEffect blurs the card's own content (text, icons),
 * making it unreadable. The semi-transparent surface already provides the
 * glassmorphism aesthetic without sacrificing legibility.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val extendedColors = StashTheme.extendedColors
    // LUMA: a frosted pane — translucent fill over the ambient glow, rounder
    // corners, and a rim that catches the light along the top edge.
    val shape = RoundedCornerShape(24.dp)
    val rim = Brush.verticalGradient(listOf(extendedColors.glassBorderBright, extendedColors.glassBorder))
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
        color = extendedColors.glassBackground,
        shape = shape,
        border = BorderStroke(1.dp, rim),
    ) {
        Box(modifier = Modifier.padding(16.dp), content = content)
    }
}
