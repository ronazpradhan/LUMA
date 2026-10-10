package com.stash.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * v0.9.16: A skip event. Captured by [com.stash.core.media.listening.ListeningRecorder]
 * when the player transitions to a new track BEFORE the listen-threshold
 * fires (i.e. the user skipped before the play would have been counted).
 *
 * Stored in a separate table from `listening_events` so the implicit
 * "every listening_event row is a real listen" invariant is preserved
 * — touching that contract risks breaking the auto-save scrobbler and
 * the synthetic-backfill path.
 *
 * Skip-rate per track is computed as
 *   skip_rate = count(skips) / (count(skips) + count(listening_events))
 * over a rolling window (typically 14 days for the recipe-shadow-block
 * threshold).
 */
@Entity(
    tableName = "track_skip_events",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["track_id"]),
        Index(value = ["skipped_at"]),
    ],
)
data class TrackSkipEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "track_id")
    val trackId: Long,

    /** Epoch millis at the moment the skip happened. */
    @ColumnInfo(name = "skipped_at")
    val skippedAt: Long,

    /**
     * Player position (millis) when the skip happened. 0 if the user
     * hit Next before the player even started progressing — most
     * aggressive form of skip; weighted heaviest.
     */
    @ColumnInfo(name = "position_ms")
    val positionMs: Long,
)
