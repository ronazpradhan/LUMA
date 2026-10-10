package com.stash.core.data.lastfm

import com.google.common.truth.Truth.assertThat
import com.stash.core.data.db.dao.ListeningEventDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.entity.ListeningEventEntity
import com.stash.core.data.db.entity.TrackEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The scrobble queue drains whenever a listen is recorded. Offline — the
 * mode a lot of this app's users actually live in — every one of those
 * submissions is doomed, and the old drain sent the WHOLE backlog anyway:
 * up to 100 HTTP requests per recorded listen, each paying DNS plus a
 * connect timeout and holding the radio awake, with no backoff, no attempt
 * ceiling and no kill switch. Twenty tracks played offline could mean
 * thousands of guaranteed-to-fail requests.
 *
 * Two rules fix that, and these tests pin them: a pass stops at its first
 * failure instead of marching through the rest, and consecutive failures
 * open the shared [LastFmRateLimitGate] so later passes skip the network
 * entirely until the cooldown elapses.
 */
class LastFmScrobblerBackoffTest {

    private val session = LastFmSession(username = "u", sessionKey = "sk")

    private fun event(id: Long) = ListeningEventEntity(
        id = id,
        trackId = id,
        startedAt = 1_000_000L + id,
        scrobbled = false,
        completedAt = 1_000_000L + id,
    )

    private fun track(id: Long) = TrackEntity(
        id = id,
        title = "t$id",
        artist = "a$id",
        album = "al",
        durationMs = 180_000,
    )

    private class Harness(
        val apiClient: LastFmApiClient = mockk(relaxed = true),
        val listeningEventDao: ListeningEventDao = mockk(relaxed = true),
        val trackDao: TrackDao = mockk(relaxed = true),
        val gate: LastFmRateLimitGate = LastFmRateLimitGate(),
    )

    private fun scrobbler(h: Harness): LastFmScrobbler {
        val sessionPreference: LastFmSessionPreference = mockk()
        every { sessionPreference.session } returns flowOf(session)
        every { sessionPreference.firstArtistOnly } returns flowOf(false)
        val credentials: LastFmCredentials = mockk()
        every { credentials.isConfigured } returns true
        return LastFmScrobbler(
            apiClient = h.apiClient,
            sessionPreference = sessionPreference,
            listeningEventDao = h.listeningEventDao,
            trackDao = h.trackDao,
            credentials = credentials,
            rateLimitGate = h.gate,
        )
    }

    @Test
    fun `a failing submission abandons the rest of the pass`() = runTest {
        val h = Harness()
        val pending = (1L..10L).map { event(it) }
        coEvery { h.listeningEventDao.pendingScrobbles(any()) } returns pending
        coEvery { h.trackDao.getById(any()) } answers { track(firstArg()) }
        coEvery {
            h.apiClient.scrobble(any(), any(), any(), any(), any())
        } returns Result.failure(java.net.UnknownHostException("offline"))

        scrobbler(h).drainNow()

        // One doomed request, not ten. Offline, the second through tenth
        // cannot succeed for any reason the first did not already prove.
        coVerify(exactly = 1) { h.apiClient.scrobble(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an open breaker skips the network entirely`() = runTest {
        val h = Harness()
        coEvery { h.listeningEventDao.pendingScrobbles(any()) } returns listOf(event(1))
        coEvery { h.trackDao.getById(any()) } answers { track(firstArg()) }
        coEvery {
            h.apiClient.scrobble(any(), any(), any(), any(), any())
        } returns Result.failure(java.net.UnknownHostException("offline"))

        val s = scrobbler(h)
        s.drainNow() // trips the breaker
        s.drainNow() // must not reach the network
        s.drainNow()

        coVerify(exactly = 1) { h.apiClient.scrobble(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a healthy pass submits the whole backlog`() = runTest {
        val h = Harness()
        val pending = (1L..10L).map { event(it) }
        coEvery { h.listeningEventDao.pendingScrobbles(any()) } returns pending
        coEvery { h.trackDao.getById(any()) } answers { track(firstArg()) }
        coEvery {
            h.apiClient.scrobble(any(), any(), any(), any(), any())
        } returns Result.success(Unit)

        scrobbler(h).drainNow()

        // Backing off must never cost a user their history when the network
        // is fine: every pending row still goes.
        coVerify(exactly = 10) { h.apiClient.scrobble(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a success after failures reopens the path`() = runTest {
        val h = Harness()
        coEvery { h.listeningEventDao.pendingScrobbles(any()) } returns listOf(event(1))
        coEvery { h.trackDao.getById(any()) } answers { track(firstArg()) }
        coEvery {
            h.apiClient.scrobble(any(), any(), any(), any(), any())
        } returns Result.failure(java.net.UnknownHostException("offline"))

        val s = scrobbler(h)
        s.drainNow()

        // Network comes back, and the gate is manually closed the way a
        // successful read elsewhere in the app would close it.
        h.gate.recordSuccess(LastFmScrobbler.SCROBBLE_GATE_KEY)
        coEvery {
            h.apiClient.scrobble(any(), any(), any(), any(), any())
        } returns Result.success(Unit)
        s.drainNow()

        coVerify(exactly = 2) { h.apiClient.scrobble(any(), any(), any(), any(), any()) }
    }
}
