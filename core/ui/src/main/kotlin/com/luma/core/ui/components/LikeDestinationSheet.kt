package com.luma.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * v0.9.13: Per-track override sheet. Pre-checked according to the
 * user's Settings defaults; subtitled with "✓ Already saved" for
 * destinations whose `*_saved_at` timestamp is already populated.
 *
 * The selection is ephemeral — saving doesn't update Settings
 * defaults. Defaults stay where they are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikeDestinationSheet(
    state: LikeDestinationSheetState,
    onDismiss: () -> Unit,
    onSave: (Set<DestinationCheckbox>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var lumaChecked by remember { mutableStateOf(state.lumaChecked) }
    var spotifyChecked by remember { mutableStateOf(state.spotifyChecked) }
    var ytChecked by remember { mutableStateOf(state.ytChecked) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Save to…",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            DestinationRow(
                label = "LUMA Liked Songs",
                subtitle = if (state.lumaAlreadySaved) "✓ Already saved" else null,
                checked = lumaChecked,
                onCheckedChange = { lumaChecked = it },
            )

            if (state.spotifyVisible) {
                DestinationRow(
                    label = "Spotify Liked Songs",
                    subtitle = if (state.spotifyAlreadySaved) "✓ Already saved" else null,
                    checked = spotifyChecked,
                    onCheckedChange = { spotifyChecked = it },
                )
            }

            if (state.ytVisible) {
                DestinationRow(
                    label = "YouTube Music Liked Music",
                    subtitle = if (state.ytAlreadySaved) "✓ Already saved" else null,
                    checked = ytChecked,
                    onCheckedChange = { ytChecked = it },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val selected = buildSet {
                        if (lumaChecked) add(DestinationCheckbox.LUMA)
                        if (spotifyChecked) add(DestinationCheckbox.SPOTIFY)
                        if (ytChecked) add(DestinationCheckbox.YT_MUSIC)
                    }
                    onSave(selected)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun DestinationRow(
    label: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * View state for [LikeDestinationSheet]. The sheet is purely a UI
 * concern; the caller (e.g., NowPlayingScreen) computes this from
 * the user's Settings defaults + the current track's `*_saved_at`
 * timestamps.
 */
data class LikeDestinationSheetState(
    val spotifyVisible: Boolean,
    val ytVisible: Boolean,
    val lumaChecked: Boolean,
    val spotifyChecked: Boolean,
    val ytChecked: Boolean,
    val lumaAlreadySaved: Boolean,
    val spotifyAlreadySaved: Boolean,
    val ytAlreadySaved: Boolean,
)

enum class DestinationCheckbox { LUMA, SPOTIFY, YT_MUSIC }
