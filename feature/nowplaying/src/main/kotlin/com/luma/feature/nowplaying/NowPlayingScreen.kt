package com.luma.feature.nowplaying

import com.luma.core.ui.components.motion.songTap
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.automirrored.filled.Logout
import com.luma.core.media.listen.ListenTogetherState
import com.luma.core.common.primaryArtist
import com.luma.core.media.SleepTimerController
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.luma.core.model.RepeatMode
import com.luma.core.model.isFlac
import com.luma.core.ui.components.SaveToPlaylistSheet
import com.luma.core.ui.components.SheetOptionRow
import com.luma.core.ui.theme.LocalIsAmoledTheme
import com.luma.feature.nowplaying.ui.AmbientBackground
import com.luma.feature.nowplaying.ui.GlowingProgressBar
import com.luma.feature.nowplaying.ui.LiveLyricsBar
import com.luma.feature.nowplaying.ui.LyricsBottomSheet
import com.luma.feature.nowplaying.ui.QueueBottomSheet

/** Light-ground ink for the pastel-wash Now Playing (the app's plum-black). */
private val NpInkLight = Color(0xFF241C36)

/**
 * Foreground ink for the Now Playing surface: white on the dark ambient,
 * plum-black on the light pastel wash. Reads the *resolved* theme background
 * (not the system setting) so manual/AMOLED overrides pick the right ink.
 */
