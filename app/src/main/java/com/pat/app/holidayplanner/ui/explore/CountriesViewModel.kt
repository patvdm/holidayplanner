package com.pat.app.holidayplanner.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.CountryRow
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import com.pat.app.holidayplanner.data.repo.CountryRepository
import com.pat.app.holidayplanner.data.repo.DeleteImpact
import com.pat.app.holidayplanner.domain.CountryCatalog
import com.pat.app.holidayplanner.domain.CountryOption
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SortMode
import com.pat.app.holidayplanner.domain.SortScope
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
    val sortMode: SortMode = SortMode.MANUAL,
    val pendingDelete: PendingDelete? = null,
) {
    val canReorder: Boolean get() = sortMode == SortMode.MANUAL
}

class CountriesViewModel(
    private val repository: CountryRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<PendingDelete?>(null)

    val uiState: StateFlow<CountriesUiState> = combine(
        repository.observeRows(),
        prefs.sortMode(SortScope.COUNTRIES),
        pendingDelete,
    ) { rows, mode, pending ->
        CountriesUiState(rows = rows.sortedCountries(mode), sortMode = mode, pendingDelete = pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CountriesUiState())

    /** The countries not added yet, which are the only ones worth offering. */
    fun availableCountries(rows: List<CountryRow>): List<CountryOption> {
        val taken = rows.map { it.country.isoCode.uppercase() }.toSet()
        return CountryCatalog.all().filterNot { it.isoCode in taken }
    }

    fun addCountry(option: CountryOption) {
        viewModelScope.launch { repository.add(name = option.name, isoCode = option.isoCode) }
    }

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch { prefs.setSortMode(SortScope.COUNTRIES, mode) }
    }

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
