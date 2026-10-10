package com.stash.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Per-destination submission outcome for one [ListeningEventEntity].
 *
 * ## Why this table exists
 *
 * Scrobble state started as a single `listening_events.scrobbled` boolean for
 * Last.fm. YouTube history then needed its own state, so it got a second column
 * (`yt_scrobbled`) plus its own DAO triplet — `pendingYtScrobbles`,
 * `pendingYtScrobbleCount`, `markYtScrobbled` — mirroring Last.fm's. Every new
 * destination on that pattern costs a column, three queries, and a schema
 * migration, and ListenBrainz would have made it three of each.
 *
 * One row per (event, destination) generalises it: adding a destination becomes a
 * new [target] string and no schema change at all.
 *
 * ## Absence means "not yet sent"
 *
 * Only *outcomes* are stored. Nothing enrols an event when it is recorded, so
 * pending work is "an event with no SENT row for this target" — the same
 * semantics the boolean columns had, minus the bookkeeping. See
 * [com.stash.core.data.db.dao.ListenSubmissionDao.pendingFor].
 *
 * ## Newly-connected destinations start from now
 *
 * A destination must NOT inherit the user's entire listening history the moment
 * they connect it: that would submit thousands of old plays as one flood nobody
 * asked for. The drain query is therefore bounded by a per-destination
 * "listening from" timestamp held by the sink, not by this table. Deliberate
 * backfill stays an explicit, separate action (as Last.fm's cold-start importer
 * already is).
 */
@Entity(
    tableName = "listen_submissions",
    primaryKeys = ["event_id", "target"],
    foreignKeys = [
        ForeignKey(
            entity = ListeningEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // Drives the pending-work scan and its reactive count.
        Index(value = ["target", "state"]),
    ],
)
data class ListenSubmissionEntity(
    @ColumnInfo(name = "event_id")
    val eventId: Long,

    /** Destination id, e.g. `lastfm`, `listenbrainz`, `youtube_history`. */
    @ColumnInfo(name = "target")
    val target: String,

    /** [SENT] or [FAILED]. A missing row means "never attempted". */
    @ColumnInfo(name = "state")
    val state: String,

    /**
     * Failed attempts so far. Lets the drain loop stop retrying a listen the
     * destination will never accept (malformed metadata, deleted recording)
     * instead of hammering it on every trigger forever.
     */
    @ColumnInfo(name = "attempts")
    val attempts: Int = 0,

    /** Last state change, epoch millis — for diagnostics and stuck-queue triage. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    companion object {
        const val SENT = "SENT"
        const val FAILED = "FAILED"

        /**
         * Give up after this many failures. Chosen to outlast a transient outage
         * (each attempt is a separate trigger: app start, a new listen, or a
         * manual sync) while still bounding a permanently-rejected row.
         */
        const val MAX_ATTEMPTS = 5
    }
}
