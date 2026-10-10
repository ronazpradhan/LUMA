package com.stash.core.data.db.dao

/**
 * Minimal projection for seed-track lookups — returns just the
 * artist+title pair without the rest of TrackEntity. Used by
 * [ListeningEventDao.getTopTracksByLocalPlays] and
 * [TrackDao.getTopTracksByLfmPlaycount] which feed the seedTracks
 * fallback chain in StashMixRefreshWorker.queueDiscoveryForRecipe.
 */
data class TrackArtistTitle(
    val artist: String,
    val title: String,
)
