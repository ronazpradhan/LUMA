package com.luma.feature.library.share

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.core.data.share.SharedMixDocument
import com.luma.core.data.share.SharedMixRepository
import com.luma.core.media.PlayerRepository
import com.luma.core.model.PlaybackSource
import com.luma.core.model.share.ShareLinks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface SharedMixUiState {
    data class Error(val message: String) : SharedMixUiState
    data class Loaded(
        val doc: SharedMixDocument,
        val busy: Boolean = false,
        /** A short error from the last action (Play, Save…), shown under the buttons. */
        val message: String? = null,
    ) : SharedMixUiState
}

/**
 * LUMA: the screen a shared playlist link opens. The whole playlist is inside the link, so it
 * opens at once, offline too, and nothing is fetched or followed.
 */
@HiltViewModel
class SharedMixViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SharedMixRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        when (val parsed = ShareLinks.parse(savedStateHandle.get<String>("link"))) {
            is ShareLinks.Parsed.Playlist -> SharedMixUiState.Loaded(
                SharedMixDocument(name = parsed.playlist.name, sharedBy = parsed.playlist.sharedBy, covers = parsed.playlist.covers, tracks = parsed.playlist.tracks),
            )
            else -> SharedMixUiState.Error("This playlist link is damaged, or from a newer LUMA. Update LUMA and open it again.")
        },
    )
    val state: StateFlow<SharedMixUiState> = _state

    /** Runs one action on the playlist; a failure (DB, IO) becomes a message, never a crash. */
    private fun withLoaded(action: String, block: suspend (SharedMixUiState.Loaded) -> Unit) {
        val s = _state.value as? SharedMixUiState.Loaded ?: return
        if (s.busy) return
        _state.value = s.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                block(s.copy(message = null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$action failed", e)
                (_state.value as? SharedMixUiState.Loaded)?.let { _state.value = it.copy(message = "Couldn't $action this playlist. Try again.") }
            } finally {
                (_state.value as? SharedMixUiState.Loaded)?.let { _state.value = it.copy(busy = false) }
            }
        }
    }

    fun play() = withLoaded("play") { s ->
        val tracks = repository.tracksFor(s.doc)
        if (tracks.isNotEmpty()) playerRepository.setQueue(tracks, 0, PlaybackSource.Unknown)
    }

    /** Saves the playlist as your own, editable copy, then [onSaved] opens it. */
    fun saveCopy(onSaved: (Long) -> Unit) = withLoaded("save") { s -> onSaved(repository.saveCopy(s.doc)) }

    private companion object {
        const val TAG = "SharedMixVM"
    }
}
