package com.luma.feature.library.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.core.common.extensions.pluralize

/** LUMA: share a playlist as a link that carries it. Keyed per playlist so each gets its own ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareMixSheet(
    playlistId: Long,
    playlistName: String,
    @Suppress("UNUSED_PARAMETER") trackCount: Int,
    onDismiss: () -> Unit,
    viewModel: ShareMixViewModel = hiltViewModel(key = "share-$playlistId"),
) {
    LaunchedEffect(playlistId) { viewModel.bind(playlistId, playlistName) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Share playlist", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "The link carries the playlist itself, so it works without any server. Friends without LUMA see the songs on LUMA's website.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            var name by rememberSaveable(state.defaultName) { mutableStateOf(state.defaultName) }
            var display by rememberSaveable(state.displayName) { mutableStateOf(state.displayName.orEmpty()) }
            OutlinedTextField(name, { name = it.take(100) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(display, { display = it.take(40) }, label = { Text("Show my name as (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.makeLink(name, display.ifBlank { null }) { link -> onDismiss(); sendLink(context, link) } },
                    enabled = !state.working,
                    modifier = Modifier.weight(1f),
                ) { Text(if (state.working) "Making link…" else "Share link") }
                OutlinedButton(
                    onClick = { viewModel.makeLink(name, display.ifBlank { null }) { link -> copy(context, link) } },
                    enabled = !state.working,
                    modifier = Modifier.weight(1f),
                ) { Text("Copy link") }
            }
        }
    }
}

private fun sendLink(context: Context, link: ShareMixLink) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${link.name}: ${pluralize(link.songs, "song")} on LUMA\n${link.url}")
    }
    context.startActivity(Intent.createChooser(intent, "Share \"${link.name}\""))
    if (link.songs < link.total) {
        Toast.makeText(context, "The link holds the first ${link.songs} of ${link.total} songs.", Toast.LENGTH_LONG).show()
    }
}

private fun copy(context: Context, link: ShareMixLink) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("LUMA playlist", link.url))
    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
}
