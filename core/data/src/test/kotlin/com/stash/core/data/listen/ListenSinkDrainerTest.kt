package com.stash.core.data.listen

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.ListenSubmissionEntity
import com.stash.core.data.db.entity.ListeningEventEntity
import com.stash.core.data.db.entity.TrackEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The drain loop's job is to be boring about the interesting cases: an outage must
 * not cost history, one malformed listen must not take its neighbours down with it,
 * and a deleted track must not be retried forever.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ListenSinkDrainerTest {

    private lateinit var db: StashDatabase
    private lateinit var drainer: ListenSinkDrainer

    private val cutoff = 1_000_000L
    private var trackId = 0L
    private val eventIds = mutableListOf<Long>()

    /** Records what it was asked to submit and answers with a scripted result. */
    private class FakeSink(
        override val id: String = "fake",
        override val maxBatchSize: Int = 50,
        private val enabled: Boolean = true,
        private val since: Long = 0L,
        private val answer: (List<Listen>) -> SinkResult = { SinkResult.Success },
    ) : ListenSink {
        val batches = mutableListOf<List<Listen>>()
        override suspend fun isEnabled() = enabled
        override suspend fun listeningSinceMs() = since
        override suspend fun submit(batch: List<Listen>): SinkResult {
            batches += batch
            return answer(batch)
        }
    }

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        ).allowMainThreadQueries().build()
        drainer = ListenSinkDrainer(db.listenSubmissionDao(), db.trackDao()) { cutoff }

        trackId = db.trackDao().insert(
            TrackEntity(
                title = "Weird Fishes",
                artist = "Radiohead",
                album = "In Rainbows",
                canonicalTitle = "weird fishes",
                canonicalArtist = "radiohead",
                durationMs = 318_000L,
            ),
        )
        repeat(3) { i ->
            eventIds += db.listeningEventDao().insert(
                ListeningEventEntity(trackId = trackId, startedAt = cutoff + i * 1_000L),
            )
        }
    }

    @After fun tearDown() { db.close() }

    @Test fun `a disabled sink is skipped without touching the queue`() = runTest {
        val sink = FakeSink(enabled = false)

        val report = drainer.drain(sink)

        assertThat(report.skipped).isTrue()
        assertThat(sink.batches).isEmpty()
    }

    @Test fun `pending listens are submitted with resolved track metadata`() = runTest {
        val sink = FakeSink(since = cutoff)

        val report = drainer.drain(sink)

        assertThat(report.submitted).isEqualTo(3)
        val submitted = sink.batches.single()
        assertThat(submitted.map { it.eventId }).containsExactlyElementsIn(eventIds)
        assertThat(submitted.first().artist).isEqualTo("Radiohead")
        assertThat(submitted.first().album).isEqualTo("In Rainbows")
        assertThat(submitted.first().durationMs).isEqualTo(318_000L)
    }

    @Test fun `a drained listen is not offered twice`() = runTest {
        val sink = FakeSink(since = cutoff)

        drainer.drain(sink)
        val second = drainer.drain(sink)

        assertThat(second.submitted).isEqualTo(0)
        assertThat(sink.batches).hasSize(1)
    }

    /**
     * The important one. A service outage must leave the listens exactly as they
     * were — if a transient failure consumed retries, a destination down for long
     * enough would silently discard the user's history.
     */
    @Test fun `a transient failure holds listens without consuming retries`() = runTest {
        val sink = FakeSink(since = cutoff, answer = { SinkResult.Transient("503") })

        val report = drainer.drain(sink)

        assertThat(report.submitted).isEqualTo(0)
        assertThat(report.failed).isEqualTo(0)
        eventIds.forEach { assertThat(db.listenSubmissionDao().rowFor(it, "fake")).isNull() }
        // Still pending, so a later drain retries them.
        assertThat(drainer.drain(sink).submitted).isEqualTo(0)
        assertThat(sink.batches).hasSize(2)
    }

    /**
     * A rejected batch is re-submitted one listen at a time, so a single bad row
     * cannot burn the retries of everything queued beside it.
     */
    @Test fun `a rejected batch is split so only the offender fails`() = runTest {
        val poison = eventIds[1]
        val sink = FakeSink(
            since = cutoff,
            answer = { batch ->
                when {
                    batch.size > 1 -> SinkResult.Rejected("bad payload")
                    batch.single().eventId == poison -> SinkResult.Rejected("unmappable")
                    else -> SinkResult.Success
                }
            },
        )

        val report = drainer.drain(sink)

        assertThat(report.submitted).isEqualTo(2)
        assertThat(report.failed).isEqualTo(1)
        val dao = db.listenSubmissionDao()
        assertThat(dao.rowFor(poison, "fake")?.state).isEqualTo(ListenSubmissionEntity.FAILED)
        assertThat(dao.rowFor(eventIds[0], "fake")?.state).isEqualTo(ListenSubmissionEntity.SENT)
        assertThat(dao.rowFor(eventIds[2], "fake")?.state).isEqualTo(ListenSubmissionEntity.SENT)
    }

    /** A sink that throws is transient, not a rejection — a crash is no evidence
     *  the listens are bad, and treating it as one would burn their retries. */
    @Test fun `a throwing sink is treated as transient`() = runTest {
        val sink = object : ListenSink {
            override val id = "boom"
            override suspend fun isEnabled() = true
            override suspend fun listeningSinceMs() = cutoff
            override suspend fun submit(batch: List<Listen>): SinkResult = error("kaboom")
        }

        val report = drainer.drain(sink)

        assertThat(report.failed).isEqualTo(0)
        eventIds.forEach { assertThat(db.listenSubmissionDao().rowFor(it, "boom")).isNull() }
    }

    /** Listens from before the cutoff stay out — the history-flood guard. */
    @Test fun `listens before the cutoff are never submitted`() = runTest {
        val old = db.listeningEventDao().insert(
            ListeningEventEntity(trackId = trackId, startedAt = cutoff - 60_000L),
        )
        val sink = FakeSink(since = cutoff)

        drainer.drain(sink)

        assertThat(sink.batches.single().map { it.eventId }).doesNotContain(old)
    }

    /**
     * A listen whose track lookup comes back null is retired, not retried forever.
     *
     * Note this is *defensive*, not a reachable state via deletion: `listening_events`
     * has ON DELETE CASCADE to `tracks`, so removing a track removes its listens too
     * and there is no orphan left to drain. (An earlier version of this test deleted
     * a track and asserted the event survived — it doesn't.) The branch guards a null
     * lookup from any cause, matching the same guard in LastFmScrobbler, so it is
     * driven here with a stubbed DAO rather than a schema state that cannot occur.
     */
    @Test fun `a listen whose track lookup returns null is retired rather than retried`() = runTest {
        val nullTrackDao = mockk<com.stash.core.data.db.dao.TrackDao>()
        coEvery { nullTrackDao.getById(any()) } returns null
        val drainerWithMissingTrack =
            ListenSinkDrainer(db.listenSubmissionDao(), nullTrackDao) { cutoff }
        val sink = FakeSink(since = cutoff)

        val report = drainerWithMissingTrack.drain(sink)

        // Nothing submitted, and every row retired so later drains skip them.
        assertThat(report.submitted).isEqualTo(0)
        assertThat(sink.batches).isEmpty()
        eventIds.forEach {
            assertThat(db.listenSubmissionDao().rowFor(it, "fake")?.state)
                .isEqualTo(ListenSubmissionEntity.SENT)
        }
    }
}
