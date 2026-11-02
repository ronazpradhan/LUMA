package com.luma.feature.library.share

import com.google.common.truth.Truth.assertThat
import com.luma.core.data.share.SharePreference
import com.luma.core.data.share.SharedMixDocument
import com.luma.core.data.share.SharedMixRepository
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
class ShareMixViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = mockk<SharedMixRepository>(relaxed = true)
    private val pref = mockk<SharePreference>(relaxed = true)
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `the link is built on the phone and carries the playlist`() = runTest(dispatcher) {
        coEvery { pref.displayName() } returns "Ronaz"
        val songs = listOf(SharedTrack("Teardrop", "Massive Attack", youtubeId = "u7K72X4eo_s"))
        coEvery { repo.buildDocument(5, "Night drive", "Ronaz") } returns SharedMixDocument(name = "Night drive", sharedBy = "Ronaz", tracks = songs)
        val vm = ShareMixViewModel(repo, pref)
        vm.bind(5, "Night drive"); advanceUntilIdle()
        assertThat(vm.state.value.displayName).isEqualTo("Ronaz")

        var link: ShareMixLink? = null
        vm.makeLink("", "Ronaz") { link = it }; advanceUntilIdle()

        assertThat(link!!.url).startsWith("https://playluma.vercel.app/p#")
        assertThat(link!!.songs).isEqualTo(1)
        val parsed = ShareLinks.parse(link!!.url) as ShareLinks.Parsed.Playlist
        assertThat(parsed.playlist.name).isEqualTo("Night drive")
        assertThat(parsed.playlist.tracks).isEqualTo(songs)
        coVerify { pref.setDisplayName("Ronaz") }
    }

    @Test fun `an empty playlist says so and makes no link`() = runTest(dispatcher) {
        coEvery { repo.buildDocument(5, any(), any()) } returns SharedMixDocument(name = "Empty", tracks = emptyList())
        val vm = ShareMixViewModel(repo, pref)
        vm.bind(5, "Empty"); advanceUntilIdle()
        var called = false
        vm.makeLink("Empty", null) { called = true }; advanceUntilIdle()
        assertThat(called).isFalse()
        assertThat(vm.state.value.error).isNotNull()
    }

    @Test fun `a playlist longer than a link holds says how many songs went in`() = runTest(dispatcher) {
        val songs = (1..700).map { SharedTrack("Song $it", "Artist") }
        coEvery { repo.buildDocument(5, any(), any()) } returns SharedMixDocument(name = "Huge", tracks = songs)
        val vm = ShareMixViewModel(repo, pref)
        vm.bind(5, "Huge"); advanceUntilIdle()
        var link: ShareMixLink? = null
        vm.makeLink("Huge", null) { link = it }; advanceUntilIdle()
        assertThat(link!!.songs).isEqualTo(ShareLinks.MAX_LINK_TRACKS)
        assertThat(link!!.total).isEqualTo(700)
    }
}
