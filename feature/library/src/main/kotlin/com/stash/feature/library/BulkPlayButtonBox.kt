package com.stash.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Wraps a Play All / Shuffle All button with a thin M3 [LinearProgressIndicator]
 * overlay along the bottom edge while a bulk-playback action is resolving.
 *
 * The indicator clips to the button's [RoundedCornerShape] so it lines up with
 * the button's rounded corners. It uses `trackColor = Transparent` so only the
 * moving segment renders — the "gradient sliding" effect that gives users
 * visual feedback even when the first-to-play track row is off-screen.
 *
 * Pass [showProgress] = true while the matching [BulkPlayAction] is in flight
 * (e.g. `bulkPlayInFlight == BulkPlayAction.PLAY_ALL`).
 *
 * Buttons stay enabled during loading — the coalescing layer handles duplicate
 * taps, and disabling would defeat the "yes, your tap registered" feedback.
 */
@Composable
internal fun BulkPlayButtonBox(
    showProgress: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()
        if (showProgress) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }
    }
}
