package com.stash.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.core.data.suggest.ListeningSuggestions
import com.stash.data.ytmusic.model.TrackSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Search tab suggests before anything is typed. [basedOn] names the song they grew from. */
data class SearchSuggestions(val basedOn: String?, val songs: List<TrackSummary>)

/** LUMA: Search's suggestions from what you listen to ([ListeningSuggestions]); pull down for a fresh set. */
@HiltViewModel
class SearchSuggestionsViewModel @Inject constructor(
    private val suggestions: ListeningSuggestions,
) : ViewModel() {

    private val _state = MutableStateFlow<SearchSuggestions?>(null)
    val state: StateFlow<SearchSuggestions?> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init { refresh() }

    fun refresh(userPulled: Boolean = false) {
        if (userPulled) _refreshing.value = true
        viewModelScope.launch {
            try {
                runCatching { suggestions.load() }.getOrNull()
                    ?.takeIf { it.songs.isNotEmpty() }
                    ?.let { _state.value = SearchSuggestions(it.basedOn, it.songs) }
            } finally {
                _refreshing.value = false
            }
        }
    }
}
