package com.luma.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.luma.core.ui.theme.LumaTheme

/**
 * A pane of liquid glass: a translucent fill over the ambient glow, lit by
 * [liquidGlass] (top sheen, bottom depth, specular rim).
 *
 * No backdrop blur here: the cards sit inside the scrolling content, which is
 * itself the blur source of the bottom bar, and blurring a card's own layer
 * would smear its text. Over the soft ambient backdrop the translucent fill
 * and the light read as glass anyway; the bars that float over content are
 * the ones that blur (LumaScaffold).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(LumaTheme.extendedColors.glassBackground)
            .liquidGlass(shape),
    ) {
        Box(modifier = Modifier.padding(16.dp), content = content)
    }
}
