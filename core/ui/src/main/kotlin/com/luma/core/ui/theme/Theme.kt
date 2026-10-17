package com.luma.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Color schemes ────────────────────────────────────────────────────────

val LumaDarkColorScheme = darkColorScheme(
    primary = LumaPurple,
    onPrimary = Color.White,
    primaryContainer = LumaPurpleDark,
    onPrimaryContainer = LumaPurpleLight,
    secondary = LumaCyan,
    onSecondary = Color.Black,
    secondaryContainer = LumaCyanDark,
    onSecondaryContainer = LumaCyanLight,
    tertiary = LumaCyan,
    onTertiary = Color.Black,
    background = LumaBackground,
    onBackground = LumaTextPrimary,
    surface = LumaSurface,
    onSurface = LumaTextPrimary,
    surfaceVariant = LumaElevatedSurface,
    onSurfaceVariant = LumaTextSecondary,
    error = LumaError,
    onError = Color.White,
    outline = LumaGlassBorder,
    outlineVariant = LumaGlassBorderBright,
)

val LumaLightColorScheme = lightColorScheme(
    primary = LumaPurpleDark,                   // deeper purple on light bg for contrast
    onPrimary = Color.White,
    primaryContainer = LumaPurpleLight,
    onPrimaryContainer = LumaPurpleDark,
    secondary = LumaCyanDark,
    onSecondary = Color.White,
    secondaryContainer = LumaCyanLight,
    onSecondaryContainer = LumaCyanDark,
    tertiary = LumaCyanDark,
    onTertiary = Color.White,
    background = LumaBackgroundLight,
    onBackground = LumaTextPrimaryLight,
    surface = LumaSurfaceLight,
    onSurface = LumaTextPrimaryLight,
    surfaceVariant = LumaElevatedSurfaceLight,
    onSurfaceVariant = LumaTextSecondaryLight,
    error = LumaError,
    onError = Color.White,
    outline = LumaGlassBorderLight,
    outlineVariant = LumaGlassBorderBrightLight,
)

/**
 * AMOLED variant of the dark scheme: every ground the eye reads as "the app's
 * canvas" is pure #000000 so OLED panels switch those pixels off entirely.
 *
 * Beyond background/surface, the whole M3 surface-container family and
 * [androidx.compose.material3.ColorScheme.surfaceTint] are forced black —
 * components that default to `surfaceContainerLow` (ModalBottomSheet) or
 * apply tonal elevation would otherwise render M3's baseline dark gray on
 * top of our black. With surfaceTint black, `surfaceColorAtElevation()` is
 * a no-op by construction. Raised tiers (surfaceVariant, containerHigh /
 * Highest) keep a dim lift so chips, thumbnails, and drag targets stay
 * distinguishable on true black — hierarchy on AMOLED is carried by the
 * glass borders, not by big tonal fills.
 */
val LumaAmoledColorScheme = LumaDarkColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceTint = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = LumaElevatedSurface,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = LumaAmoledSurfaceHigh,
    surfaceContainerHighest = LumaElevatedSurface,
)

// ── LocalIsDarkTheme — queried by composables that need theme-aware assets
//    (e.g. the wordmark drawable selection in HomeScreen). ────────────────
val LocalIsDarkTheme = staticCompositionLocalOf { true }

// ── LocalIsAmoledTheme — true only when the pure-black dark theme is
//    active. Queried by surfaces that paint their own canvas (e.g. Now
//    Playing's AmbientBackground) instead of sniffing colorScheme values. ──
val LocalIsAmoledTheme = staticCompositionLocalOf { false }

/**
 * Root theme for the LUMA app.
 *
 * Accepts an explicit [darkTheme] flag so the caller can wire in a user
 * preference (Light/Dark/System) instead of always following the OS.
 * When the caller wants to mirror the system, pass
 * `isSystemInDarkTheme()` — that's also the default for safety.
 *
 * [amoled] upgrades the DARK scheme to pure black ([LumaAmoledColorScheme]);
 * it composes with any mode — on light (or system-day) it is dormant and
 * kicks in whenever the effective theme is dark.
 *
 * Switching between schemes is done purely in Compose state — no activity
 * recreation, no resource configuration override — so the flip is instant
 * and any `animateColorAsState` wrappers can animate between the two sets.
 *
 * The system status-bar and navigation-bar icon colors are updated via a
 * [SideEffect] so they flip in sync with the in-app theme.
 */
@Composable
fun LumaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val useAmoled = darkTheme && amoled
    val colorScheme = when {
        useAmoled -> LumaAmoledColorScheme
        darkTheme -> LumaDarkColorScheme
        else -> LumaLightColorScheme
    }
    val extendedColors = if (darkTheme) LumaExtendedColorsDark else LumaExtendedColorsLight

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            // Light status-bar icons on dark theme, dark icons on light theme.
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalLumaColors provides extendedColors,
        LocalIsDarkTheme provides darkTheme,
        LocalIsAmoledTheme provides useAmoled,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LumaTypography,
            shapes = LumaShapes,
            content = content,
        )
    }
}

object LumaTheme {
    val extendedColors: LumaExtendedColors
        @Composable
        get() = LocalLumaColors.current
}
