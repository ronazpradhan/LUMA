package com.stash.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Restore points for "Undo last sync".
 *
 * A sync is destructive in two ways that the user experiences as lost data:
 *  - `deactivateMissingForSource` hides playlists the run didn't return;
 *  - REFRESH's `clearSyncedPlaylistTracks` hard-deletes a playlist's membership
 *    before re-inserting it from the snapshot.
 * Both are captured here BEFORE the diff runs, so the previous state can be put
 * back exactly.
 *
 * Deliberately NOT captured: `tracks` rows and downloaded files. Sync doesn't
 * delete track rows, and a deleted audio file cannot be restored from a database
 * snapshot at all — so copying them would add size and FK edge cases while
 * protecting nothing. Undo therefore restores *what the library looked like*,
 * not the bytes on disk.
 *
 * Sized for a real library (14.5k memberships ≈ 0.6 MB/point), so a handful of
 * points is cheap; [com.stash.core.data.db.dao.SyncUndoDao] prunes to the newest
 * few.
 */
@Entity(tableName = "sync_undo_points")
data class SyncUndoPointEntity(
    /** The sync run this point was captured before. */
    @PrimaryKey
    @ColumnInfo(name = "sync_id")
    val syncId: Long,

    @ColumnInfo(name = "created_at")
    val createdAtEpochMs: Long,

    /** Counts captured up front so the UI can describe a point without a join. */
    @ColumnInfo(name = "playlist_count")
    val playlistCount: Int,

    @ColumnInfo(name = "membership_count")
    val membershipCount: Int,
)

/** Per-playlist visibility flags as they were before the sync. */
@Entity(
    tableName = "sync_undo_playlists",
    primaryKeys = ["sync_id", "playlist_id"],
)
data class SyncUndoPlaylistEntity(
    @ColumnInfo(name = "sync_id")
    val syncId: Long,

    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean,

    @ColumnInfo(name = "sync_enabled")
    val syncEnabled: Boolean,
)

/**
 * A copy of `playlist_tracks` as it was before the sync.
 *
 * No foreign keys on purpose: a restore point must survive the very deletions it
 * exists to reverse (CASCADE would wipe the backup along with the data). Restore
 * re-checks referential integrity instead — see
 * [com.stash.core.data.db.dao.SyncUndoDao.restoreMemberships].
 */
@Entity(
    tableName = "sync_undo_memberships",
    primaryKeys = ["sync_id", "playlist_id", "track_id"],
    indices = [Index(value = ["sync_id"])],
)
data class SyncUndoMembershipEntity(
    @ColumnInfo(name = "sync_id")
    val syncId: Long,

    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,

    @ColumnInfo(name = "track_id")
    val trackId: Long,

    val position: Int,

    @ColumnInfo(name = "added_at")
    val addedAt: Long?,

    @ColumnInfo(name = "removed_at")
    val removedAt: Long?,

    @ColumnInfo(name = "locally_added")
    val locallyAdded: Boolean,
)
