package com.luma.feature.library.share

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.luma.core.data.share.SharedMixDocument
import com.luma.core.data.share.SharedMixRepository
import com.luma.core.media.PlayerRepository
import com.luma.core.model.Track
import com.luma.core.model.share.ShareLinks
import com.luma.core.model.share.SharedTrack
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedMixViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = mockk<SharedMixRepository>(relaxed = true)
    private val player = mockk<PlayerRepository>(relaxed = true)
    private val songs = listOf(SharedTrack("Teardrop", "Massive Attack"), SharedTrack("Angel", "Massive Attack"))
    private val link = ShareLinks.playlistUrl("Mezzanine mood", "Ronaz", songs)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }
    private fun vm(route: String? = link) = SharedMixViewModel(SavedStateHandle(mapOf("link" to route)), repo, player)

    @Test fun `opens straight from the link, with nothing fetched`() = runTest(dispatcher) {
        val s = vm().state.value as SharedMixUiState.Loaded
        assertThat(s.doc.name).isEqualTo("Mezzanine mood")
        assertThat(s.doc.sharedBy).isEqualTo("Ronaz")
        assertThat(s.doc.tracks).isEqualTo(songs)
    }

    @Test fun `a damaged link says so`() = runTest(dispatcher) {
        assertThat(vm("https://playluma.vercel.app/p#broken").state.value).isInstanceOf(SharedMixUiState.Error::class.java)
        assertThat(vm(null).state.value).isInstanceOf(SharedMixUiState.Error::class.java)
    }

    @Test fun `play queues the songs and save to library opens the new playlist`() = runTest(dispatcher) {
        val queued = listOf(mockk<Track>(relaxed = true))
        coEvery { repo.tracksFor(any()) } returns queued
        coEvery { repo.saveCopy(any<SharedMixDocument>()) } returns 42L
        val vm = vm()
        vm.play(); advanceUntilIdle()
        coVerify { player.setQueue(queued, 0, any()) }
        var opened = 0L
        vm.saveCopy { opened = it }; advanceUntilIdle()
        assertThat(opened).isEqualTo(42L)
    }
}
