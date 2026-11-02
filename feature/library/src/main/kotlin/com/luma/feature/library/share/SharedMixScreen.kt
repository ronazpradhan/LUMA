package com.luma.feature.library.share

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.luma.core.common.extensions.pluralize
import com.luma.core.model.share.ShareConfig

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharedMixScreen(
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    /** Save to library: opens the new playlist in place of this screen. */
    onJoinedPlaylist: (Long) -> Unit = onOpenPlaylist,
    viewModel: SharedMixViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        when (val s = state) {
            is SharedMixUiState.Error -> Column(
                Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(s.message, style = MaterialTheme.typography.titleMedium)
            }
            is SharedMixUiState.Loaded -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SharedCover(s.doc.covers, s.doc.tracks.firstNotNullOfOrNull { it.youtubeId })
                        Spacer(Modifier.size(16.dp))
                        Column {
                            Text(s.doc.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val by = s.doc.sharedBy?.let { " · shared by $it" }.orEmpty()
                            Text("${pluralize(s.doc.tracks.size, "track")}$by", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::play, enabled = !s.busy) { Text("Play") }
                        OutlinedButton(onClick = { viewModel.saveCopy(onJoinedPlaylist) }, enabled = !s.busy) { Text("Save to library") }
                    }
                    s.message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                itemsIndexed(s.doc.tracks) { i, t ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("${i + 1}. ${t.title}", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * The shared playlist's picture: its own cover, or four song covers as a 2x2 mosaic (as the link
 * carries them), else the first song's YouTube thumbnail. Off-list hosts never load.
 */
@Composable
private fun SharedCover(covers: List<String>, firstVideo: String?) {
    val shape = RoundedCornerShape(12.dp)
    val safe = covers.filter(ShareConfig::isAllowedCover)
    val box = Modifier.size(96.dp).clip(shape)
    when {
        safe.size >= 4 -> Column(box) {
            for (row in 0..1) Row(Modifier.weight(1f)) {
                for (col in 0..1) {
                    AsyncImage(safe[row * 2 + col], null, Modifier.weight(1f).fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
        safe.isNotEmpty() -> AsyncImage(safe.first(), null, box, contentScale = ContentScale.Crop)
        firstVideo != null -> AsyncImage("https://i.ytimg.com/vi/$firstVideo/hqdefault.jpg", null, box, contentScale = ContentScale.Crop)
        else -> Box(box.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
