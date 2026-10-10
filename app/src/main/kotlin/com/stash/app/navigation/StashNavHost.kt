package com.stash.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import com.stash.core.ui.components.LocalBottomChromePadding
import com.stash.core.ui.components.ambientBackdrop
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.stash.data.ytmusic.model.AlbumSource
import com.stash.feature.community.CommunityHostViewModel
import com.stash.feature.community.CommunityPostScreen
import com.stash.feature.community.CommunityPosting
import com.stash.feature.community.CommunityPostingHost
import com.stash.feature.community.CommunityScreen
import com.stash.feature.community.CommunitySection
import com.stash.feature.community.rememberCommunityPosting
import com.stash.feature.home.HomeScreen
import com.stash.feature.home.MixBrowseScreen
import com.stash.feature.home.MixRail
import com.stash.feature.home.PlaylistBrowseScreen
import com.stash.feature.library.AlbumDetailScreen
import com.stash.feature.library.ArtistDetailScreen
import com.stash.feature.library.LibraryScreen
import com.stash.feature.library.LikedSongsDetailScreen
import com.stash.feature.library.PlaylistDetailScreen
import com.stash.feature.library.mixbuilder.MixBuilderScreen
import com.stash.feature.nowplaying.NowPlayingScreen
import com.stash.feature.search.AlbumDiscoveryScreen
import com.stash.feature.search.ArtistProfileScreen
import com.stash.feature.search.SearchScreen
import com.stash.feature.settings.BlockedSongsScreen
import com.stash.feature.settings.SettingsHubScreen
import com.stash.feature.settings.equalizer.EqualizerScreen
import com.stash.feature.settings.libraryhealth.LibraryHealthScreen
import com.stash.feature.sync.FailedDownloadsScreen
import com.stash.feature.sync.DownloadManagementScreen
import com.stash.feature.sync.FailedMatchesScreen
import com.stash.feature.sync.ManagePlaylistsScreen
import com.stash.feature.sync.SyncScreen

/** Transition duration for the Now Playing slide animation in milliseconds. */
private const val SLIDE_DURATION_MS = 350

/**
 * Main navigation host for the Stash app.
 *
 * Contains all top-level tab destinations plus the full-screen Now Playing
 * route which enters with a slide-up and exits with a slide-down transition.
 */
@Composable
fun StashNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    // Forwarded to detail screens that support multi-select so the host can hide
    // the mini-player while a screen is in selection mode. General by design:
    // the same lambda will be wired to Liked/Album/Artist/Library detail screens
    // in later tasks — only the Playlist destination consumes it today.
    onSelectionModeChanged: (Boolean) -> Unit = {},
    // The scaffold's canonical bottom-nav tab switch, for screens that trigger
    // one themselves (Home's Liked card → Library ▸ Liked).
    onNavigateToTab: (TopLevelDestination) -> Unit = {},
    // Open Stash on (#428). Fixed for the activity's life; MainActivity saves it across recreation.
    startDestination: Any = HomeRoute,
) {
    // Stash Community (spec 2026-09-26 §3): LocalPostToCommunity and the posting sheets sit over every screen.
    val posting = rememberCommunityPosting()
    val host: CommunityHostViewModel = hiltViewModel()
    // Community turned off (spec §3: nothing Community anywhere): a tab can restore a Community screen it saved
    // while it was on, so leave any that's showing. null is "not read yet": a restored one stays until the switch is
    // read, so a cold start with Community on doesn't lose it.
    val communityOn by host.on.collectAsStateWithLifecycle()
    val top by navController.currentBackStackEntryAsState()
    LaunchedEffect(communityOn, top) {
        if (communityOn == false) {
            while (navController.currentDestination?.let { it.hasRoute<CommunityRoute>() || it.hasRoute<CommunityPostRoute>() } == true &&
                navController.popBackStack()) Unit
        }
    }
    CommunityPostingHost(
        posting,
        onSeeMyPosts = {
            // From See all itself, replace it rather than stacking a second one; from anywhere else, just open it.
            val onSeeAll = navController.currentBackStackEntry?.destination?.hasRoute<CommunityRoute>() == true
            navController.navigate(CommunityRoute(mine = true)) { if (onSeeAll) popUpTo<CommunityRoute> { inclusive = true } }
        },
        viewModel = host,
    ) {
        StashNavGraph(navController, modifier, onSelectionModeChanged, onNavigateToTab, posting, startDestination)
    }
}

