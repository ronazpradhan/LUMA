package com.luma.core.ui.components.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.composed
import com.luma.core.ui.theme.LumaMotion

/** Scales content to LumaMotion.PRESS_SCALE while pressed via [interactionSource]. */
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) LumaMotion.PRESS_SCALE else 1f,
        animationSpec = tween(LumaMotion.DUR_SHORT_MS, easing = LumaMotion.EaseEnter),
        label = "pressScale",
    )
    this.scale(scale)
}
