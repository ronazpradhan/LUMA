package com.stash.core.data.lastfm

/**
 * v0.9.16: Snapshot of period-sliced top tracks/artists fetched
 * once per refresh-worker run. Different mix recipes consume
 * different periods (Daily Discover → 1month, Throwback diff =
 * overall - 3month, etc.) — all five fit in a single struct
 * passed down the refresh pipeline.
 *
 * Empty when the user is not Last.fm-connected or when the fetch
 * times out; recipes that depend on a non-empty persona fall back
 * to local listening_events data.
 */
data class LastFmPersonas(
    val topTracksByPeriod: Map<LastFmPeriod, List<LastFmTopTrack>>,
    val topArtistsByPeriod: Map<LastFmPeriod, List<LastFmTopArtist>>,
) {
    companion object {
        val EMPTY = LastFmPersonas(emptyMap(), emptyMap())
    }
}
