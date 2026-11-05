package com.pat.app.holidayplanner.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.CountryRow
import com.pat.app.holidayplanner.data.repo.CountryRepository
import com.pat.app.holidayplanner.data.repo.DeleteImpact
import com.pat.app.holidayplanner.domain.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

/** A delete waiting for confirmation, with what it would take with it. */
data class PendingDelete(val id: String, val name: String, val impact: DeleteImpact)

data class CountriesUiState(
    val rows: List<CountryRow> = emptyList(),
    val pendingDelete: PendingDelete? = null,
)

/**
 * The country list: high priority first, then the rest, each in the order it was dragged into. A
 * new country goes to the top of its group, and starring one moves it to the top of the other.
 */
class CountriesViewModel(
    private val repository: CountryRepository,
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<PendingDelete?>(null)

    val uiState: StateFlow<CountriesUiState> = combine(
        repository.observeRows(),
        pendingDelete,
    ) { rows, pending ->
        CountriesUiState(
            rows = rows.sortedWith(
                compareBy<CountryRow> { it.country.priority.ordinal }
                    .thenBy { it.country.sortKey }
                    .thenBy { it.country.meta.id },
            ),
            pendingDelete = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CountriesUiState())

    fun setPriority(id: String, priority: Priority) {
        viewModelScope.launch { repository.setPriority(id, priority) }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch { repository.rename(id, name) }
    }

    fun move(id: String, previousKey: String?, nextKey: String?) {
        viewModelScope.launch { repository.move(id, previousKey, nextKey) }
    }

    fun requestDelete(id: String, name: String) {
        viewModelScope.launch {
            pendingDelete.value = PendingDelete(id, name, repository.deleteImpact(id))
        }
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete() {
        val target = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch { repository.delete(target.id) }
    }
}
