package com.luma.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.luma.core.media.streaming.StreamCacheLimit
import com.luma.feature.settings.components.SettingsGroupCard
import com.luma.feature.settings.components.SettingsPickerRow
import com.luma.feature.settings.components.SettingsSectionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** LUMA: Settings › Library & Storage › Streaming cache. */
@HiltViewModel
class StreamCacheViewModel @Inject constructor(
    private val limit: StreamCacheLimit,
) : ViewModel() {
    val limitMb: StateFlow<Long> = limit.limitMb

    private val _usedBytes = MutableStateFlow<Long?>(null)
    val usedBytes: StateFlow<Long?> = _usedBytes.asStateFlow()

    fun refresh() {
        viewModelScope.launch { _usedBytes.value = runCatching { limit.usedBytes() }.getOrNull() }
    }

    fun setLimit(mb: Long) {
        viewModelScope.launch {
            limit.setLimitMb(mb)
            _usedBytes.value = runCatching { limit.usedBytes() }.getOrNull()
        }
    }

    fun clear() {
        viewModelScope.launch {
            runCatching { limit.clear() }
            _usedBytes.value = runCatching { limit.usedBytes() }.getOrNull()
        }
    }
}

private fun formatMb(mb: Long): String = if (mb >= 1024) "${mb / 1024} GB" else "$mb MB"

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        mb >= 1024 -> "%.1f GB".format(mb / 1024)
        mb >= 1 -> "%.0f MB".format(mb)
        bytes > 0 -> "<1 MB"
        else -> "0 MB"
    }
}

/**
 * How much streamed music LUMA keeps on the phone. Replays from the cache start
 * instantly and use no data; downloads are separate and never counted here.
 */
@Composable
internal fun StreamCacheSection(viewModel: StreamCacheViewModel = hiltViewModel()) {
    val limitMb by viewModel.limitMb.collectAsStateWithLifecycle()
    val used by viewModel.usedBytes.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    SettingsSectionLabel("Streaming cache")
    Text(
        text = "Songs you stream are kept on your phone so replays start instantly and use no data. " +
            "Pick how much space that can take. Downloads aren't counted.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
    SettingsGroupCard(
        rows = StreamCacheLimit.OPTIONS_MB.map<Long, @Composable () -> Unit> { mb ->
            {
                SettingsPickerRow(
                    selected = mb == limitMb,
                    title = formatMb(mb),
                    subtitle = "About ${mb / 8} songs",
                    onClick = { viewModel.setLimit(mb) },
                )
            }
        } + listOf<@Composable () -> Unit>({
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = used?.let { "Using ${formatBytes(it)} of ${formatMb(limitMb)}" } ?: "Checking…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::clear, enabled = (used ?: 0L) > 0L) { Text("Clear cache") }
            }
        }),
    )
}