private val TAB_ROUTES: Set<String?> = TopLevelDestination.entries.map { it.route::class.qualifiedName }.toSet()
private fun NavBackStackEntry.isTab(): Boolean = destination.route in TAB_ROUTES
private fun NavBackStackEntry.isNowPlaying(): Boolean = destination.hasRoute<NowPlayingRoute>()

private const val PUSH_MS = 320
private const val POP_MS = 280
/** Material 3's emphasized easing: quick off the mark, long soft landing. */
private val EMPHASIZED = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val TAB_ENTER = fadeIn(tween(220, delayMillis = 40)) + scaleIn(tween(260, easing = EMPHASIZED), initialScale = 0.97f)
private val TAB_EXIT = fadeOut(tween(110))

/**
 * A destination that sits above the glass bottom chrome: padded by its height,
 * so nothing ends up under it. [behindChrome] screens (Home, Search, Sync) draw
 * under the glass instead and pad their own lists by [LocalBottomChromePadding].
 */
private inline fun <reified T : Any> NavGraphBuilder.screen(
    behindChrome: Boolean = false,
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        val scope = this
        val bottom = if (behindChrome) 0.dp else LocalBottomChromePadding.current
        val top = com.stash.core.ui.components.LocalTopInsetPadding.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .ambientBackdrop()
                .padding(top = top, bottom = bottom)
                .consumeWindowInsets(PaddingValues(top = top)),
            propagateMinConstraints = true,
        ) {
            scope.content(entry)
        }
    }
}

