package com.pat.app.holidayplanner.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.RouteEntity
import com.pat.app.holidayplanner.data.local.RouteStopRow
import com.pat.app.holidayplanner.data.location.DeviceLocation
import com.pat.app.holidayplanner.data.repo.CityRepository
import com.pat.app.holidayplanner.data.repo.RouteRepository
import com.pat.app.holidayplanner.domain.GeoPoint
import com.pat.app.holidayplanner.domain.MapsUrls
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.Proximity
import com.pat.app.holidayplanner.domain.RoutePoint
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.domain.TravelMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * One stop on a route: a spot that was put on this walk, and whether it goes into the directions.
 *
 * [onMaps] is not whether it belongs to the route — everything here does — but whether Google is
 * asked to walk you past it. A stop left out stays in the list and keeps its place in the order.
 */
data class RouteStop(
    val stopId: String,
    val spotId: String,
    val name: String,
    val address: String,
    val lat: Double?,
    val lng: Double?,
    val googlePlaceId: String,
    val sortKey: String,
    val onMaps: Boolean,
    val imageUrl: String,
    val priority: Priority,
    val status: SpotStatus,
    val type: SpotType,
    val marks: String,
    val notes: String,
)

data class RouteUiState(
    val route: RouteEntity? = null,
    val cityName: String = "",
    val stops: List<RouteStop> = emptyList(),
    val sorting: Boolean = false,
) {
    val name: String get() = route?.name.orEmpty()

    val cityId: String get() = route?.cityId.orEmpty()

    val completed: Boolean get() = route?.completed == true

    val isEmpty: Boolean get() = stops.isEmpty()

    /** The stops Google is asked to walk, in the order the list has them. */
    val walked: List<RouteStop> get() = stops.filter { it.onMaps }

    /** Maps takes eleven stops at a time, so a longer walk has to go out as several routes. */
    val parts: List<List<RouteStop>> get() = MapsUrls.splitForMaps(walked)

    val tooLongForOneRoute: Boolean get() = parts.size > 1

    val canOpen: Boolean get() = walked.size >= 2

    val stopsWithoutCoordinates: Int get() = walked.count { it.lat == null || it.lng == null }
}

/**
 * One route: the spots put on it, in what order, and the Maps link that walks them.
 *
 * Every change is written immediately. The list is the plan, so there is nothing a Save button
 * would mean.
 */
class RouteViewModel(
    private val routeId: String,
    private val routes: RouteRepository,
    private val location: DeviceLocation,
    cities: CityRepository,
) : ViewModel() {

    private val sorting = MutableStateFlow(false)
    private val route: Flow<RouteEntity?> = routes.observeRoute(routeId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val cityName: Flow<String> = route.flatMapLatest { current ->
        val cityId = current?.cityId.orEmpty()
        if (cityId.isEmpty()) flowOf("") else cities.observeCity(cityId).map { it?.name.orEmpty() }
    }

    val uiState: StateFlow<RouteUiState> = combine(
        route,
        cityName,
        routes.observeStops(routeId),
        sorting,
    ) { current, city, stops, busy ->
        RouteUiState(route = current, cityName = city, stops = stops.map(RouteStopRow::asStop), sorting = busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RouteUiState())

    /** Ticking a stop puts it in the directions; unticking leaves it on the walk and out of them. */
    fun setOnMaps(stop: RouteStop, onMaps: Boolean) {
        viewModelScope.launch { routes.setOnMaps(stop.stopId, onMaps) }
    }

    /** Takes a stop off this route. The spot itself is untouched and stays in its city. */
    fun remove(stop: RouteStop) {
        viewModelScope.launch { routes.removeStop(stop.stopId) }
    }

    fun addSpot(spotId: String) {
        viewModelScope.launch { routes.addStop(routeId, spotId) }
    }

    fun move(stopId: String, previousKey: String?, nextKey: String?) {
        viewModelScope.launch { routes.move(stopId, previousKey, nextKey) }
    }

    /**
     * Reorders the walked stops so each is the nearest one to the last, among the places they
     * already occupy in the list. Stops left out of the directions are left where they are.
     *
     * With [fromHere] the walk starts from where you are standing, which is what you want once the
     * day has begun. Without it the first stop stays put, because you chose to start there.
     */
    fun sortByProximity(fromHere: Boolean) {
        if (sorting.value) return
        viewModelScope.launch {
            sorting.value = true
            val start = if (fromHere) location.current() else null
            val ordered = Proximity.orderByProximity(uiState.value.walked, from = start) { it.point() }
            routes.reorder(ordered.map { it.stopId })
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
    fun mapsUrl(part: List<RouteStop>): String? =
        MapsUrls.route(part.map { it.toRoutePoint() }, TravelMode.WALKING)

    /** Directions to one stop from wherever you are now. */
    fun directionsTo(stop: RouteStop): String = MapsUrls.directionsTo(stop.toRoutePoint(), TravelMode.WALKING)
}

private fun RouteStopRow.asStop(): RouteStop = RouteStop(
    stopId = stopId,
    spotId = spotId,
    name = name,
    address = address,
    lat = lat,
    lng = lng,
    googlePlaceId = googlePlaceId,
    sortKey = sortKey,
    onMaps = onMaps,
    imageUrl = imageUrl,
    priority = priority,
    status = status,
    type = type,
    marks = marks,
    notes = notes,
)

private fun RouteStop.point(): GeoPoint? =
    if (lat != null && lng != null) GeoPoint(lat, lng) else null

private fun RouteStop.toRoutePoint(): RoutePoint = RoutePoint(
    label = listOf(name, address).filter { it.isNotBlank() }.joinToString(", "),
    lat = lat,
    lng = lng,
    googlePlaceId = googlePlaceId,
)
