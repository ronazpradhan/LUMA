package com.stash.core.data.navigation

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * v0.9.13: Cross-feature handoff for "open Settings and scroll to a
 * specific section". The Settings route is a `data object` and the
 * bottom-nav setup keys off the route type, so we don't add a route
 * argument; instead Home writes a one-shot focus request here, then
 * navigates, and Settings reads + clears the request on entry.
 *
 * Why a singleton state-flow rather than a navArg:
 *  - Avoids the cascade of edits that converting `data object SettingsRoute`
 *    to a parameterized class would force across the bottom-nav setup,
 *    StashScaffold, and every existing `navigate(SettingsRoute)` call site.
 *  - The focus signal is purely UI-side and ephemeral — no persistence
 *    needed, no back-stack restoration concern.
 *  - Two-feature contract is small enough that a typed singleton is
 *    clearer than the equivalent navigation plumbing.
 *
 * Single-shot semantics: once a Settings instance reads the value via
 * [consume], it's cleared. A second observer or a back-navigation
 * won't re-fire.
 */
@Singleton
class SettingsDeepLinkController @Inject constructor() {
    private val _focus = MutableStateFlow<SettingsFocus?>(null)
    val focus: StateFlow<SettingsFocus?> = _focus.asStateFlow()

    /** Caller-side: queue a focus request just before navigating to Settings. */
    fun request(focus: SettingsFocus) {
        _focus.value = focus
    }

    /** Settings-side: read the pending focus (if any) and clear it atomically. */
    fun consume(): SettingsFocus? {
        var taken: SettingsFocus? = null
        _focus.update { current ->
            taken = current
            null
        }
        return taken
    }
}

/**
 * Sections within Settings that a deep-link can target. Add a new value
 * here when a new Home banner / nudge needs to land the user in a
 * specific Settings card.
 */
enum class SettingsFocus {
    LOSSLESS,
    LASTFM,
}
