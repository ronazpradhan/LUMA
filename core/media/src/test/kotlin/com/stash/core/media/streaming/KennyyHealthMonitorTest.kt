package com.stash.core.media.streaming

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KennyyHealthMonitorTest {

    @Test
    fun newMonitorIsHealthy() = runTest {
        val monitor = KennyyHealthMonitor()
        assertThat(monitor.isHealthy.value).isTrue()
    }

    @Test
    fun threeFailuresInWindowMarksUnhealthy() = runTest {
        val monitor = KennyyHealthMonitor()
        repeat(3) { monitor.recordFailure() }
        assertThat(monitor.isHealthy.value).isFalse()
    }

    @Test
    fun twoFailuresInWindowStaysHealthy() = runTest {
        val monitor = KennyyHealthMonitor()
        repeat(2) { monitor.recordFailure() }
        assertThat(monitor.isHealthy.value).isTrue()
    }

    @Test
    fun threeSuccessesAfterUnhealthyRestoresHealth() = runTest {
        val monitor = KennyyHealthMonitor()
        repeat(3) { monitor.recordFailure() }
        assertThat(monitor.isHealthy.value).isFalse()
        repeat(3) { monitor.recordSuccess() }
        assertThat(monitor.isHealthy.value).isTrue()
    }

    @Test
    fun noMatchDoesNotCountAsFailure() = runTest {
        val monitor = KennyyHealthMonitor()
        repeat(10) { monitor.recordNoMatch() }
        assertThat(monitor.isHealthy.value).isTrue()
    }

    @Test
    fun windowSlidesAfterFiveOutcomes() = runTest {
        val monitor = KennyyHealthMonitor()
        repeat(3) { monitor.recordFailure() }
        assertThat(monitor.isHealthy.value).isFalse()
        repeat(5) { monitor.recordSuccess() }
        assertThat(monitor.isHealthy.value).isTrue()
    }
}
