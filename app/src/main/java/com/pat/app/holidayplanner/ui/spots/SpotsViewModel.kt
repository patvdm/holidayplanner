package com.pat.app.holidayplanner.ui.spots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.SpotListRow
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import com.pat.app.holidayplanner.data.repo.SpotRepository
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SortMode
import com.pat.app.holidayplanner.domain.SortScope
import com.pat.app.holidayplanner.domain.SpotMark
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.hasMark
import com.pat.app.holidayplanner.domain.SpotType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

/** One country as offered in the filter row. */
data class CountryChoice(val id: String, val name: String, val isoCode: String)

/** The filters on the global list. Null means "everything". */
data class SpotListFilters(
    val type: SpotType? = null,
    val status: SpotStatus? = null,
    val priority: Priority? = null,
    val countryId: String? = null,
    val mark: SpotMark? = null,
) {
    val any: Boolean
        get() = type != null || status != null || priority != null || countryId != null || mark != null

    fun matches(row: SpotListRow): Boolean =
        (type == null || row.spot.type == type) &&
            (status == null || row.spot.status == status) &&
            (priority == null || row.spot.priority == priority) &&
            (countryId == null || row.countryId == countryId) &&
            (mark == null || row.spot.marks.hasMark(mark))
}

data class SpotsUiState(
    val rows: List<SpotListRow> = emptyList(),
    val countries: List<CountryChoice> = emptyList(),
    val filters: SpotListFilters = SpotListFilters(),
    val sortMode: SortMode = SortMode.ALPHABETICAL,
    val total: Int = 0,
) {
    val nothingMatches: Boolean get() = rows.isEmpty() && total > 0
}

/**
 * Every spot anywhere, narrowed by what it is and where, in whichever order helps.
 *
 * There is no manual order here on purpose: spots are dragged into an order inside their city,
 * and a list across every city has no single order anyone chose.
 */
class SpotsViewModel(
    repository: SpotRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val filters = MutableStateFlow(SpotListFilters())

    val uiState: StateFlow<SpotsUiState> = combine(
        repository.observeAllRows(),
        filters,
        prefs.sortMode(SortScope.ALL_SPOTS),
    ) { rows, active, mode ->
        SpotsUiState(
            rows = rows.filter(active::matches).sorted(mode),
            countries = rows
                .map { CountryChoice(it.countryId, it.countryName, it.isoCode) }
                .distinct()
                .sortedBy { it.name.lowercase() },
            filters = active,
            sortMode = mode,
            total = rows.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SpotsUiState())

    fun setTypeFilter(type: SpotType?) = filters.update { it.copy(type = type) }

    fun setStatusFilter(status: SpotStatus?) = filters.update { it.copy(status = status) }

    fun setPriorityFilter(priority: Priority?) = filters.update { it.copy(priority = priority) }

    fun setCountryFilter(countryId: String?) = filters.update { it.copy(countryId = countryId) }

    fun setMarkFilter(mark: SpotMark?) = filters.update { it.copy(mark = mark) }

    fun clearFilters() {
        filters.value = SpotListFilters()
    }

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch { prefs.setSortMode(SortScope.ALL_SPOTS, mode) }
    }
}

/** Priority puts the must-sees first; within a mode, names break ties so the order is stable. */
private fun List<SpotListRow>.sorted(mode: SortMode): List<SpotListRow> = when (mode) {
    SortMode.PRIORITY -> sortedWith(compareBy<SpotListRow> { it.spot.priority }.thenBy { it.spot.name.lowercase() })
    SortMode.RECENT -> sortedByDescending { it.spot.meta.createdAt }
    else -> sortedBy { it.spot.name.lowercase() }
}