@Composable
private fun npInk(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else NpInkLight

/**
 * Album palettes can be too pale to hold contrast on the light wash — darken
 * toward ink until they do. Dark theme passes the raw palette through.
 */
@Composable
private fun npAccent(raw: Color): Color =
    if (MaterialTheme.colorScheme.background.luminance() >= 0.5f && raw.luminance() > 0.55f) {
        lerp(raw, NpInkLight, 0.35f)
    } else {
        raw
    }

/**
 * Full-screen Now Playing screen with premium visual design.
 *
 * Displays album art with ambient background, playback controls, progress bar,
 * and track information. Colors are extracted from album art via Palette API.
 * The ambient ground + inks follow the app theme: near-black with drifting
 * art-derived orbs in dark, the same orbs as a pastel wash over lavender
 * paper in light.
 *
 * @param onDismiss Callback invoked when the user taps the dismiss (down arrow) button.
 * @param viewModel The [NowPlayingViewModel] provided by Hilt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    onDismiss: () -> Unit,
    onNavigateToArtist: (id: String, name: String, avatarUrl: String?, focusAlbum: String?) -> Unit,
    onNavigateToAlbum: (albumId: String, name: String, artUrl: String?, artistName: String) -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val track = uiState.currentTrack
    // LUMA: the player's glass and accents follow the album art, crossfading per song.
    val dynTint by androidx.compose.animation.animateColorAsState(
        targetValue = uiState.dominantColor,
        animationSpec = androidx.compose.animation.core.tween(800),
        label = "npTint",
    )
    val dynAccent by androidx.compose.animation.animateColorAsState(
        targetValue = androidx.compose.ui.graphics.lerp(uiState.vibrantColor, Color.White, 0.22f),
        animationSpec = androidx.compose.animation.core.tween(800),
        label = "npAccent",
    )
    // The song's colour for the player's menus (3-dot, speed, sleep timer).
    val sheetLead = remember(uiState.vibrantColor, uiState.dominantColor) {
        com.luma.core.ui.theme.songLeadColor(uiState.vibrantColor, uiState.dominantColor)
    }
    val resolvingArtist by viewModel.resolvingArtist.collectAsStateWithLifecycle()
    val isDownloadingCurrent by viewModel.isDownloadingCurrent.collectAsStateWithLifecycle()
    val exportingLyricsTrackId by viewModel.exportingLyricsTrackId.collectAsStateWithLifecycle()
    val radioLabel by viewModel.radioSeedLabel.collectAsStateWithLifecycle()
    val radioTuning by viewModel.radioTuning.collectAsStateWithLifecycle()
    // One-shot lock pulse: fires on the tuning→active transition. Also pulses
    // when the screen re-opens with a station already live — reads as "station
    // is active", not a bug. Keys on real state changes, not recomposition.
    var radioLock by remember { mutableStateOf(false) }
    LaunchedEffect(radioTuning, radioLabel) {
        if (!radioTuning && radioLabel != null) {
            radioLock = true
            kotlinx.coroutines.delay(com.luma.feature.nowplaying.ui.LOCK_HOLD_MS)
            radioLock = false
        }
    }
    val ambientAnimationEnabled = viewModel.ambientAnimationEnabled.collectAsStateWithLifecycle().value ?: return
    // Read with the switch above (one DataStore), so a Classic user never sees a frame of Soft glow.
    val ambientStyle = viewModel.ambientStyle.collectAsStateWithLifecycle().value ?: return
    var showQueue by remember { mutableStateOf(false) }
    var showSaveSheet by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showCustomTimerDialog by remember { mutableStateOf(false) }
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val sleepTimerSheetState = rememberModalBottomSheetState()
    val optionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showEqualizer by remember { mutableStateOf(false) }
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showCustomSpeedDialog by remember { mutableStateOf(false) }
    val speedSheetState = rememberModalBottomSheetState()
    // The queue row (if any) whose Save-to-Playlist picker is open.
    var queueSaveTrack by remember { mutableStateOf<com.luma.core.model.Track?>(null) }
    val shareTrack by viewModel.shareTrack.collectAsStateWithLifecycle()
    // Listen Together (spec 2026-09-24 §5).
    val together: com.luma.feature.nowplaying.listen.ListenTogetherViewModel = hiltViewModel()
    val togetherState by together.state.collectAsStateWithLifecycle()
    val room = togetherState as? ListenTogetherState.InRoom
    var showStartTogether by remember { mutableStateOf(false) }
    // Google Cast (spec 2026-10-06 §5). Hidden on phones without Play Services.
    val castViewModel: com.luma.feature.nowplaying.cast.CastViewModel = hiltViewModel()
    val castConnection by castViewModel.connection.collectAsStateWithLifecycle()
    val casting = castConnection is com.luma.core.media.cast.CastConnection.Connected
    var showCastSheet by remember { mutableStateOf(false) }
    // A listener follows the room: no skip or seek; their play/pause is their own (spec §5).
    val isListener = room != null && !room.isHost
    // The Session sheet: people, Up next, suggestions, invite, leave (design 2026-09-25).
    var showSession by remember { mutableStateOf(false) }
    var confirmEndTogether by remember { mutableStateOf(false) }
    var confirmLeaveTogether by remember { mutableStateOf(false) }
    // Haptics at the moments that matter: you're in (start or join), and you became host.
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var wasInRoom by remember { mutableStateOf(room != null) }
    LaunchedEffect(room != null) {
        if (room != null && !wasInRoom) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
        wasInRoom = room != null
    }
    LaunchedEffect(Unit) {
        together.events.collect { e ->
            val me = (together.state.value as? ListenTogetherState.InRoom)?.myId
            if (e.kind == com.luma.core.media.listen.SessionEvent.Kind.HOSTING && e.memberId == me) {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
            }
        }
    }
    // "This song is wrong" dialog — shown when the flag icon is tapped.
    // Decouples the Flag button (which is just "there's a problem") from
    // the action (find a replacement / delete / delete + block).
    var showWrongMatchDialog by remember { mutableStateOf(false) }

    // Scroll state is intentionally not keyed by track — on tall screens
    // content doesn't overflow so scroll stays at 0; on narrow screens the
    // user's scroll position aligns with controls and we want it preserved
    // across track changes.

    // One-shot Toast confirmation for the "wrong match" flag action. Toast
    // instead of Snackbar so we don't have to restructure the screen into
    // a Scaffold — the full-screen ambient background would fight with
    // Material's Snackbar surface anyway.
    val toastContext = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.userMessages.collect { msg ->
            android.widget.Toast.makeText(toastContext, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // Tap-to-artist: the VM resolves the artist name → browseId off the main
    // thread and emits a one-shot nav target; forward it to the host.
    LaunchedEffect(Unit) {
        viewModel.artistNavEvents.collect { t ->
            onNavigateToArtist(t.artistId, t.name, t.avatarUrl, t.focusAlbum)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.albumNavEvents.collect { a ->
            onNavigateToAlbum(a.albumId, a.name, a.artUrl, a.artistName)
        }
    }

    // Share sheet (fork issue ParaliyzedEvo/LUMA#40) — VM-driven with the
    // FULL DB track: the player-state Track is a slim media-session
    // reconstruction whose spotifyUri/youtubeId are null, which is why the
    // link rows never appeared when this read uiState.currentTrack.
    shareTrack?.let { full ->
        com.luma.core.ui.components.ShareTrackSheet(
            track = full,
            onDismiss = viewModel::onShareDismissed,
        )
    }

    // Queue bottom sheet
    if (showQueue) {
        QueueBottomSheet(
            queue = uiState.queue,
            currentIndex = uiState.currentIndex,
            accentColor = npAccent(uiState.vibrantColor),
            source = uiState.source,
            isShuffleEnabled = uiState.shuffleEnabled,
            // LUMA: Shuffle lives here now; a Listen Together room keeps everyone in one order.
            onToggleShuffle = if (room == null) viewModel::onToggleShuffle else null,
            onDismiss = { showQueue = false },
            onTrackClick = { index ->
                viewModel.onSkipToQueueIndex(index)
                showQueue = false
            },
            onRemoveTrack = viewModel::onRemoveFromQueue,
            onMoveTrack = viewModel::onMoveInQueue,
            onPlayNext = viewModel::queuePlayNext,
            onStartRadio = viewModel::startRadioForTrack,
            onSaveToPlaylist = { queueSaveTrack = it },
            onShare = viewModel::onShareTrack,
            onToggleDownload = viewModel::toggleDownload,
        )
    }

    // Save-to-playlist sheet for a queue row's ⋮ (separate from the current-
    // track save sheet so both can target their own track).
    queueSaveTrack?.let { t ->
        SaveToPlaylistSheet(
            playlists = uiState.userPlaylists,
            onSaveToPlaylist = { playlistId ->
                viewModel.saveTrackToPlaylist(t.id, playlistId)
                queueSaveTrack = null
            },
            onCreatePlaylist = { name ->
                viewModel.createPlaylistAndAddTrack(name, t.id)
                queueSaveTrack = null
            },
            onDismiss = { queueSaveTrack = null },
        )
    }

    // Lyrics bottom sheet — opened by tapping the LiveLyricsBar pinned at
    // the screen's bottom edge (`onShowLyrics`); the bar and the sheet share
    // the one subscription collected just below.
    val showLyrics by viewModel.lyricsSheetOpen.collectAsStateWithLifecycle()
    // Collected unconditionally (not just while the sheet is open): the bar
    // needs the state, and this subscription is what arms the ViewModel's
    // WhileSubscribed fetch trigger from screen-open onward. The screen
    // already recomposes every 250ms from uiState position ticks, so the
    // extra position collect adds no new recomposition pressure.
    val lyricsState by viewModel.lyricsViewState.collectAsStateWithLifecycle()
    val lyricsPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val liveLyricsEnabled by viewModel.liveLyricsBarEnabled.collectAsStateWithLifecycle()
    val lyricsSyncOffsetMs by viewModel.lyricsSyncOffsetMs.collectAsStateWithLifecycle()
    if (showLyrics) {
        LyricsBottomSheet(
            state = lyricsState,
            currentPositionMs = lyricsPositionMs,
            liveLyricsEnabled = liveLyricsEnabled,
            onLiveLyricsToggle = viewModel::setLiveLyricsBarEnabled,
            isPlaying = uiState.isPlaying && !uiState.isBuffering,
            playbackSpeed = playbackSpeed,
            onSeek = { if (!isListener) viewModel.onLyricsLineSeek(it) }, // a listener follows the room
            canSaveToFile = track?.isDownloaded == true,
            savingToFile = exportingLyricsTrackId != null,
            onSaveToFile = viewModel::exportLyricsForCurrentTrack,
            onRetry = viewModel::onLyricsRetry,
            onDismiss = viewModel::onDismissLyrics,
            currentOffsetMs = lyricsSyncOffsetMs,
            onOffsetChange = viewModel::setLyricsSyncOffsetMs,
        )
    }

    // Save to playlist bottom sheet
    if (showSaveSheet && track != null) {
        SaveToPlaylistSheet(
            playlists = uiState.userPlaylists,
            onSaveToPlaylist = { playlistId ->
                viewModel.saveTrackToPlaylist(track.id, playlistId)
            },
            onCreatePlaylist = { name ->
                viewModel.createPlaylistAndAddTrack(name, track.id)
            },
            onDismiss = { showSaveSheet = false },
        )
    }

    if (showStartTogether) {
        com.luma.feature.nowplaying.listen.ListenTogetherStartDialog(
            name = together.name,
            onNameChange = together::onNameChange,
            onStart = { together.start(); showStartTogether = false; showSession = true },
            onDismiss = { showStartTogether = false },
        )
    }

    if (confirmEndTogether) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmEndTogether = false },
            title = { Text("End the session for everyone?") },
            text = {
                val others = (room?.members?.size ?: 1) - 1
                Text(if (others == 1) "The person listening with you will be dropped." else "The $others people listening with you will be dropped.")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { together.end(); confirmEndTogether = false },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("End") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmEndTogether = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmLeaveTogether) {
        val nextHost = room?.members?.filter { it.id != room.myId }?.minByOrNull { it.joinedAt }?.name ?: "Someone"
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmLeaveTogether = false },
            title = { Text("Leave the session?") },
            text = { Text("$nextHost will host, and the music keeps playing for everyone else.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { together.leave(); confirmLeaveTogether = false }) { Text("Leave") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmLeaveTogether = false }) { Text("Cancel") }
            },
        )
    }

    if (showSession) {
        com.luma.feature.nowplaying.listen.SessionSheet(
            state = togetherState,
            onDismiss = { showSession = false },
            onAnswer = together::answerSuggestion,
            onMakeHost = together::makeHost,
            onEditQueue = together::editQueue,
            onCopyLink = { com.luma.feature.nowplaying.listen.copyInvite(toastContext, it) },
            onShare = { com.luma.feature.nowplaying.listen.shareInvite(toastContext, it) },
            onLeave = {
                val r = room
                when {
                    r == null -> together.leave()
                    !r.isHost -> { together.leave(); showSession = false }
                    r.members.size <= 1 -> { together.end(); showSession = false } // alone: leaving is ending
                    else -> confirmLeaveTogether = true
                }
            },
            // Alone there's nobody to drop, so no confirmation.
            onEnd = {
                if ((room?.members?.size ?: 1) <= 1) { together.end(); showSession = false } else confirmEndTogether = true
            },
        )
    }

    if (showSpeedSheet) {
        NpSheetTheme(sheetLead) {
            SpeedSheet(
                currentSpeed = playbackSpeed,
                onSelect = { viewModel.onSetPlaybackSpeed(it); showSpeedSheet = false },
                onCustom = { showSpeedSheet = false; showCustomSpeedDialog = true },
                onDismiss = { showSpeedSheet = false },
                sheetState = speedSheetState,
            )
        }
    }

    if (showCustomSpeedDialog) {
        CustomSpeedDialog(
            initial = playbackSpeed,
            onConfirm = { viewModel.onSetPlaybackSpeed(it); showCustomSpeedDialog = false },
            onDismiss = { showCustomSpeedDialog = false },
        )
    }

    // Cast speaker sheet — opened by the cast icon in the top bar.
    if (showCastSheet) {
        com.luma.feature.nowplaying.cast.CastSheet(
            viewModel = castViewModel,
            onDismiss = { showCastSheet = false },
        )
    }

    // Sleep timer bottom sheet — opened by the icon next to the track title.
    if (showSleepTimerSheet) {
        NpSheetTheme(sheetLead) {
            SleepTimerSheet(
                currentState = sleepTimerState,
                onSelectMinutes = { minutes ->
                    viewModel.onSleepTimerMinutes(minutes)
                    showSleepTimerSheet = false
                },
                onSelectEndOfTrack = {
                    viewModel.onSleepTimerEndOfTrack()
                    showSleepTimerSheet = false
                },
                onSelectCustom = {
                    showSleepTimerSheet = false
                    showCustomTimerDialog = true
                },
                onCancel = {
                    viewModel.onSleepTimerCancel()
                    showSleepTimerSheet = false
                },
                onDismiss = { showSleepTimerSheet = false },
                sheetState = sleepTimerSheetState,
            )
        }
    }

    // Custom sleep-timer minutes dialog — opened from the sheet's "Custom" row.
    if (showCustomTimerDialog) {
        CustomSleepTimerDialog(
            onConfirm = { minutes ->
                viewModel.onSleepTimerMinutes(minutes)
                showCustomTimerDialog = false
            },
            onDismiss = { showCustomTimerDialog = false },
        )
    }

    // "This song is wrong" — 3-option dialog triggered by the flag icon.
    // Separated from the icon's direct action so the same entry point
    // covers three very different outcomes: mark for replacement, delete
    // the file, delete + permanently block.
    if (showWrongMatchDialog && track != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showWrongMatchDialog = false },
            title = {
                androidx.compose.material3.Text(
                    text = "What's wrong with this song?",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
            },
            text = {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.Text(
                        text = "Pick what should happen to '${track.title}'.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.foundation.layout.Spacer(
                        modifier = Modifier.height(4.dp),
                    )
                    if (false && !track.isFlac) { // LUMA: no "Find in FLAC" (no lossless)
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                viewModel.findInFlacForCurrentTrack()
                                showWrongMatchDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            androidx.compose.material3.Text("Find in FLAC")
                        }
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            viewModel.flagCurrentTrackAsWrongMatch()
                            showWrongMatchDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        androidx.compose.material3.Text("Find a better match")
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            viewModel.deleteCurrentTrack(alsoBlock = false)
                            showWrongMatchDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        androidx.compose.material3.Text("Delete from library")
                    }
                    androidx.compose.material3.Button(
                        onClick = {
                            viewModel.deleteCurrentTrack(alsoBlock = true)
                            showWrongMatchDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        androidx.compose.material3.Text("Delete and block forever")
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showWrongMatchDialog = false },
                ) {
                    androidx.compose.material3.Text("Cancel")
                }
            },
        )
    }

    NpGlassTheme {
    Box(
        modifier = Modifier
            .holdHeightWhileLeaving()
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Ambient animated background behind everything — dark canvas, the
        // light pastel wash, or a dead-black AMOLED ground, following the
        // resolved app theme. It runs to the screen's bottom edge, under the
        // system navigation bar (the scaffold hides the tab bar on this route).
        if (ambientAnimationEnabled) when (ambientStyle) {
            com.luma.core.data.prefs.AmbientStyle.SOFT_GLOW -> AmbientBackground(
                palette = uiState.ambientPalette,
                lightMode = MaterialTheme.colorScheme.background.luminance() >= 0.5f,
                amoledMode = LocalIsAmoledTheme.current,
                modifier = Modifier.fillMaxSize(),
            )
            // Settings > Appearance > Ambient style: the original ambient, unchanged.
            com.luma.core.data.prefs.AmbientStyle.CLASSIC -> com.luma.feature.nowplaying.ui.ClassicAmbientBackground(
                dominantColor = uiState.dominantColor,
                vibrantColor = uiState.vibrantColor,
                mutedColor = uiState.mutedColor,
                lightMode = MaterialTheme.colorScheme.background.luminance() >= 0.5f,
                amoledMode = LocalIsAmoledTheme.current,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // LUMA: the whole player is one sheet of frosted glass: the album art,
        // full-screen and heavily blurred, under a frost tint, with every control
        // sitting straight on it (no separate capsules).
        FrostedArtBackdrop(
            artModel = track?.albumArtPath ?: track?.albumArtUrl,
            tint = dynTint,
            modifier = Modifier.fillMaxSize(),
        )

        // The raw navigation-bar inset (never consumed by the scaffold here), so the
        // controls sit above the gesture handle or the 3-button bar and do not move
        // while the screen slides away.
        val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // LUMA: the player is one screen tall; scroll down past it for the live lyrics.
        androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val pageHeight = maxHeight
        Column(modifier = Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        Column(modifier = Modifier.fillMaxWidth().height(pageHeight).padding(bottom = navBarInset)) {
            // #333 v2 — the slot system. No estimated heights, no thresholds:
            // the title/progress/controls stack is FIXED and anchors to the
            // bottom of the column (directly above the lyrics bar when it's
            // present), while the album art lives in a weight(1f) slot that
            // absorbs exactly whatever height this phone/scale/bar
            // combination leaves over. The art sizes to its slot (capped by
            // width and the 375dp design ceiling) and centers in it, so any
            // slack splits evenly around the art instead of pooling anywhere.
            // Every phone renders the same anatomy, scaled — nothing clips,
            // nothing crowds, nothing pools.
            NowPlayingBody(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp),
                top = {
                // -- Top bar: dismiss, radio, more (options sheet) --
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // LUMA: the sleep timer sits beside the close arrow.
                if (uiState.hasTrack) {
                    val sleepTimerActive = sleepTimerState != SleepTimerController.State.Off
                    IconButton(
                        onClick = { showSleepTimerSheet = true },
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = if (sleepTimerActive) "Sleep timer active" else "Set sleep timer",
                            tint = if (sleepTimerActive) dynAccent else npInk(),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Text(
                    text = "Now Playing",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = npInk(),
                )
                TopBar(
                    onDismiss = onDismiss,
                    onMoreClick = { showOptionsSheet = true },
                    onQueueClick = { showQueue = true },
                    hasTrack = uiState.hasTrack,
                    isDownloading = isDownloadingCurrent,
                    // Radio toggle: start a station seeded from this song, or stop
                    // the active one. Lives in the TopBar icon row (no vertical
                    // footprint); accented while a station is running.
                    radioActive = radioLabel != null,
                    radioTuning = radioTuning,
                    radioLock = radioLock,
                    onStartRadio = viewModel::startRadioFromCurrent,
                    onStopRadio = viewModel::stopRadio,
                    // No radio in a session, for host or listener: the room owns the queue.
                    showRadio = false, // LUMA: no radio button; autoplay keeps the music going like YouTube Music
                    // Nor the queue: the sheet would show the user's own queue, which the room set aside.
                    showQueueButton = false, // LUMA: Up Next's "See all" opens the queue
                    accentColor = npAccent(uiState.vibrantColor),
                    // Cast and Listen Together exclude each other (cast spec §3).
                    castButton = {
                        if (room == null) {
                            com.luma.feature.nowplaying.cast.CastButton(
                                connection = castConnection,
                                tint = npInk(),
                                accentColor = npAccent(uiState.vibrantColor),
                                onClick = { showCastSheet = true },
                            )
                        }
                    },
                )
                }
                if (room != null) {
                    com.luma.feature.nowplaying.listen.SessionBar(
                        room = room,
                        onClick = { showSession = true },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                },
                art = {
                // -- Album art: NowPlayingBody sizes this slot (a square) --
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    AlbumArtSection(
                        albumArtUrl = track?.albumArtUrl,
                        albumArtPath = track?.albumArtPath,
                        accentColor = npAccent(uiState.vibrantColor),
                        onBitmapLoaded = viewModel::onAlbumArtLoaded,
                    )
                    if (track != null) {
                        // LUMA: the like heart floats on the art, on a small glass disc.
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            com.luma.core.ui.components.LikeButton(
                                isLiked = track.lumaLikedAt != null,
                                onTap = viewModel::onLikeTap,
                                unlikedTint = Color.White,
                                size = 22.dp,
                            )
                        }
                    }
                    if (room != null) {
                        // "Maya joined", "Rawn skipped": over the art, so the layout never shifts.
                        com.luma.feature.nowplaying.listen.SessionEventChip(together.events, room.myId, Modifier.align(Alignment.TopCenter))
                    }
                }
                },
                stack = {
                // -- Track info (LUMA): title and artist on the left (tap for the
                // artist page), the quality badge on the right.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (track != null) {
                                    Modifier.clickable(enabled = !resolvingArtist) { viewModel.onTrackInfoTapped() }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        Text(
                            text = track?.title ?: "Not Playing",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = npInk(),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        )
                        Spacer(modifier = Modifier.height(StackRhythm.TitleToArtist))
                        Text(
                            text = track?.artist?.primaryArtist().orEmpty(),
                            fontSize = 15.sp,
                            color = npInk().copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (track != null) {
                        Spacer(modifier = Modifier.width(12.dp))
                        val badge = trackQualityText(track)?.substringBefore(' ')?.substringBefore('·')?.trim()
                        if (resolvingArtist) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = npInk())
                        } else if (!badge.isNullOrBlank()) {
                            Text(
                                text = badge.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .border(1.dp, dynAccent.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        // LUMA: download right beside the quality badge.
                        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            if (isDownloadingCurrent) {
                                com.luma.core.ui.components.DownloadingIndicator(color = npInk())
                            } else {
                                IconButton(onClick = viewModel::toggleDownloadForCurrentTrack) {
                                    Icon(
                                        imageVector = if (track.isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                        contentDescription = if (track.isDownloaded) "Remove download" else "Download",
                                        tint = if (track.isDownloaded) dynAccent else npInk().copy(alpha = 0.85f),
                                    )
                                }
                            }
                        }
                    }
                }

                if (room != null) com.luma.feature.nowplaying.listen.PickLine(room)

                Spacer(modifier = Modifier.height(StackRhythm.InfoToProgress))

                // -- Progress bar --
                GlowingProgressBar(
                    progress = uiState.progressFraction,
                    accentColor = dynAccent,
                    elapsedMs = uiState.currentPositionMs,
                    totalMs = uiState.durationMs,
                    onSeek = viewModel::onSeekTo,
                    seekable = !isListener,
                    modifier = Modifier.fillMaxWidth(),
                    center = {
                        if (isListener) {
                            com.luma.feature.nowplaying.listen.LivePill(
                                pausedLocally = room.pausedLocally,
                                accent = npAccent(uiState.vibrantColor),
                                ink = npInk(),
                                onRejoin = together::rejoin,
                            )
                        }
                    },
                )

                Spacer(modifier = Modifier.height(StackRhythm.ProgressToControls))

                // -- Playback controls -- the same row for host and listener, so a role change
                // doesn't move anything; a listener's skip slots are simply empty.
                run {
                    PlaybackControls(
                        isPlaying = uiState.isPlaying,
                        isBuffering = uiState.isBuffering,
                        repeatMode = uiState.repeatMode,
                        accentColor = dynAccent,
                        onPlayPauseClick = viewModel::onPlayPauseClick,
                        onSkipNext = viewModel::onSkipNext,
                        onSkipPrevious = viewModel::onSkipPrevious,
                        onCycleRepeatMode = viewModel::onCycleRepeatMode,
                        // In a session: shuffle and repeat do nothing there, so React takes their place.
                        onReact = if (room != null) together::react else null,
                        canSkip = !isListener,
                        onOpenEqualizer = { showEqualizer = true },
                    )
                }


                },
            )

            // LUMA: Up Next, a glass card with the next song (tap to skip to it)
            // and "See all" for the queue. Not in a Listen Together room, which
            // owns the queue.
            val nextTrack = uiState.queue.getOrNull(uiState.currentIndex + 1)
            if (room == null && nextTrack != null) {
                UpNextCard(
                    track = nextTrack,
                    accent = dynAccent,
                    onPlay = viewModel::onSkipNext,
                    onSeeAll = { showQueue = true },
                )
            }
            // The cue that lyrics wait below.
            if (track != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = npInk().copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
                    Text("Lyrics", fontSize = 12.sp, color = npInk().copy(alpha = 0.55f))
                }
            }
        }
        if (track != null) {
            NpLyricsSection(
                state = lyricsState,
                positionMs = lyricsPositionMs,
                isPlaying = uiState.isPlaying && !uiState.isBuffering,
                playbackSpeed = playbackSpeed,
                onSeek = viewModel::onSeekTo,
                onRetry = viewModel::onLyricsRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pageHeight * 0.88f)
                    .padding(bottom = navBarInset),
            )
        }
        }
        }
        if (room != null) com.luma.feature.nowplaying.listen.FloatingReactions(together.reactions, animate = ambientAnimationEnabled, modifier = Modifier.fillMaxSize())

        // LUMA: the equalizer is a full screen of its own over the player: it slides in
        // from the right on the song's own blurred cover, in the song's colours, and
        // back (system or arrow) slides it away again.
        androidx.activity.compose.BackHandler(enabled = showEqualizer) { showEqualizer = false }
        androidx.compose.animation.AnimatedVisibility(
            visible = showEqualizer,
            enter = androidx.compose.animation.slideInHorizontally(
                androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            ) { it } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.slideOutHorizontally(
                androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            ) { it } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(250)),
            modifier = Modifier.fillMaxSize(),
        ) {
            NpSheetTheme(sheetLead) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(com.luma.core.ui.theme.LumaTheme.extendedColors.elevatedSurface)
                        // Swallow taps so nothing reaches the player underneath.
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    FrostedArtBackdrop(
                        artModel = track?.albumArtPath ?: track?.albumArtUrl,
                        tint = dynTint,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // EqualizerScreen's Scaffold pads itself for the status and navigation bars.
                    com.luma.feature.nowplaying.equalizer.EqualizerScreen(
                        onNavigateBack = { showEqualizer = false },
                    )
                }
            }
        }
    }
    }

    if (showOptionsSheet && track != null) {
        val postToCommunity = com.luma.core.ui.components.LocalPostToCommunity.current
        NpSheetTheme(sheetLead) {
            NowPlayingOptionsSheet(
                onSaveClick = { showSaveSheet = true },
                onShareClick = viewModel::onShareCurrent,
                onPostToCommunity = postToCommunity?.let { post -> { post(com.luma.core.model.community.PostTarget.Song(track)) } },
                onFlagWrongMatch = { showWrongMatchDialog = true },
                onViewAlbum = viewModel::onViewAlbumTapped,
                // The speaker decodes while casting, so speed is the phone's no longer.
                showSpeed = room == null && !casting,
                speed = playbackSpeed,
                onSpeedClick = { showSpeedSheet = true },
                together = togetherState,
                onStartTogether = { showStartTogether = true },
                onOpenSession = { showSession = true },
                onDismiss = { showOptionsSheet = false },
                sheetState = optionsSheetState,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Layout
// ---------------------------------------------------------------------------

/**
 * LUMA: Now Playing's backdrop is the cover itself: filling the screen, scaled up
 * and softly blurred so its shapes and colours still show (Apple Music style),
 * with the sharp cover sitting on top in the middle. A light scrim that deepens
 * toward the bottom keeps the title and controls readable on any cover.
 * Crossfades with the track. Android 11 and older can't blur, so there the cover
 * only tints faintly over the ambient underneath.
 */
@Composable
private fun FrostedArtBackdrop(artModel: Any?, tint: Color, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val canBlur = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    Box(modifier) {
        androidx.compose.animation.Crossfade(
            targetState = artModel,
            animationSpec = androidx.compose.animation.core.tween(800),
            label = "frostedArt",
        ) { model ->
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // Richer colour, so the glass reads tinted instead of grey.
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                        androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(1.3f) },
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.2f
                            scaleY = 1.2f
                            alpha = if (canBlur) 1f else 0.22f
                        }
                        .blur(40.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Rectangle),
                )
            }
        }
        // A light scrim with a hint of the cover's colour, deepening toward the
        // bottom so the title and controls stay readable on any cover.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to androidx.compose.ui.graphics.lerp(tint, Color.Black, 0.6f).copy(alpha = 0.30f),
                        0.45f to androidx.compose.ui.graphics.lerp(tint, Color.Black, 0.7f).copy(alpha = 0.38f),
                        1f to androidx.compose.ui.graphics.lerp(tint, Color.Black, 0.85f).copy(alpha = 0.72f),
                    ),
                ),
        )
    }
}