@Composable
private fun StashNavGraph(
    navController: NavHostController,
    modifier: Modifier,
    onSelectionModeChanged: (Boolean) -> Unit,
    onNavigateToTab: (TopLevelDestination) -> Unit,
    posting: CommunityPosting,
    startDestination: Any,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        // LUMA: quick, fluid motion instead of the library's 700 ms cross-fade
        // (the "stuck" feel). Tab to tab: a fast fade with a slight zoom. Into a
        // screen: it glides in from the right while the one behind drifts left;
        // back reverses that. Now Playing brings its own slide and leaves the
        // screen under it still.
        enterTransition = {
            when {
                initialState.isNowPlaying() -> EnterTransition.None
                initialState.isTab() && targetState.isTab() -> TAB_ENTER
                else -> slideInHorizontally(tween(PUSH_MS, easing = EMPHASIZED)) { it / 3 } + fadeIn(tween(220))
            }
        },
        exitTransition = {
            when {
                targetState.isNowPlaying() -> ExitTransition.KeepUntilTransitionsFinished
                initialState.isTab() && targetState.isTab() -> TAB_EXIT
                else -> slideOutHorizontally(tween(PUSH_MS, easing = EMPHASIZED)) { -it / 8 } + fadeOut(tween(200))
            }
        },
        popEnterTransition = {
            when {
                initialState.isNowPlaying() -> EnterTransition.None
                initialState.isTab() && targetState.isTab() -> TAB_ENTER
                else -> slideInHorizontally(tween(PUSH_MS, easing = EMPHASIZED)) { -it / 8 } + fadeIn(tween(220))
            }
        },
        popExitTransition = {
            when {
                initialState.isTab() && targetState.isTab() -> TAB_EXIT
                else -> slideOutHorizontally(tween(POP_MS, easing = EMPHASIZED)) { it / 3 } + fadeOut(tween(180))
            }
        },
    ) {
        screen<HomeRoute>(behindChrome = true) {
            HomeScreen(
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute) {
                        // Clear top so repeated taps don't stack Settings entries.
                        launchSingleTop = true
                    }
                },
                // Home's lossless-offline banner routes through onNavigateToSettings
                // (Settings › Audio), where every lossless path — your Qobuz account,
                // a relay endpoint — is set up.
                onNavigateToPlaylist = { playlistId ->
                    navController.navigate(PlaylistDetailRoute(playlistId))
                },
                onShareMix = { id -> navController.navigate(PlaylistDetailRoute(id, openShare = true)) },
                // Liked card: the ViewModel queued the Liked focus; perform the
                // canonical tab switch so Back + bottom-bar state behave exactly
                // like tapping the Library tab — then pop any RESTORED detail
                // screens so LibraryScreen actually composes and consumes the
                // focus now (restoreState can resurrect a playlist detail on
                // top, which would swallow the tap and fire the jump on a later
                // Back). No-op when the restored stack is just Library root.
                onNavigateToLibrary = {
                    onNavigateToTab(TopLevelDestination.LIBRARY)
                    navController.popBackStack(LibraryRoute, inclusive = false)
                },
                // Qobuz discovery album/playlist taps → the shared album-detail
                // screen (playlists ride the same route via QOBUZ_PLAYLIST source).
                onNavigateToAlbum = { album ->
                    navController.navigate(
                        SearchAlbumRoute(
                            browseId = album.id,
                            title = album.title,
                            artist = album.artist,
                            thumbnailUrl = album.thumbnailUrl,
                            year = album.year,
                            source = album.source,
                            qobuzTrackCount = album.trackCount,
                        ),
                    )
                },
                onSeeAllPlaylists = { genre ->
                    navController.navigate(PlaylistBrowseRoute(genre))
                },
                onNavigateToMixBuilder = { recipeId ->
                    navController.navigate(MixBuilderRoute(recipeId))
                },
                onSeeAllMixes = { rail ->
                    navController.navigate(MixBrowseRoute(rail.name))
                },
                // Report an issue = diagnostics preview first, GitHub from there.
                onReportIssue = { navController.navigate(DiagnosticsPreviewRoute) },
                communitySection = {
                    CommunitySection(
                        onOpenPost = { id -> navController.navigate(CommunityPostRoute(id)) },
                        onSeeAll = { navController.navigate(CommunityRoute()) },
                        onPost = posting.pick,
                    )
                },
            )
        }
        screen<PlaylistBrowseRoute> {
            PlaylistBrowseScreen(
                onBack = { navController.popBackStack() },
                onNavigateToAlbum = { album ->
                    navController.navigate(
                        SearchAlbumRoute(
                            browseId = album.id,
                            title = album.title,
                            artist = album.artist,
                            thumbnailUrl = album.thumbnailUrl,
                            year = album.year,
                            source = album.source,
                        ),
                    )
                },
            )
        }
        screen<MixBrowseRoute> {
            MixBrowseScreen(
                rail = MixRail.valueOf(it.toRoute<MixBrowseRoute>().rail),
                onBack = { navController.popBackStack() },
                onOpenMix = { id -> navController.navigate(PlaylistDetailRoute(id)) },
            )
        }
        screen<MixBuilderRoute> {
            MixBuilderScreen(onBack = { navController.popBackStack() })
        }
        screen<LibraryRoute> {
            LibraryScreen(
                onNavigateToPlaylist = { playlistId ->
                    navController.navigate(PlaylistDetailRoute(playlistId))
                },
                onNavigateToArtist = { artistName ->
                    navController.navigate(ArtistDetailRoute(artistName))
                },
                onNavigateToAlbum = { _, name, _, artistName ->
                    navController.navigate(AlbumDetailRoute(name, artistName))
                },
                onNavigateToRemoteAlbum = { albumId, name, artUrl, artistName ->
                    navController.navigate(
                        SearchAlbumRoute(
                            browseId = albumId,
                            title = name,
                            artist = artistName,
                            thumbnailUrl = artUrl,
                            year = null,
                            source = com.stash.data.ytmusic.model.AlbumSource.YOUTUBE,
                        ),
                    )
                },
                onSelectionModeChanged = onSelectionModeChanged,
            )
        }
        screen<SearchRoute>(behindChrome = true) {
            SearchScreen(
                onNavigateToArtist = { id, name, avatar ->
                    navController.navigate(SearchArtistRoute(id, name, avatar))
                },
                onNavigateToAlbum = { album ->
                    navController.navigate(
                        SearchAlbumRoute(
                            browseId = album.id,
                            title = album.title,
                            artist = album.artist,
                            thumbnailUrl = album.thumbnailUrl,
                            year = album.year,
                            source = album.source,
                        ),
                    )
                },
            )
        }
        screen<SyncRoute>(behindChrome = true) {
            SyncScreen(
                onNavigateToFailedMatches = {
                    navController.navigate(FailedMatchesRoute)
                },
                // Phase 8: Library actions (Blocked Songs + Fix wrong-version)
                // moved out of Settings into the Sync tab's Library section.
                onNavigateToBlockedSongs = { navController.navigate(BlockedSongsRoute) },
                onNavigateToFailedDownloads = {
                    navController.navigate(FailedDownloadsRoute)
                },
                onNavigateToDownloads = {
                    navController.navigate(DownloadManagementRoute)
                },
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute) {
                        launchSingleTop = true
                    }
                },
                onManageSource = { source -> navController.navigate(ManagePlaylistsRoute(source.name)) },
            )
        }
        screen<ManagePlaylistsRoute> {
            ManagePlaylistsScreen(
                source = com.stash.feature.sync.SyncSource.valueOf(it.toRoute<ManagePlaylistsRoute>().source),
                onBack = { navController.popBackStack() },
            )
        }
        screen<SettingsRoute> {
            SettingsHubScreen(
                onOpenPlayback = { navController.navigate(SettingsPlaybackRoute) },
                onOpenAudioQuality = { navController.navigate(SettingsAudioQualityRoute) },
                onOpenAccounts = { navController.navigate(SettingsAccountsRoute) },
                onOpenLibraryStorage = { navController.navigate(SettingsLibraryStorageRoute) },
                onOpenAppearance = { navController.navigate(SettingsAppearanceRoute) },
                onOpenAbout = { navController.navigate(SettingsAboutRoute) },
            )
        }

        screen<SettingsPlaybackRoute> { backStackEntry ->
            val settingsEntry = remember(backStackEntry) { navController.getBackStackEntry(SettingsRoute) }
            val viewModel: com.stash.feature.settings.SettingsViewModel =
                androidx.hilt.navigation.compose.hiltViewModel(settingsEntry)
            com.stash.feature.settings.SettingsPlaybackScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel,
            )
        }
        screen<SettingsAudioQualityRoute> { backStackEntry ->
            val settingsEntry = remember(backStackEntry) { navController.getBackStackEntry(SettingsRoute) }
            val viewModel: com.stash.feature.settings.SettingsViewModel =
                androidx.hilt.navigation.compose.hiltViewModel(settingsEntry)
            com.stash.feature.settings.SettingsAudioQualityScreen(
                onBack = { navController.popBackStack() },
                onNavigateToEqualizer = { navController.navigate(EqualizerRoute) },
                onNavigateToSquidWtfCaptcha = { navController.navigate(SquidWtfCaptchaRoute) },
                viewModel = viewModel,
            )
        }
        screen<SettingsAccountsRoute> { backStackEntry ->
            val settingsEntry = remember(backStackEntry) { navController.getBackStackEntry(SettingsRoute) }
            val viewModel: com.stash.feature.settings.SettingsViewModel =
                androidx.hilt.navigation.compose.hiltViewModel(settingsEntry)
            com.stash.feature.settings.SettingsAccountsScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel,
            )
        }
        screen<SettingsLibraryStorageRoute> { backStackEntry ->
            val settingsEntry = remember(backStackEntry) { navController.getBackStackEntry(SettingsRoute) }
            val viewModel: com.stash.feature.settings.SettingsViewModel =
                androidx.hilt.navigation.compose.hiltViewModel(settingsEntry)
            com.stash.feature.settings.SettingsLibraryStorageScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLibraryHealth = { navController.navigate(LibraryHealthRoute) },
                viewModel = viewModel,
            )
        }
        screen<SettingsAppearanceRoute> { backStackEntry ->
            val settingsEntry = remember(backStackEntry) { navController.getBackStackEntry(SettingsRoute) }
            val viewModel: com.stash.feature.settings.SettingsViewModel =
                androidx.hilt.navigation.compose.hiltViewModel(settingsEntry)
            com.stash.feature.settings.SettingsAppearanceScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel,
            )
        }
        screen<SettingsAboutRoute> { backStackEntry ->
            val settingsEntry = remember(backStackEntry) { navController.getBackStackEntry(SettingsRoute) }
            val viewModel: com.stash.feature.settings.SettingsViewModel =
                androidx.hilt.navigation.compose.hiltViewModel(settingsEntry)
            com.stash.feature.settings.SettingsAboutScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDiagnosticsPreview = { navController.navigate(DiagnosticsPreviewRoute) },
                viewModel = viewModel,
            )
        }

        screen<SquidWtfCaptchaRoute> { backStackEntry ->
            // Reach the Settings ViewModel from the parent route so the
            // captured cookie value writes to the same DataStore the
            // interceptor reads from. We grab the *Settings* nav entry
            // (not this one) so the ViewModel survives this route's
            // dispose and the value lands in prefs immediately.
            val settingsEntry = remember(backStackEntry) {
                navController.getBackStackEntry(SettingsRoute)
            }
            val viewModel: com.stash.feature.settings.SettingsViewModel =
                androidx.hilt.navigation.compose.hiltViewModel(settingsEntry)
            com.stash.feature.settings.components.SquidWtfCaptchaScreen(
                onCookieCaptured = viewModel::onSquidWtfCaptchaCookieChanged,
                onClose = { navController.popBackStack() },
            )
        }

        screen<EqualizerRoute> {
            EqualizerScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        screen<LibraryHealthRoute> {
            LibraryHealthScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        screen<DiagnosticsPreviewRoute> {
            com.stash.feature.settings.diagnostics.DiagnosticsPreviewScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        screen<BlockedSongsRoute> {
            BlockedSongsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        screen<PlaylistDetailRoute> {
            PlaylistDetailScreen(
                onBack = { navController.popBackStack() },
                onSelectionModeChanged = onSelectionModeChanged,
            )
        }

        screen<SharedMixRoute> {
            com.stash.feature.library.share.SharedMixScreen(
                onBack = { navController.popBackStack() },
                onOpenPlaylist = { id -> navController.navigate(PlaylistDetailRoute(id)) },
                // Follow / Save a copy land on the new playlist; Back skips the mix screen.
                onJoinedPlaylist = { id ->
                    navController.navigate(PlaylistDetailRoute(id)) { popUpTo<SharedMixRoute> { inclusive = true } }
                },
            )
        }

        screen<SharedTrackRoute> {
            com.stash.feature.library.share.SharedTrackScreen(onBack = { navController.popBackStack() })
        }

        screen<CommunityRoute> {
            CommunityScreen(
                mine = it.toRoute<CommunityRoute>().mine,
                onBack = { navController.popBackStack() },
                onOpenPost = { id -> navController.navigate(CommunityPostRoute(id)) },
                onPost = posting.pick,
            )
        }

        screen<CommunityPostRoute> { entry ->
            // Save a copy opens the new playlist (Back returns to the post), and a take-down closes the post. Both run
            // from a ViewModel coroutine, so each acts only while this entry is the top of a live back stack: a
            // take-down's reply after Back (the ViewModel lives through the 700 ms fade) would otherwise pop the screen
            // below too (from Home: Home itself, a blank screen), and a callback left over from before a rotation holds
            // the old NavController, whose entry is DESTROYED (navigate() on it throws, so a finished save would read
            // as failed). CommunityPostViewModel reads the route's `postId` from SavedStateHandle by that name.
            val onTop = {
                navController.currentBackStackEntry === entry && entry.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)
            }
            CommunityPostScreen(
                onBack = { if (onTop()) navController.popBackStack() },
                onOpenPlaylist = { id -> if (onTop()) navController.navigate(PlaylistDetailRoute(id)) },
            )
        }

        screen<ListenJoinRoute> {
            com.stash.feature.nowplaying.listen.JoinSessionScreen(
                onBack = { navController.popBackStack() },
                // After joining, Now Playing is the session; Back from it skips the Join screen. Reuse an
                // existing Now Playing entry rather than stack a second one (as the mini player does).
                onJoined = {
                    navController.popBackStack<ListenJoinRoute>(inclusive = true)
                    if (!navController.popBackStack(NowPlayingRoute, inclusive = false)) {
                        navController.navigate(NowPlayingRoute) { launchSingleTop = true }
                    }
                },
            )
        }

        screen<ArtistDetailRoute> {
            ArtistDetailScreen(
                onBack = { navController.popBackStack() },
                onSelectionModeChanged = onSelectionModeChanged,
            )
        }

        screen<AlbumDetailRoute> {
            AlbumDetailScreen(
                onBack = { navController.popBackStack() },
                onSelectionModeChanged = onSelectionModeChanged,
            )
        }

        screen<LikedSongsDetailRoute> {
            LikedSongsDetailScreen(
                onBack = { navController.popBackStack() },
                onSelectionModeChanged = onSelectionModeChanged,
            )
        }

        screen<FailedMatchesRoute> {
            FailedMatchesScreen(
                onBack = { navController.popBackStack() },
            )
        }

        screen<FailedDownloadsRoute> {
            FailedDownloadsScreen(onBack = { navController.popBackStack() })
        }
        screen<DownloadManagementRoute> {
            DownloadManagementScreen(onBack = { navController.popBackStack() })
        }

        screen<SearchArtistRoute> {
            ArtistProfileScreen(
                onBack = { navController.popBackStack() },
                onNavigateToAlbum = { album ->
                    navController.navigate(
                        SearchAlbumRoute(
                            browseId = album.id,
                            title = album.title,
                            artist = album.artist,
                            thumbnailUrl = album.thumbnailUrl,
                            year = album.year,
                            source = album.source,
                            // Set on the Qobuz gap-fill albums, null on YouTube's.
                            qobuzTrackCount = album.trackCount,
                        ),
                    )
                },
                onNavigateToArtist = { id, name, avatar ->
                    navController.navigate(SearchArtistRoute(id, name, avatar))
                },
            )
        }

        screen<SearchAlbumRoute> {
            AlbumDiscoveryScreen(
                onBack = { navController.popBackStack() },
                onNavigateToAlbum = { album ->
                    navController.navigate(
                        SearchAlbumRoute(
                            browseId = album.id,
                            title = album.title,
                            artist = album.artist,
                            thumbnailUrl = album.thumbnailUrl,
                            year = album.year,
                            source = album.source,
                        ),
                    )
                },
            )
        }

        composable<NowPlayingRoute>(
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(SLIDE_DURATION_MS),
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(SLIDE_DURATION_MS),
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(SLIDE_DURATION_MS),
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(SLIDE_DURATION_MS),
                )
            },
        ) {
            // Now Playing pads for the status bar itself (statusBarsPadding), so its
            // frosted glass runs up behind it.
            NowPlayingScreen(
                onDismiss = { navController.popBackStack() },
                onNavigateToArtist = { id, name, avatar, focusAlbum ->
                    navController.navigate(SearchArtistRoute(id, name, avatar, focusAlbum))
                },
                onNavigateToAlbum = { albumId, name, artUrl, artistName ->
                    navController.navigate(
                        SearchAlbumRoute(
                            browseId = albumId,
                            title = name,
                            artist = artistName,
                            thumbnailUrl = artUrl,
                            year = null,
                            source = AlbumSource.YOUTUBE,
                        ),
                    )
                },
            )
        }
    }
}
