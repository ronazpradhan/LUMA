package com.stash.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.core.data.radio.RadioSeed
import com.stash.core.media.PlayerRepository
import com.stash.core.model.RadioStartResult
import com.stash.core.model.MusicSource
import com.stash.core.model.PlaybackSource
import com.stash.core.model.SyncResult
import com.stash.core.model.Track
import com.stash.core.model.TrackItem
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.model.YTHomeItem
import com.stash.data.ytmusic.model.YTHomeShelf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home's "YouTube Music" section: the rows of the YouTube Music home feed
 * (Quick picks, Listen again, Mixed for you...). A song plays as YouTube
 * Music's own radio from it, the way its app does; a playlist or mix plays
 * from the top. Everything streams.
 */
@HiltViewModel
class YouTubeHomeFeedViewModel @Inject constructor(
    private val ytMusic: YTMusicApiClient,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _shelves = MutableStateFlow<List<YTHomeShelf>>(emptyList())
    val shelves: StateFlow<List<YTHomeShelf>> = _shelves.asStateFlow()

    private var loading = false

    init { refresh() }

    /** Re-reads the feed; a failed read keeps the rows already shown. */
    fun refresh() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            try {
                val feed = runCatching { ytMusic.getHomeFeed() }.getOrDefault(emptyList())
                if (feed.isNotEmpty()) _shelves.value = feed
            } finally {
                loading = false
            }
        }
    }

    fun play(item: YTHomeItem) {
        viewModelScope.launch {
            when (item) {
                is YTHomeItem.Song -> playSong(item)
                is YTHomeItem.Playlist -> playPlaylist(item)
                is YTHomeItem.Album -> Unit // opened by the screen
            }
        }
    }

    private suspend fun playSong(item: YTHomeItem.Song) {
        val t = item.track
        val radio = runCatching {
            playerRepository.startRadio(RadioSeed.Song(t.title, t.artist, t.videoId), keepCurrent = false)
        }.getOrNull()
        if (radio != RadioStartResult.Started) {
            playerRepository.playFromStream(
                TrackItem(t.videoId, t.title, t.artist, t.durationSeconds, t.thumbnailUrl, t.album),
            )
        }
    }

    private suspend fun playPlaylist(item: YTHomeItem.Playlist) {
        val result = runCatching { ytMusic.getPlaylistTracks(item.playlistId) }.getOrNull()
        val tracks = (result as? SyncResult.Success)?.data?.tracks.orEmpty().map { yt ->
            Track(
                id = yt.videoId.hashCode().toLong(),
                title = yt.title,
                artist = yt.artists,
                album = yt.album.orEmpty(),
                durationMs = yt.durationMs ?: 0L,
                albumArtUrl = com.stash.core.common.ArtUrlUpgrader.upgrade(yt.thumbnailUrl),
                youtubeId = yt.videoId,
                source = MusicSource.YOUTUBE,
                isStreamable = true,
            )
        }
        if (tracks.isNotEmpty()) {
            playerRepository.setQueue(tracks, startIndex = 0, source = PlaybackSource.Radio(item.title))
        }
    }
}
