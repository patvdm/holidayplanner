package com.pat.app.holidayplanner.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.local.SpotPin
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import com.pat.app.holidayplanner.data.repo.LinkRepository
import com.pat.app.holidayplanner.data.repo.RouteRepository
import com.pat.app.holidayplanner.data.repo.SpotPinRepository
import com.pat.app.holidayplanner.data.repo.SpotRepository
import com.pat.app.holidayplanner.domain.GeoPoint
import com.pat.app.holidayplanner.domain.MapView
import com.pat.app.holidayplanner.domain.MapPinFilter
import com.pat.app.holidayplanner.domain.SpotMark
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.domain.hasMark
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlin.math.log2

private const val STOP_TIMEOUT_MS = 5_000L

/** Tight enough to see one building. */
const val SPOT_ZOOM = 16f

/** A city fits on the screen at roughly this zoom. */
const val CITY_ZOOM = 12f

/** Everything you have saved, wherever it is. */
const val WORLD_ZOOM = 4f

/**
 * Where a map should point. Nothing here filters the pins: the map always shows every spot, so a
 * neighbouring city's spots are visible just over the edge rather than mysteriously absent.
 */
data class MapScope(
    val countryId: String = "",
    val cityId: String = "",
    val spotId: String = "",
    val routeId: String = "",
)

/** Where the camera starts, and how close; [spotId] or [routeId] when opened for one of those. */
data class MapFocus(val centre: GeoPoint, val zoom: Float, val spotId: String = "", val routeId: String = "")

/** What a pin's card shows beyond the pin itself: what was written about it and where to read more. */
data class PinDetail(
    val spotId: String,
    val notes: String,
    val links: List<LinkEntity>,
    val googlePlaceId: String = "",
)

data class MapUiState(
    /** The pins to draw, after the food filter. */
    val pins: List<SpotPin> = emptyList(),
    /** Whether there is anything with a position at all, filter or not. */
    val hasPins: Boolean = false,
    val focus: MapFocus? = null,
    /** Every route as a line, walked ones marked; empty while they are switched off. */
    val lines: List<RouteLine> = emptyList(),
    /** The route sign each pin on a route wears, by spot id. */
    val badges: Map<String, RouteBadge> = emptyMap(),
    val showsRoutes: Boolean = true,
    val filter: MapPinFilter = MapPinFilter.ALL,
)

/**
 * Every spot with a position, plus where to look first.
 *
 * The focus is worked out from the pins themselves rather than stored anywhere, so opening the map
 * from a city centres on that city's spots without the city needing coordinates of its own.
 */
