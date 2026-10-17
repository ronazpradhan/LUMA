package com.luma.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * LUMA: the light on a pane of liquid glass, drawn over whatever fills it (a
 * backdrop blur, a translucent tint or cover art) and under its content:
 *
 *  - a sheen across the top, where light enters the glass,
 *  - a little depth at the bottom, where the glass thickens,
 *  - a specular rim: bright at the top-left and the bottom-right, where
 *    the light would catch a curved edge, and almost gone along the sides.
 *
 * Put it after the fill in the modifier chain (after `clip` + the background or
 * blur): the sheen sits on the fill with the content crisp above it, and the
 * rim is drawn last, over everything.
 * [strength] scales every highlight (1 = a bar or a card; lower for small chips).
 */
@Composable
fun Modifier.liquidGlass(shape: Shape, strength: Float = 1f): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val k = strength.coerceIn(0f, 1.5f)
        fun w(alphaDark: Float, alphaLight: Float) =
            Color.White.copy(alpha = ((if (dark) alphaDark else alphaLight) * k).coerceIn(0f, 1f))
        val sheen = Brush.verticalGradient(
            0f to w(0.12f, 0.26f),
            0.42f to Color.Transparent,
        )
        val depth = Brush.verticalGradient(
            0.62f to Color.Transparent,
            1f to Color.Black.copy(alpha = (if (dark) 0.16f else 0.04f) * k),
        )
        val rim = Brush.linearGradient(
            0f to w(0.60f, 0.95f),
            0.30f to w(0.12f, 0.40f),
            0.70f to w(0.05f, 0.25f),
            1f to w(0.32f, 0.75f),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        // Stroked on the outline and clipped to it, so exactly half of it shows: a 1dp rim inside the edge.
        val rimWidth = 2.dp.toPx()
        onDrawWithContent {
            clipPath(path) {
                drawRect(sheen)
                drawRect(depth)
            }
            drawContent()
            // The rim goes over the content, so a full-bleed cover still gets its edge of light.
            clipPath(path) { drawPath(path, rim, style = Stroke(width = rimWidth)) }
        }
    }
}
