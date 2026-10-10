package com.stash.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * v0.9.15: Identity-keyed blocklist. Replaces the prior `tracks.is_blacklisted`
 * flag, which leaked because (a) the flag could be silently cleared by REPLACE
 * upserts and the Track↔Entity mapper, and (b) every read path that forgot to
 * filter the flag would surface blocked tracks. The blocklist is now keyed by
 * canonical identity (artist + title), so blocked tracks can't reappear via a
 * different `tracks` row, a different source, or a canonical normaliser
 * disagreement.
 *
 * Identity precedence at lookup time is: [canonicalKey] (always present) →
 * [spotifyUri] / [youtubeId] (belt-and-braces match for cross-source dupes).
 */
@Entity(
    tableName = "track_blocklist",
    indices = [
        Index(value = ["spotify_uri"], unique = false),
        Index(value = ["youtube_id"], unique = false),
    ],
)
data class TrackBlocklistEntity(
    /** Canonical key: `${canonicalArtist}|${canonicalTitle}`. Primary key. */
    @PrimaryKey
    @ColumnInfo(name = "canonical_key")
    val canonicalKey: String,

    @ColumnInfo(name = "artist")
    val artist: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "spotify_uri")
    val spotifyUri: String? = null,

    @ColumnInfo(name = "youtube_id")
    val youtubeId: String? = null,

    @ColumnInfo(name = "blocked_at")
    val blockedAt: Long,

    /**
     * Attribution: where the block originated. Used for telemetry and the
     * Settings UI's "blocked from N" label. Values: NOW_PLAYING, CONTEXT_MENU,
     * PLAYLIST_DELETE, MIGRATION_V19, INTEGRITY_WORKER, OTHER.
     */
    @ColumnInfo(name = "blocked_from")
    val blockedFrom: String,
)
