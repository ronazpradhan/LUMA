package com.luma.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.ui.unit.dp
import com.luma.core.data.prefs.LyricsSourcePreference
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.core.common.constants.LumaConstants
import com.luma.feature.settings.components.SettingsGroupCard
import com.luma.feature.settings.components.SettingsRowPadH
import com.luma.feature.settings.components.SettingsRowPadV
import com.luma.core.media.SleepTimerController
import com.luma.feature.settings.components.SettingsScaffold
import com.luma.feature.settings.components.SettingsSectionLabel
import com.luma.feature.settings.components.SettingsSegmented
import com.luma.feature.settings.components.SettingsToggleRow
import kotlin.math.roundToInt

/**
 * The Playback spoke of the hub-and-spoke Settings redesign.
 *
 * This re-homes the original `LumaConstants.STREAMING_ENGINE_ENABLED` block from
 * the monolithic `SettingsScreen.kt`: the Online/Offline mode picker plus the
 * streaming toggles (cellular, YouTube fallback, antra-only). This is a
 * behavior-preserving relocation + restyle — every control calls the SAME
 * [SettingsViewModel] method the old screen used; no logic is changed.
 */
@Composable
fun SettingsPlaybackScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val streamOnCellular by viewModel.streamOnCellular.collectAsStateWithLifecycle()
    val forceYouTubeFallback by viewModel.forceYouTubeFallback.collectAsStateWithLifecycle()
    val forceQbdlxOnly by viewModel.forceQbdlxOnly.collectAsStateWithLifecycle()
    // Gates developer instruments out of release builds. Read from the installed
    // app's own flags rather than a module BuildConfig: it is the actual property we
    // care about ("is this a debuggable install"), and it needs no build-file change.
    val context = LocalContext.current
    val isDebuggableBuild = remember(context) {
        context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
    val autoplayRadioEnabled by viewModel.autoplayRadioEnabled.collectAsStateWithLifecycle()
    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsStateWithLifecycle()
    val crossfadeDurationMs by viewModel.crossfadeDurationMs.collectAsStateWithLifecycle()

    SettingsScaffold(title = "Playback", onBack = onBack, modifier = modifier) {
        if (LumaConstants.STREAMING_ENGINE_ENABLED) {
            // LUMA: no "Stream only / Download" mode picker here.
            SettingsSectionLabel("Streaming")
            SettingsGroupCard(
                rows = buildList {
                    add {
                        SettingsToggleRow(
                            title = "Stream on cellular",
                            subtitle = "Allow streaming over mobile data (5G / LTE). Off by default to avoid surprise data use.",
                            checked = streamOnCellular,
                            onCheckedChange = viewModel::onStreamOnCellularToggle,
                        )
                    }
                    // LUMA: no lossless, so no "Stream via YouTube" / "Force Qobuz" switches.
                },
            )
        } else {
            Text(
                text = "Streaming is unavailable in this build.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Autoplay: the queue's end seeds a song radio (streams, so it needs a connection).
        SettingsSectionLabel("Autoplay")
        SettingsGroupCard(
            rows = listOf {
                SettingsToggleRow(
                    title = "Autoplay radio",
                    subtitle = "When the last song in the queue starts, keep the music going with similar songs, the way YouTube Music fills Up next. Needs a connection.",
                    checked = autoplayRadioEnabled,
                    onCheckedChange = viewModel::onAutoplayRadioToggle,
                )
            },
        )
        // Crossfade applies to both streamed and downloaded tracks, so it sits
        // outside the streaming-engine gate.
        SettingsSectionLabel("Crossfade")
        SettingsGroupCard(
            rows = buildList {
                add {
                    SettingsToggleRow(
                        title = "Crossfade",
                        subtitle = "Fade the ending track into the next on auto-advance. Manual skips still cut instantly.",
                        checked = crossfadeEnabled,
                        onCheckedChange = viewModel::onCrossfadeToggle,
                    )
                }
                if (crossfadeEnabled) {
                    add {
                        CrossfadeDurationRow(
                            seconds = (crossfadeDurationMs / 1000L).toInt().coerceIn(1, 12),
                            onSecondsChange = { viewModel.onCrossfadeDurationChange(it * 1000L) },
                        )
                    }
                }
            },
        )
        // LUMA: no Lyrics section; LUMA keeps the default source.
    }
}

/** One tappable sleep-timer choice row. */
@Composable
private fun SleepTimerChoiceRow(
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = tint,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SettingsRowPadH, vertical = SettingsRowPadV),
    )
}

/**
 * Duration slider row for the Crossfade section: 1–12 s in whole-second steps
 * (Material [Slider] `steps` counts internal stops, so 10 → 12 positions).
 * Stateless; the caller owns persistence.
 */
@Composable
private fun CrossfadeDurationRow(
    seconds: Int,
    onSecondsChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsRowPadH, vertical = SettingsRowPadV),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Duration",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$seconds sec",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = seconds.toFloat(),
            onValueChange = { onSecondsChange(it.roundToInt()) },
            valueRange = 1f..12f,
            steps = 10,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}
