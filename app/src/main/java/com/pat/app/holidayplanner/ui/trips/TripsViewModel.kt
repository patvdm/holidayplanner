package com.pat.app.holidayplanner.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.CountryEntity
import com.pat.app.holidayplanner.data.local.TripRow
import com.pat.app.holidayplanner.data.repo.CountryRepository
import com.pat.app.holidayplanner.data.repo.TripRepository
import com.pat.app.holidayplanner.domain.AppClock
import com.pat.app.holidayplanner.domain.LocalTimes
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
    val upcoming: List<TripRow> = emptyList(),
    val past: List<TripRow> = emptyList(),
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

    val uiState: StateFlow<TripsUiState> = repository.observeTripRows()
        .map { rows ->
            val byId = rows.associateBy { it.trip.meta.id }
            val split = rows.map { it.trip }.splitAround(today())
            TripsUiState(
                upcoming = split.upcoming.mapNotNull { byId[it.meta.id] },
                past = split.past.mapNotNull { byId[it.meta.id] },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TripsUiState())

    /**
     * A trip is a country and the days it covers, both decided up front, since the days are what
     * gets planned. An unnamed trip takes the country's name, which is what most of them would
     * have been called anyway, and can be renamed in the editor.
     */
    fun addTrip(
        name: String,
        country: CountryEntity,
        start: LocalDate,
        end: LocalDate,
        onCreated: (String) -> Unit,
    ) {
        viewModelScope.launch {
            onCreated(
                repository.add(
                    name = name.trim().ifBlank { country.name },
                    countryId = country.meta.id,
                    startDate = LocalTimes.writeDate(start),
                    endDate = LocalTimes.writeDate(end),
                ),
            )
        }
    }

    private fun today(): LocalDate =
        Instant.ofEpochMilli(clock.now()).atZone(ZoneId.systemDefault()).toLocalDate()
}
