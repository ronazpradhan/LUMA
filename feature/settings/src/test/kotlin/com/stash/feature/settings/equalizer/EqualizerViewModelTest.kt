// EqualizerViewModelTest.kt
package com.stash.feature.settings.equalizer

import com.google.common.truth.Truth.assertThat
import com.stash.core.data.audio.LoudnessProgressStore
import com.stash.core.media.equalizer.EqController
import com.stash.core.media.equalizer.EqState
import com.stash.core.media.equalizer.LoudnessController
import com.stash.core.media.equalizer.LoudnessState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EqualizerViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before fun setUp() { Dispatchers.setMain(dispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private val state = MutableStateFlow(EqState())
  private val ctrl = mockk<EqController>(relaxed = true).also {
    every { it.state } returns state
  }

  private val loudnessState = MutableStateFlow(LoudnessState())
  private val loudnessCtrl = mockk<LoudnessController>(relaxed = true).also {
    every { it.state } returns loudnessState
  }

  private val progressFlow = MutableStateFlow(LoudnessProgressStore.Snapshot())
  private val progressStore = mockk<LoudnessProgressStore>(relaxed = true).also {
    every { it.flow } returns progressFlow
  }

  private val firstRunFlow = MutableStateFlow(false)
  private val firstRunStore = mockk<LoudnessFirstRunStore>(relaxed = true).also {
    every { it.noticeShownFlow } returns firstRunFlow
  }

  private fun newVm() = EqualizerViewModel(ctrl, loudnessCtrl, progressStore, firstRunStore)

  @Test fun `state flow forwards to UI state`() = runTest {
    val vm = newVm()
    state.value = EqState(enabled = true, presetId = "rock")
    advanceUntilIdle()
    assertThat(vm.uiState.value.enabled).isTrue()
    assertThat(vm.uiState.value.activePresetId).isEqualTo("rock")
  }

  @Test fun `onToggle calls controller setEnabled`() {
    val vm = newVm()
    vm.onToggle(true)
    verify { ctrl.setEnabled(true) }
  }

  @Test fun `onBandChanged calls controller setBandGain`() {
    val vm = newVm()
    vm.onBandChanged(2, 4.5f)
    verify { ctrl.setBandGain(2, 4.5f) }
  }

  @Test fun `loudness ui state combines controller and progress store`() = runTest {
    val vm = newVm()
    loudnessState.value = LoudnessState(enabled = false)
    progressFlow.value = LoudnessProgressStore.Snapshot(remaining = 7, total = 20)
    // WhileSubscribed flow needs an active collector to emit; .first() subscribes.
    val emitted = vm.loudnessUiState.first { it.backfillTotal == 20 }
    assertThat(emitted.enabled).isFalse()
    assertThat(emitted.backfillRemaining).isEqualTo(7)
    assertThat(emitted.backfillTotal).isEqualTo(20)
  }

  @Test fun `onLoudnessToggle calls loudness controller setEnabled`() {
    val vm = newVm()
    vm.onLoudnessToggle(false)
    verify { loudnessCtrl.setEnabled(false) }
  }
}
