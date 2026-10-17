package com.luma.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * LUMA: the colour a song "is": the cover's vivid colour when it has one,
 * else its dominant colour, or null for a black-and-white cover (the brand
 * colours then stay). A dark photo's dominant colour is often near-black, so
 * the vivid one leads.
 */
fun songLeadColor(vibrant: Color, dominant: Color): Color? {
    fun saturated(c: Color): Boolean {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        return hsv[1] > 0.25f && hsv[2] > 0.2f
    }
    return when {
        saturated(vibrant) -> vibrant
        saturated(dominant) -> dominant
        else -> null
    }
}

/**
 * [lead] nudged until it reads as an accent on this theme's background: lifted
 * toward white on dark, pressed toward black on light, so buttons, switches and
 * highlights tinted with it keep their contrast.
 */
fun songAccent(lead: Color, dark: Boolean): Color {
    var c = lead
    repeat(8) {
        c = when {
            dark && c.luminance() < 0.38f -> lerp(c, Color.White, 0.15f)
            !dark && c.luminance() > 0.20f -> lerp(c, Color.Black, 0.15f)
            else -> return c
        }
    }
    return c
}

/** A deep version of [lead] for surfaces that carry white text (the player's menus). */
fun songDeepSurface(lead: Color): Color = lerp(lead, Color.Black, 0.62f)

/**
 * Re-themes [content] around the playing song: the accent colours (primary,
 * secondary, tertiary and their containers) become the song's, and surfaces
 * and sheets take a faint wash of it. With no song, or a black-and-white
 * cover ([lead] null), the theme passes through unchanged.
 *
 * Driven by the song's target colours, not an animation, so a song change
 * recomposes the app once rather than on every frame of a fade.
 */
@Composable
fun SongTheme(lead: Color?, content: @Composable () -> Unit) {
    if (lead == null) {
        content()
        return
    }
    val base = MaterialTheme.colorScheme
    val extended = LumaTheme.extendedColors
    val dark = LocalIsDarkTheme.current
    val scheme = remember(base, lead, dark) {
        val accent = songAccent(lead, dark)
        val onAccent = if (accent.luminance() > 0.45f) Color(0xFF14121A) else Color.White
        val container = if (dark) lerp(lead, Color.Black, 0.55f) else lerp(lead, Color.White, 0.78f)
        val onContainer = if (dark) lerp(lead, Color.White, 0.80f) else lerp(lead, Color.Black, 0.70f)
        val wash = if (dark) 0.10f else 0.06f
        base.copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = container,
            onSecondaryContainer = onContainer,
            tertiary = accent,
            onTertiary = onAccent,
            surface = lerp(base.surface, lead, wash),
            surfaceContainer = lerp(base.surfaceContainer, lead, wash),
            surfaceContainerLow = lerp(base.surfaceContainerLow, lead, wash),
            surfaceContainerHigh = lerp(base.surfaceContainerHigh, lead, wash),
            surfaceVariant = lerp(base.surfaceVariant, lead, wash),
        )
    }
    val songExtended = remember(extended, lead, dark) {
        extended.copy(elevatedSurface = lerp(extended.elevatedSurface, lead, if (dark) 0.16f else 0.08f))
    }
    CompositionLocalProvider(LocalLumaColors provides songExtended) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}
