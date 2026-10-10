// EqualizerViewModel.kt
package com.stash.feature.settings.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.core.data.audio.LoudnessProgressStore
import com.stash.core.media.equalizer.EqController
import com.stash.core.media.equalizer.LoudnessController
import com.stash.core.media.equalizer.NamedPreset
import com.stash.core.media.equalizer.PresetCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EqUiState(
  val enabled: Boolean = false,
  val gainsDb: FloatArray = FloatArray(5),
  val preampDb: Float = 0f,
  val bassBoostDb: Float = 0f,
  val activePresetId: String = "flat",
  val allPresets: List<NamedPreset> = PresetCatalog.builtIn,
) {
  // FloatArray uses reference equality by default, so override with contentEquals.
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is EqUiState) return false
    return enabled == other.enabled &&
      gainsDb.contentEquals(other.gainsDb) &&
      preampDb == other.preampDb &&
      bassBoostDb == other.bassBoostDb &&
      activePresetId == other.activePresetId &&
      allPresets == other.allPresets
  }
  override fun hashCode(): Int {
    var r = enabled.hashCode()
    r = 31 * r + gainsDb.contentHashCode()
    r = 31 * r + preampDb.hashCode()
    r = 31 * r + bassBoostDb.hashCode()
    r = 31 * r + activePresetId.hashCode()
    r = 31 * r + allPresets.hashCode()
    return r
  }
}

/**
 * UI projection of [LoudnessController.state] + [LoudnessProgressStore.flow].
 *
 * Exposed to the EqualizerScreen's loudness card so it can render an
 * enable/disable toggle and a progress bar while the backfill worker
 * measures LUFS for the un-measured backlog.
 */
data class LoudnessUiState(
  val enabled: Boolean = true,
  val backfillRemaining: Int = 0,
  val backfillTotal: Int = 0,
)

@HiltViewModel
class EqualizerViewModel @Inject constructor(
  private val controller: EqController,
  private val loudnessController: LoudnessController,
  private val loudnessProgressStore: LoudnessProgressStore,
  private val loudnessFirstRunStore: LoudnessFirstRunStore,
) : ViewModel() {
  val uiState: StateFlow<EqUiState> = controller.state.map { s ->
    EqUiState(
      enabled = s.enabled,
      gainsDb = s.gainsDb,
      preampDb = s.preampDb,
      bassBoostDb = s.bassBoostDb,
      activePresetId = s.presetId,
      allPresets = PresetCatalog.allFor(s.customPresets),
    )
  }.stateIn(viewModelScope, SharingStarted.Eagerly, EqUiState())

  val loudnessUiState: StateFlow<LoudnessUiState> = combine(
    loudnessController.state,
    loudnessProgressStore.flow,
  ) { settings, progress ->
    LoudnessUiState(
      enabled = settings.enabled,
      backfillRemaining = progress.remaining,
      backfillTotal = progress.total,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoudnessUiState())

  /**
   * Emits `true` while the one-time "loudness normalization is on" Snackbar
   * has not yet been shown/dismissed by the user. Backed by
   * [LoudnessFirstRunStore] so the notice survives process restart but
   * disappears forever after the first acknowledgement.
   */
  val showFirstRunNotice: StateFlow<Boolean> = loudnessFirstRunStore.noticeShownFlow
    .map { !it }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

  fun onToggle(enabled: Boolean) = controller.setEnabled(enabled)
  fun onBandChanged(band: Int, dB: Float) = controller.setBandGain(band, dB)
  fun onPreampChanged(dB: Float) = controller.setPreampDb(dB)
  fun onBassBoostChanged(dB: Float) = controller.setBassBoostDb(dB)
  fun onPresetSelected(id: String) = controller.setPreset(id)
  fun onSaveCurrentPreset(name: String) = controller.saveCurrentAsPreset(name)
  fun onDeletePreset(id: String) = controller.deleteCustomPreset(id)

  fun onLoudnessToggle(enabled: Boolean) = loudnessController.setEnabled(enabled)

  /**
   * Marks the first-run notice as shown so it never re-appears. Called both
   * from the Snackbar dismiss/action callbacks and as a side-effect of the
   * user interacting with the loudness toggle (interaction implies
   * acknowledgement, no separate dismiss needed).
   */
  fun onFirstRunNoticeDismissed() {
    viewModelScope.launch { loudnessFirstRunStore.markShown() }
  }
}
