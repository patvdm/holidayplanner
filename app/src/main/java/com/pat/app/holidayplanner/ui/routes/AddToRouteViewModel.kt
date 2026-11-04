package com.pat.app.holidayplanner.ui.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.RouteRow
import com.pat.app.holidayplanner.data.local.SpotContext
import com.pat.app.holidayplanner.data.repo.RouteRepository
import com.pat.app.holidayplanner.data.repo.SpotRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

/** One route the spot could go on, and whether it already does. */
data class RouteChoice(
    val routeId: String,
    val name: String,
    val cityName: String,
    val stopCount: Int,
    val walked: Boolean,
    val holdsSpot: Boolean,
)

data class AddToRouteUiState(
    val loaded: Boolean = false,
    val routes: List<RouteChoice> = emptyList(),
    /** The spot's city, which a new route goes through; empty for a spot filed on its country. */
    val cityId: String = "",
    val cityName: String = "",
) {
    /** A route always goes through a city, so a spot with none can only join an existing one. */
    val canCreate: Boolean get() = cityId.isNotEmpty()
}

/**
 * Which routes one spot is on, for ticking it on or off them in one tap each.
 *
 * Offered are the routes through the spot's own city, since a route is a walk through one city;
 * a spot filed straight on its country has no city, so it is offered its country's routes. Walks
 * still ahead come before walked ones.
 */
class AddToRouteViewModel(
    private val spotId: String,
    spots: SpotRepository,
    private val routes: RouteRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AddToRouteUiState> = spots.observeContext(spotId)
        .distinctUntilChanged()
        .flatMapLatest { context ->
            if (context == null) {
                flowOf(AddToRouteUiState(loaded = true))
            } else {
                combine(
                    routes.observeRoutesInCountry(context.countryId),
                    routes.observeRouteIdsWithSpot(spotId),
                ) { all, holding -> stateFor(context, all, holding.toSet()) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AddToRouteUiState())

    /** On if it was off, off if it was on. */
    fun toggle(choice: RouteChoice) {
        viewModelScope.launch {
            if (choice.holdsSpot) routes.removeSpot(choice.routeId, spotId) else routes.addStop(choice.routeId, spotId)
        }
    }

    /** A new walk through the spot's city, named after it like any new route, with this spot on. */
    fun createRoute() {
        val state = uiState.value
        if (!state.canCreate) return
        viewModelScope.launch { routes.create(state.cityId, state.cityName, listOf(spotId)) }
    }

    private fun stateFor(context: SpotContext, all: List<RouteRow>, holding: Set<String>): AddToRouteUiState {
        val here = if (context.cityId.isEmpty()) all else all.filter { it.route.cityId == context.cityId }
        val choices = here.map {
            RouteChoice(
                routeId = it.route.meta.id,
                name = it.route.name,
                cityName = it.cityName,
                stopCount = it.stopCount,
                walked = it.route.completed,
                holdsSpot = it.route.meta.id in holding,
            )
        }.sortedBy { it.walked }
        return AddToRouteUiState(loaded = true, routes = choices, cityId = context.cityId, cityName = context.cityName)
    }
}
