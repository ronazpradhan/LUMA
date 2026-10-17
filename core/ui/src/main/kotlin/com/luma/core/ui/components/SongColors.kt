package com.luma.core.ui.components

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * LUMA: the colours of the song that's playing, shared app-wide so the
 * background glow and the mini player follow the music. Every field is a
 * [State] that the scaffold animates between songs: read them while drawing
 * (drawBehind, graphicsLayer), not during composition, so a colour change only
 * redraws and never recomposes the screens. [presence] is 0 with nothing
 * playing (screens keep the brand glow) and 1 with a song.
 */
class SongColors(
    val dominant: State<Color>,
    val vibrant: State<Color>,
    val muted: State<Color>,
    val presence: State<Float>,
)

/** No song: the brand glow, unchanged. */
private val NoSong = SongColors(
    dominant = mutableStateOf(Color.Transparent),
    vibrant = mutableStateOf(Color.Transparent),
    muted = mutableStateOf(Color.Transparent),
    presence = mutableFloatStateOf(0f),
)

val LocalSongColors = staticCompositionLocalOf { NoSong }
