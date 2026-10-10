package com.stash.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.core.data.radio.RadioSeed
import com.stash.core.media.PlayerRepository
import com.stash.core.media.StreamRoutingResult
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
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
    private val listeningSuggestions: com.stash.core.data.suggest.ListeningSuggestions,
    private val tokenManager: com.stash.core.auth.TokenManager,
) : ViewModel() {

    /**
     * True once Spotify or YouTube Music is signed in: Home then drops its
     * "Personalize" card. Also true when a sign-in cookie is stored but the
     * profile fetch never landed (the service still syncs; it just isn't
     * reported as Connected).
     */
    val anyServiceConnected: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        tokenManager.spotifyAuthState,
        tokenManager.youTubeAuthState,
    ) { spotify, youTube -> spotify to youTube }
        .map { (spotify, youTube) ->
            spotify is com.stash.core.auth.model.AuthState.Connected ||
                youTube is com.stash.core.auth.model.AuthState.Connected ||
                runCatching {
                    !tokenManager.getSpDcCookie().isNullOrBlank() || !tokenManager.getYouTubeCookie().isNullOrBlank()
                }.getOrDefault(false)
        }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    private val _shelves = MutableStateFlow<List<YTHomeShelf>>(emptyList())
    val shelves: StateFlow<List<YTHomeShelf>> = _shelves.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    /** True while a pull-to-refresh is reloading the feed. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var loading = false

    init { refresh() }

    /** Re-reads the feed; a failed read keeps the rows already shown. [userPulled] shows the spinner. */
    fun refresh(userPulled: Boolean = false) {
        if (loading) return
        loading = true
        if (userPulled) _refreshing.value = true
        // The feed and "Suggested for you" load side by side and each lands as soon
        // as it is ready; the rows on screen stay until then. The spinner stops
        // with the feed, not with the slower suggestions.
        var suggested: YTHomeShelf? = _shelves.value.firstOrNull { it.title == SUGGESTED_TITLE }
        var home: List<YTHomeShelf> = _shelves.value.filter { it.title != SUGGESTED_TITLE }
        fun publish() {
            val next = listOfNotNull(suggested) + home
            if (next.isNotEmpty()) _shelves.value = next
        }
        viewModelScope.launch {
            val picks = launch {
                val s = kotlinx.coroutines.withTimeoutOrNull(SUGGEST_TIMEOUT_MS) {
                    runCatching { listeningSuggestions.load() }.getOrNull()
                }
                s?.takeIf { it.basedOn != null && it.songs.isNotEmpty() }?.let { found ->
                    suggested = YTHomeShelf(SUGGESTED_TITLE, found.songs.map { YTHomeItem.Song(it) })
                    publish()
                }
            }
            try {
                val feed = runCatching { ytMusic.getHomeFeed() }.getOrDefault(emptyList())
                if (feed.isNotEmpty()) {
                    home = feed.map { shelf -> if (userPulled) shelf.copy(items = shelf.items.shuffled()) else shelf }
                    publish()
                }
            } finally {
                _refreshing.value = false
            }
            picks.join()
            loading = false
        }
    }

    private companion object {
        const val SUGGESTED_TITLE = "Suggested for you"
        const val SUGGEST_TIMEOUT_MS = 6_000L
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
        // Play the song first, then build its radio behind it: building the
        // radio before playing cost a second or two on every tap.
        val played = playerRepository.playFromStream(
            TrackItem(t.videoId, t.title, t.artist, t.durationSeconds, t.thumbnailUrl, t.album),
        )
        if (played is StreamRoutingResult.Item) {
            runCatching {
                playerRepository.startRadio(RadioSeed.Song(t.title, t.artist, t.videoId), keepCurrent = true)
            }
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