private val NpIndigoDeep = Color(0xFF0D0C12)
private val NpViolet = Color(0xFFB794F6)

/**
 * LUMA: the player always wears its own deep-indigo glass, whatever the app
 * theme: a dark scheme for everything drawn on it (the sheets it opens keep
 * the app theme, as they are drawn outside this).
 */
@Composable
private fun NpGlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            background = NpIndigoDeep,
            surface = Color(0xFF1C1745),
            surfaceVariant = Color(0xFF2A2458),
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFD8D1FF),
        ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

/**
 * LUMA: the player's menus wear the song too: a deep version of its colour
 * with white text, matching the player behind them. A black-and-white cover
 * ([lead] null) gets the player's own deep indigo.
 */
@Composable
private fun NpSheetTheme(lead: Color?, content: @Composable () -> Unit) {
    val deep = lead?.let { com.luma.core.ui.theme.songDeepSurface(it) } ?: Color(0xFF1C1745)
    val accent = lead?.let { com.luma.core.ui.theme.songAccent(it, dark = true) } ?: NpViolet
    val extended = com.luma.core.ui.theme.LumaTheme.extendedColors
    androidx.compose.runtime.CompositionLocalProvider(
        // The sheet is always dark, so its glass, borders and faint text take the
        // dark theme's values even when the app is light.
        com.luma.core.ui.theme.LocalLumaColors provides com.luma.core.ui.theme.LumaExtendedColorsDark.copy(
            elevatedSurface = deep,
            spotifyGreen = extended.spotifyGreen,
            youtubeRed = extended.youtubeRed,
        ),
        com.luma.core.ui.theme.LocalIsDarkTheme provides true,
    ) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                primary = accent,
                surface = deep,
                surfaceContainerLow = deep,
                background = deep,
                onBackground = Color.White,
                onSurface = Color.White,
                onSurfaceVariant = Color.White.copy(alpha = 0.72f),
                outlineVariant = Color.White.copy(alpha = 0.15f),
            ),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}

