package com.pat.app.holidayplanner.ui.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.CityOption
import com.pat.app.holidayplanner.data.local.RouteRow
import com.pat.app.holidayplanner.data.repo.CityRepository
import com.pat.app.holidayplanner.data.repo.RouteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

data class RoutesUiState(
    val rows: List<RouteRow> = emptyList(),
    /** Every city with spots, which is where a new route can be walked. */
    val cities: List<CityOption> = emptyList(),
) {
    val toWalk: List<RouteRow> get() = rows.filter { !it.route.completed }
    val walked: List<RouteRow> get() = rows.filter { it.route.completed }
}

/** Every route anywhere, still to walk first, and the way to start a new one. */
class RoutesViewModel(
    private val routes: RouteRepository,
    cityRepository: CityRepository,
) : ViewModel() {

    val uiState: StateFlow<RoutesUiState> = combine(
        routes.observeRoutes(),
        cityRepository.observeOptions(),
    ) { rows, cities ->
        RoutesUiState(rows = rows, cities = cities)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RoutesUiState())

    /** A new, empty route through [city], named after it until somebody has a better idea. */
    /** A new route through [city], holding the spots chosen for it. */
    fun create(city: CityOption, spotIds: List<String>, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(routes.create(city.cityId, city.cityName, spotIds)) }
    }
}
