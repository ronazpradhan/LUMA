package com.luma.core.model

enum class MusicSource {
    SPOTIFY,
    YOUTUBE,

    /**
     * User-imported audio files from the local device (files picked via
     * SAF picker or shared into LUMA). These are not tied to any
     * streaming service — `spotifyUri` and `youtubeId` are both null.
     * Files live in the same managed library location as downloaded
     * synced tracks (internal or SAF target, per storage preference).
     */
    LOCAL,

    BOTH,
}
