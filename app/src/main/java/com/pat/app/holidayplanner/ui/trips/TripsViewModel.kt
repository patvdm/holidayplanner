package com.pat.app.holidayplanner.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.TripEntity
import com.pat.app.holidayplanner.data.repo.HierarchyResolver
import com.pat.app.holidayplanner.data.repo.TripRepository
import com.pat.app.holidayplanner.domain.AppClock
import com.pat.app.holidayplanner.domain.CountryOption
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
    private val resolver: HierarchyResolver,
    private val clock: AppClock,
) : ViewModel() {

    val uiState: StateFlow<TripsUiState> = repository.observeTrips()
        .map { trips ->
            val split = trips.splitAround(today())
            TripsUiState(upcoming = split.upcoming, past = split.past)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TripsUiState())

    /**
     * A trip starts life as a name and a country; the dates come later.
     *
     * The country is found or created, so planning a trip somewhere new puts that country on the
     * explore list too rather than leaving the trip pointing at nothing.
     */
    fun addTrip(name: String, country: CountryOption, onCreated: (String) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val countryId = resolver.resolveCountry(country.isoCode, country.name)
            onCreated(repository.add(name = name, startDate = "", endDate = "", countryIds = listOf(countryId)))
        }
    }

    private fun today(): LocalDate =
        Instant.ofEpochMilli(clock.now()).atZone(ZoneId.systemDefault()).toLocalDate()
}
