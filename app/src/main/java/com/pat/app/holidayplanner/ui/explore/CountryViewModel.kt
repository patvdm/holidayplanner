package com.pat.app.holidayplanner.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.CountryEntity
import com.pat.app.holidayplanner.data.local.CityRow
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import com.pat.app.holidayplanner.data.repo.CountryRepository
import com.pat.app.holidayplanner.data.repo.CityRepository
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

data class CountryUiState(
    val country: CountryEntity? = null,
    val rows: List<CityRow> = emptyList(),
    val sortMode: SortMode = SortMode.MANUAL,
    val pendingDelete: PendingDelete? = null,
) {
    val canReorder: Boolean get() = sortMode == SortMode.MANUAL
}

class CountryViewModel(
    private val countryId: String,
    private val countryRepository: CountryRepository,
    private val cityRepository: CityRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<PendingDelete?>(null)

    val uiState: StateFlow<CountryUiState> = combine(
        countryRepository.observeCountry(countryId),
        cityRepository.observeRows(countryId),
        prefs.sortMode(SortScope.CITIES),
        pendingDelete,
    ) { country, rows, mode, pending ->
        CountryUiState(country = country, rows = rows.sortedPlaces(mode), sortMode = mode, pendingDelete = pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CountryUiState())

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch { prefs.setSortMode(SortScope.CITIES, mode) }
    }

    fun setPriority(id: String, priority: Priority) {
        viewModelScope.launch { cityRepository.setPriority(id, priority) }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch { cityRepository.rename(id, name) }
    }

    fun move(id: String, previousKey: String?, nextKey: String?) {
        viewModelScope.launch { cityRepository.move(id, previousKey, nextKey) }
    }

    fun requestDelete(id: String, name: String) {
        viewModelScope.launch {
            pendingDelete.value = PendingDelete(id, name, cityRepository.deleteImpact(id))
        }
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete() {
        val target = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch { cityRepository.delete(target.id) }
    }
}
