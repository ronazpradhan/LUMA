package com.luma.feature.library.share

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.core.data.share.SharePreference
import com.luma.core.data.share.SharedMixRepository
import com.luma.core.model.share.ShareLinks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ShareMixUiState(
    val defaultName: String = "",
    val displayName: String? = null,
    val working: Boolean = false,
    val error: String? = null,
)

/** A link ready to send: [url] carries the whole playlist; [songs] it holds of [total]. */
data class ShareMixLink(val url: String, val name: String, val songs: Int, val total: Int)

/**
 * LUMA: the share sheet. The link is built on the phone and carries the playlist itself, so there is
 * nothing to create on a server, keep updated or take down. A later share sends the playlist as it is then.
 */
@HiltViewModel
class ShareMixViewModel @Inject constructor(
    private val repository: SharedMixRepository,
    private val sharePreference: SharePreference,
) : ViewModel() {
    private val _state = MutableStateFlow(ShareMixUiState())
    val state: StateFlow<ShareMixUiState> = _state
    private var playlistId = 0L

    fun bind(playlistId: Long, playlistName: String) {
        if (this.playlistId == playlistId) {
            // A reopened sheet starts clean, not with the last attempt's error.
            _state.value = _state.value.copy(error = null, working = false)
            return
        }
        this.playlistId = playlistId
        _state.value = ShareMixUiState(defaultName = playlistName)
        viewModelScope.launch {
            val display = safely("read the display name") { sharePreference.displayName() }
            _state.value = _state.value.copy(displayName = display)
        }
    }

    /** Builds the link for the playlist as it is now; [onReady] sends or copies it. */
    fun makeLink(name: String, displayName: String?, onReady: (ShareMixLink) -> Unit) {
        val s = _state.value
        if (s.working) return
        _state.value = s.copy(working = true, error = null)
        viewModelScope.launch {
            val link = safely("build the link") {
                sharePreference.setDisplayName(displayName)
                val doc = repository.buildDocument(playlistId, name.ifBlank { s.defaultName }, displayName)
                if (doc.tracks.isEmpty()) {
                    null
                } else {
                    val url = ShareLinks.playlistUrl(doc.name, doc.sharedBy, doc.tracks, doc.covers)
                    ShareMixLink(url, doc.name, minOf(doc.tracks.size, ShareLinks.MAX_LINK_TRACKS), doc.tracks.size)
                }
            }
            _state.value = _state.value.copy(
                working = false,
                error = if (link == null) "This playlist has no songs to share." else null,
            )
            link?.let(onReady)
        }
    }

    /** Runs [block]; a failure (DB, IO) is logged and becomes null, never a crash. */
    private suspend fun <T> safely(action: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$action for $playlistId failed", e)
        null
    }

    private companion object { const val TAG = "ShareMixVM" }
}
