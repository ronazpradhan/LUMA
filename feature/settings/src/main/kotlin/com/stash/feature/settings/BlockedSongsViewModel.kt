package com.stash.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.core.data.blocklist.BlocklistGuard
import com.stash.core.data.db.entity.TrackBlocklistEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Presentation-layer row for the Blocked Songs viewer. v0.9.15: keyed by
 * [canonicalKey] (not trackId) since the underlying tracks row is gone
 * after a block. Album art is no longer carried — `track_blocklist` rows
 * persist past row deletion and have no associated artwork.
 */
data class BlockedTrackRow(
    val canonicalKey: String,
    val title: String,
    val artist: String,
)

/**
 * ViewModel backing the Settings → Blocked Songs screen.
 *
 * v0.9.15: Sources directly from [BlocklistGuard.observeBlocklist] —
 * the new identity-keyed `track_blocklist` table — instead of the old
 * `tracks.is_blacklisted = 1` query. The unblock action removes by
 * canonical key, which is what the new table is indexed by.
 */
@HiltViewModel
class BlockedSongsViewModel @Inject constructor(
    private val blocklistGuard: BlocklistGuard,
) : ViewModel() {

    /** Reactive list of blocked tracks, ordered most-recent-block first. */
    val blockedTracks: StateFlow<List<BlockedTrackRow>> =
        blocklistGuard.observeBlocklist()
            .map { entries -> entries.map { it.toRow() } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /**
     * Unblock by canonical key. Removes the row from `track_blocklist`;
     * the next sync re-queues the identity normally.
     */
    fun unblock(canonicalKey: String) {
        viewModelScope.launch {
            blocklistGuard.unblock(canonicalKey)
        }
    }
}

private fun TrackBlocklistEntity.toRow() = BlockedTrackRow(
    canonicalKey = canonicalKey,
    title = title,
    artist = artist,
)
