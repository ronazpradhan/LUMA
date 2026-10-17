package com.luma.feature.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.core.ui.components.GlassCard
import com.luma.feature.settings.components.AccountConnectionCard
import com.luma.feature.settings.components.SettingsScaffold
import com.luma.feature.settings.components.SettingsSectionLabel

/**
 * The Accounts & Sync spoke of the hub-and-spoke Settings redesign.
 *
 * Behavior-preserving relocation of the original "Accounts" section from the
 * monolithic [SettingsScreen]: the Spotify and YouTube Music connection cards
 * (each with their inline auto-save / history-sync extras), the Last.fm card,
 * and the like-mirroring opt-ins (plus the mirror-warning ack dialog). Every
 * control calls the SAME [SettingsViewModel] method the old screen used; no
 * logic is changed. The `bringIntoViewRequester` on the Last.fm card (a
 * deep-link affordance) is intentionally dropped here.
 */
@Composable
fun SettingsAccountsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val extendedColors = com.luma.core.ui.theme.LumaTheme.extendedColors

    // Spotify WebView login (full-screen overlay). Replaces the screen content
    // while active — the connect flow on the Spotify card flips this flag.
    if (uiState.showSpotifyWebLogin) {
        com.luma.feature.settings.components.SpotifyLoginWebView(
            onCookieExtracted = viewModel::onSpotifyWebLoginCookieExtracted,
            onDismiss = viewModel::onDismissSpotifyWebLogin,
            onManualFallback = viewModel::onConnectSpotifyManual,
        )
        return
    }

    // YouTube Music WebView login (full-screen overlay).
    if (uiState.showYouTubeWebLogin) {
        com.luma.feature.settings.components.YouTubeLoginWebView(
            onCookieExtracted = viewModel::onYouTubeWebLoginCookieExtracted,
            onDismiss = viewModel::onDismissYouTubeWebLogin,
            onManualFallback = viewModel::onConnectYouTubeManual,
        )
        return
    }

    // Spotify sp_dc cookie input dialog (manual fallback).
    if (uiState.showSpotifyCookieDialog) {
        com.luma.feature.settings.components.SpotifyCookieDialog(
            isValidating = uiState.isSpotifyCookieValidating,
            errorMessage = uiState.spotifyCookieError,
            onConnect = { cookie, username -> viewModel.onConnectSpotifyWithCookie(cookie, username) },
            onDismiss = viewModel::onDismissSpotifyCookieDialog,
        )
    }

    // YouTube Music cookie input dialog.
    if (uiState.showYouTubeCookieDialog) {
        com.luma.feature.settings.components.YouTubeCookieDialog(
            isValidating = uiState.isYouTubeCookieValidating,
            errorMessage = uiState.youTubeCookieError,
            onConnect = viewModel::onConnectYouTubeWithCookie,
            onDismiss = viewModel::onDismissYouTubeCookieDialog,
        )
    }


    // YouTube error dialog (missing credentials, network failure, etc.).
    if (uiState.youTubeError != null) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissYouTubeError,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            title = {
                Text(
                    text = "YouTube Music",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Text(
                    text = uiState.youTubeError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::onDismissYouTubeError) {
                    Text("OK")
                }
            },
        )
    }

    SettingsScaffold(title = "Accounts & Sync", onBack = onBack, modifier = modifier) {
        // Like-mirroring opt-in ack. Rendered at the top of the scaffold so it
        // overlays the content. Confirming writes the pref; dismissing leaves
        // mirroring off (no writes without this ack).
        uiState.pendingMirrorWarning?.let { dest ->
            com.luma.feature.settings.components.LikeMirrorWarningDialog(
                serviceName = if (dest == com.luma.core.data.social.Destination.SPOTIFY) "Spotify" else "YouTube Music",
                onConfirm = viewModel::onMirrorWarningConfirmed,
                onDismiss = viewModel::onMirrorWarningDismissed,
            )
        }

        SettingsSectionLabel("Connections")

        AccountConnectionCard(
            serviceName = "Spotify",
            icon = Icons.Rounded.MusicNote,
            accentColor = extendedColors.spotifyGreen,
            authState = uiState.spotifyAuthState,
            onConnect = viewModel::onConnectSpotify,
            onDisconnect = viewModel::onDisconnectSpotify,
            extraContent = {
                com.luma.feature.settings.components.SpotifyAutoSaveSection(
                    enabled = uiState.autoSaveEnabled,
                    threshold = uiState.autoSaveThreshold,
                    autoSavedCountLast7Days = uiState.autoSavedCountLast7Days,
                    spotifyConnected = uiState.spotifyAuthState is com.luma.core.auth.model.AuthState.Connected,
                    onToggle = viewModel::onAutoSaveEnabledChanged,
                    onThresholdChanged = viewModel::onAutoSaveThresholdChanged,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            },
        )

        AccountConnectionCard(
            serviceName = "YouTube Music",
            icon = Icons.Rounded.PlayCircle,
            accentColor = extendedColors.youtubeRed,
            authState = uiState.youTubeAuthState,
            onConnect = viewModel::onConnectYouTube,
            onDisconnect = viewModel::onDisconnectYouTube,
            extraContent = {
                com.luma.feature.settings.components.YouTubeHistorySyncSection(
                    enabled = uiState.ytHistoryEnabled,
                    health = uiState.ytHistoryHealth,
                    pendingCount = uiState.ytPendingCount,
                    ytConnected = uiState.youTubeAuthState is com.luma.core.auth.model.AuthState.Connected,
                    onToggle = viewModel::onYouTubeHistoryEnabledChanged,
                    onRetry = viewModel::onRetryYouTubeHistory,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            },
        )

        // LUMA: no Discord status, Last.fm or ListenBrainz connections.

        SettingsSectionLabel("Sync your likes", beta = true)

        GlassCard {
            val mirrorLastFm by viewModel.mirrorLikesLastFm.collectAsStateWithLifecycle()
            com.luma.feature.settings.components.LikeMirrorSection(
                spotifyEnabled = uiState.mirrorLikesSpotify,
                ytMusicEnabled = uiState.mirrorLikesYtMusic,
                lastFmEnabled = mirrorLastFm,
                spotifyConnected = uiState.spotifyAuthState is com.luma.core.auth.model.AuthState.Connected,
                ytConnected = uiState.youTubeAuthState is com.luma.core.auth.model.AuthState.Connected,
                lastFmConnected = uiState.lastFmState is LastFmAuthState.Connected,
                onSpotifyToggle = { viewModel.onMirrorToggleRequested(com.luma.core.data.social.Destination.SPOTIFY, it) },
                onYtMusicToggle = { viewModel.onMirrorToggleRequested(com.luma.core.data.social.Destination.YT_MUSIC, it) },
                onLastFmToggle = { viewModel.onMirrorToggleRequested(com.luma.core.data.social.Destination.LAST_FM, it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
