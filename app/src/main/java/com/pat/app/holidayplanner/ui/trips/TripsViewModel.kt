package com.pat.app.holidayplanner.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.TripEntity
import com.pat.app.holidayplanner.data.repo.TripRepository
import com.pat.app.holidayplanner.domain.AppClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

data class TripsUiState(
    val upcoming: List<TripEntity> = emptyList(),
    val past: List<TripEntity> = emptyList(),
) {
    val isEmpty: Boolean get() = upcoming.isEmpty() && past.isEmpty()
}

class TripsViewModel(
    private val repository: TripRepository,
    private val clock: AppClock,
) : ViewModel() {

    val uiState: StateFlow<TripsUiState> = repository.observeTrips()
        .map { trips ->
            val split = trips.splitAround(today())
            TripsUiState(upcoming = split.upcoming, past = split.past)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TripsUiState())

    /** Dates come later; a trip starts life as a name so the idea can be written down. */
    fun addTrip(name: String, onCreated: (String) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            onCreated(repository.add(name = name, startDate = "", endDate = "", countryIds = emptyList()))
        }
    }

    private fun today(): LocalDate =
        Instant.ofEpochMilli(clock.now()).atZone(ZoneId.systemDefault()).toLocalDate()
}
