package com.stash.feature.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.stash.core.ui.components.SongColors

private const val COLOUR_FADE_MS = 800

/**
 * LUMA: reads the playing song's colours app-wide, not only while Now Playing
 * is open. Loads a small copy of the cover (Palette needs a software bitmap)
 * whenever the song changes and hands it to [viewModel], then returns the
 * colours animated between songs, for [com.stash.core.ui.components.LocalSongColors].
 */
@Composable
fun rememberSongColors(viewModel: NowPlayingViewModel): SongColors {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val track = uiState.currentTrack
    val artModel = track?.albumArtPath ?: track?.albumArtUrl
    val context = LocalContext.current

    LaunchedEffect(artModel) {
        if (artModel == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(artModel)
            .size(128)
            .allowHardware(false)
            .build()
        val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull()
        val bitmap = (result as? SuccessResult)?.image?.let { runCatching { it.toBitmap() }.getOrNull() }
        if (bitmap != null) viewModel.onAlbumArtLoaded(bitmap)
    }

    val fade = tween<androidx.compose.ui.graphics.Color>(COLOUR_FADE_MS)
    val dominant = animateColorAsState(uiState.dominantColor, fade, label = "songDominant")
    val vibrant = animateColorAsState(uiState.vibrantColor, fade, label = "songVibrant")
    val muted = animateColorAsState(uiState.mutedColor, fade, label = "songMuted")
    val presence = animateFloatAsState(if (uiState.hasTrack) 1f else 0f, tween(COLOUR_FADE_MS), label = "songPresence")
    return remember { SongColors(dominant, vibrant, muted, presence) }
}
