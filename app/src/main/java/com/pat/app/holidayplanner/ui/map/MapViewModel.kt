package com.pat.app.holidayplanner.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.SpotPin
import com.pat.app.holidayplanner.data.repo.SpotPinRepository
import com.pat.app.holidayplanner.domain.GeoPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
)

/** Where the camera starts, and how close; [spotId] when the map was opened for one spot. */
data class MapFocus(val centre: GeoPoint, val zoom: Float, val spotId: String = "")

data class MapUiState(
    val pins: List<SpotPin> = emptyList(),
    val focus: MapFocus? = null,
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
) : ViewModel() {

    val uiState: StateFlow<MapUiState> = pins.observeAll()
        .map { all -> MapUiState(pins = all, focus = all.focus()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MapUiState())

    /**
     * Narrowest wins: a named spot, then its city, then its country, then everything. Each step
     * falls through when nothing matches, so a stale id cannot leave the map pointing at the sea.
     */
    private fun List<SpotPin>.focus(): MapFocus? {
        if (isEmpty()) return null

        val spot = firstOrNull { it.spotId == scope.spotId }
        val inCity = filter { scope.cityId.isNotEmpty() && it.cityId == scope.cityId }
        val inCountry = filter { scope.countryId.isNotEmpty() && it.countryId == scope.countryId }

        return when {
            spot != null -> MapFocus(GeoPoint(spot.lat, spot.lng), SPOT_ZOOM, spot.spotId)
            inCity.isNotEmpty() -> MapFocus(inCity.centre(), CITY_ZOOM)
            inCountry.isNotEmpty() -> MapFocus(inCountry.centre(), inCountry.zoomForSpread())
            else -> MapFocus(centre(), zoomForSpread())
        }
    }
}

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
