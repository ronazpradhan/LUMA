package com.stash.core.media.service

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Pins [idleStopTicker] — the service-owned idle-shutdown countdown.
 *
 * The battery contract: a paused (or ended, or errored-out) player must not
 * keep the service — and with it the whole process — alive forever. The
 * previous attempt released the MediaController from the repository side and
 * was reverted after device testing: it made the repo deaf to media-button
 * playback and silently lost scrobbles. This ticker lives in the SERVICE, so
 * the service stops itself and the repository merely reacts.
 *
 * Emissions repeat every [timeoutMs] while idle persists, so a stop attempt
 * that gets skipped (e.g. a bound Android Auto client keeps the service
 * alive) is retried instead of never firing again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IdleStopTickerTest {

    private val timeout = 5 * 60_000L

    @Test
    fun `no emission before the timeout elapses`() = runTest {
        val idle = MutableStateFlow(true)
        var fires = 0

        backgroundScope.launch { idleStopTicker(idle, timeout).collect { fires++ } }
        runCurrent()
        advanceTimeBy(timeout - 1)
        runCurrent()

        assertThat(fires).isEqualTo(0)
    }

    @Test
    fun `fires once the player has been idle for the full timeout`() = runTest {
        val idle = MutableStateFlow(true)
        var fires = 0

        backgroundScope.launch { idleStopTicker(idle, timeout).collect { fires++ } }
        runCurrent()
        advanceTimeBy(timeout)
        runCurrent()

        assertThat(fires).isEqualTo(1)
    }

    @Test
    fun `keeps firing every timeout while idle persists`() = runTest {
        // A skipped stop (bound client) must be retried, not abandoned.
        val idle = MutableStateFlow(true)
        var fires = 0

        backgroundScope.launch { idleStopTicker(idle, timeout).collect { fires++ } }
        runCurrent()
        advanceTimeBy(3 * timeout)
        runCurrent()

        assertThat(fires).isEqualTo(3)
    }

    @Test
    fun `never fires while playing`() = runTest {
        val idle = MutableStateFlow(false)
        var fires = 0

        backgroundScope.launch { idleStopTicker(idle, timeout).collect { fires++ } }
        runCurrent()
        advanceTimeBy(24 * 60 * 60_000L)
        runCurrent()

        assertThat(fires).isEqualTo(0)
    }

    @Test
    fun `resuming playback cancels the countdown`() = runTest {
        val idle = MutableStateFlow(true)
        var fires = 0

        backgroundScope.launch { idleStopTicker(idle, timeout).collect { fires++ } }
        runCurrent()
        advanceTimeBy(timeout - 1_000L)
        idle.value = false
        runCurrent()
        advanceTimeBy(24 * 60 * 60_000L)
        runCurrent()

        assertThat(fires).isEqualTo(0)
    }

    @Test
    fun `a pause interrupted by playback restarts the countdown from zero`() = runTest {
        val idle = MutableStateFlow(true)
        var fires = 0

        backgroundScope.launch { idleStopTicker(idle, timeout).collect { fires++ } }
        runCurrent()
        advanceTimeBy(timeout - 1_000L)
        idle.value = false
        runCurrent()
        idle.value = true
        runCurrent()
        advanceTimeBy(timeout - 1_000L)
        runCurrent()

        assertThat(fires).isEqualTo(0)

        advanceTimeBy(1_000L)
        runCurrent()
        assertThat(fires).isEqualTo(1)
    }

    @Test
    fun `repeated identical idle values do not restart the countdown`() = runTest {
        // The service listener re-publishes state on unrelated events;
        // re-emitting `true` must not push the deadline out.
        val idle = MutableStateFlow(true)
        var fires = 0

        backgroundScope.launch { idleStopTicker(idle, timeout).collect { fires++ } }
        runCurrent()
        advanceTimeBy(timeout / 2)
        idle.value = true
        runCurrent()
        advanceTimeBy(timeout / 2)
        runCurrent()

        assertThat(fires).isEqualTo(1)
    }
}

class IsPlayerIdleTest {

    @Test
    fun `paused is idle`() {
        assertThat(isPlayerIdle(playWhenReady = false, playbackState = Player.STATE_READY)).isTrue()
    }

    @Test
    fun `playing is not idle`() {
        assertThat(isPlayerIdle(playWhenReady = true, playbackState = Player.STATE_READY)).isFalse()
    }

    @Test
    fun `buffering with intent to play is not idle`() {
        // isPlaying=false during a rebuffer must NOT arm the countdown —
        // only the user's intent (playWhenReady) and terminal states count.
        assertThat(isPlayerIdle(playWhenReady = true, playbackState = Player.STATE_BUFFERING)).isFalse()
    }

    @Test
    fun `queue ended is idle even though playWhenReady stays true`() {
        assertThat(isPlayerIdle(playWhenReady = true, playbackState = Player.STATE_ENDED)).isTrue()
    }

    @Test
    fun `error-idled player is idle even though playWhenReady stays true`() {
        assertThat(isPlayerIdle(playWhenReady = true, playbackState = Player.STATE_IDLE)).isTrue()
    }
}
