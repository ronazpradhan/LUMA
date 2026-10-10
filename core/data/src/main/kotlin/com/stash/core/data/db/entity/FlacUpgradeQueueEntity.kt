package com.stash.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stash.core.model.FlacUpgradeStatus
import java.time.Instant

/**
 * One track in the current batch "Upgrade to FLAC" run (spec 2026-07-22 §3).
 * A new batch clears the previous batch's rows (single-batch semantics);
 * rows are the worker's persisted worklist so the batch survives process
 * death and stays cancelable. Nothing reads terminal rows — they linger
 * only until the next batch's clear-and-insert.
 */
@Entity(
    tableName = "flac_upgrade_queue",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["status"])],
)
data class FlacUpgradeQueueEntity(
    @PrimaryKey
    @ColumnInfo(name = "track_id")
    val trackId: Long,

    val status: FlacUpgradeStatus = FlacUpgradeStatus.PENDING,

    @ColumnInfo(name = "enqueued_at")
    val enqueuedAt: Instant = Instant.now(),
)
