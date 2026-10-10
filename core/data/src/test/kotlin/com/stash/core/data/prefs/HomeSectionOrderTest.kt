package com.stash.core.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Merge semantics for the saved Home section order (see [resolveHomeSectionOrder]). */
class HomeSectionOrderTest {

    /** Every section LUMA still shows, in default order (the Qobuz rows are retired). */
    private val live = HomeSection.entries - RETIRED_HOME_SECTIONS

    @Test
    fun `empty saved order yields the default layout`() {
        assertEquals(live, resolveHomeSectionOrder(emptyList()))
    }

    @Test
    fun `retired qobuz sections never come back, saved or not`() {
        val saved = listOf("new_releases", "radios", "qobuz_playlists", "top_albums")
        val resolved = resolveHomeSectionOrder(saved)
        RETIRED_HOME_SECTIONS.forEach { assertFalse(it in resolved) }
        assertFalse(RETIRED_HOME_SECTIONS.any { it in DEFAULT_HOME_SECTIONS })
    }

    @Test
    fun `saved permutation is honored`() {
        val saved = listOf("made_for_you", "radios", "mood_decades", "youtube_feed")
        assertEquals(
            listOf(
                HomeSection.MADE_FOR_YOU, HomeSection.RADIOS, HomeSection.MOOD_DECADES, HomeSection.YOUTUBE_FEED,
                // appended: added after this arrangement was saved.
                HomeSection.YOUR_PLAYLISTS, HomeSection.COMMUNITY,
            ),
            resolveHomeSectionOrder(saved),
        )
    }

    @Test
    fun `unknown keys are dropped and missing sections appended in default order`() {
        val saved = listOf("radios", "gone_section", "mood_decades")
        assertEquals(
            listOf(
                HomeSection.RADIOS, HomeSection.MOOD_DECADES,
                // appended in default order:
                HomeSection.YOUR_PLAYLISTS, HomeSection.YOUTUBE_FEED,
                HomeSection.MADE_FOR_YOU, HomeSection.COMMUNITY,
            ),
            resolveHomeSectionOrder(saved),
        )
    }

    @Test
    fun `duplicate keys keep first occurrence`() {
        val saved = listOf("radios", "radios", "made_for_you")
        val result = resolveHomeSectionOrder(saved)
        assertEquals(live.size, result.size)
        assertEquals(HomeSection.RADIOS, result[0])
        assertEquals(HomeSection.MADE_FOR_YOU, result[1])
    }

    @Test
    fun `your playlists leads the default order, the youtube music feed right after`() {
        assertEquals(
            listOf(HomeSection.YOUR_PLAYLISTS, HomeSection.YOUTUBE_FEED),
            resolveHomeSectionOrder(emptyList()).take(2),
        )
    }

    @Test
    fun `saved order without your playlists appends it, preserving the arrangement`() {
        val saved = listOf("made_for_you", "radios")
        val resolved = resolveHomeSectionOrder(saved)
        assertEquals(HomeSection.MADE_FOR_YOU, resolved[0])
        assertEquals(HomeSection.RADIOS, resolved[1])
        // New-in-an-update section can't be hidden by a stale pref: appended.
        assertTrue(HomeSection.YOUR_PLAYLISTS in resolved)
        assertTrue(resolved.indexOf(HomeSection.YOUR_PLAYLISTS) >= 2)
    }

    @Test
    fun `community is off by default, including right after the update that adds it`() {
        assertFalse(HomeSection.COMMUNITY in visibleHomeSections(resolveHomeSectionOrder(emptyList()), emptySet(), communityOn = false))
        val savedBeforeTheUpdate = listOf("your_playlists", "youtube_feed", "made_for_you", "radios", "mood_decades")
        assertEquals(
            savedBeforeTheUpdate.mapNotNull(HomeSection::fromKey),
            visibleHomeSections(resolveHomeSectionOrder(savedBeforeTheUpdate), emptySet(), communityOn = false),
        )
        // Home before the preference loads, and the preference's fallback when it can't be read.
        assertFalse(HomeSection.COMMUNITY in DEFAULT_HOME_SECTIONS)
    }

    @Test
    fun `turned on, community shows where it sits in the order`() {
        val order = withCommunityFirst(resolveHomeSectionOrder(emptyList()))
        assertEquals(HomeSection.COMMUNITY, visibleHomeSections(order, emptySet(), communityOn = true).first())
        assertEquals(order - HomeSection.COMMUNITY, visibleHomeSections(order, emptySet(), communityOn = false))
    }

    @Test
    fun `withCommunityFirst moves community to the top and keeps the rest in order`() {
        val order = resolveHomeSectionOrder(listOf("radios", "top_albums"))
        val moved = withCommunityFirst(order)
        assertEquals(HomeSection.COMMUNITY, moved.first())
        assertEquals(order - HomeSection.COMMUNITY, moved.drop(1))
    }
}
