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

/**
 * Which spots a map shows. Empty ids mean everywhere, which is what a map of the whole collection
 * would use.
 */
data class MapScope(val countryId: String = "", val cityId: String = "")

data class MapUiState(val pins: List<SpotPin> = emptyList()) {
    /** Where to point the camera: the middle of everything shown. */
    val centre: GeoPoint?
        get() = if (pins.isEmpty()) {
            null
        } else {
            GeoPoint(pins.sumOf { it.lat } / pins.size, pins.sumOf { it.lng } / pins.size)
        }

    /** The widest gap between pins in degrees, which is enough to pick a sensible zoom. */
    val spanDegrees: Double
        get() = if (pins.size < 2) {
            0.0
        } else {
            maxOf(
                pins.maxOf { it.lat } - pins.minOf { it.lat },
                pins.maxOf { it.lng } - pins.minOf { it.lng },
            )
        }
}

/**
 * The pins for one map.
 *
 * It loads its own data rather than taking it from the screen's view model, so putting a map on a
 * screen costs that screen nothing but a toggle.
 */
class MapViewModel(
    scope: MapScope,
    pins: SpotPinRepository,
) : ViewModel() {

    val uiState: StateFlow<MapUiState> = when {
        scope.cityId.isNotEmpty() -> pins.observeInCity(scope.cityId)
        scope.countryId.isNotEmpty() -> pins.observeInCountry(scope.countryId)
        else -> pins.observeAll()
    }
        .map { MapUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MapUiState())
}
