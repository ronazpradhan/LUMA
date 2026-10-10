package com.stash.core.data.mix

import com.stash.core.data.lastfm.LastFmApiClient
import com.stash.core.data.lastfm.LastFmPersonas
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

enum class MixSeedStrategy(val storedValue: String) {
    ARTIST_SIMILAR("ARTIST_SIMILAR"),
    TAG_GRAPH("TAG_GRAPH"),
    TRACK_SIMILAR("TRACK_SIMILAR"),
    NONE("NONE");

    companion object {
        fun fromStored(s: String): MixSeedStrategy =
            entries.firstOrNull { it.storedValue == s } ?: ARTIST_SIMILAR
    }
}

/**
 * v0.9.16: Per-recipe candidate generators. Each strategy queries
 * a different Last.fm graph using different inputs from the user's
 * personas + library. Output is the candidate list passed to
 * [com.stash.core.data.mix.MixGenerator.queueDiscoveryCandidates].
 */
@Singleton
class MixSeedGenerator @Inject constructor(
    private val apiClient: LastFmApiClient,
) {
    /** Inter-call rate-limiter — keeps Stash under Last.fm's 5 req/sec ceiling. */
    private val intervalMs: Long = 220L

    suspend fun generate(
        strategy: MixSeedStrategy,
        seedArtists: List<String>,
        topTags: List<String>,
        seedTracks: List<Pair<String, String>>,  // (artist, title)
        @Suppress("UNUSED_PARAMETER") personas: LastFmPersonas,
    ): List<MixGenerator.DiscoveryCandidate> = when (strategy) {
        MixSeedStrategy.ARTIST_SIMILAR -> generateArtistSimilar(seedArtists)
        MixSeedStrategy.TAG_GRAPH -> generateTagGraph(topTags)
        MixSeedStrategy.TRACK_SIMILAR -> generateTrackSimilar(seedTracks)
        MixSeedStrategy.NONE -> emptyList()
    }

    private suspend fun generateArtistSimilar(
        seedArtists: List<String>,
    ): List<MixGenerator.DiscoveryCandidate> {
        val out = mutableListOf<MixGenerator.DiscoveryCandidate>()
        for (seed in seedArtists) {
            val similar = apiClient.getSimilarArtists(seed, 5).getOrNull().orEmpty()
            for (sim in similar) {
                val top = apiClient.getArtistTopTracks(sim.name, 3).getOrNull().orEmpty()
                top.forEach {
                    out += MixGenerator.DiscoveryCandidate(it.artist, it.title, seed)
                }
                delay(intervalMs)
            }
        }
        return out
    }

    private suspend fun generateTagGraph(
        topTags: List<String>,
    ): List<MixGenerator.DiscoveryCandidate> {
        val out = mutableListOf<MixGenerator.DiscoveryCandidate>()
        for (tag in topTags.take(10)) {
            val tracks = apiClient.getTagTopTracks(tag, 30).getOrNull().orEmpty()
            tracks.forEach {
                out += MixGenerator.DiscoveryCandidate(it.artist, it.title, "tag:$tag")
            }
            delay(intervalMs)
        }
        return out
    }

    private suspend fun generateTrackSimilar(
        seedTracks: List<Pair<String, String>>,
    ): List<MixGenerator.DiscoveryCandidate> {
        val out = mutableListOf<MixGenerator.DiscoveryCandidate>()
        for ((artist, title) in seedTracks.take(10)) {
            val similar = apiClient.getSimilarTracks(artist, title, 10).getOrNull().orEmpty()
            similar.forEach {
                out += MixGenerator.DiscoveryCandidate(it.artist, it.title, "$artist - $title")
            }
            delay(intervalMs)
        }
        return out
    }
}
