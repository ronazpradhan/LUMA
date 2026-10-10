package com.stash.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * LUMA: the "downloading" glyph — an arrow that drops quickly into a tray,
 * over and over, instead of a spinning ring. With [progress] the tray fills
 * left to right as the download lands; without it the tray shimmers.
 */
@Composable
fun DownloadingIndicator(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    progress: Float? = null,
) {
    val t = rememberInfiniteTransition(label = "downloading")
    val drop by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing)),
        label = "drop",
    )
    val shimmer by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmer",
    )
    val fill by animateFloatAsState(progress?.coerceIn(0f, 1f) ?: 0f, label = "fill")
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.11f
        val trayY = h * 0.86f
        val trayL = w * 0.18f
        val trayR = w * 0.82f
        // The tray: faint, filled to the progress (or shimmering when unknown).
        drawLine(color.copy(alpha = 0.30f), Offset(trayL, trayY), Offset(trayR, trayY), stroke, StrokeCap.Round)
        val filled = if (progress != null) fill else 0.35f + 0.65f * shimmer
        drawLine(
            color.copy(alpha = if (progress != null) 1f else 0.55f + 0.45f * shimmer),
            Offset(trayL, trayY),
            Offset(trayL + (trayR - trayL) * filled, trayY),
            stroke,
            StrokeCap.Round,
        )
        // The arrow: slides from above the frame down into the tray, fading as it lands.
        val travel = h * 0.62f
        val tipY = h * 0.08f + travel * drop
        val alpha = if (drop < 0.8f) 1f else (1f - drop) / 0.2f
        val shaft = h * 0.36f
        val cx = w / 2f
        clipRect(top = 0f, bottom = trayY - stroke) {
            drawLine(color.copy(alpha = alpha), Offset(cx, tipY - shaft), Offset(cx, tipY), stroke, StrokeCap.Round)
            drawLine(color.copy(alpha = alpha), Offset(cx - w * 0.2f, tipY - w * 0.2f), Offset(cx, tipY), stroke, StrokeCap.Round)
            drawLine(color.copy(alpha = alpha), Offset(cx + w * 0.2f, tipY - w * 0.2f), Offset(cx, tipY), stroke, StrokeCap.Round)
        }
    }
}
