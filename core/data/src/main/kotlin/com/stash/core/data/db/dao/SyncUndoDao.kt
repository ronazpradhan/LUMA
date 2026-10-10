package com.stash.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.stash.core.data.db.entity.SyncUndoPointEntity
import kotlinx.coroutines.flow.Flow

/**
 * Capture/restore for "Undo last sync".
 *
 * Everything is bulk `INSERT … SELECT` against the live tables — no rows are
 * marshalled through Kotlin — so capturing a 14.5k-membership library is a single
 * fast statement rather than 14.5k object round-trips.
 */
@Dao
interface SyncUndoDao {

    // ── Capture (before the diff runs) ──────────────────────────────────────

    @Query(
        """
        INSERT OR REPLACE INTO sync_undo_playlists (sync_id, playlist_id, is_active, sync_enabled)
        SELECT :syncId, id, is_active, sync_enabled FROM playlists
        """
    )
    suspend fun capturePlaylists(syncId: Long)

    @Query(
        """
        INSERT OR REPLACE INTO sync_undo_memberships
            (sync_id, playlist_id, track_id, position, added_at, removed_at, locally_added)
        SELECT :syncId, playlist_id, track_id, position, added_at, removed_at, locally_added
        FROM playlist_tracks
        """
    )
    suspend fun captureMemberships(syncId: Long)

    @Query("INSERT OR REPLACE INTO sync_undo_points (sync_id, created_at, playlist_count, membership_count) VALUES (:syncId, :createdAtEpochMs, :playlistCount, :membershipCount)")
    suspend fun insertPoint(syncId: Long, createdAtEpochMs: Long, playlistCount: Int, membershipCount: Int)

    @Query("SELECT COUNT(*) FROM sync_undo_playlists WHERE sync_id = :syncId")
    suspend fun countCapturedPlaylists(syncId: Long): Int

    @Query("SELECT COUNT(*) FROM sync_undo_memberships WHERE sync_id = :syncId")
    suspend fun countCapturedMemberships(syncId: Long): Int

    /**
     * Snapshot the library's current shape, then drop all but the newest
     * [keep] points. One transaction so a kill mid-capture can't leave a point
     * advertising a restore its rows don't back.
     */
    @Transaction
    suspend fun capture(syncId: Long, nowEpochMs: Long, keep: Int = MAX_UNDO_POINTS) {
        capturePlaylists(syncId)
        captureMemberships(syncId)
        insertPoint(
            syncId = syncId,
            createdAtEpochMs = nowEpochMs,
            playlistCount = countCapturedPlaylists(syncId),
            membershipCount = countCapturedMemberships(syncId),
        )
        prune(keep)
    }

    // ── Restore ─────────────────────────────────────────────────────────────

    @Query(
        """
        UPDATE playlists SET
            is_active = (SELECT u.is_active FROM sync_undo_playlists u
                         WHERE u.sync_id = :syncId AND u.playlist_id = playlists.id),
            sync_enabled = (SELECT u.sync_enabled FROM sync_undo_playlists u
                            WHERE u.sync_id = :syncId AND u.playlist_id = playlists.id)
        WHERE id IN (SELECT playlist_id FROM sync_undo_playlists WHERE sync_id = :syncId)
        """
    )
    suspend fun restorePlaylistFlags(syncId: Long): Int

    /**
     * Clear only SYNC-added membership for the snapshotted playlists. Rows the
     * user added by hand (`locally_added = 1`) are left alone — undoing a sync
     * must never throw away the user's own work, including additions made after
     * the sync ran.
     */
    @Query(
        """
        DELETE FROM playlist_tracks
        WHERE locally_added = 0
          AND playlist_id IN (SELECT playlist_id FROM sync_undo_playlists WHERE sync_id = :syncId)
        """
    )
    suspend fun clearSyncedMembershipForRestore(syncId: Long): Int

    /**
     * Put the snapshotted membership back.
     *
     * `OR IGNORE` + the two `IN` guards stand in for the foreign keys the undo
     * tables deliberately don't have: a playlist or track deleted since the
     * snapshot is skipped rather than failing the whole restore, and a surviving
     * user-added row is never overwritten.
     */
    @Query(
        """
        INSERT OR IGNORE INTO playlist_tracks
            (playlist_id, track_id, position, added_at, removed_at, locally_added)
        SELECT playlist_id, track_id, position, added_at, removed_at, locally_added
        FROM sync_undo_memberships
        WHERE sync_id = :syncId
          AND playlist_id IN (SELECT id FROM playlists)
          AND track_id IN (SELECT id FROM tracks)
        """
    )
    suspend fun restoreMemberships(syncId: Long)

    /**
     * How many snapshotted memberships are still restorable — same predicate as
     * [restoreMemberships]. Room can't return a row count from an INSERT, and the
     * honest number is the one that survives the FK re-checks, not the raw
     * snapshot size.
     */
    @Query(
        """
        SELECT COUNT(*) FROM sync_undo_memberships
        WHERE sync_id = :syncId
          AND playlist_id IN (SELECT id FROM playlists)
          AND track_id IN (SELECT id FROM tracks)
        """
    )
    suspend fun countRestorableMemberships(syncId: Long): Int

    /**
     * Undo one sync: playlist visibility and membership go back to the captured
     * state, atomically. Returns the number of membership rows restored.
     *
     * The point is consumed (deleted) afterwards so the same undo can't be
     * applied twice — a second application would be a no-op today, but only by
     * accident, and a stale "Undo" button that silently does nothing is worse
     * than no button.
     */
    @Transaction
    suspend fun restore(syncId: Long): Int {
        restorePlaylistFlags(syncId)
        clearSyncedMembershipForRestore(syncId)
        val restored = countRestorableMemberships(syncId)
        restoreMemberships(syncId)
        deletePoint(syncId)
        return restored
    }

    // ── Listing / retention ─────────────────────────────────────────────────

    @Query("SELECT * FROM sync_undo_points ORDER BY created_at DESC LIMIT 1")
    fun latestPoint(): Flow<SyncUndoPointEntity?>

    @Query("SELECT * FROM sync_undo_points ORDER BY created_at DESC LIMIT 1")
    suspend fun latestPointNow(): SyncUndoPointEntity?

    @Transaction
    suspend fun deletePoint(syncId: Long) {
        deletePointRow(syncId)
        deletePointPlaylists(syncId)
        deletePointMemberships(syncId)
    }

    @Query("DELETE FROM sync_undo_points WHERE sync_id = :syncId")
    suspend fun deletePointRow(syncId: Long)

    @Query("DELETE FROM sync_undo_playlists WHERE sync_id = :syncId")
    suspend fun deletePointPlaylists(syncId: Long)

    @Query("DELETE FROM sync_undo_memberships WHERE sync_id = :syncId")
    suspend fun deletePointMemberships(syncId: Long)

    @Query("SELECT sync_id FROM sync_undo_points ORDER BY created_at DESC LIMIT -1 OFFSET :keep")
    suspend fun pointIdsBeyond(keep: Int): List<Long>

    /** Drop everything except the newest [keep] points. */
    @Transaction
    suspend fun prune(keep: Int = MAX_UNDO_POINTS) {
        pointIdsBeyond(keep).forEach { deletePoint(it) }
    }

    companion object {
        /**
         * How many restore points to keep. Undo is a "that sync went wrong, put it
         * back" tool used within minutes, not a backup history — and each point is
         * a full copy of the membership table, so the cap keeps the DB bounded.
         */
        const val MAX_UNDO_POINTS = 3
    }
}
