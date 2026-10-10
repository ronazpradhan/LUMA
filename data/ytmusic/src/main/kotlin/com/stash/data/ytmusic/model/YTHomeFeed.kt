package com.stash.data.ytmusic.model

/** One titled row of the YouTube Music home feed ("Quick picks", "Mixed for you", "Listen again"...). */
data class YTHomeShelf(
    val title: String,
    val items: List<YTHomeItem>,
)

/** A tile on a [YTHomeShelf]: a song to play, a playlist or mix to play, or an album to open. */
sealed interface YTHomeItem {
    val title: String
    val thumbnailUrl: String?

    data class Song(val track: TrackSummary) : YTHomeItem {
        override val title: String get() = track.title
        override val thumbnailUrl: String? get() = track.thumbnailUrl
    }

    /** [playlistId] has no `VL` prefix; radios and mixes (`RD…`) are playlists too. */
    data class Playlist(
        val playlistId: String,
        override val title: String,
        override val thumbnailUrl: String?,
    ) : YTHomeItem

    data class Album(val album: AlbumSummary) : YTHomeItem {
        override val title: String get() = album.title
        override val thumbnailUrl: String? get() = album.thumbnailUrl
    }
}