/**
 * LUMA: a music visualizer, bars rising and falling just past the art's left
 * and right edges (the art covers the middle). Still while paused.
 */
@Composable
private fun Modifier.visualizerBars(playing: Boolean, color: Color): Modifier {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "visualizer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "visualizerPhase",
    )
    val level by androidx.compose.animation.core.animateFloatAsState(if (playing) 1f else 0.25f, label = "visualizerLevel")
    return this.drawBehind {
        val barW = 4.dp.toPx()
        val gap = 5.dp.toPx()
        val reach = 26.dp.toPx()
        val count = (reach / (barW + gap)).toInt().coerceAtLeast(3)
        val midY = size.height / 2f
        for (side in 0..1) {
            for (i in 0 until count) {
                val x = if (side == 0) -reach + i * (barW + gap) else size.width + gap + i * (barW + gap)
                val wave = (kotlin.math.sin(phase * (1 + i % 3) + i * 1.3f + side * 2f) + 1f) / 2f
                val h = size.height * (0.18f + 0.42f * wave * level) * (1f - 0.12f * i)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.10f), color.copy(alpha = 0.55f), color.copy(alpha = 0.10f)),
                        startY = midY - h / 2f,
                        endY = midY + h / 2f,
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(x, midY - h / 2f),
                    size = androidx.compose.ui.geometry.Size(barW, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f),
                )
            }
        }
    }
}

