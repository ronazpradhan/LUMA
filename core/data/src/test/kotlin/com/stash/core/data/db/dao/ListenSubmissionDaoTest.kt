package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.ListenSubmissionEntity
import com.stash.core.data.db.entity.ListeningEventEntity
import com.stash.core.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Per-destination scrobble queue. Replaces the boolean-column-per-destination
 * pattern (`scrobbled` for Last.fm, `yt_scrobbled` for YouTube history) that would
 * have needed a third column plus a third DAO triplet for ListenBrainz.
 *
 * The behaviours worth locking are the ones that are easy to get wrong and
 * expensive when wrong: destinations must not see each other's state, a newly
 * connected destination must not inherit the user's whole history, and a listen a
 * destination will never accept must eventually stop being retried.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ListenSubmissionDaoTest {

    private lateinit var db: StashDatabase
    private lateinit var dao: ListenSubmissionDao
    private lateinit var events: ListeningEventDao

    private var oldEventId = 0L
    private var newEventId = 0L

    private val connectedAt = 1_000_000L

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = db.listenSubmissionDao()
        events = db.listeningEventDao()

        val trackId = db.trackDao().insert(
            TrackEntity(
                title = "Weird Fishes",
                artist = "Radiohead",
                canonicalTitle = "weird fishes",
                canonicalArtist = "radiohead",
            ),
        )
        oldEventId = events.insert(
            ListeningEventEntity(trackId = trackId, startedAt = connectedAt - 5_000L),
        )
        newEventId = events.insert(
            ListeningEventEntity(trackId = trackId, startedAt = connectedAt + 5_000L),
        )
    }

    @After fun tearDown() { db.close() }

    @Test fun `an unattempted listen after the cutoff is pending`() = runTest {
        val pending = dao.pendingFor("listenbrainz", sinceMs = connectedAt).map { it.id }
        assertThat(pending).contains(newEventId)
    }

    /**
     * The history-flood guard. Connecting a destination must not hand it every
     * play the user has ever made — that submits thousands of listens nobody
     * asked for. Deliberate backfill stays a separate, explicit action.
     */
    @Test fun `listens from before the cutoff are never pending`() = runTest {
        val pending = dao.pendingFor("listenbrainz", sinceMs = connectedAt).map { it.id }
        assertThat(pending).doesNotContain(oldEventId)
    }

    @Test fun `a sent listen stops being pending`() = runTest {
        dao.markSent(newEventId, "listenbrainz", nowMs = connectedAt)

        assertThat(dao.pendingFor("listenbrainz", sinceMs = connectedAt)).isEmpty()
        assertThat(dao.pendingCountFor("listenbrainz", sinceMs = connectedAt).first()).isEqualTo(0)
    }

    /** Destinations are independent: sending to one must not satisfy another. */
    @Test fun `marking one destination leaves the other pending`() = runTest {
        dao.markSent(newEventId, "listenbrainz", nowMs = connectedAt)

        assertThat(dao.pendingFor("lastfm", sinceMs = connectedAt).map { it.id })
            .contains(newEventId)
    }

    @Test fun `a failed listen is retried and counts its attempts`() = runTest {
        dao.markFailed(newEventId, "listenbrainz", nowMs = connectedAt)

        assertThat(dao.pendingFor("listenbrainz", sinceMs = connectedAt).map { it.id })
            .contains(newEventId)
        assertThat(dao.rowFor(newEventId, "listenbrainz")?.attempts).isEqualTo(1)

        dao.markFailed(newEventId, "listenbrainz", nowMs = connectedAt)
        assertThat(dao.rowFor(newEventId, "listenbrainz")?.attempts).isEqualTo(2)
    }

    /** A listen the destination will never accept must not be retried forever. */
    @Test fun `a listen stops being retried once attempts are exhausted`() = runTest {
        repeat(ListenSubmissionEntity.MAX_ATTEMPTS) {
            dao.markFailed(newEventId, "listenbrainz", nowMs = connectedAt)
        }

        assertThat(dao.pendingFor("listenbrainz", sinceMs = connectedAt)).isEmpty()
        assertThat(dao.exhaustedCount("listenbrainz")).isEqualTo(1)
    }

    /** Success after failures clears the attempt count rather than accumulating. */
    @Test fun `a later success resets a failed row`() = runTest {
        dao.markFailed(newEventId, "listenbrainz", nowMs = connectedAt)
        dao.markSent(newEventId, "listenbrainz", nowMs = connectedAt)

        val row = dao.rowFor(newEventId, "listenbrainz")
        assertThat(row?.state).isEqualTo(ListenSubmissionEntity.SENT)
        assertThat(row?.attempts).isEqualTo(0)
    }

    @Test fun `disconnecting a destination forgets only its own state`() = runTest {
        dao.markSent(newEventId, "listenbrainz", nowMs = connectedAt)
        dao.markSent(newEventId, "lastfm", nowMs = connectedAt)

        assertThat(dao.clearTarget("listenbrainz")).isEqualTo(1)
        assertThat(dao.rowFor(newEventId, "listenbrainz")).isNull()
        assertThat(dao.rowFor(newEventId, "lastfm")).isNotNull()
    }

    /** Deleting the listen must not leave orphaned submission rows behind. */
    @Test fun `submission rows cascade when the listening event is deleted`() = runTest {
        dao.markSent(newEventId, "listenbrainz", nowMs = connectedAt)

        db.openHelper.writableDatabase.execSQL("DELETE FROM listening_events WHERE id = $newEventId")

        assertThat(dao.rowFor(newEventId, "listenbrainz")).isNull()
    }
}
