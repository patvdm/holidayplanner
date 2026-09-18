package com.pat.app.holidayplanner.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.RouteEntity
import com.pat.app.holidayplanner.data.local.RouteStopRow
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.location.DeviceLocation
import com.pat.app.holidayplanner.data.repo.RouteRepository
import com.pat.app.holidayplanner.data.repo.SpotRepository
import com.pat.app.holidayplanner.domain.GeoPoint
import com.pat.app.holidayplanner.domain.MapsUrls
import com.pat.app.holidayplanner.domain.Proximity
import com.pat.app.holidayplanner.domain.RoutePoint
import com.pat.app.holidayplanner.domain.TravelMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * One spot in the city, and whether it is on the walk.
 *
 * Unselected spots still appear, because choosing a walk is mostly a matter of looking at
 * everything nearby and ticking a few.
 */
data class RouteCandidate(
    val spotId: String,
    val name: String,
    val address: String,
    val lat: Double?,
    val lng: Double?,
    val googlePlaceId: String,
    val stopId: String?,
    val sortKey: String,
) {
    val selected: Boolean get() = stopId != null
}

data class RouteUiState(
    val route: RouteEntity? = null,
    val candidates: List<RouteCandidate> = emptyList(),
    val sorting: Boolean = false,
) {
    val name: String get() = route?.name.orEmpty()

    val completed: Boolean get() = route?.completed == true

    /** The walk itself, in order. */
    val stops: List<RouteCandidate> get() = candidates.filter { it.selected }

    /** Maps takes eleven stops at a time, so a longer walk has to go out as several routes. */
    val parts: List<List<RouteCandidate>> get() = MapsUrls.splitForMaps(stops)

    val tooLongForOneRoute: Boolean get() = parts.size > 1

    val canOpen: Boolean get() = stops.size >= 2

    val stopsWithoutCoordinates: Int get() = stops.count { it.lat == null || it.lng == null }
}

/**
 * One route: which of its city's spots are on it, in what order, and the Maps link that walks them.
 *
 * Every change is written immediately. The list is the plan, so there is nothing a Save button
 * would mean.
 */
class RouteViewModel(
    private val routeId: String,
    private val routes: RouteRepository,
    spots: SpotRepository,
    private val location: DeviceLocation,
) : ViewModel() {

    private val sorting = MutableStateFlow(false)
    private val route: Flow<RouteEntity?> = routes.observeRoute(routeId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val citySpots: Flow<List<SpotEntity>> = route.flatMapLatest { current ->
        val cityId = current?.cityId.orEmpty()
        if (cityId.isEmpty()) flowOf(emptyList()) else spots.observeSpots(cityId)
    }

    val uiState: StateFlow<RouteUiState> = combine(
        route,
        citySpots,
        routes.observeStops(routeId),
        sorting,
    ) { current, inCity, stops, busy ->
        RouteUiState(route = current, candidates = inCity.asCandidates(stops), sorting = busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RouteUiState())

    /** Ticking a spot puts it on the walk; unticking takes it off and leaves it in the list. */
    fun setOnRoute(candidate: RouteCandidate, onRoute: Boolean) {
        viewModelScope.launch {
            val stopId = candidate.stopId
            if (onRoute) routes.addStop(routeId, candidate.spotId) else stopId?.let { routes.removeStop(it) }
        }
    }

    fun move(stopId: String, previousKey: String?, nextKey: String?) {
        viewModelScope.launch { routes.move(stopId, previousKey, nextKey) }
    }

    /**
     * Reorders the walk so each stop is the nearest one to the last. Only selected spots move;
     * the rest are not on the walk and have no place in its order.
     *
     * With [fromHere] the walk starts from where you are standing, which is what you want once the
     * day has begun. Without it the first stop stays put, because you chose to start there.
     */
    fun sortByProximity(fromHere: Boolean) {
        if (sorting.value) return
        viewModelScope.launch {
            sorting.value = true
            val start = if (fromHere) location.current() else null
            val ordered = Proximity.orderByProximity(uiState.value.stops, from = start) { it.point() }
            routes.reorder(ordered.mapNotNull { it.stopId })
            sorting.value = false
        }
    }

    fun rename(name: String) {
        viewModelScope.launch { routes.rename(routeId, name) }
    }

    fun setCompleted(completed: Boolean) {
        viewModelScope.launch { routes.setCompleted(routeId, completed) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            routes.delete(routeId)
            onDeleted()
        }
    }

    /** The Maps link for one part of the walk. Routes are always walked. */
    fun mapsUrl(part: List<RouteCandidate>): String? =
        MapsUrls.route(part.map { it.toRoutePoint() }, TravelMode.WALKING)
}

/**
 * Pairs the city's spots with the stops of this route: selected ones first in walking order, then
 * everything else in the city's own order.
 */
private fun List<SpotEntity>.asCandidates(stops: List<RouteStopRow>): List<RouteCandidate> {
    val bySpot = stops.associateBy { it.spotId }
    val candidates = map { spot ->
        val stop = bySpot[spot.meta.id]
        RouteCandidate(
            spotId = spot.meta.id,
            name = spot.name,
            address = spot.address,
            lat = spot.lat,
            lng = spot.lng,
            googlePlaceId = spot.googlePlaceId,
            stopId = stop?.stopId,
            sortKey = stop?.sortKey ?: spot.sortKey,
        )
    }
    val (on, off) = candidates.partition { it.selected }
    return on.sortedBy { it.sortKey } + off
}

private fun RouteCandidate.point(): GeoPoint? =
    if (lat != null && lng != null) GeoPoint(lat, lng) else null

private fun RouteCandidate.toRoutePoint(): RoutePoint = RoutePoint(
    label = listOf(name, address).filter { it.isNotBlank() }.joinToString(", "),
    lat = lat,
    lng = lng,
    googlePlaceId = googlePlaceId,
)