/** LUMA: live lyrics, right on the player's glass below the controls; always live, no switch. */
@Composable
private fun NpLyricsSection(
    state: com.luma.feature.nowplaying.ui.LyricsViewState,
    positionMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 8.dp)) {
        Text(
            "Lyrics",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (state) {
                com.luma.feature.nowplaying.ui.LyricsViewState.Loading ->
                    com.luma.feature.nowplaying.ui.CenteredSpinner("Fetching lyrics\u2026")
                is com.luma.feature.nowplaying.ui.LyricsViewState.Synced -> com.luma.feature.nowplaying.ui.LyricsSyncedRenderer(
                    lines = state.lines,
                    currentPositionMs = positionMs,
                    onLineTap = onSeek,
                    syllables = state.syllables,
                    isPlaying = isPlaying,
                    playbackSpeed = playbackSpeed,
                )
                is com.luma.feature.nowplaying.ui.LyricsViewState.Plain ->
                    com.luma.feature.nowplaying.ui.LyricsPlainRenderer(state.text)
                com.luma.feature.nowplaying.ui.LyricsViewState.Instrumental ->
                    com.luma.feature.nowplaying.ui.CenteredPlacard("\u266A Instrumental")
                com.luma.feature.nowplaying.ui.LyricsViewState.None ->
                    com.luma.feature.nowplaying.ui.CenteredPlacard(label = "No lyrics found", action = "Retry", onAction = onRetry)
                is com.luma.feature.nowplaying.ui.LyricsViewState.Error ->
                    com.luma.feature.nowplaying.ui.CenteredPlacard(
                        label = "Couldn't load lyrics",
                        action = if (state.retryable) "Retry" else null,
                        onAction = onRetry,
                    )
            }
        }
    }
}

