package com.luma.feature.library.share

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.core.data.repository.MusicRepository
import com.luma.core.data.social.LikeCoordinator
import com.luma.core.media.PlayerRepository
import com.luma.core.model.share.ShareLinks
import com.luma.core.model.share.SharedTrack
import com.luma.core.model.share.toTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface SharedTrackUiState {
    /** A short link's song is on its way from the share Worker. */
    data object Loading : SharedTrackUiState
    data class Loaded(val track: SharedTrack) : SharedTrackUiState
    data class Error(val message: String, val retryable: Boolean) : SharedTrackUiState
}

/** The card an incoming shared-track link opens (spec §6). */
@HiltViewModel
class SharedTrackViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository,
    private val likeCoordinator: LikeCoordinator,
) : ViewModel() {
    /** Parsed from the route's link, so the card survives process death. The link carries the song itself. */
    private val parsed = ShareLinks.parse(savedStateHandle.get<String>("link"))
    private val _state = MutableStateFlow(
        when (parsed) {
            is ShareLinks.Parsed.Track -> SharedTrackUiState.Loaded(parsed.track)
            else -> SharedTrackUiState.Error("This link has expired. Open it again.", retryable = false)
        },
    )
    val state: StateFlow<SharedTrackUiState> = _state
    private val _liked = MutableStateFlow(false)
    val liked: StateFlow<Boolean> = _liked
    private val _message = MutableStateFlow<String?>(null)
    /** A short error from the last action, shown on the card. */
    val message: StateFlow<String?> = _message

    /** The song is inside the link, so there is never anything to fetch; kept for the card's Retry. */
    fun load() = Unit

    fun play() = safely("play") { track ->
        val id = musicRepository.ensureTrackPersisted(track.toTrack())
        val t = musicRepository.observeTrackById(id).first() ?: return@safely
        _liked.value = t.lumaLikedAt != null
        playerRepository.setQueue(listOf(t), 0)
    }

    fun like() = safely("add") { track ->
        val id = musicRepository.ensureTrackPersisted(track.toTrack())
        likeCoordinator.setLiked(id, true)
        _liked.value = musicRepository.observeTrackById(id).first()?.lumaLikedAt != null
    }

    private var busy = false

    /**
     * Runs [block] on the song shown; does nothing while it's still loading. A failure (DB, IO) becomes a
     * message, never a crash. [busy] drops a double tap.
     */
    private fun safely(action: String, block: suspend (SharedTrack) -> Unit) {
        val track = (_state.value as? SharedTrackUiState.Loaded)?.track ?: return
        if (busy) return
        busy = true
        _message.value = null
        viewModelScope.launch {
            try {
                block(track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$action failed", e)
                _message.value = "Couldn't $action this song. Try again."
            } finally {
                busy = false
            }
        }
    }

    private companion object {
        const val TAG = "SharedTrackVM"
    }
}
