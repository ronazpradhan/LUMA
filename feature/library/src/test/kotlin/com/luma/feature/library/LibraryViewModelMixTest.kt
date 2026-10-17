package com.luma.feature.library

import com.google.common.truth.Truth.assertThat
import com.luma.core.auth.TokenManager
import com.luma.core.auth.model.AuthState
import com.luma.core.data.db.dao.ArtistImageDao
import com.luma.core.data.prefs.StreamingPreference
import com.luma.core.data.repository.MusicRepository
import com.luma.core.media.PlayerRepository
import com.luma.core.model.MusicSource
import com.luma.core.model.PlayerState
import com.luma.core.model.Playlist
import com.luma.core.model.PlaylistType
import com.luma.core.model.Track
import com.luma.data.download.files.LocalImportCoordinator
import com.luma.data.download.files.LocalImportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Pins that the Library Playlists tab surfaces CUSTOM playlists only (mixes
 * and Liked moved to Home) and the delete-preview math. Mix-slice derivation
 * itself now lives in Home's `MixRailClassifierTest`.
 *
 * Same harness as [LibraryViewModelTest] / [PlaylistDetailViewModelTest]:
 * StandardTestDispatcher + setMain/resetMain, runTest{}, mockito-kotlin.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelMixTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun uiState_playlists_tab_shows_only_custom_playlists() = runTest {
        // One of every PlaylistType — mixes + liked now live in the dedicated
        // Mixes group, so the Playlists tab must surface ONLY the CUSTOM one.
        val lumaMix = playlist(11L, PlaylistType.LUMA_MIX, MusicSource.SPOTIFY)
        val dailyMix = playlist(20L, PlaylistType.DAILY_MIX, MusicSource.SPOTIFY)
        val liked = playlist(30L, PlaylistType.LIKED_SONGS, MusicSource.SPOTIFY)
        val downloadsMix = playlist(35L, PlaylistType.DOWNLOADS_MIX, MusicSource.SPOTIFY)
        val custom = playlist(40L, PlaylistType.CUSTOM, MusicSource.SPOTIFY)

        val musicRepo = musicRepoMock(
            playlists = listOf(lumaMix, dailyMix, liked, downloadsMix, custom),
        )

        val vm = buildVm(musicRepository = musicRepo)

        val state = vm.uiState.first { !it.isLoading }

        // Playlists tab = user (CUSTOM) playlists only; mixes/liked are excluded.
        assertThat(state.playlists.map { it.id }).containsExactly(40L)
    }

    @Test
    fun previewPlaylistDelete_returns_correct_willDelete() = runTest {
        // N=3 tracks in the playlist; K=1 (track id 2) is protected elsewhere.
        val tracks = listOf(
            Track(id = 1L, title = "A", artist = "X"),
            Track(id = 2L, title = "B", artist = "X"),
            Track(id = 3L, title = "C", artist = "X"),
        )
        val musicRepo: MusicRepository = mock {
            on { getAllTracks() } doReturn flowOf(emptyList())
            on { getAllPlaylists() } doReturn flowOf(emptyList())
            on { getAllArtists() } doReturn flowOf(emptyList())
            on { getAllAlbums() } doReturn flowOf(emptyList())
            on { getUserCreatedPlaylists() } doReturn flowOf(emptyList())
            on { getRecentlyAdded(any()) } doReturn flowOf(emptyList())
            on { getTracksByPlaylist(77L) } doReturn flowOf(tracks)
            // Stub every invocation: an unstubbed suspend Boolean returns null
            // (mockito default) → NPE on Kotlin's primitive unbox. Only id 2 is
            // protected elsewhere.
            onBlocking { isTrackProtectedExcluding(1L, 77L) } doReturn false
            onBlocking { isTrackProtectedExcluding(2L, 77L) } doReturn true
            onBlocking { isTrackProtectedExcluding(3L, 77L) } doReturn false
        }

        val vm = buildVm(musicRepository = musicRepo)

        val preview = vm.previewPlaylistDelete(
            playlist(77L, PlaylistType.CUSTOM, MusicSource.SPOTIFY),
        )

        assertThat(preview.willDelete).isEqualTo(2) // N=3 − K=1
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun playlist(id: Long, type: PlaylistType, source: MusicSource) =
        Playlist(id = id, name = "PL $id", source = source, type = type)

    private fun playerRepoMock(): PlayerRepository = mock {
        on { playerState } doReturn MutableStateFlow(PlayerState())
    }

    private fun musicRepoMock(playlists: List<Playlist>): MusicRepository = mock {
        on { getAllTracks() } doReturn flowOf(emptyList())
        on { getAllPlaylists() } doReturn flowOf(playlists)
        on { getAllArtists() } doReturn flowOf(emptyList())
        on { getAllAlbums() } doReturn flowOf(emptyList())
        on { getUserCreatedPlaylists() } doReturn flowOf(emptyList())
        on { getRecentlyAdded(any()) } doReturn flowOf(emptyList())
    }

    private fun tokenManagerMock(): TokenManager = mock {
        on { spotifyAuthState } doReturn MutableStateFlow<AuthState>(AuthState.NotConnected)
        on { youTubeAuthState } doReturn MutableStateFlow<AuthState>(AuthState.NotConnected)
    }

    private fun libraryPreferencesStoreMock(): LibraryPreferencesStore = mock {
        onBlocking { getSortOrder() } doReturn SortOrder.RECENT
        onBlocking { getSourceFilter() } doReturn SourceFilter.ALL
    }

    private fun buildVm(
        playerRepository: PlayerRepository = playerRepoMock(),
        musicRepository: MusicRepository,
        tokenManager: TokenManager = tokenManagerMock(),
        playlistImageHelper: PlaylistImageHelper = mock(),
        localImportCoordinator: LocalImportCoordinator = mock {
            on { state } doReturn MutableStateFlow<LocalImportState>(LocalImportState.Idle)
        },
        streamingPreference: StreamingPreference = mock { on { enabled } doReturn flowOf(false) },
        libraryPreferencesStore: LibraryPreferencesStore = libraryPreferencesStoreMock(),
        artistImageDao: ArtistImageDao = mock { on { observeAll() } doReturn flowOf(emptyList()) },
    ): LibraryViewModel = LibraryViewModel(
        musicRepository = musicRepository,
        playerRepository = playerRepository,
        tokenManager = tokenManager,
        playlistImageHelper = playlistImageHelper,
        localImportCoordinator = localImportCoordinator,
        streamingPreference = streamingPreference,
        flacUpgradeEnqueuer = org.mockito.kotlin.mock(),
        ytMusicApiClient = org.mockito.kotlin.mock(),
        libraryPreferencesStore = libraryPreferencesStore,
        libraryDeepLinkController = com.luma.core.data.navigation.LibraryDeepLinkController(),
        artistImageDao = artistImageDao,
        sharedMixRepository = org.mockito.kotlin.mock { on { observeActiveFollowedIds() }.thenReturn(kotlinx.coroutines.flow.flowOf(emptyList())) },
    )
}