/** LUMA: the next song, on a glass card at the foot of the player. */
@Composable
private fun UpNextCard(
    track: com.luma.core.model.Track,
    accent: Color,
    onPlay: () -> Unit,
    onSeeAll: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.06f))),
                shape,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Up Next", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
            Text(
                "See all",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSeeAll).padding(6.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().songTap(onClick = onPlay).clip(RoundedCornerShape(12.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = track.albumArtPath ?: track.albumArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, fontSize = 13.sp, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
    }
}

/** The fixed steps inside the title-to-controls stack. */
private object StackRhythm {
    val TitleToArtist = 4.dp
    val ArtistToQuality = 6.dp
    val InfoToProgress = 16.dp
    val ProgressToControls = 12.dp
}

/** The art's design ceiling. */
private val ArtMax = 375.dp

/*
 * The three flexible gaps around the art, at their full size and at their floor:
 * top bar -> art, art -> title, controls -> the bottom (the lyrics bar or the edge).
 * They shrink together towards the floor only when keeping them would make the
 * art smaller than ART_COMFORT of its full size (a short phone, a large font).
 */
private val GapTop = 20.dp
private val GapTopMin = 8.dp
private val GapArt = 36.dp
private val GapArtMin = 16.dp
private val GapBottom = 28.dp
private val GapBottomMin = 12.dp
private const val ART_COMFORT = 0.75f

/**
 * Now Playing's vertical rhythm: [top] (the icon row), a square [art] slot, and the
 * [stack] (title, artist, quality, progress, controls). The top and stack take their
 * natural heights; the art gets what is left after the three gaps, capped by the
 * width (so it lines up with the progress bar and the controls) and [ArtMax].
 * Any height still over (a tall phone) is split evenly above the art and below the
 * controls, so the whole group sits centred with its internal spacing unchanged.
 * Nothing scrolls and nothing overlaps: on a short screen the gaps give way first,
 * then the art.
 */
@Composable
private fun NowPlayingBody(
    modifier: Modifier = Modifier,
    top: @Composable () -> Unit,
    art: @Composable () -> Unit,
    stack: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.ui.layout.Layout(
        contents = listOf(
            { Column(horizontalAlignment = Alignment.CenterHorizontally) { top() } },
            art,
            { Column(horizontalAlignment = Alignment.CenterHorizontally, content = stack) },
        ),
        modifier = modifier,
    ) { (topM, artM, stackM), constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val loose = constraints.copy(minWidth = w, maxWidth = w, minHeight = 0)
        val topP = topM.first().measure(loose)
        val stackP = stackM.first().measure(loose)
        val avail = (h - topP.height - stackP.height).coerceAtLeast(0)

        val full = minOf(w, ArtMax.roundToPx())
        val nominal = GapTop.roundToPx() + GapArt.roundToPx() + GapBottom.roundToPx()
        val floor = GapTopMin.roundToPx() + GapArtMin.roundToPx() + GapBottomMin.roundToPx()
        val shortfall = (full * ART_COMFORT - (avail - nominal)).coerceIn(0f, (nominal - floor).toFloat())
        val k = if (nominal > floor) 1f - shortfall / (nominal - floor) else 1f
        fun gap(max: androidx.compose.ui.unit.Dp, min: androidx.compose.ui.unit.Dp): Int =
            (min.roundToPx() + (max.roundToPx() - min.roundToPx()) * k).toInt()
        val gTop = gap(GapTop, GapTopMin)
        val gArt = gap(GapArt, GapArtMin)
        val gBottom = gap(GapBottom, GapBottomMin)
        val artSide = (avail - gTop - gArt - gBottom).coerceIn(0, full)
        val slack = (avail - gTop - gArt - gBottom - artSide).coerceAtLeast(0)

        val artP = artM.first().measure(androidx.compose.ui.unit.Constraints.fixed(artSide, artSide))
        layout(w, h) {
            topP.place(0, 0)
            val artY = topP.height + gTop + slack / 2
            artP.place((w - artSide) / 2, artY)
            stackP.place(0, artY + artSide + gArt)
        }
    }
}

/**
 * Keeps Now Playing at its full height while it slides away. As it closes, the
 * scaffold brings the tab bar and mini player straight back, which would shrink
 * this screen mid-slide and reflow the art; instead, while its back-stack entry is
 * not resumed (entering or leaving), it keeps the tallest height it has had and
 * runs past the bottom, under the returning bars.
 */
@Composable
private fun Modifier.holdHeightWhileLeaving(): Modifier {
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    val resumed = state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    val held = remember { intArrayOf(0, 0) } // width, height
    return this.layout { measurable, constraints ->
        val w = constraints.maxWidth
        val maxH = constraints.maxHeight
        if (held[0] != w) {
            held[0] = w
            held[1] = 0
        }
        val height = if (resumed) maxH else maxOf(held[1], maxH)
        held[1] = height
        val p = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
        layout(w, maxH) { p.place(0, 0) }
    }
}

// ---------------------------------------------------------------------------
// Private composables
// ---------------------------------------------------------------------------

/**
 * Top bar: dismiss, radio toggle, a "more" kebab that opens
 * [NowPlayingOptionsSheet] (the current track's actions), and a
 * dedicated Queue button pinned to the far-right edge.
 *
 * @param onDismiss    Callback when the down-arrow is tapped.
 * @param onMoreClick  Callback when the kebab (more actions) icon is tapped.
 * @param onQueueClick Callback when the far-right Queue icon is tapped.
 * @param hasTrack     Whether a track is currently loaded.
 */
@Composable
private fun TopBar(
    onDismiss: () -> Unit,
    onMoreClick: () -> Unit,
    onQueueClick: () -> Unit,
    hasTrack: Boolean,
    isDownloading: Boolean,
    radioActive: Boolean,
    radioTuning: Boolean,
    radioLock: Boolean,
    onStartRadio: () -> Unit,
    onStopRadio: () -> Unit,
    showRadio: Boolean,
    showQueueButton: Boolean,
    accentColor: Color,
    castButton: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Dismiss",
                tint = npInk(),
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Cast — left of Radio. Draws nothing where Cast isn't available.
        if (hasTrack) castButton()

        // Radio toggle — start a station from the current song, or stop the
        // running one. Accent tint signals an active station; the radar sweep
        // spins around the icon while the station is being built.
        if (hasTrack && showRadio) {
            Box(contentAlignment = Alignment.Center) {
                com.luma.feature.nowplaying.ui.RadarSweep(
                    tuning = radioTuning,
                    lock = radioLock,
                    color = accentColor,
                    modifier = Modifier.matchParentSize(),
                )
                IconButton(
                    onClick = {
                        when {
                            radioTuning -> Unit // ignore taps while tuning
                            radioActive -> onStopRadio()
                            else -> onStartRadio()
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = when {
                            radioTuning -> "Tuning radio"
                            radioActive -> "Stop radio"
                            else -> "Start radio"
                        },
                        tint = if (radioActive || radioTuning) accentColor else npInk(),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        // More actions — opens the options sheet (Save, Share, Speed, Download,
        // Flag, View Album). While a download is in flight a spinner replaces
        // the icon so it isn't a silent background job.
        if (hasTrack) {
            if (isDownloading) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = npInk(),
                    )
                }
            } else {
                IconButton(onClick = onMoreClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More actions",
                        tint = npInk(),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Queue — a permanent, dedicated control at the far-right edge
            // (not buried in the options sheet); it's the one action users
            // reach for constantly while a song is playing.
            if (showQueueButton) {
                IconButton(onClick = onQueueClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = npInk(),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * Album art with a colored glow shadow behind it.
 *
 * Uses Coil 3 [AsyncImage] to load the art. When the image is loaded
 * successfully, the bitmap is forwarded to [onBitmapLoaded] for palette
 * extraction.
 */
@Composable
private fun AlbumArtSection(
    albumArtUrl: String?,
    albumArtPath: String?,
    accentColor: Color,
    onBitmapLoaded: (android.graphics.Bitmap?) -> Unit,
) {
    val context = LocalContext.current
    val artModel = albumArtPath ?: albumArtUrl
    // An image that cannot load (deleted upstream, a 404, a network failure) must
    // read as "no art" — a quiet tile in the surface tone, same shape as the art —
    // not as a black hole in the middle of the screen. Coil draws nothing at all
    // for an error unless told otherwise, and never retries a failed URL.
    val missingArtColor = MaterialTheme.colorScheme.surfaceVariant
    val missingArt = remember(missingArtColor) { ColorPainter(missingArtColor) }
    // remember: an inline ImageRequest.Builder is a new object every
    // recomposition, which makes Coil re-evaluate the request each time this
    // recomposes (and this screen recomposes on every 250ms position tick).
    val artRequest = remember(context, artModel) {
        ImageRequest.Builder(context)
            .data(artModel)
            .allowHardware(false) // Required for Palette bitmap extraction.
            .build()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Glow behind the artwork.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .shadow(
                    elevation = 40.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = accentColor.copy(alpha = 0.25f),
                    spotColor = accentColor.copy(alpha = 0.25f),
                ),
        )

        AsyncImage(
            model = artRequest,
            contentDescription = "Album art",
            contentScale = ContentScale.Crop,
            error = missingArt,
            fallback = missingArt,
            onSuccess = { state: AsyncImagePainter.State.Success ->
                try {
                    val bitmap = state.result.image.toBitmap()
                    onBitmapLoaded(bitmap)
                } catch (_: Exception) {
                    // Bitmap extraction failed; palette will use defaults.
                    onBitmapLoaded(null)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp)),
        )
    }
}

/**
 * Playback controls row: equalizer, previous, play/pause, next, repeat (LUMA: shuffle is in the queue).
 */
@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    repeatMode: RepeatMode,
    accentColor: Color,
    onPlayPauseClick: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onReact: ((String) -> Unit)? = null,
    /** False for a Listen Together listener: the room skips, so the slots stay empty rather than dead. */
    canSkip: Boolean = true,
    /** LUMA: opens the equalizer, from its button left of previous. */
    onOpenEqualizer: () -> Unit = {},
) {
    // LUMA: equalizer + previous | Play | next + repeat. Two halves of equal
    // width keep Play exactly centred.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
    Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenEqualizer) {
            Icon(
                imageVector = Icons.Default.Equalizer,
                contentDescription = "Equalizer",
                tint = npInk().copy(alpha = 0.75f),
                modifier = Modifier.size(24.dp),
            )
        }

        // LUMA: no shuffle button here; Shuffle lives in the queue (QueueBottomSheet).

        // Previous
        if (!canSkip) Spacer(Modifier.size(48.dp)) else IconButton(onClick = onSkipPrevious) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                tint = npInk(),
                modifier = Modifier.size(36.dp),
            )
        }
    }

        // Play / Pause — large gradient circle. While the track is still
        // resolving/buffering, show a spinner in place of the icon so it
        // doesn't look frozen — but the button STAYS enabled: a slow or hung
        // stream resolve must never lock the user out of pausing (Media3's
        // COMMAND_PLAY_PAUSE is valid during STATE_BUFFERING).
        IconButton(
            onClick = onPlayPauseClick,
            // LUMA: a frosted glass disc with a violet glow, not a solid accent circle.
            modifier = Modifier
                .size(76.dp)
                .shadow(elevation = 24.dp, shape = CircleShape, ambientColor = accentColor, spotColor = accentColor)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.12f)),
                    ),
                    shape = CircleShape,
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.45f), CircleShape),
        ) {
            // On the accent-gradient circle, not the ambient ground — stays
            // white in both themes (the accent is contrast-adjusted instead).
            if (isBuffering) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

    Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Next
        if (!canSkip) Spacer(Modifier.size(48.dp)) else IconButton(onClick = onSkipNext) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = npInk(),
                modifier = Modifier.size(36.dp),
            )
        }

        // Repeat; in a session, React.
        if (onReact != null) {
            com.luma.feature.nowplaying.listen.ReactionButton(onReact = onReact)
        } else IconButton(onClick = onCycleRepeatMode) {
            Icon(
                imageVector = when (repeatMode) {
                    RepeatMode.ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                },
                contentDescription = "Repeat",
                tint = when (repeatMode) {
                    RepeatMode.OFF -> npInk().copy(alpha = 0.6f)
                    else -> accentColor
                },
                modifier = Modifier.size(24.dp),
            )
        }
    }
    }
}

