package com.stash.core.data.mix

import com.stash.core.data.lastfm.LastFmApiClient
import com.stash.core.data.lastfm.LastFmPersonas
import com.stash.core.data.lastfm.LastFmSimilarArtist
import com.stash.core.data.lastfm.LastFmSimilarTrack
import com.stash.core.data.lastfm.LastFmTopTrack
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MixSeedStrategyTest {

    @Test
    fun `fromStored returns ARTIST_SIMILAR for unknown strings`() {
        assertEquals(MixSeedStrategy.ARTIST_SIMILAR, MixSeedStrategy.fromStored("garbage"))
        assertEquals(MixSeedStrategy.TAG_GRAPH, MixSeedStrategy.fromStored("TAG_GRAPH"))
        assertEquals(MixSeedStrategy.NONE, MixSeedStrategy.fromStored("NONE"))
    }

    @Test
    fun `ARTIST_SIMILAR strategy calls getSimilarArtists then getArtistTopTracks`() = runTest {
        val client = mockk<LastFmApiClient>()
        coEvery { client.getSimilarArtists("Beach House", 5) } returns
            Result.success(listOf(LastFmSimilarArtist("Slowdive", 0.9f)))
        coEvery { client.getArtistTopTracks("Slowdive", 3) } returns
            Result.success(listOf(LastFmTopTrack("Slowdive", "Sugar for the Pill", 1000)))

        val gen = MixSeedGenerator(client)
        val out = gen.generate(
            strategy = MixSeedStrategy.ARTIST_SIMILAR,
            seedArtists = listOf("Beach House"),
            topTags = emptyList(),
            seedTracks = emptyList(),
            personas = LastFmPersonas.EMPTY,
        )

        assertEquals(1, out.size)
        assertEquals("Slowdive", out[0].artist)
        assertEquals("Sugar for the Pill", out[0].title)
        assertEquals("Beach House", out[0].seedArtist)
        coVerify(exactly = 1) { client.getSimilarArtists("Beach House", 5) }
        coVerify(exactly = 1) { client.getArtistTopTracks("Slowdive", 3) }
    }

    @Test
    fun `TAG_GRAPH strategy calls getTagTopTracks per tag`() = runTest {
        val client = mockk<LastFmApiClient>()
        coEvery { client.getTagTopTracks("shoegaze", 30) } returns
            Result.success(listOf(LastFmTopTrack("My Bloody Valentine", "Only Shallow", 500)))
        coEvery { client.getTagTopTracks("dream pop", 30) } returns
            Result.success(listOf(LastFmTopTrack("Cocteau Twins", "Heaven or Las Vegas", 700)))

        val gen = MixSeedGenerator(client)
        val out = gen.generate(
            strategy = MixSeedStrategy.TAG_GRAPH,
            seedArtists = emptyList(),
            topTags = listOf("shoegaze", "dream pop"),
            seedTracks = emptyList(),
            personas = LastFmPersonas.EMPTY,
        )

        assertEquals(2, out.size)
        coVerify(exactly = 1) { client.getTagTopTracks("shoegaze", 30) }
        coVerify(exactly = 1) { client.getTagTopTracks("dream pop", 30) }
    }

    @Test
    fun `TRACK_SIMILAR strategy calls getSimilarTracks per seed track`() = runTest {
        val client = mockk<LastFmApiClient>()
        coEvery { client.getSimilarTracks("Arctic Monkeys", "505", 10) } returns
            Result.success(listOf(LastFmSimilarTrack("The Last Shadow Puppets", "Standing Next to Me", 0.8f)))

        val gen = MixSeedGenerator(client)
        val out = gen.generate(
            strategy = MixSeedStrategy.TRACK_SIMILAR,
            seedArtists = emptyList(),
            topTags = emptyList(),
            seedTracks = listOf("Arctic Monkeys" to "505"),
            personas = LastFmPersonas.EMPTY,
        )

        assertEquals(1, out.size)
        assertEquals("The Last Shadow Puppets", out[0].artist)
        coVerify(exactly = 1) { client.getSimilarTracks("Arctic Monkeys", "505", 10) }
    }

    @Test
    fun `NONE strategy returns empty list and calls no endpoints`() = runTest {
        val client = mockk<LastFmApiClient>()  // not relaxed — any unexpected call fails
        val gen = MixSeedGenerator(client)
        val out = gen.generate(
            strategy = MixSeedStrategy.NONE,
            seedArtists = listOf("X"),
            topTags = listOf("y"),
            seedTracks = emptyList(),
            personas = LastFmPersonas.EMPTY,
        )
        assertEquals(emptyList<MixGenerator.DiscoveryCandidate>(), out)
    }
}
