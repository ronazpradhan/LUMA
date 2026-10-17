package com.luma.core.ui.components

import androidx.compose.runtime.compositionLocalOf
import com.luma.core.model.community.PostTarget

/**
 * Opens LUMA Community's confirm sheet for a song or playlist (spec 2026-09-26 §3). The app provides it only
 * while Community is on, so every "Post to Community" entry hides while it's null.
 */
val LocalPostToCommunity = compositionLocalOf<((PostTarget) -> Unit)?> { null }
