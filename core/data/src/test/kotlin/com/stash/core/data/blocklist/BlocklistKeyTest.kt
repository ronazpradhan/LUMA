package com.stash.core.data.blocklist

import com.stash.core.data.sync.TrackMatcher
import org.junit.Assert.assertEquals
import org.junit.Test

class BlocklistKeyTest {
    private val matcher = TrackMatcher()

    @Test
    fun `canonical key combines artist and title with pipe separator`() {
        val key = BlocklistKey.of("Arctic Monkeys", "505", matcher)
        assertEquals("arctic monkeys|505", key)
    }

    @Test
    fun `canonical key strips parenthetical version suffixes from titles`() {
        val key = BlocklistKey.of("Arctic Monkeys", "505 (Remastered)", matcher)
        assertEquals("arctic monkeys|505", key)
    }

    @Test
    fun `canonical key strips feat-style suffixes from titles`() {
        val key = BlocklistKey.of("Drake", "Way 2 Sexy feat. Future", matcher)
        assertEquals("drake|way 2 sexy", key)
    }

    @Test
    fun `canonical key normalises multi-artist ordering`() {
        // canonicalArtist splits on common separators, lowercases each
        // chunk, sorts alphabetically, and rejoins with ", " — so the same
        // pair of artists in different orders collapses to one identity.
        val key1 = BlocklistKey.of("Drake & Future", "Way 2 Sexy", matcher)
        val key2 = BlocklistKey.of("Future, Drake", "Way 2 Sexy", matcher)
        assertEquals(key1, key2)
        assertEquals("drake, future|way 2 sexy", key1)
    }

    @Test
    fun `canonical key is case insensitive`() {
        val k1 = BlocklistKey.of("ARCTIC MONKEYS", "505", matcher)
        val k2 = BlocklistKey.of("arctic monkeys", "505", matcher)
        assertEquals(k1, k2)
    }

    @Test
    fun `fromStoredCanonicals concatenates without renormalising`() {
        // Used by the migration (which can only do SQL string concat over
        // the already-normalised canonical_artist + canonical_title columns)
        // and the integrity worker — both must agree on key shape.
        val key = BlocklistKey.fromStoredCanonicals("arctic monkeys", "505")
        assertEquals("arctic monkeys|505", key)
    }
}
