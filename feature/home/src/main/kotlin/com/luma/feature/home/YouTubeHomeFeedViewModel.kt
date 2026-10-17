package com.luma.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.core.data.radio.RadioSeed
import com.luma.core.media.PlayerRepository
import com.luma.core.media.StreamRoutingResult
import com.luma.core.model.MusicSource
import com.luma.core.model.PlaybackSource
import com.luma.core.model.SyncResult
import com.luma.core.model.Track
import com.luma.core.model.TrackItem
import com.luma.data.ytmusic.YTMusicApiClient
import com.luma.data.ytmusic.model.YTHomeItem
import com.luma.data.ytmusic.model.YTHomeShelf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
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
    private val listeningSuggestions: com.luma.core.data.suggest.ListeningSuggestions,
    private val tokenManager: com.luma.core.auth.TokenManager,
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
            spotify is com.luma.core.auth.model.AuthState.Connected ||
                youTube is com.luma.core.auth.model.AuthState.Connected ||
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

    /** The two halves of Home's rows: "Suggested for you" and YouTube Music's own feed. */
    private var suggested: YTHomeShelf? = null
    private var feed: List<YTHomeShelf> = emptyList()
    private var suggestJob: kotlinx.coroutines.Job? = null
    private var lastSuggestAt = 0L

    init {
        refresh()
        // LUMA: Home keeps up with you. Each time a song counts as listened, the
        // suggestions are worked out again in the background (at most every
        // SUGGEST_MIN_GAP_MS), so the row reflects what you've been playing.
        viewModelScope.launch {
            listeningSuggestions.listeningChanges().drop(1).collect { refreshSuggestions() }
        }
    }

    /** Puts both halves on screen. Suggestions never repeat a song the feed below shows, or the one playing. */
    private fun publish() {
        val shownBelow = feedSongIds()
        val playing = playerRepository.playerState.value.currentTrack?.youtubeId
        val picks = suggested?.let { shelf ->
            shelf.copy(items = shelf.items.filterNot { it is YTHomeItem.Song && (it.track.videoId in shownBelow || it.track.videoId == playing) })
        }?.takeIf { it.items.size >= MIN_SUGGESTIONS }
        val next = listOfNotNull(picks) + feed
        if (next.isNotEmpty()) _shelves.value = next
    }

    private fun feedSongIds(): Set<String> =
        feed.flatMap { shelf -> shelf.items.filterIsInstance<YTHomeItem.Song>().map { it.track.videoId } }.toSet()

    /** Re-reads the feed and the suggestions; a failed read keeps the rows already shown. [userPulled] shows the spinner until both land. */
    fun refresh(userPulled: Boolean = false) {
        if (loading) return
        loading = true
        if (userPulled) _refreshing.value = true
        viewModelScope.launch {
            try {
                val picks = launch { loadSuggestions() }
                val fresh = runCatching { ytMusic.getHomeFeed() }.getOrDefault(emptyList())
                if (fresh.isNotEmpty()) {
                    feed = fresh.map { shelf -> if (userPulled) shelf.copy(items = shelf.items.shuffled()) else shelf }
                    publish()
                }
                picks.join()
            } finally {
                _refreshing.value = false
                loading = false
            }
        }
    }

    /** Suggestions only, in the background: after a listen, without touching the feed. */
    private fun refreshSuggestions() {
        if (suggestJob?.isActive == true) return
        if (System.currentTimeMillis() - lastSuggestAt < SUGGEST_MIN_GAP_MS) return
        suggestJob = viewModelScope.launch { loadSuggestions() }
    }

    private suspend fun loadSuggestions() {
        lastSuggestAt = System.currentTimeMillis()
        val now = playerRepository.playerState.value.currentTrack
        val found = kotlinx.coroutines.withTimeoutOrNull(SUGGEST_TIMEOUT_MS) {
            runCatching {
                listeningSuggestions.load(
                    nowPlaying = now?.youtubeId?.let { com.luma.core.data.suggest.NowPlaying(it, now.title, now.artist) },
                    alsoExclude = feedSongIds(),
                )
            }.getOrNull()
        }
        found?.takeIf { it.basedOn != null && it.songs.isNotEmpty() }?.let {
            suggested = YTHomeShelf(SUGGESTED_TITLE, it.songs.map { song -> YTHomeItem.Song(song) })
            publish()
        }
    }

    private companion object {
        const val SUGGESTED_TITLE = "Suggested for you"
        const val SUGGEST_TIMEOUT_MS = 12_000L
        const val SUGGEST_MIN_GAP_MS = 90_000L
        /** Fewer than this after removing on-screen songs and the row is not worth showing. */
        const val MIN_SUGGESTIONS = 4
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
                albumArtUrl = com.luma.core.common.ArtUrlUpgrader.upgrade(yt.thumbnailUrl),
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
