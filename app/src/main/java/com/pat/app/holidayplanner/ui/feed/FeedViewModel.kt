package com.pat.app.holidayplanner.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.FeedRow
import com.pat.app.holidayplanner.data.repo.FeedRepository
import com.pat.app.holidayplanner.domain.FeedEntryType
import com.pat.app.holidayplanner.domain.FeedGroup
import com.pat.app.holidayplanner.domain.FeedGrouping
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val STOP_TIMEOUT_MS = 5_000L

data class FeedUiState(
    val groups: List<FeedGroup> = emptyList(),
    val rows: List<FeedRow> = emptyList(),
) {
    val hasNews: Boolean get() = groups.isNotEmpty()
}

/**
 * Shared by the strip on Countries and by the feed screen, because both answer the same question
 * and differ only in how much of the answer they show.
 */
class FeedViewModel(repository: FeedRepository) : ViewModel() {

    val uiState: StateFlow<FeedUiState> = repository.observeThisWeek()
        .map { rows ->
            FeedUiState(
                groups = FeedGrouping.group(
                    items = rows,
                    typeOf = { FeedEntryType.fromRow(it.type) },
                    placeOf = { it.place },
                    createdAtOf = { it.createdAt },
                ),
                rows = rows,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FeedUiState())
}
