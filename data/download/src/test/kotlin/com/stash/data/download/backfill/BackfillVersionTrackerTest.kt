package com.stash.data.download.backfill

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.stash.core.common.AppVersionProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * DataStore-backed unit tests for [BackfillVersionTracker].
 *
 * Mirrors the [MetadataBackfillStateTest] isolation pattern (Robolectric +
 * ApplicationProvider). Because [BackfillVersionTracker] has no public
 * "reset" inverse for `markEnqueuedForCurrentVersion`, `@Before` resets
 * the shared `metadata_backfill_state` DataStore by editing it directly
 * — `it.clear()` nukes every key (including `KEY_ENQUEUED_VERSION` from
 * this class plus the four progress keys owned by
 * [MetadataBackfillState]) so each test starts from a known-empty
 * baseline regardless of the cached singleton DataStore instance held by
 * the top-level property delegate.
 */
@RunWith(RobolectricTestRunner::class)
class BackfillVersionTrackerTest {

    private lateinit var context: Context
    private val versionProvider = object : AppVersionProvider {
        override val versionName: String = "0.9.35"
        override val versionCode: Int = 71
    }
    private lateinit var subject: BackfillVersionTracker

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Wipe any prior DataStore state from earlier test runs. The
        // property delegate caches a single DataStore per file per
        // process, so file deletion alone isn't enough — we additionally
        // clear every key through the live instance.
        context.filesDir.resolve("datastore/metadata_backfill_state.preferences_pb")
            .delete()
        runBlocking {
            context.backfillDataStore.edit { it.clear() }
        }
        subject = BackfillVersionTracker(context, versionProvider)
    }

    @After
    fun tearDown() {
        runBlocking {
            context.backfillDataStore.edit { it.clear() }
        }
    }

    @Test
    fun `runs for current version when never run`() = runTest {
        assertTrue(subject.shouldRunForCurrentVersion(METADATA_KEY))
    }

    @Test
    fun `markEnqueued then shouldRun returns false at same version`() = runTest {
        subject.markEnqueuedForCurrentVersion(METADATA_KEY)
        assertFalse(subject.shouldRunForCurrentVersion(METADATA_KEY))
    }

    @Test
    fun `bumping version re-arms the tracker`() = runTest {
        subject.markEnqueuedForCurrentVersion(METADATA_KEY)
        val newerProvider = object : AppVersionProvider {
            override val versionName: String = "0.9.36"
            override val versionCode: Int = 72
        }
        val subject2 = BackfillVersionTracker(context, newerProvider)
        assertTrue(subject2.shouldRunForCurrentVersion(METADATA_KEY))
    }

    @Test
    fun `two distinct keys do not interfere`() = runTest {
        val tracker = BackfillVersionTracker(context, fakeAppVersion(versionCode = 100))

        // Mark metadata enqueued for current version
        tracker.markEnqueuedForCurrentVersion("backfill_enqueued_for_version")
        assertFalse(tracker.shouldRunForCurrentVersion("backfill_enqueued_for_version"))

        // Lyrics key should still report should-run
        assertTrue(tracker.shouldRunForCurrentVersion("lyrics_backfill_enqueued_for_version"))

        // Mark lyrics; metadata stays marked
        tracker.markEnqueuedForCurrentVersion("lyrics_backfill_enqueued_for_version")
        assertFalse(tracker.shouldRunForCurrentVersion("backfill_enqueued_for_version"))
        assertFalse(tracker.shouldRunForCurrentVersion("lyrics_backfill_enqueued_for_version"))
    }

    private fun fakeAppVersion(versionCode: Int): AppVersionProvider =
        object : AppVersionProvider {
            override val versionName: String = "fake"
            override val versionCode: Int = versionCode
        }

    private companion object {
        const val METADATA_KEY = "backfill_enqueued_for_version"
    }
}
