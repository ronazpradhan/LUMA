package com.luma.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.core.data.db.dao.TrackDao
import com.luma.core.data.mapper.toDomain
import com.luma.core.media.PlayerRepository
import com.luma.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** One day's group in the History list. */
data class HistoryDay(val label: String, val tracks: List<Track>)

/**
 * LUMA: Library's History tab — the last [HISTORY_LIMIT] songs you listened to,
 * newest first and grouped by day. A song shows once, at its latest play; it
 * counts once it has played long enough to be a listen (the same rule as play
 * counts). "Clear" hides everything played so far without touching play counts
 * or suggestions: it just moves the start of the history to now, so Undo can
 * move it back.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val playerRepository: PlayerRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val clearedAt = MutableStateFlow(prefs.getLong(KEY_CLEARED_AT, 0L))

    /** Newest first; null while loading. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val days: StateFlow<List<HistoryDay>?> = clearedAt
        .flatMapLatest { since -> trackDao.observeListeningHistory(HISTORY_LIMIT, since) }
        .map { rows -> groupByDay(rows.map { it.toDomain() }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Plays the whole history from [track], in the order shown. */
    fun play(track: Track) {
        val all = days.value.orEmpty().flatMap { it.tracks }
        val index = all.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        if (all.isEmpty()) return
        viewModelScope.launch { playerRepository.setQueue(all, startIndex = index) }
    }

    /** Where the history started before the last [clear], so [undoClear] can put it back. */
    private var beforeClear: Long? = null

    fun clear() {
        beforeClear = clearedAt.value
        setClearedAt(System.currentTimeMillis())
    }

    /** Brings back what the last [clear] hid. */
    fun undoClear() {
        val previous = beforeClear ?: return
        beforeClear = null
        setClearedAt(previous)
    }

    private fun setClearedAt(ms: Long) {
        prefs.edit().putLong(KEY_CLEARED_AT, ms).apply()
        clearedAt.value = ms
    }

    companion object {
        /** How many songs History keeps. */
        const val HISTORY_LIMIT = 50
        private const val PREFS = "listening_history"
        private const val KEY_CLEARED_AT = "cleared_at"

        internal fun groupByDay(
            tracks: List<Track>,
            today: LocalDate = LocalDate.now(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): List<HistoryDay> = tracks
            .groupBy { t ->
                val day = java.time.Instant.ofEpochMilli(t.lastPlayed ?: 0L).atZone(zone).toLocalDate()
                when {
                    day == today -> "Today"
                    day == today.minusDays(1) -> "Yesterday"
                    day.isAfter(today.minusDays(7)) ->
                        day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
                    else -> "Earlier"
                }
            }
            .map { (label, list) -> HistoryDay(label, list) }
    }
}
