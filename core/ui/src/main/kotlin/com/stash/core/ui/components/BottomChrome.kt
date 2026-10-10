package com.stash.core.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * LUMA: height of the floating glass bottom chrome (mini player + tab bar +
 * the system navigation bar under them). Screens that scroll behind the glass
 * add it to their list's bottom content padding so the last row can scroll
 * clear of it; every other screen is padded by it from outside.
 */
val LocalBottomChromePadding = staticCompositionLocalOf<Dp> { 0.dp }

/** LUMA: the status-bar inset, applied inside each screen so its ambient backdrop runs up behind the status bar. */
val LocalTopInsetPadding = staticCompositionLocalOf<Dp> { 0.dp }
