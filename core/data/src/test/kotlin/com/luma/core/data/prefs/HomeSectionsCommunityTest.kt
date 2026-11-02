package com.luma.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeSectionsCommunityTest {
    private lateinit var context: Context

    // The DataStore instance outlives a test, so deleting its file doesn't reset it: each test starts from an empty store.
    @Before fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        context.homeSectionsDataStore.edit { it.clear() }
    }

    // LUMA: Community lived on Stash's share server; LUMA has none, so Community never shows.
    @Test fun `community stays off whatever asks to turn it on`() = runTest {
        val pref = HomeSectionsPreference(context)
        assertFalse(pref.communityOn.first())
        pref.setCommunityOn(true)
        assertFalse(pref.communityOn.first())
        assertFalse(HomeSection.COMMUNITY in pref.visibleSections.first())
    }

    @Test fun `a switch left on by an older version doesn't bring it back`() = runTest {
        context.homeSectionsDataStore.edit { it[androidx.datastore.preferences.core.booleanPreferencesKey("community_on")] = true }
        val pref = HomeSectionsPreference(context)
        assertFalse(pref.communityOn.first())
        assertFalse(HomeSection.COMMUNITY in pref.visibleSections.first())
    }

}
