package com.stash.core.data.sync.workers

import com.google.common.truth.Truth.assertThat
import com.stash.core.model.PlaylistType
import org.junit.Test

/**
 * Unit test for [defaultSyncEnabled] — the one-line decision that keeps every
 * newly-discovered playlist opt-in, in both modes.
 */
class DefaultSyncEnabledTest {

    /**
     * #368: DAILY_MIX used to auto-enable in Online mode so mixes would surface
     * immediately with no download. That was redundant — getAllVisible's
     * streamable escape hatch surfaces a sync_enabled = 0 mix anyway (pinned by
     * PlaylistDaoMixVisibilityTest) — and the flag's only other effects were
     * making the mix download-eligible and making the orphan sweep spare its
     * tracks. Mixes rotate, so each new one pulled a fresh batch of downloads
     * nobody asked for.
     */
    @Test
    fun `daily mix is opt-in in both modes`() {
        assertThat(defaultSyncEnabled(PlaylistType.DAILY_MIX, online = true)).isFalse()
        assertThat(defaultSyncEnabled(PlaylistType.DAILY_MIX, online = false)).isFalse()
    }

    @Test
    fun `custom playlist always opt-in`() {
        assertThat(defaultSyncEnabled(PlaylistType.CUSTOM, online = true)).isFalse()
        assertThat(defaultSyncEnabled(PlaylistType.CUSTOM, online = false)).isFalse()
    }

    @Test
    fun `liked songs always opt-in`() {
        assertThat(defaultSyncEnabled(PlaylistType.LIKED_SONGS, online = true)).isFalse()
    }

    // STASH_MIX is a "mix" by name and sits next to DAILY_MIX in findOrCreatePlaylist's
    // art-refresh logic — guard against a future refactor broadening the check to
    // "mix-like" types and accidentally auto-enabling locally-generated mixes.
    @Test
    fun `stash mix always opt-in`() {
        assertThat(defaultSyncEnabled(PlaylistType.STASH_MIX, online = true)).isFalse()
    }
}
