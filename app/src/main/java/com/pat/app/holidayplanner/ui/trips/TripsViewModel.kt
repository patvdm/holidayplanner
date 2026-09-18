package com.pat.app.holidayplanner.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.CountryEntity
import com.pat.app.holidayplanner.data.local.TripEntity
import com.pat.app.holidayplanner.data.repo.CountryRepository
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
    countryRepository: CountryRepository,
    private val clock: AppClock,
) : ViewModel() {

    /** A trip is to a country you have spots in, so these are the only choices. */
    val countryOptions = countryRepository.observeCountries()

    val uiState: StateFlow<TripsUiState> = repository.observeTrips()
        .map { trips ->
            val split = trips.splitAround(today())
            TripsUiState(upcoming = split.upcoming, past = split.past)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TripsUiState())

    /**
     * A trip starts life as the country it is to; the dates come later.
     *
     * An unnamed trip takes the country's name, which is what most of them would have been called
     * anyway, and can be renamed in the editor.
     */
    fun addTrip(name: String, country: CountryEntity, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            onCreated(
                repository.add(
                    name = name.trim().ifBlank { country.name },
                    countryId = country.meta.id,
                    startDate = "",
                    endDate = "",
                ),
            )
        }
    }

    private fun today(): LocalDate =
        Instant.ofEpochMilli(clock.now()).atZone(ZoneId.systemDefault()).toLocalDate()
}
