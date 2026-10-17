package com.luma.core.ui.components.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * LUMA: how a song row feels under the finger. It dips a little and springs
 * back, a tint of the theme colour washes over it while pressed, the ripple is
 * the theme colour rather than a faint grey (it showed poorly on the glass),
 * and a tap gives a light tick (a long-press a firmer one).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.songTap(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    cornerRadius: Dp = 12.dp,
    enabled: Boolean = true,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "songTapScale",
    )
    val wash by animateFloatAsState(
        targetValue = if (pressed) 0.12f else 0f,
        animationSpec = tween(if (pressed) 60 else 220),
        label = "songTapWash",
    )
    val tint = MaterialTheme.colorScheme.primary
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (wash > 0f) {
                drawRoundRect(tint.copy(alpha = wash), cornerRadius = CornerRadius(cornerRadius.toPx()))
            }
        }
        .combinedClickable(
            interactionSource = source,
            indication = ripple(color = tint),
            enabled = enabled,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            onLongClick = onLongClick?.let { long ->
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    long()
                }
            },
        )
}
