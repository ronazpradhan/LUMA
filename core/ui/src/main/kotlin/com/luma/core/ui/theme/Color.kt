package com.luma.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Brand constants (shared by both themes) ────────────────────────────────
val LumaPurple = Color(0xFF8B5CF6)
val LumaPurpleLight = Color(0xFFA78BFA)
val LumaPurpleDark = Color(0xFF7C3AED)
val LumaCyan = Color(0xFF06B6D4)
val LumaCyanLight = Color(0xFF22D3EE)
val LumaCyanDark = Color(0xFF0891B2)
val LumaSpotifyGreen = Color(0xFF1DB954)
val LumaYouTubeRed = Color(0xFFFF0033)
// Last.fm brand red — the Sync-tab Last.fm source card's brand bar (issue #255).
val LumaLastfmRed = Color(0xFFD51007)
val LumaError = Color(0xFFEF4444)
val LumaWarning = Color(0xFFF59E0B)
val LumaSuccess = Color(0xFF10B981)

// ── Dark theme palette ─────────────────────────────────────────────────────
val LumaBackground = Color(0xFF06060C)
val LumaSurface = Color(0xFF0D0D18)
val LumaElevatedSurface = Color(0xFF1A1A2E)

// ── AMOLED (pure-black dark) ───────────────────────────────────────────────
// Backgrounds and surfaces go to true #000000 so OLED pixels switch off;
// only the raised-container tier keeps a dim lift for hierarchy.
val LumaAmoledSurfaceHigh = Color(0xFF14141E)
val LumaTextPrimary = Color(0xFFE8E8F0)
val LumaTextSecondary = Color(0xFFA0A0B8)
val LumaTextTertiary = Color(0xFF606078)
val LumaGlassBackground = Color(0x17FFFFFF)        // LUMA: frosted pane over the ambient glow
val LumaGlassBackgroundHover = Color(0x24FFFFFF)
val LumaGlassBorder = Color(0x1FFFFFFF)
val LumaGlassBorderBright = Color(0x38FFFFFF)

// ── Light theme palette (cream / lavender, preserves brand purple) ─────────
//
// Design goal: not a generic "white background + colored accents" light mode.
// Instead, a warm cream/lavender base that echoes the dark theme's purple
// saturation and keeps the premium feel.
val LumaBackgroundLight = Color(0xFFF6F3FF)      // soft lavender-tinted white
val LumaSurfaceLight = Color(0xFFFFFFFF)          // pure white cards stand out
val LumaElevatedSurfaceLight = Color(0xFFEEE7FC)  // subtle purple wash for raised elements
val LumaTextPrimaryLight = Color(0xFF1A0B2E)      // deep aubergine, high contrast
val LumaTextSecondaryLight = Color(0xFF5B4680)    // muted purple-gray for secondary text
val LumaTextTertiaryLight = Color(0xFF8B7AB0)     // lightest tier, for metadata
val LumaGlassBackgroundLight = Color(0x8CFFFFFF)  // LUMA: 55% white, frosted over the lavender glow
val LumaGlassBackgroundHoverLight = Color(0xB3FFFFFF)
val LumaGlassBorderLight = Color(0xCCFFFFFF)      // LUMA: bright white rim, like iOS glass
val LumaGlassBorderBrightLight = Color(0x337C3AED)

@Immutable
data class LumaExtendedColors(
    val spotifyGreen: Color = LumaSpotifyGreen,
    val youtubeRed: Color = LumaYouTubeRed,
    val lastfmRed: Color = LumaLastfmRed,
    val cyan: Color = LumaCyan,
    val cyanLight: Color = LumaCyanLight,
    val cyanDark: Color = LumaCyanDark,
    val purpleLight: Color = LumaPurpleLight,
    val purpleDark: Color = LumaPurpleDark,
    val elevatedSurface: Color = LumaElevatedSurface,
    val glassBackground: Color = LumaGlassBackground,
    val glassBackgroundHover: Color = LumaGlassBackgroundHover,
    val glassBorder: Color = LumaGlassBorder,
    val glassBorderBright: Color = LumaGlassBorderBright,
    val textTertiary: Color = LumaTextTertiary,
    val warning: Color = LumaWarning,
    val success: Color = LumaSuccess,
)

/** Dark-theme instance of extended colors (current default). */
val LumaExtendedColorsDark = LumaExtendedColors()

/** Light-theme instance of extended colors — cream/lavender palette. */
val LumaExtendedColorsLight = LumaExtendedColors(
    elevatedSurface = LumaElevatedSurfaceLight,
    glassBackground = LumaGlassBackgroundLight,
    glassBackgroundHover = LumaGlassBackgroundHoverLight,
    glassBorder = LumaGlassBorderLight,
    glassBorderBright = LumaGlassBorderBrightLight,
    textTertiary = LumaTextTertiaryLight,
    // Brand colors (spotify/youtube/cyan/purple variants) stay identical —
    // they're identity markers, not theme-dependent.
)

val LocalLumaColors = staticCompositionLocalOf { LumaExtendedColorsDark }