/**
 * Formats a one-line quality summary for the Now Playing screen.
 *
 * Examples:
 *   - All four fields known:  `FLAC · 24-bit/96.0 kHz · 4233 kbps`
 *   - Codec + bitrate only:    `OPUS · 160 kbps`
 *   - Codec only:              `FLAC` (data not yet backfilled)
 *
 * Returns null only when the codec is blank — in that case the caller
 * should render no line at all.
 */
private fun trackQualityText(track: com.luma.core.model.Track): String? {
    // v0.9.13 fix: tracks downloaded before format-tracking was wired (pre-v0.9.11)
    // default to file_format = "opus" regardless of the actual codec — so a FLAC
    // file would render "OPUS · 4233 kbps", which is the source of "every track says
    // Opus" complaints. The Library Health backfill writes correct values from disk
    // but only when the user opens that screen. Cheap interim correction: if the
    // track has a downloaded filePath, prefer the file extension as canonical.
    val extension = track.filePath
        ?.takeIf { it.isNotBlank() }
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase()
    val codec = when (extension) {
        "flac", "alac", "wav", "ape", "tta", "wv", "aiff" -> extension!!.uppercase()
        "opus", "m4a", "mp3", "ogg", "aac" -> extension!!.uppercase()
        else -> track.fileFormat.takeIf { it.isNotBlank() }?.uppercase() ?: return null
    }
    val bitDepth = track.bitsPerSample
    val sampleRateKHz = track.sampleRateHz?.let { it / 1000.0 }
    val bitrate = track.qualityKbps.takeIf { it > 0 }
    return buildList {
        add(codec)
        if (bitDepth != null && sampleRateKHz != null) {
            add("${bitDepth}-bit/${"%.1f".format(sampleRateKHz)} kHz")
        }
        if (bitrate != null) add("$bitrate kbps")
        // Flag the YouTube fallback so the user can tell when a track is
        // playing from yt-dlp/InnerTube extraction rather than Qobuz. The
        // codec ("AAC") alone doesn't convey this — Qobuz also serves AAC
        // at MP3_320 tier. Only the streamOrigin field distinguishes the
        // two. We don't badge "via Kennyy" / "via squid" because those
        // are the expected primary sources; only the lossy fallback
        // deserves a callout.
        when (track.streamOrigin) {
            "jiosaavn" -> add("via JioSaavn")
            "youtube" -> add("via YT")
        }
    }.joinToString(" · ")
}

/**
 * Renders the codec/bitrate quality line beneath the artist · album row.
 * When [isStreaming] is `true` a small wifi glyph is prefixed so the
 * user can tell at a glance that playback is coming from the network
 * rather than a local file. The icon picks up
 * [MaterialTheme.colorScheme.primary] so it stands out against the
 * white-on-ambient quality text without clashing with the album-art
 * palette.
 *
 * Centered as a Row so the prefix-icon variant stays visually balanced
 * with the icon-less variant — the original `Text(textAlign = Center)`
 * call is preserved when there is nothing to prefix.
 */
