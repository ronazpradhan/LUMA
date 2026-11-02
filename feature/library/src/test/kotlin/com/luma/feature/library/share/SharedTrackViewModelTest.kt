package com.luma.feature.library.share

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.luma.core.data.repository.MusicRepository
import com.luma.core.data.social.LikeCoordinator
import com.luma.core.media.PlayerRepository
import com.luma.core.model.Track
import com.luma.core.model.share.ShareLinks
import com.luma.core.model.share.SharedTrack
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedTrackViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val music = mockk<MusicRepository>(relaxed = true)
    private val player = mockk<PlayerRepository>(relaxed = true)
    private val likes = mockk<LikeCoordinator>(relaxed = true)
    private val song = SharedTrack("Teardrop", "Massive Attack", "Mezzanine", artUrl = "https://i.scdn.co/image/a")
    private val link = ShareLinks.trackUrl(SharedTrack("Teardrop", "Massive Attack"))

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }
    private fun vm(route: String? = link) =
        SharedTrackViewModel(SavedStateHandle(mapOf("link" to route)), music, player, likes)

    @Test fun `a long link shows its song at once, with no network call`() = runTest(dispatcher) {
        val vm = vm(); advanceUntilIdle()
        assertThat(vm.state.value).isEqualTo(SharedTrackUiState.Loaded(SharedTrack("Teardrop", "Massive Attack")))
    }

    @Test fun `a route that isn't a song link shows the expired message`() = runTest(dispatcher) {
        val vm = vm(route = null); advanceUntilIdle()
        assertThat(vm.state.value).isEqualTo(SharedTrackUiState.Error("This link has expired. Open it again.", retryable = false))
    }

    @Test fun `play shows an existing like and a double tap runs once`() = runTest(dispatcher) {
        val saved = mockk<Track> { every { lumaLikedAt } returns 1L }
        coEvery { music.ensureTrackPersisted(any()) } returns 7L
        every { music.observeTrackById(7L) } returns flowOf(saved)
        val vm = vm()
        vm.play(); vm.play(); advanceUntilIdle()
        coVerify(exactly = 1) { music.ensureTrackPersisted(any()) }
        coVerify(exactly = 1) { player.setQueue(listOf(saved), 0, any()) }
        assertThat(vm.liked.value).isTrue()
    }


}
