package com.pat.app.holidayplanner.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.RouteStopRow
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.data.repo.RouteRepository
import com.pat.app.holidayplanner.data.repo.TripRepository
import com.pat.app.holidayplanner.domain.GeoPoint
import com.pat.app.holidayplanner.domain.MapsUrls
import com.pat.app.holidayplanner.domain.Proximity
import com.pat.app.holidayplanner.domain.RoutePoint
import com.pat.app.holidayplanner.domain.TravelMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

data class RouteUiState(
    val item: TripItemEntity? = null,
    val stops: List<RouteStopRow> = emptyList(),
) {
    val travelMode: TravelMode get() = item?.travelMode ?: TravelMode.WALKING

    /** Maps takes eleven stops at a time, so a longer day has to go out as several routes. */
    val parts: List<List<RouteStopRow>> get() = MapsUrls.splitForMaps(stops)

    val tooLongForOneRoute: Boolean get() = parts.size > 1

    val canOpen: Boolean get() = stops.size >= 2

    /** Stops we could not place on the map, which Maps can only search for by name. */
    val stopsWithoutCoordinates: Int get() = stops.count { it.lat == null || it.lng == null }
}

/**
 * One day route: which spots are on it, in what order, and the Maps link that walks them.
 *
 * Unlike the item editor, every change here is written immediately. The list is the document, so
 * there is nothing a Save button would mean.
 */
class RouteViewModel(
    private val itemId: String,
    private val routes: RouteRepository,
    private val trips: TripRepository,
) : ViewModel() {

    private val item = MutableStateFlow<TripItemEntity?>(null)

    val uiState: StateFlow<RouteUiState> = combine(
        item,
        routes.observeStops(itemId),
    ) { current, stops ->
        RouteUiState(item = current, stops = stops)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RouteUiState())

    init {
        reloadItem()
    }

    fun addStop(spotId: String) {
        viewModelScope.launch { routes.addStop(itemId, spotId) }
    }

    fun removeStop(stopId: String) {
        viewModelScope.launch { routes.removeStop(stopId) }
    }

    fun move(stopId: String, previousKey: String?, nextKey: String?) {
        viewModelScope.launch { routes.move(stopId, previousKey, nextKey) }
    }

    /** Reorders the day so each stop is the nearest one to the last, keeping the first where it is. */
    fun sortByProximity() {
        val ordered = Proximity.orderByProximity(uiState.value.stops) { it.point() }
        viewModelScope.launch { routes.reorder(ordered.map { it.stopId }) }
    }

    fun setTravelMode(mode: TravelMode) {
        val current = item.value ?: return
        viewModelScope.launch {
            trips.updateItem(current.copy(travelMode = mode))
            reloadItem()
        }
    }

    /** The Maps link for one part of the day, or null when that part is not a route. */
    fun mapsUrl(part: List<RouteStopRow>): String? =
        MapsUrls.route(part.map { it.toRoutePoint() }, uiState.value.travelMode)

    private fun reloadItem() {
        viewModelScope.launch { item.value = trips.findItem(itemId) }
    }
}

private fun RouteStopRow.point(): GeoPoint? =
    if (lat != null && lng != null) GeoPoint(lat, lng) else null

private fun RouteStopRow.toRoutePoint(): RoutePoint = RoutePoint(
    label = listOf(name, address).filter { it.isNotBlank() }.joinToString(", "),
    lat = lat,
    lng = lng,
    googlePlaceId = googlePlaceId,
)
