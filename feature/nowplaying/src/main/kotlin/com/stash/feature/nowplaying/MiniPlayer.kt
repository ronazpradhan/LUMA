package com.stash.feature.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.stash.core.common.primaryArtist
import com.stash.core.media.listen.ListenTogetherState
import com.stash.core.ui.components.DownloadingIndicator
import com.stash.core.ui.components.LikeButton
import com.stash.core.ui.components.ListenTogetherRole
import com.stash.core.ui.components.LocalListenTogetherRole
import com.stash.feature.nowplaying.listen.ListenTogetherViewModel
import com.stash.feature.nowplaying.listen.MiniSessionFaces

/**
 * Compact mini player pill that sits above the bottom navigation.
 *
 * LUMA: laid out like a music widget: the round cover ringed by the song's
 * progress, title and artist, like and download, a hairline, then previous /
 * play-pause (a solid disc) / next. Slides in/out based on whether a track is
 * currently loaded.
 *
 * @param onExpand Callback invoked when the user taps the bar to expand
 *                 into the full Now Playing screen.
 * @param modifier Standard Compose [Modifier].
 * @param viewModel The shared [NowPlayingViewModel] provided by Hilt.
 */
@Composable
fun MiniPlayer(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    /** LUMA: the glass bottom chrome passes a see-through color and its own shape. */
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape,
    viewModel: NowPlayingViewModel = hiltViewModel(),
    listen: ListenTogetherViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val together by listen.state.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloadingCurrent.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = uiState.hasTrack,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        val dominantColor = uiState.dominantColor

        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(onClick = onExpand),
            color = containerColor,
            shape = shape,
            tonalElevation = if (containerColor.alpha < 0.9f) 0.dp else 2.dp,
        ) {
            // LUMA: the mini player wears the song's colours: a deep wash of its
            // dominant colour into its muted one, crossfading per song. The ink
            // (text, icons, the play disc) flips dark or light to stay readable.
            val toward = androidx.compose.ui.graphics.Color.Black
            // The cover's most vivid colour leads; a dark photo's "dominant" is often
            // near-black, which would make every mini player grey. Grey covers fall
            // back to the dominant colour.
            val lead = remember(uiState.vibrantColor, uiState.dominantColor) {
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(uiState.vibrantColor.toArgb(), hsv)
                if (hsv[1] > 0.25f && hsv[2] > 0.2f) uiState.vibrantColor else uiState.dominantColor
            }
            val fillA by androidx.compose.animation.animateColorAsState(
                androidx.compose.ui.graphics.lerp(lead, toward, 0.35f),
                androidx.compose.animation.core.tween(800),
                label = "miniFillA",
            )
            val fillB by androidx.compose.animation.animateColorAsState(
                androidx.compose.ui.graphics.lerp(uiState.dominantColor, toward, 0.50f),
                androidx.compose.animation.core.tween(800),
                label = "miniFillB",
            )
            val tinted = containerColor.alpha < 0.9f
            val lightFill = tinted && fillA.luminance() > 0.45f
            val songInk = if (lightFill) androidx.compose.ui.graphics.Color(0xFF14121A) else androidx.compose.ui.graphics.Color.White
            Column(
                modifier = if (tinted) {
                    Modifier.background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(fillA.copy(alpha = 0.94f), fillB.copy(alpha = 0.90f)),
                        ),
                    )
                } else {
                    Modifier
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val track = uiState.currentTrack
                    val artModel = track?.albumArtPath ?: track?.albumArtUrl
                    val ink = if (tinted) songInk else MaterialTheme.colorScheme.onSurface
                    val ringTrack = ink.copy(alpha = 0.18f)

                    // Round cover, with the song's progress as a ring around it.
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .drawBehind {
                                val stroke = 2.dp.toPx()
                                val topLeft = Offset(stroke / 2, stroke / 2)
                                val arcSize = Size(size.width - stroke, size.height - stroke)
                                drawArc(ringTrack, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                                drawArc(
                                    color = if (tinted) ink else dominantColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * uiState.progressFraction.coerceIn(0f, 1f),
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(stroke, cap = StrokeCap.Round),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (artModel != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(artModel)
                                    .build(),
                                contentDescription = "Album art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // Track title and artist.
                    Column(modifier = Modifier.weight(1f)) {
                        // primaryArtist() collapses collaboration credits
                        // ("Metro Boomin, Travis Scott" → "Metro Boomin").
                        // remember keeps the split off the hot path: this
                        // composable recomposes on every progress tick.
                        val artist = remember(track?.artist) { track?.artist?.primaryArtist() }
                        Text(
                            text = track?.title ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = artist ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = ink.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    (together as? ListenTogetherState.InRoom)?.let { MiniSessionFaces(it) }

                    if (track != null) {
                        LikeButton(
                            isLiked = track.stashLikedAt != null,
                            onTap = viewModel::onLikeTap,
                            unlikedTint = ink.copy(alpha = 0.85f),
                            size = 20.dp,
                            modifier = Modifier.size(36.dp),
                        )
                        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            if (isDownloading) {
                                DownloadingIndicator(color = ink, size = 20.dp)
                            } else {
                                IconButton(
                                    onClick = viewModel::toggleDownloadForCurrentTrack,
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = if (track.isDownloaded) Icons.Filled.CheckCircle else Icons.Outlined.ArrowCircleDown,
                                        contentDescription = if (track.isDownloaded) "Remove download" else "Download",
                                        tint = if (track.isDownloaded) ink else ink.copy(alpha = 0.85f),
                                        modifier = Modifier.size(21.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Hairline between the song's actions and the transport.
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .width(1.dp)
                            .height(22.dp)
                            .background(ink.copy(alpha = 0.18f)),
                    )

                    // A Listen Together listener can't change the room's song: previous/next keep
                    // their slots, empty, so play/pause doesn't move when the host role changes hands.
                    val listener = LocalListenTogetherRole.current == ListenTogetherRole.LISTENER
                    if (listener) {
                        Spacer(Modifier.size(36.dp))
                    } else {
                        IconButton(onClick = viewModel::onSkipPrevious, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = ink, modifier = Modifier.size(24.dp))
                        }
                    }

                    // Play / Pause: a solid disc. Spinner while the track is still
                    // resolving/buffering (e.g. a slow YouTube-fallback resolve).
                    // The play glyph is cut from the disc in the song's colour.
                    val onInk = if (tinted) fillA else MaterialTheme.colorScheme.surface
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ink)
                            .clickable(enabled = !uiState.isBuffering, onClick = viewModel::onPlayPauseClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (uiState.isBuffering) {
                            CircularProgressIndicator(color = onInk, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                                tint = onInk,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    if (listener) {
                        Spacer(Modifier.size(36.dp))
                    } else {
                        IconButton(onClick = viewModel::onSkipNext, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = ink, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}
