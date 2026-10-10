package com.stash.feature.sync

import com.stash.core.model.SyncDisplayStatus
import com.stash.core.model.SyncState

/**
 * Summarised sync status information displayed in the Sync tab's
 * [com.stash.feature.sync.components.SyncStatusCard].
 *
 * Lives in `:feature:sync` (moved here from `:feature:home` in the
 * SyncStatusCard relocation refactor). Carries everything the card
 * needs to render: per-source counts, storage breakdown, latest
 * sync timestamp, and a richer [SyncDisplayStatus] so partial /
 * interrupted runs aren't reported as generic failures.
 */
data class SyncStatusInfo(
    val lastSyncTime: Long? = null,
    val nextSyncTime: Long? = null,
    val totalTracks: Int = 0,
    val spotifyTracks: Int = 0,
    val youTubeTracks: Int = 0,
    val totalPlaylists: Int = 0,
    val storageUsedBytes: Long = 0,
    /** Count of downloaded FLAC tracks. Subset of [totalTracks]. */
    val flacTracks: Int = 0,
    /** Sum of file sizes for downloaded FLAC tracks. Subset of [storageUsedBytes]. */
    val flacStorageBytes: Long = 0,
    val state: SyncState = SyncState.IDLE,
    /** Richer display-oriented summary of the latest sync outcome. */
    val displayStatus: SyncDisplayStatus = SyncDisplayStatus.Idle,
)
