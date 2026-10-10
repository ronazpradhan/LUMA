package com.stash.core.data.blocklist

import com.stash.core.data.sync.TrackMatcher

/**
 * v0.9.15: Identity key used to look up a (artist, title) pair in
 * `track_blocklist`. Reuses the same canonicalisers [TrackMatcher] uses
 * for sync deduplication so a track blocked here is recognised under
 * the same identity rules sync uses to merge cross-source duplicates.
 *
 * Format: `"${canonicalArtist}|${canonicalTitle}"` (lowercase, normalised).
 */
object BlocklistKey {

    /**
     * Derive a key from raw artist + title strings. Use this on inputs
     * that haven't been through the canonicaliser yet — sync snapshots,
     * search results, user-provided strings.
     */
    fun of(artist: String, title: String, matcher: TrackMatcher): String {
        val canonicalArtist = matcher.canonicalArtist(artist)
        val canonicalTitle = matcher.canonicalTitle(title)
        return "$canonicalArtist|$canonicalTitle"
    }

    /**
     * Derive a key from a [com.stash.core.data.db.entity.TrackEntity]'s
     * already-normalised `canonical_artist` and `canonical_title` columns.
     * Use this when the canonical fields are already in the row (migration,
     * integrity worker). Use [of] when you have raw artist/title strings.
     *
     * The migration v18→v19 uses SQL string concat over the same fields,
     * so this helper produces an identical key — important for the
     * integrity worker's cleanup of pre-migration leaked rows.
     */
    fun fromStoredCanonicals(canonicalArtist: String, canonicalTitle: String): String =
        "$canonicalArtist|$canonicalTitle"
}
