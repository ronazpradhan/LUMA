package com.stash.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistTypeTest {
    @Test
    fun `DOWNLOADS_MIX is a first-class enum value`() {
        val value = PlaylistType.valueOf("DOWNLOADS_MIX")
        assertEquals(PlaylistType.DOWNLOADS_MIX, value)
    }

    /**
     * Tripwire, not a formality: these names are persisted verbatim in the
     * `playlists.type` column, so adding one is fine but RENAMING or REMOVING one
     * orphans existing rows and needs a migration. Update this set deliberately —
     * if you're here because it failed, check whether the change needs a DB
     * migration before pasting the new name in.
     *
     * (It missed STASH_LIKED, added back in v0.9.13, and sat red for months.)
     */
    @Test
    fun `enum contains the expected set`() {
        assertEquals(
            setOf("DAILY_MIX", "LIKED_SONGS", "CUSTOM", "STASH_MIX", "DOWNLOADS_MIX", "STASH_LIKED"),
            PlaylistType.entries.map { it.name }.toSet(),
        )
    }
}
