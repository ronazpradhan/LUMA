package com.stash.core.data.sync.workers

import com.google.common.truth.Truth.assertThat
import com.stash.core.model.SyncMode
import org.junit.Test

/**
 * The one gate deciding whether a sync may soft-hide playlists the remote didn't
 * return this run. Shared by Spotify (PlaylistFetchWorker) and YouTube (DiffWorker).
 *
 * YouTube used to open-code only the inventory half, so an ACCUMULATE sync still
 * hid YouTube playlists — including user-created ones — while Spotify correctly
 * kept them. That asymmetry is the bug these cases exist to prevent returning.
 */
class PlaylistDeactivationGateTest {

    @Test fun `refresh with complete inventory deactivates missing playlists`() {
        assertThat(shouldDeactivateMissingPlaylists(SyncMode.REFRESH, inventoryComplete = true))
            .isTrue()
    }

    @Test fun `partial or unavailable inventory preserves missing playlists`() {
        assertThat(shouldDeactivateMissingPlaylists(SyncMode.REFRESH, inventoryComplete = false))
            .isFalse()
    }

    /**
     * The exact shape of the reported bug: accumulating, and the remote simply
     * didn't list every playlist this run. Nothing may be hidden either way —
     * ACCUMULATE's promise is "never remove anything", inventory notwithstanding.
     */
    @Test fun `accumulate never deactivates, complete inventory or not`() {
        assertThat(shouldDeactivateMissingPlaylists(SyncMode.ACCUMULATE, inventoryComplete = true))
            .isFalse()
        assertThat(shouldDeactivateMissingPlaylists(SyncMode.ACCUMULATE, inventoryComplete = false))
            .isFalse()
    }
}
