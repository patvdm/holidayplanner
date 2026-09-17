package com.pat.app.holidayplanner.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.SearchHit
import com.pat.app.holidayplanner.data.repo.SearchRepository
import com.pat.app.holidayplanner.domain.SearchHitType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

private const val TYPING_DEBOUNCE_MS = 250L
private const val STOP_TIMEOUT_MS = 5_000L

/** Results of one kind, so the list can be broken up under headings. */
data class SearchSection(val type: SearchHitType, val hits: List<SearchHit>)

data class SearchUiState(
    val query: String = "",
    val sections: List<SearchSection> = emptyList(),
) {
    val searched: Boolean get() = query.isNotBlank()

    val nothingFound: Boolean get() = searched && sections.isEmpty()
}

class SearchViewModel(repository: SearchRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val hits = query
        .debounce(TYPING_DEBOUNCE_MS)
        .flatMapLatest(repository::search)

    val uiState: StateFlow<SearchUiState> = combine(query, hits) { text, results ->
        SearchUiState(query = text, sections = results.toSections())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SearchUiState())

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun clear() {
        query.value = ""
    }
}

/**
 * Groups rows under their kind, in the order the enum declares rather than alphabetically, so the
 * headings stay put between searches instead of reshuffling as results change.
 */
private fun List<SearchHit>.toSections(): List<SearchSection> {
    val byType = groupBy { SearchHitType.fromRow(it.type) }
    return SearchHitType.entries.mapNotNull { type ->
        byType[type]?.takeIf { it.isNotEmpty() }?.let { SearchSection(type, it) }
    }
}
