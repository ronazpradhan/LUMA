package com.stash.feature.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stash.core.model.QualityTier

/**
 * Reusable radio group for the legacy YouTube quality tier
 * (MAX/BEST/HIGH/NORMAL/LOW). Used both at top-level when lossless
 * is off and inside the lossless card's YouTube-fallback expander
 * when lossless is on.
 *
 * Pure extraction of the inline radio-row block previously rendered
 * directly inside SettingsScreen's Audio Quality card. Visual output
 * is identical to that prior inline block.
 */
@Composable
internal fun AudioQualityPicker(
    selected: QualityTier,
    onSelected: (QualityTier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.selectableGroup()) {
        QualityTier.entries.forEach { tier ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == tier,
                        onClick = { onSelected(tier) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected == tier,
                    onClick = null, // handled by Row's selectable
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = tier.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${tier.bitrateKbps} kbps  ~${tier.sizeMbPerMinute} MB/min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
