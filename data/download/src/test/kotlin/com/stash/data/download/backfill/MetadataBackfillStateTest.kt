package com.stash.data.download.backfill

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * DataStore-backed unit tests for [MetadataBackfillState].
 *
 * Mirrors the Robolectric + ApplicationProvider convention established by
 * [com.stash.data.download.lossless.LosslessSourcePreferencesYoutubeFallbackTest].
 * The on-disk wipe is paired with an explicit
 * [MetadataBackfillState.markFinishedAcknowledged] reset because the
 * top-level `preferencesDataStore(...)` delegate keeps a per-process
 * cache: deleting the `.preferences_pb` file alone doesn't evict
 * in-memory state from prior tests, so we additionally reset the public
 * keys through the live instance to guarantee each test starts from
 * IDLE/0/0/0.
 */
@RunWith(RobolectricTestRunner::class)
class MetadataBackfillStateTest {

    private lateinit var context: Context
    private lateinit var subject: MetadataBackfillState

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Wipe any prior DataStore state from earlier test runs.
        context.filesDir.resolve("datastore/metadata_backfill_state.preferences_pb")
            .delete()
        subject = MetadataBackfillState(context)
        // Belt-and-braces: the property delegate keeps an in-memory cache
        // that survives file deletion, so explicitly bring every key back
        // to its default through the public API.
        runBlocking {
            subject.markStarted(0)
            subject.markFinishedAcknowledged()
        }
    }

    @Test
    fun `defaults to IDLE with zero counts`() = runTest {
        val snapshot = subject.snapshot.first()
        assertEquals(MetadataBackfillState.State.IDLE, snapshot.state)
        assertEquals(0, snapshot.processed)
        assertEquals(0, snapshot.total)
        assertEquals(0, snapshot.safSkipped)
    }

    @Test
    fun `markStarted transitions to RUNNING and sets total`() = runTest {
        subject.markStarted(50)
        val snapshot = subject.snapshot.first()
        assertEquals(MetadataBackfillState.State.RUNNING, snapshot.state)
        assertEquals(50, snapshot.total)
        assertEquals(0, snapshot.processed)
    }

    @Test
    fun `publishProgress updates processed and total`() = runTest {
        subject.markStarted(50)
        subject.publishProgress(processed = 23, total = 50)
        val snapshot = subject.snapshot.first()
        assertEquals(23, snapshot.processed)
        assertEquals(50, snapshot.total)
    }

    @Test
    fun `incrementSafSkipped accumulates`() = runTest {
        subject.markStarted(50)
        subject.incrementSafSkipped()
        subject.incrementSafSkipped()
        subject.incrementSafSkipped()
        val snapshot = subject.snapshot.first()
        assertEquals(3, snapshot.safSkipped)
    }

    @Test
    fun `markFinished transitions to FINISHED`() = runTest {
        subject.markStarted(50)
        subject.publishProgress(50, 50)
        subject.markFinished()
        val snapshot = subject.snapshot.first()
        assertEquals(MetadataBackfillState.State.FINISHED, snapshot.state)
    }

    @Test
    fun `markFinishedAcknowledged transitions back to IDLE`() = runTest {
        subject.markStarted(50)
        subject.markFinished()
        subject.markFinishedAcknowledged()
        val snapshot = subject.snapshot.first()
        assertEquals(MetadataBackfillState.State.IDLE, snapshot.state)
    }
}
