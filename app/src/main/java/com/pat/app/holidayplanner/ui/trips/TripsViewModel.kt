package com.pat.app.holidayplanner.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.CityOption
import com.pat.app.holidayplanner.data.local.TripEntity
import com.pat.app.holidayplanner.data.repo.CityRepository
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
    cityRepository: CityRepository,
    private val clock: AppClock,
) : ViewModel() {

    /** A trip is to a city you have already saved, so these are the only choices. */
    val cityOptions = cityRepository.observeOptions()

    val uiState: StateFlow<TripsUiState> = repository.observeTrips()
        .map { trips ->
            val split = trips.splitAround(today())
            TripsUiState(upcoming = split.upcoming, past = split.past)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TripsUiState())

    /**
     * A trip starts life as a name and the city it is to; the dates come later.
     *
     * Tying it to a city rather than a country is what lets everything after this be a choice from
     * that city's spots instead of a search.
     */
    fun addTrip(name: String, city: CityOption, onCreated: (String) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            onCreated(
                repository.add(name = name, cityId = city.cityId, countryIds = listOf(city.countryId)),
            )
        }
    }

    private fun today(): LocalDate =
        Instant.ofEpochMilli(clock.now()).atZone(ZoneId.systemDefault()).toLocalDate()
}
