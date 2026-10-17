package com.luma.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.core.data.repository.MusicRepository
import com.luma.core.model.MusicSource
import com.luma.core.model.SyncResult
import com.luma.core.model.Track
import com.luma.data.spotify.SpotifyApiClient
import com.luma.data.ytmusic.YTMusicApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject

/** Where a Spotify-link import is. */
sealed interface SpotifyImportState {
    data object Idle : SpotifyImportState
    data class Working(val done: Int, val total: Int) : SpotifyImportState
    data class Done(val playlistId: Long, val name: String, val imported: Int, val missed: Int) : SpotifyImportState
    data class Failed(val message: String) : SpotifyImportState
}

/**
 * LUMA: Library ▸ + ▸ "Paste Spotify playlist link". Reads the playlist's
 * songs from Spotify (public playlists need no sign-in; private ones use your
 * Spotify sign-in), matches each one on YouTube Music, and saves them as a new
 * playlist of your own, optionally downloading it all in one go.
 */
@HiltViewModel
class SpotifyLinkImportViewModel @Inject constructor(
    private val spotify: SpotifyApiClient,
    private val ytMusic: YTMusicApiClient,
    private val musicRepository: MusicRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SpotifyImportState>(SpotifyImportState.Idle)
    val state: StateFlow<SpotifyImportState> = _state.asStateFlow()

    fun dismiss() {
        if (_state.value !is SpotifyImportState.Working) _state.value = SpotifyImportState.Idle
    }

    fun import(link: String, downloadAll: Boolean) {
        if (_state.value is SpotifyImportState.Working) return
        val playlistId = playlistIdFrom(link) ?: run {
            _state.value = SpotifyImportState.Failed("That isn't a Spotify playlist link.")
            return
        }
        _state.value = SpotifyImportState.Working(0, 0)
        // Off the main thread: the Spotify client does blocking network I/O.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Spotify's public embed page first (no sign-in, no rate-limited API);
                // the signed-in client for whatever it can't read.
                val embed = embedPlaylist(playlistId)
                val items: List<ImportSong> = embed?.second?.takeIf { it.isNotEmpty() }
                    ?: when (val r = spotify.getPlaylistTracks(playlistId)) {
                        is SyncResult.Success -> r.data.mapNotNull { it.track }.map { t ->
                            ImportSong(
                                title = t.name,
                                artist = t.artists.joinToString(", ") { it.name },
                                leadArtist = t.artists.firstOrNull()?.name,
                                album = t.album?.name.orEmpty(),
                                durationMs = t.duration_ms,
                                uri = t.uri,
                                artUrl = t.album?.images?.firstOrNull()?.url,
                            )
                        }
                        else -> emptyList()
                    }
                if (items.isEmpty()) {
                    _state.value = SpotifyImportState.Failed(
                        "Couldn't read that playlist. If it's private, sign in to Spotify in Settings › Accounts.",
                    )
                    return@launch
                }
                val name = embed?.first ?: playlistName(playlistId) ?: "Spotify playlist"
                var done = 0
                _state.value = SpotifyImportState.Working(0, items.size)
                val gate = Semaphore(MATCH_PARALLELISM)
                val trackIds = coroutineScope {
                    items.map { t ->
                        async {
                            gate.withPermit {
                                val match = runCatching { ytMusic.searchCanonicalMatch(t.leadArtist ?: t.artist, t.title) }.getOrNull()
                                val id = match?.let {
                                    runCatching {
                                        musicRepository.ensureTrackPersisted(
                                            Track(
                                                title = t.title,
                                                artist = t.artist,
                                                album = t.album,
                                                durationMs = t.durationMs,
                                                source = MusicSource.SPOTIFY,
                                                spotifyUri = t.uri.takeIf { u -> u.isNotBlank() },
                                                youtubeId = it.videoId,
                                                albumArtUrl = t.artUrl ?: it.thumbnailUrl,
                                                isStreamable = true,
                                            ),
                                        )
                                    }.getOrNull()
                                }
                                synchronized(this@SpotifyLinkImportViewModel) { done++ }
                                _state.value = SpotifyImportState.Working(done, items.size)
                                id
                            }
                        }
                    }.awaitAll()
                }
                val ids = trackIds.filterNotNull().distinct()
                if (ids.isEmpty()) {
                    _state.value = SpotifyImportState.Failed("None of the songs could be found on YouTube Music.")
                    return@launch
                }
                val newId = musicRepository.createPlaylistWithTracks(name, ids)
                // On keeps it on the phone; off also keeps Download mode's next sync from
                // downloading it (a new playlist is synced by default there).
                musicRepository.setPlaylistDownload(newId, downloadAll)
                _state.value = SpotifyImportState.Done(newId, name, ids.size, items.size - ids.size)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = SpotifyImportState.Failed("Import failed. Check your connection and try again.")
            }
        }
    }

    /** One song to import, from either Spotify source. */
    private data class ImportSong(
        val title: String,
        val artist: String,
        val leadArtist: String?,
        val album: String,
        val durationMs: Long,
        val uri: String,
        val artUrl: String?,
    )

    /**
     * The playlist's name and songs from Spotify's public embed page
     * (open.spotify.com/embed/playlist/…), which needs no sign-in. Null when the
     * page can't be read or its data moved.
     */
    private suspend fun embedPlaylist(id: String): Pair<String?, List<ImportSong>>? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("https://open.spotify.com/embed/playlist/$id").openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            val json = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.get(1) ?: return@runCatching null
            val entity = org.json.JSONObject(json).getJSONObject("props").getJSONObject("pageProps")
                .getJSONObject("state").getJSONObject("data").getJSONObject("entity")
            val list = entity.optJSONArray("trackList") ?: return@runCatching null
            val songs = (0 until list.length()).mapNotNull { i ->
                val t = list.optJSONObject(i) ?: return@mapNotNull null
                val title = t.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val artist = t.optString("subtitle")
                ImportSong(
                    title = title,
                    artist = artist,
                    leadArtist = artist.split(",").firstOrNull()?.trim()?.takeIf { it.isNotBlank() },
                    album = "",
                    durationMs = t.optLong("duration"),
                    uri = t.optString("uri"),
                    artUrl = null,
                )
            }
            (entity.optString("name").takeIf { it.isNotBlank() }) to songs
        }.getOrNull()
    }

    /** The playlist's title, from Spotify's public oEmbed (no sign-in). */
    private suspend fun playlistName(id: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val page = URLEncoder.encode("https://open.spotify.com/playlist/$id", "UTF-8")
            val conn = URL("https://open.spotify.com/oembed?url=$page").openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.inputStream.bufferedReader().use { it.readText() }
                .let { org.json.JSONObject(it).optString("title") }
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    companion object {
        private const val MATCH_PARALLELISM = 6

        /** The playlist id from an open.spotify.com/playlist/… link or a spotify:playlist:… URI. */
        internal fun playlistIdFrom(link: String): String? {
            val s = link.trim()
            Regex("""playlist[/:]([A-Za-z0-9]{10,40})""").find(s)?.let { return it.groupValues[1] }
            return null
        }
    }
}
