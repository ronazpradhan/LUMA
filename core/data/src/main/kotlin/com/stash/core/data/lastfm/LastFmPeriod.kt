package com.stash.core.data.lastfm

/**
 * v0.9.16: Last.fm `period` parameter for user.getTopTracks /
 * user.getTopArtists. Each value maps to a different temporal window
 * of the user's listening history, pre-computed by Last.fm — free
 * temporal slicing for the recommender.
 */
enum class LastFmPeriod(val apiValue: String) {
    SEVEN_DAY("7day"),
    ONE_MONTH("1month"),
    THREE_MONTH("3month"),
    SIX_MONTH("6month"),
    TWELVE_MONTH("12month"),
    OVERALL("overall"),
}