class MapViewModel(
    private val scope: MapScope,
    pins: SpotPinRepository,
    private val prefs: AppPreferences,
    private val spots: SpotRepository,
    links: LinkRepository,
    routes: RouteRepository,
) : ViewModel() {

    private val selectedId = MutableStateFlow<String?>(null)

    /** The full notes and saved links of the pin tapped last, for its card. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val selection: StateFlow<PinDetail?> = selectedId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                combine(spots.observeSpot(id), links.observeLinks(id)) { spot, all ->
                    PinDetail(
                        spotId = id,
                        notes = spot?.notes.orEmpty().trim(),
                        links = all.filterNot { it.source.isPhoto },
                        googlePlaceId = spot?.googlePlaceId.orEmpty(),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun select(spotId: String?) {
        selectedId.value = spotId
    }

    // The remembered view is read once, on opening: following it live would pull the camera back
    // to wherever it was saved a moment ago, every time the map settles.
    val uiState: StateFlow<MapUiState> = combine(
        pins.observeAll(),
        prefs.mapView.take(1),
        routes.observeLinePoints(),
        prefs.mapShowsRoutes,
        prefs.mapPinFilter,
    ) { all, saved, stops, showsRoutes, filter ->
        val shown = if (showsRoutes) stops.toRouteLines() else emptyList()
        // The route the map was opened for is drawn whatever else is: even walked, even switched off.
        val askedStops = stops.filter { scope.routeId.isNotEmpty() && it.routeId == scope.routeId }
        val asked = askedStops.toRouteLines().firstOrNull()
            ?.takeIf { line -> shown.none { it.routeId == line.routeId } }
        MapUiState(
            // The spot the map was opened for stays whatever the filter, or "Our map" would show nothing.
            pins = all.filter { it.spotId == scope.spotId || filter.lets(it) },
            hasPins = all.isNotEmpty(),
            focus = all.focus(saved, askedStops.map { GeoPoint(it.lat, it.lng) }),
            lines = shown + listOfNotNull(asked),
            badges = stops.routeBadges(),
            showsRoutes = showsRoutes,
            filter = filter,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MapUiState())

    fun setShowsRoutes(show: Boolean) {
        viewModelScope.launch { prefs.setMapShowsRoutes(show) }
    }

    fun setVisited(spotId: String, visited: Boolean) {
        viewModelScope.launch { spots.setStatus(spotId, if (visited) SpotStatus.VISITED else SpotStatus.UNVISITED) }
    }

    fun setFilter(filter: MapPinFilter) {
        viewModelScope.launch { prefs.setMapPinFilter(filter) }
    }

    /** Called each time the map comes to rest, so the next plain opening carries on from here. */
    fun rememberView(view: MapView) {
        viewModelScope.launch { prefs.setMapView(view) }
    }

    /**
     * Narrowest wins: a named spot, then a named route's stops, then a city, then a country. Opened for nothing in
     * particular, it carries on where it was last left, and only the very first time shows
     * everything. Each step falls through when nothing matches, so a stale id cannot leave the map
     * pointing at the sea.
     */
    private fun List<SpotPin>.focus(saved: MapView?, routeStops: List<GeoPoint>): MapFocus? {
        if (isEmpty()) return null

        val spot = firstOrNull { it.spotId == scope.spotId }
        val inCity = filter { scope.cityId.isNotEmpty() && it.cityId == scope.cityId }
        val inCountry = filter { scope.countryId.isNotEmpty() && it.countryId == scope.countryId }

        return when {
            spot != null -> MapFocus(GeoPoint(spot.lat, spot.lng), SPOT_ZOOM, spot.spotId)
            routeStops.isNotEmpty() -> MapFocus(routeStops.middle(), routeStops.zoomToFit(), routeId = scope.routeId)
            inCity.isNotEmpty() -> MapFocus(inCity.centre(), CITY_ZOOM)
            inCountry.isNotEmpty() -> MapFocus(inCountry.centre(), inCountry.zoomForSpread())
            saved != null -> MapFocus(GeoPoint(saved.lat, saved.lng), saved.zoom)
            else -> MapFocus(centre(), zoomForSpread())
        }
    }
}

/** Whether [pin] passes this filter. Drone means the mark, whatever kind of place carries it. */
internal fun MapPinFilter.lets(pin: SpotPin): Boolean = when (this) {
    MapPinFilter.ALL -> true
    MapPinFilter.FOOD -> pin.type == SpotType.RESTAURANT
    MapPinFilter.DRONE -> pin.marks.hasMark(SpotMark.DRONE)
}

private fun List<GeoPoint>.middle(): GeoPoint =
    GeoPoint((maxOf { it.lat } + minOf { it.lat }) / 2, (maxOf { it.lng } + minOf { it.lng }) / 2)

/**
 * Roughly the zoom at which a phone screen holds the points with a margin: each zoom step halves
 * the degrees across the screen, and about [FIT_DEGREES_AT_ZOOM_0] of longitude fit at zoom 0 once
 * the margin is allowed for. Latitude is stretched because the screen is taller than it is wide
 * but a degree of latitude covers more of it at these latitudes.
 */
internal fun List<GeoPoint>.zoomToFit(): Float {
    val span = maxOf(
        maxOf { it.lng } - minOf { it.lng },
        (maxOf { it.lat } - minOf { it.lat }) * LAT_STRETCH,
    )
    if (span <= 0.0) return SPOT_ZOOM
    return log2(FIT_DEGREES_AT_ZOOM_0 / span).toFloat().coerceIn(WORLD_ZOOM, SPOT_ZOOM)
}

private const val FIT_DEGREES_AT_ZOOM_0 = 400.0
private const val LAT_STRETCH = 1.6

private fun List<SpotPin>.centre(): GeoPoint =
    GeoPoint(sumOf { it.lat } / size, sumOf { it.lng } / size)

/** Above a degree or so the pins are spread across a country rather than a city. */
private const val CITY_SPREAD_DEGREES = 1.0

private fun List<SpotPin>.zoomForSpread(): Float {
    if (size < 2) return CITY_ZOOM
    val spread = maxOf(
        maxOf { it.lat } - minOf { it.lat },
        maxOf { it.lng } - minOf { it.lng },
    )
    return if (spread > CITY_SPREAD_DEGREES) WORLD_ZOOM else CITY_ZOOM
}
