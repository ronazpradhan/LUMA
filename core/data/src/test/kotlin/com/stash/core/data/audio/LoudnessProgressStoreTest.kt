package com.stash.core.data.audio

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Unit tests for [LoudnessProgressStore].
 *
 * The store wraps a Preferences DataStore so the loudness backfill worker can
 * persist remaining-work / total / last-completed-at across process restarts.
 * Each test gets its own temp datastore file to keep runs isolated.
 */
@RunWith(RobolectricTestRunner::class)
class LoudnessProgressStoreTest {

    private lateinit var store: LoudnessProgressStore
    private lateinit var file: File

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        file = ctx.preferencesDataStoreFile("loudness_progress_test")
        val ds = PreferenceDataStoreFactory.create { file }
        store = LoudnessProgressStore(ds)
    }

    @After fun tearDown() { file.delete() }

    @Test fun `flow emits zero snapshot by default`() = runBlocking {
        val snap = store.flow.first()
        assertEquals(0, snap.remaining)
        assertEquals(0, snap.total)
        assertEquals(0L, snap.lastCompletedAt)
    }

    @Test fun `setTotal updates only total`() = runBlocking {
        store.setTotal(42)
        val snap = store.flow.first()
        assertEquals(42, snap.total)
        assertEquals(0, snap.remaining)
        assertEquals(0L, snap.lastCompletedAt)
    }

    @Test fun `setRemaining updates only remaining`() = runBlocking {
        store.setRemaining(17)
        val snap = store.flow.first()
        assertEquals(17, snap.remaining)
        assertEquals(0, snap.total)
    }

    @Test fun `recordBatchComplete subtracts from remaining and stamps timestamp`() = runBlocking {
        store.setRemaining(10)
        store.recordBatchComplete(completed = 3, at = 1_700_000_000_000L)

        val snap = store.flow.first()
        assertEquals(7, snap.remaining)
        assertEquals(1_700_000_000_000L, snap.lastCompletedAt)
    }

    @Test fun `recordBatchComplete clamps remaining at zero`() = runBlocking {
        store.setRemaining(2)
        store.recordBatchComplete(completed = 5, at = 1_700_000_001_000L)

        val snap = store.flow.first()
        assertEquals(0, snap.remaining)
        assertEquals(1_700_000_001_000L, snap.lastCompletedAt)
    }
}