@Composable
private fun QualityLine(
    qualityText: String,
    isStreaming: Boolean,
) {
    if (isStreaming) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = "Streaming",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = qualityText,
                style = MaterialTheme.typography.bodySmall,
                color = npInk().copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Text(
            text = qualityText,
            style = MaterialTheme.typography.bodySmall,
            color = npInk().copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Premium track options bottom sheet, opened via the [TopBar]'s "more"
 * kebab icon. Consolidates the current track's actions. Queue
 * is NOT here — it has its own permanent icon at the TopBar's far right, next
 * to Dismiss and Radio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingOptionsSheet(
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    onPostToCommunity: (() -> Unit)?,
    onFlagWrongMatch: () -> Unit,
    onViewAlbum: () -> Unit,
    showSpeed: Boolean,
    speed: Float,
    onSpeedClick: () -> Unit,
    together: ListenTogetherState,
    onStartTogether: () -> Unit,
    onOpenSession: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: androidx.compose.material3.SheetState,
    modifier: Modifier = Modifier,
) {
    val extendedColors = com.luma.core.ui.theme.LumaTheme.extendedColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = extendedColors.elevatedSurface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
        ) {
            Text(
                text = "Track Options",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(bottom = 20.dp)
                    .align(Alignment.CenterHorizontally)
            )

            // LUMA: no "Listen together" to start; only a session already running shows here.
            if (together is ListenTogetherState.InRoom) {
                SheetOptionRow(icon = Icons.Default.Groups, label = "Listening together", onClick = { onOpenSession(); onDismiss() })
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Save to Playlist
            SheetOptionRow(
                icon = Icons.Default.BookmarkBorder,
                label = "Save to Playlist",
                onClick = {
                    onSaveClick()
                    onDismiss()
                }
            )

            // LUMA: no "Share song".

            // Post to Community (only while Community is on)
            if (onPostToCommunity != null) {
                Spacer(modifier = Modifier.height(8.dp))
                SheetOptionRow(
                    icon = Icons.Default.Public,
                    label = "Post to Community",
                    onClick = { onPostToCommunity(); onDismiss() },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // View Album — resolves and opens the actual remote album page,
            SheetOptionRow(
                icon = Icons.Default.Album,
                label = "View Album",
                onClick = { onViewAlbum(); onDismiss() }
            )

            // Playback speed — opens the speed picker. Shows the speed when it isn't 1x.
            // Not in a Listen Together session: the room keeps everyone in sync.
            if (showSpeed) {
                Spacer(modifier = Modifier.height(8.dp))
                SheetOptionRow(
                    icon = Icons.Default.Speed,
                    label = if (speed == 1f) "Playback speed" else "Playback speed · ${formatSpeed(speed)}",
                    onClick = { onSpeedClick(); onDismiss() },
                )
            }

            // LUMA: no Download here; the player's own download button covers it.

            Spacer(modifier = Modifier.height(8.dp))

            // Flag as Wrong Match
            SheetOptionRow(
                icon = Icons.Default.Flag,
                label = "Flag as Wrong Match",
                onClick = {
                    onFlagWrongMatch()
                    onDismiss()
                }
            )
        }
    }
}

/**
 * Sleep-timer picker, opened from the icon beside the track title. Shows
 * the current countdown/end-of-track status (if armed), then the preset
 * durations, a Custom entry, End of Track, and — only while a timer is
 * running — a Cancel row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    currentState: SleepTimerController.State,
    onSelectMinutes: (Int) -> Unit,
    onSelectEndOfTrack: () -> Unit,
    onSelectCustom: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: androidx.compose.material3.SheetState,
) {
    val extendedColors = com.luma.core.ui.theme.LumaTheme.extendedColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = extendedColors.elevatedSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
        ) {
            Text(
                text = "Sleep Timer",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .padding(bottom = 20.dp)
                    .align(Alignment.CenterHorizontally),
            )

            when (currentState) {
                is SleepTimerController.State.Countdown -> {
                    val minutesLeft = ((currentState.endsAtMs - System.currentTimeMillis()) / 60_000L)
                        .coerceAtLeast(0) + 1
                    Text(
                        text = "Music pauses in about $minutesLeft min",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                SleepTimerController.State.EndOfTrack -> Text(
                    text = "Music pauses when the current track ends",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                SleepTimerController.State.Off -> Unit
            }

            listOf(15, 30, 45, 60, 120, 180).forEach { minutes ->
                SheetOptionRow(
                    icon = Icons.Default.Timer,
                    label = "$minutes min",
                    onClick = { onSelectMinutes(minutes) },
                )
                Spacer(Modifier.height(8.dp))
            }
            SheetOptionRow(icon = Icons.Default.Edit, label = "Custom", onClick = onSelectCustom)
            Spacer(Modifier.height(8.dp))
            SheetOptionRow(icon = Icons.Default.Bedtime, label = "End of Track", onClick = onSelectEndOfTrack)

            if (currentState != SleepTimerController.State.Off) {
                Spacer(Modifier.height(8.dp))
                SheetOptionRow(
                    icon = Icons.Default.Close,
                    label = "Cancel timer",
                    onClick = onCancel,
                )
            }
        }
    }
}

/** Plain numeric-minutes entry, reached via the sheet's "Custom" row. */
@Composable
private fun CustomSleepTimerDialog(
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutesText by remember { mutableStateOf("") }
    val parsedMinutes = minutesText.toIntOrNull()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom sleep timer") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = minutesText,
                onValueChange = { if (it.all(Char::isDigit)) minutesText = it },
                label = { Text("Minutes") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { parsedMinutes?.let(onConfirm) },
                enabled = parsedMinutes != null && parsedMinutes > 0,
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSheet(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: androidx.compose.material3.SheetState,
) {
    val extendedColors = com.luma.core.ui.theme.LumaTheme.extendedColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = extendedColors.elevatedSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
        ) {
            Text(
                text = "Playback speed",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .align(Alignment.CenterHorizontally),
            )
            Text(
                text = speedLabel(currentSpeed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .align(Alignment.CenterHorizontally),
            )

            // Four to a row when every label fits, else two: one line per chip at any font size.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val labelStyle = MaterialTheme.typography.labelMedium
                val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                val density = androidx.compose.ui.platform.LocalDensity.current
                val widestLabelPx = remember(labelStyle, density) {
                    SpeedPresets.maxOf { measurer.measure(speedLabel(it), labelStyle, maxLines = 1).size.width }
                }
                val columns = with(density) {
                    speedPresetColumns(
                        rowWidthPx = constraints.maxWidth,
                        widestChipPx = widestLabelPx + SpeedChipLabelInset.roundToPx(),
                        gapPx = 8.dp.roundToPx(),
                    )
                }
                Column {
                    SpeedPresets.chunked(columns).forEach { rowSpeeds ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowSpeeds.forEach { s ->
                                FilterChip(
                                    selected = currentSpeed == s,
                                    onClick = { onSelect(s) },
                                    label = {
                                        Text(
                                            text = speedLabel(s),
                                            style = labelStyle,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    },
                                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            SheetOptionRow(icon = Icons.Default.Edit, label = "Custom", onClick = onCustom)
        }
    }
}

/**
 * A FilterChip's label sits inside 8 dp of chip padding and 8 dp of label padding on each side
 * (Material 3's chip layout); the column choice above measures the label plus this.
 */
private val SpeedChipLabelInset = 32.dp

/** Free-form speed entry, reached via the sheet's "Custom" row. */
@Composable
private fun CustomSpeedDialog(
    initial: Float,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(if (initial == 1f) "" else formatSpeed(initial).removeSuffix("x")) }
    val parsed = parseCustomSpeed(text)
    val valid = parsed != null
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom speed") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { new -> customSpeedInput(new)?.let { text = it } },
                label = { Text("Speed (0.1 – 4.0)") },
                suffix = { Text("x") },
                singleLine = true,
                isError = text.isNotEmpty() && !valid,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = valid) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "QualityLine — streaming",
    showBackground = true,
    backgroundColor = 0xFF101012,
)
@Composable
private fun PreviewQualityLineStreaming() {
    com.luma.core.ui.theme.LumaTheme {
        QualityLine(
            qualityText = "OPUS \u00B7 160 kbps",
            isStreaming = true,
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "QualityLine — local",
    showBackground = true,
    backgroundColor = 0xFF101012,
)
@Composable
private fun PreviewQualityLineLocal() {
    com.luma.core.ui.theme.LumaTheme {
        QualityLine(
            qualityText = "FLAC \u00B7 24-bit/96.0 kHz \u00B7 4233 kbps",
            isStreaming = false,
        )
    }
}
