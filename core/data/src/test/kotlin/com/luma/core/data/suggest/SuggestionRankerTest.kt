package com.luma.core.data.suggest

import com.google.common.truth.Truth.assertThat
import com.luma.data.ytmusic.model.TrackSummary
import org.junit.Test
import kotlin.random.Random

class SuggestionRankerTest {

    private fun song(id: String, artist: String) = TrackSummary(id, "Song $id", artist, null, 200.0, null)

    private fun seed(id: String, artist: String, plays: Int = 1, liked: Boolean = false) =
        SeedCandidate(id, "Seed $id", artist, "${artist.lowercase()}|seed $id", plays, liked)

    @Test fun `seeds are one per artist and skip excluded songs`() {
        val recent = listOf(
            seed("a1", "Alpha"), seed("a2", "Alpha"), seed("b1", "Beta"), seed("c1", "Gamma"),
        )
        val seeds = SuggestionRanker.pickSeeds(
            recent = recent,
            mostPlayed = emptyList(),
            excludedKeys = setOf("gamma|seed c1"),
            count = 4,
            random = Random(1),
        )
        assertThat(seeds.map { it.artist }).containsNoDuplicates()
        assertThat(seeds.map { it.videoId }).doesNotContain("c1")
        assertThat(seeds).hasSize(2)
    }

    @Test fun `songs several seeds lead to rank first`() {
        val shared = song("s", "Shared")
        val radios = listOf(
            1.0 to listOf(song("seed1", "X"), song("only1", "One"), shared),
            1.0 to listOf(song("seed2", "Y"), song("only2", "Two"), shared),
        )
        val ranked = SuggestionRanker.rank(radios, isExcluded = { false }, limit = 10, random = Random(3))
        assertThat(ranked.first()).isEqualTo(shared)
        // A radio's first row is its seed and never comes back as a suggestion.
        assertThat(ranked.map { it.videoId }).containsNoneOf("seed1", "seed2")
    }

    @Test fun `known songs are dropped and artists are capped and spread`() {
        val radio = listOf(song("seed", "S")) +
            (1..5).map { song("m$it", "Many") } +
            listOf(song("o1", "Other"), song("known", "Other2"))
        val ranked = SuggestionRanker.rank(
            radios = listOf(1.0 to radio),
            isExcluded = { it.videoId == "known" },
            limit = 10,
            random = Random(7),
        )
        assertThat(ranked.map { it.videoId }).doesNotContain("known")
        assertThat(ranked.count { it.artist == "Many" }).isEqualTo(2)
        ranked.zipWithNext().forEach { (a, b) -> assertThat(a.artist).isNotEqualTo(b.artist) }
    }
}
