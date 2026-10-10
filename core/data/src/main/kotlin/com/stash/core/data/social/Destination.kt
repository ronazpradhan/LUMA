package com.stash.core.data.social

/**
 * v0.9.13: Where a "like" goes. Enum is small and stable; new
 * platforms (Apple Music, ListenBrainz) would add a value plus a
 * matching dispatcher branch.
 */
enum class Destination {
    /** Local Stash "Liked Songs" playlist — always available. */
    STASH,

    /** Spotify Liked Songs — requires connected Spotify account + spotifyUri. */
    SPOTIFY,

    /** YouTube Music Liked Music — requires connected YT account + youtubeId. */
    YT_MUSIC,

    /**
     * Last.fm loved tracks — requires a connected Last.fm session. Unlike the
     * others this needs no platform id on the track: Last.fm matches on
     * artist + title, so any track with metadata is eligible and no
     * cross-platform id resolution step is required.
     */
    LAST_FM,
}
