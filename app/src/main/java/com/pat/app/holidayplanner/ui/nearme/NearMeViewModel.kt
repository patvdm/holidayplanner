package com.pat.app.holidayplanner.ui.nearme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.SpotPin
import com.pat.app.holidayplanner.data.location.DeviceLocation
import com.pat.app.holidayplanner.data.repo.SpotPinRepository
import com.pat.app.holidayplanner.domain.GeoPoint
import com.pat.app.holidayplanner.domain.Proximity
import com.pat.app.holidayplanner.domain.SpotStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

/** The radii offered as chips, in metres. Two kilometres is a walk, ten is a day out. */
val RADIUS_OPTIONS_M = listOf(1_000, 2_000, 5_000, 10_000)

private const val DEFAULT_RADIUS_M = 2_000

/** A spot near you, with how far away it is. */
data class NearbySpot(val pin: SpotPin, val metres: Double)

data class NearMeUiState(
    val radiusMetres: Int = DEFAULT_RADIUS_M,
    val here: GeoPoint? = null,
    val locating: Boolean = false,
    val permissionDenied: Boolean = false,
    val nearby: List<NearbySpot> = emptyList(),
) {
    val located: Boolean get() = here != null

    val nothingNear: Boolean get() = located && !locating && nearby.isEmpty()
}

/**
 * Unvisited spots within a radius, nearest first.
 *
 * Visited ones are left out on purpose: this answers "what could we do while we are here", and
 * somewhere already been is not an answer to that.
 */
class NearMeViewModel(
    pins: SpotPinRepository,
    private val location: DeviceLocation,
) : ViewModel() {

    private val radius = MutableStateFlow(DEFAULT_RADIUS_M)
    private val here = MutableStateFlow<GeoPoint?>(null)
    private val locating = MutableStateFlow(false)
    private val denied = MutableStateFlow(false)

    val uiState: StateFlow<NearMeUiState> = combine(
        pins.observeAll(),
        radius,
        here,
        locating,
        denied,
    ) { spots, radiusM, position, busy, refused ->
        NearMeUiState(
            radiusMetres = radiusM,
            here = position,
            locating = busy,
            permissionDenied = refused,
            nearby = if (position == null) emptyList() else spots.near(position, radiusM),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NearMeUiState())

    fun setRadius(metres: Int) {
        radius.value = metres
    }

    /** Called on open and by the retry button, and after the permission prompt is answered. */
    fun locate() {
        if (locating.value) return
        viewModelScope.launch {
            locating.value = true
            val found = location.current()
            denied.value = found == null && !location.hasPermission()
            if (found != null) here.value = found
            locating.value = false
        }
    }
}

private fun List<SpotPin>.near(from: GeoPoint, radiusMetres: Int): List<NearbySpot> = this
    .asSequence()
    .filter { it.status == SpotStatus.UNVISITED }
    .map { NearbySpot(it, Proximity.distanceMetres(from, GeoPoint(it.lat, it.lng))) }
    .filter { it.metres <= radiusMetres }
    .sortedBy { it.metres }
    .toList()
