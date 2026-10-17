package com.luma.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.luma.core.ui.components.LocalBottomChromePadding
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.chrisbanes.haze.HazeInputScale
import com.luma.core.ui.theme.LumaMotion
import kotlinx.coroutines.launch
import com.luma.core.ui.components.liquidGlass
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.core.data.prefs.StartTab
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.luma.app.RequestNotificationPermissionOnce
import com.luma.core.ui.theme.LumaTheme
import com.luma.data.download.lossless.squid.CaptchaExpiredNotifier
import com.luma.feature.nowplaying.MiniPlayer

/**
 * Root scaffold for the LUMA app.
 *
 * Hosts the [LumaNavHost], bottom navigation bar, and the [MiniPlayer]
 * which sits between the content area and the navigation bar.
 */
@Composable
fun LumaScaffold(
    pendingDeepLink: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    startTab: StartTab = StartTab.HOME,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val onNowPlaying = currentRoute == NowPlayingRoute::class.qualifiedName

    // Whether a detail screen is currently in multi-select mode. Detail screens
    // signal this via `onSelectionModeChanged`; while it is true we hide the
    // whole bottom chrome (mini-player AND nav bar) so the screen's own bottom
    // selection action bar owns the bottom edge instead of stacking on / being
    // crowded by it (premium multi-select pattern, avoids mis-taps).
    var selectionActive by remember { mutableStateOf(false) }

    // Safeguard: a selection-capable screen normally clears its selection on
    // every exit path (✕ / Back / last-deselect), which fires
    // `onSelectionModeChanged(false)` before it leaves composition. Resetting on
    // route change as well guarantees the mini-player can never stay hidden if a
    // screen leaves the stack without that signal landing.
    LaunchedEffect(currentRoute) { selectionActive = false }

    // Android 13+ runtime permission for notifications. One-shot per install.
    RequestNotificationPermissionOnce()
    // LUMA: "Update available" popup when GitHub has a newer LUMA release.
    com.luma.app.update.UpdatePrompt()

    // Canonical bottom-nav tab switch (save/restore semantics + reselect-pop).
    // Local fun rather than inline in LumaBottomBar's onNavigate so screens
    // that trigger a tab switch themselves (Home's Liked card → Library) route
    // through the exact same logic via LumaNavHost's onNavigateToTab.
    fun navigateToTab(dest: TopLevelDestination) {
        val destRoute = dest.route::class.qualifiedName
        // The "current tab" is the top-most top-level route in
        // the live back stack (the start tab, Home unless Open LUMA
        // on says otherwise, is always at the bottom; the active tab
        // sits above it, with detail screens above that).
        val topLevelRoutes =
            TopLevelDestination.entries.map { it.route::class.qualifiedName }.toSet()
        val currentTabRoute = navController.currentBackStack.value
            .lastOrNull { it.destination.route in topLevelRoutes }
            ?.destination?.route

        if (currentTabRoute == destRoute && currentRoute != destRoute) {
            // Reselecting the tab we're already deep inside. The
            // save/restore path below would popUpTo(start) SAVE
            // this exact stack keyed by the tab, then restoreState
            // bring it right back — a byte-identical no-op that
            // reads as a dead tab button (only Back escaped). Pop
            // back to the tab's own entry instead; this also drops
            // any Now Playing pushed above it.
            navController.popBackStack(dest.route, inclusive = false)
        } else {
            // Genuine tab switch. Now Playing is transient
            // full-screen chrome — remove it (wherever it sits)
            // before the switch so restoreState can never
            // resurrect it into a tab's saved stack.
            navController.popBackStack(NowPlayingRoute, inclusive = true)
            val start = navController.graph.findStartDestination()
            if (start.route == destRoute) {
                // Going back to the start tab (Home by default) is a pop, not a navigate. The
                // navigate below would popUpTo(start) with saveState, which files the tab we're
                // leaving under the START destination's id too; restoreState for the start tab
                // then brings that tab straight back, so Home could never be reached again.
                navController.popBackStack(start.id, inclusive = false, saveState = true)
                return
            }
            navController.navigate(dest.route) {
                // Save each tab's back stack + state when leaving
                // it, and restore it when returning — so tabbing to
                // Settings and back to Search lands on your results,
                // not the landing screen (canonical bottom-nav).
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = false
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Process notification deep-link extras handed in from MainActivity.
    // Only one target right now (the captcha verifier); easy to extend
    // when more deep-link surfaces show up.
    LaunchedEffect(pendingDeepLink) {
        when (pendingDeepLink) {
            CaptchaExpiredNotifier.DEEP_LINK_TARGET -> {
                // Push Settings onto the back stack BEFORE the captcha screen.
                // SquidWtfCaptchaRoute reaches into the SettingsViewModel via
                // `navController.getBackStackEntry(SettingsRoute)` to share the
                // ViewModel's cookie-write callback — that throws
                // IllegalArgumentException ("No destination with route
                // SettingsRoute in BackStack") if Settings isn't already on the
                // stack. Cold-start from a notification has no stack history,
                // so we synthesize the parent here. When the user closes the
                // captcha screen they land on Settings — natural UX.
                navController.navigate(SettingsRoute) {
                    launchSingleTop = true
                }
                navController.navigate(SquidWtfCaptchaRoute) {
                    launchSingleTop = true
                }
                onDeepLinkConsumed()
            }
            null -> Unit
            else -> {
                if (pendingDeepLink.startsWith(com.luma.app.MainActivity.DEEP_LINK_SHARED_MIX_PREFIX)) {
                    val id = pendingDeepLink.removePrefix(com.luma.app.MainActivity.DEEP_LINK_SHARED_MIX_PREFIX)
                    navController.navigate(SharedMixRoute(id))
                } else if (pendingDeepLink.startsWith(com.luma.app.MainActivity.DEEP_LINK_SHARED_TRACK_PREFIX)) {
                    val link = pendingDeepLink.removePrefix(com.luma.app.MainActivity.DEEP_LINK_SHARED_TRACK_PREFIX)
                    navController.navigate(SharedTrackRoute(link))
                } else if (pendingDeepLink.startsWith(com.luma.app.MainActivity.DEEP_LINK_LISTEN_PREFIX)) {
                    val code = pendingDeepLink.removePrefix(com.luma.app.MainActivity.DEEP_LINK_LISTEN_PREFIX)
                    navController.navigate(ListenJoinRoute(code))
                }
                onDeepLinkConsumed() // unknown targets are cleared too, so we don't loop
            }
        }
    }

    // LUMA: the bottom chrome (Qobuz banner, mini player, tab bar) floats over the
    // content as frosted glass, iOS / One UI style. Content draws under it: Home,
    // Search and Sync scroll behind the glass and pad their own lists by
    // LocalBottomChromePadding; LumaNavHost pads every other screen by it. The
    // height is measured only while the chrome sits settled on screen, so its
    // slide away for Now Playing never resizes the screen underneath.
    val hazeState = remember { HazeState() }
    val chromeShown = remember { MutableTransitionState(true) }
    chromeShown.targetState = !onNowPlaying
    var chromeHeightPx by remember { mutableIntStateOf(0) }
    val chromePadding = if (selectionActive) 0.dp else with(LocalDensity.current) { chromeHeightPx.toDp() }

    // The tab the user is in: the top-most tab in the back stack, so it stays lit
    // on that tab's detail screens (and Home stays lit under Settings).
    val topLevelRoutes = remember { TopLevelDestination.entries.map { it.route::class.qualifiedName }.toSet() }
    val currentTabRoute = remember(navBackStackEntry) {
        navController.currentBackStack.value.lastOrNull { it.destination.route in topLevelRoutes }?.destination?.route
    }

    // LUMA: the playing song themes the whole app: the background glow on every
    // screen (songColors, animated), and the accent colours, surfaces and sheets
    // (SongTheme, switched per song).
    val npViewModel = androidx.hilt.navigation.compose.hiltViewModel<com.luma.feature.nowplaying.NowPlayingViewModel>()
    val songColors = com.luma.feature.nowplaying.rememberSongColors(npViewModel)
    val npState by npViewModel.uiState.collectAsStateWithLifecycle()
    val songLead = remember(npState.hasTrack, npState.vibrantColor, npState.dominantColor) {
        if (npState.hasTrack) com.luma.core.ui.theme.songLeadColor(npState.vibrantColor, npState.dominantColor) else null
    }
    com.luma.core.ui.theme.SongTheme(songLead) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Default safe-drawing insets: screens avoid the status bar via the top
        // padding below. The bottom is the glass chrome's job.
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val contentPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection),
            // The top inset is applied inside each screen (LumaNavHost), under its backdrop.
            top = 0.dp,
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = 0.dp,
        )
        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                com.luma.core.ui.components.LocalSongColors provides songColors,
                LocalBottomChromePadding provides chromePadding,
                com.luma.core.ui.components.LocalTopInsetPadding provides innerPadding.calculateTopPadding(),
            ) {
                LumaNavHost(
                    navController = navController,
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(contentPadding)
                        .consumeWindowInsets(contentPadding),
                    onSelectionModeChanged = { selectionActive = it },
                    onNavigateToTab = ::navigateToTab,
                    startDestination = when (startTab) {
                        StartTab.HOME -> HomeRoute
                        StartTab.LIBRARY -> LibraryRoute
                        StartTab.SEARCH -> SearchRoute
                    },
                )
            }

            // While a screen is selecting, no bottom chrome at all: the screen's own
            // selection action bar (which handles its own nav insets) takes the edge.
            if (!selectionActive) {
                val qobuzStatusViewModel: QobuzStatusViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                val qobuzStatus by qobuzStatusViewModel.bannerStatus.collectAsStateWithLifecycle()
                val miniShape = RoundedCornerShape(percent = 50) // LUMA: a pill

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged {
                            if (chromeShown.isIdle && chromeShown.currentState) chromeHeightPx = it.height
                        }
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                ) {
                    // LUMA: no Qobuz, so no Qobuz status banner.
                    AnimatedVisibility(
                        visibleState = chromeShown,
                        // Same curve as Now Playing's slide, so bar and player move as one piece.
                        enter = slideInVertically(tween(NAV_BAR_SLIDE_MS, easing = LumaMotion.Emphasized)) { it } +
                            fadeIn(tween(NAV_BAR_SLIDE_MS / 2, delayMillis = NAV_BAR_SLIDE_MS / 4)),
                        exit = slideOutVertically(tween(NAV_BAR_SLIDE_MS, easing = LumaMotion.Emphasized)) { it } +
                            fadeOut(tween(NAV_BAR_SLIDE_MS / 2)),
                    ) {
                        Column {
                            MiniPlayer(
                                onExpand = {
                                    // Return to an existing Now Playing entry instead
                                    // of pushing a duplicate: every buried entry's
                                    // ViewModel keeps its 4Hz player combine running.
                                    if (!navController.popBackStack(NowPlayingRoute, inclusive = false)) {
                                        navController.navigate(NowPlayingRoute) {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                containerColor = Color.Transparent,
                                shape = miniShape,
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .glass(hazeState, miniShape),
                            )
                            GlassTabBar(
                                currentTabRoute = currentTabRoute,
                                hazeState = hazeState,
                                onNavigate = ::navigateToTab,
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

/** The tab bar's slide on and off Now Playing. */
private const val NAV_BAR_SLIDE_MS = 350 // = LumaNavHost SLIDE_DURATION_MS, so bar and player move as one

/**
 * LUMA's liquid glass: whatever scrolls behind is blurred and only lightly
 * tinted, so the colours underneath still glow through (a heavy tint turns
 * glass into a milky panel), then lit by [liquidGlass]: a sheen across the
 * top, depth at the bottom and a specular rim. Below API 31 Haze can't blur,
 * so the fallback tint is near-opaque to keep text readable.
 */
@Composable
private fun Modifier.glass(hazeState: HazeState, shape: Shape): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val surface = MaterialTheme.colorScheme.surface
    return this
        .shadow(elevation = 20.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.28f), spotColor = Color.Black.copy(alpha = 0.34f))
        .clip(shape)
        .hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = MaterialTheme.colorScheme.background,
                // Enough frost that icons and labels stay readable over busy album art,
                // light enough that colour still glows through.
                tints = listOf(HazeTint(surface.copy(alpha = if (dark) 0.58f else 0.72f))),
                blurRadius = 30.dp,
                noiseFactor = 0.04f,
                fallbackTint = HazeTint(surface.copy(alpha = if (dark) 0.90f else 0.92f)),
            ),
        ) {
            // Blur a downscaled copy of what's behind: the blur hides the lost detail, and
            // each frame of a scroll or slide costs a fraction of the GPU work.
            inputScale = HazeInputScale.Auto
        }
        .liquidGlass(shape)
}

/**
 * Floating glass tab bar: a lit pill glides under the selected tab with a
 * spring, and the tab's icon pops a little as it lands.
 */
@Composable
private fun GlassTabBar(
    currentTabRoute: String?,
    hazeState: HazeState,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    val tabs = TopLevelDestination.entries
    val selectedIndex = tabs.indexOfFirst { it.route::class.qualifiedName == currentTabRoute }
    val haptics = LocalHapticFeedback.current
    val primary = MaterialTheme.colorScheme.primary
    // Not textTertiary: over glass that pale grey all but vanished.
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    val barShape = RoundedCornerShape(32.dp)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .glass(hazeState, barShape),
    ) {
        // The lit lens moves like a drop of liquid: its leading edge springs
        // ahead and the trailing edge catches up, so it stretches as it
        // travels and settles back to a pill where it lands. The edges are
        // in tab units and read only while drawing, so the slide never
        // recomposes or re-lays-out the bar: it runs on the draw pass alone.
        val target = selectedIndex.coerceAtLeast(0)
        val lensLeft = remember { Animatable(target.toFloat()) }
        val lensRight = remember { Animatable(target + 1f) }
        LaunchedEffect(target) {
            val movingRight = target >= lensLeft.targetValue
            val lead = spring<Float>(dampingRatio = 0.74f, stiffness = 700f)
            val trail = spring<Float>(dampingRatio = 0.86f, stiffness = 240f)
            launch { lensLeft.animateTo(target.toFloat(), if (movingRight) trail else lead) }
            launch { lensRight.animateTo(target + 1f, if (movingRight) lead else trail) }
        }
        if (selectedIndex >= 0) {
            val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            val count = tabs.size
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val inset = 6.dp.toPx()
                        val item = size.width / count
                        val h = size.height - inset * 2
                        val corner = CornerRadius(h / 2, h / 2)
                        val fill = primary.copy(alpha = 0.14f)
                        val sheen = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = if (dark) 0.12f else 0.35f),
                            0.5f to Color.Transparent,
                            startY = inset,
                            endY = inset + h,
                        )
                        val rim = Color.White.copy(alpha = if (dark) 0.22f else 0.60f)
                        val rimWidth = 1.dp.toPx()
                        onDrawBehind {
                            val l = lensLeft.value * item + inset
                            val w = (lensRight.value * item - inset) - l
                            if (w <= 0f) return@onDrawBehind
                            val topLeft = Offset(l, inset)
                            val box = Size(w, h)
                            drawRoundRect(fill, topLeft, box, corner)
                            drawRoundRect(sheen, topLeft, box, corner)
                            drawRoundRect(
                                rim,
                                topLeft + Offset(rimWidth / 2, rimWidth / 2),
                                Size(w - rimWidth, h - rimWidth),
                                corner,
                                style = Stroke(rimWidth),
                            )
                        }
                    },
            )
        }
        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, dest ->
                val selected = index == selectedIndex
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.12f else 1f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 520f),
                    label = "tabIconScale",
                )
                val tint by animateColorAsState(
                    targetValue = if (selected) primary else idle,
                    animationSpec = tween(220),
                    label = "tabTint",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigate(dest)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                        contentDescription = dest.label,
                        tint = tint,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(text = dest.label, style = MaterialTheme.typography.labelSmall, color = tint)
                }
            }
        }
    }
}
