package com.pat.app.holidayplanner.ui.trips

import com.pat.app.holidayplanner.data.local.RouteStopHours
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.CityOption
import com.pat.app.holidayplanner.data.local.CountryEntity
import com.pat.app.holidayplanner.data.local.RouteRow
import com.pat.app.holidayplanner.data.local.SpotPickerRow
import com.pat.app.holidayplanner.data.local.TripEntity
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.data.repo.CityRepository
import com.pat.app.holidayplanner.data.repo.CountryRepository
import com.pat.app.holidayplanner.data.repo.RouteRepository
import com.pat.app.holidayplanner.data.repo.TripRepository
import com.pat.app.holidayplanner.domain.LocalTimes
import com.pat.app.holidayplanner.domain.TripItemType
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

data class TripUiState(
    val trip: TripEntity? = null,
    val plan: TripPlan = TripPlan(emptyList(), emptyList()),
    val countries: List<CountryEntity> = emptyList(),
    /** The routes through this trip's country, which a day can pick from. */
    val routes: List<RouteRow> = emptyList(),
    /** The cities of this trip's country, which a new route can walk. */
    val cities: List<CityOption> = emptyList(),
    /** When the stops of this trip's routes are open, by route id. */
    val stopHours: Map<String, List<RouteStopHours>> = emptyMap(),
) {
    /** The country this trip is to, which is where its spots and routes come from. */
    val country: CountryEntity? get() = countries.firstOrNull { it.meta.id == trip?.countryId }

    val hasDates: Boolean
        get() = trip != null &&
            LocalTimes.parseDate(trip.startDate) != null &&
            LocalTimes.parseDate(trip.endDate) != null

    val isEmpty: Boolean get() = plan.days.all { it.entries.isEmpty() } && plan.undated.isEmpty()
}

/** The parts of the state that depend on which country the trip is to. */
private data class CountryScope(val routes: List<RouteRow>, val cities: List<CityOption>)

class TripViewModel(
    private val tripId: String,
    private val repository: TripRepository,
    countryRepository: CountryRepository,
    private val routeRepository: RouteRepository,
    cityRepository: CityRepository,
) : ViewModel() {

    private val trip: Flow<TripEntity?> = repository.observeTrip(tripId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val inCountry: Flow<CountryScope> = trip.flatMapLatest { current ->
        val countryId = current?.countryId.orEmpty()
        if (countryId.isEmpty()) {
            flowOf(CountryScope(emptyList(), emptyList()))
        } else {
            combine(
                routeRepository.observeRoutesInCountry(countryId),
                cityRepository.observeOptions(),
            ) { routes, cities -> CountryScope(routes, cities.filter { it.countryId == countryId }) }
        }
    }

    val uiState: StateFlow<TripUiState> = combine(
        trip,
        repository.observeItemRows(tripId),
        countryRepository.observeCountries(),
        inCountry,
        repository.observeStopHours(tripId),
    ) { current, rows, countries, scope, stopHours ->
        TripUiState(
            trip = current,
            plan = rows.toPlan(
                start = current?.let { LocalTimes.parseDate(it.startDate) },
                end = current?.let { LocalTimes.parseDate(it.endDate) },
            ),
            countries = countries,
            routes = scope.routes,
            cities = scope.cities,
            stopHours = stopHours.groupBy { it.routeId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TripUiState())

    /** A flight starts as a day and is filled in on its own screen, which opens at once. */
    fun addFlight(date: LocalDate, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = repository.addItem(
                tripId = tripId,
                type = TripItemType.FLIGHT,
                startLocal = LocalTimes.writeDate(date),
            )
            onCreated(id)
        }
    }

    fun addSpot(date: LocalDate, spot: SpotPickerRow) = addSpot(date, spot.spotId, spot.spotName)

    /** A spot just found through Google: the row reads its name from the spot itself. */
    fun addSpot(date: LocalDate, spotId: String, name: String = "") {
        viewModelScope.launch {
            repository.addItem(
                tripId = tripId,
                type = TripItemType.SPOT,
                title = name,
                spotId = spotId,
                startLocal = LocalTimes.writeDate(date),
            )
        }
    }

    fun addRoute(date: LocalDate, route: RouteRow) {
        viewModelScope.launch {
            repository.addItem(
                tripId = tripId,
                type = TripItemType.ROUTE,
                title = route.route.name,
                routeId = route.route.meta.id,
                startLocal = LocalTimes.writeDate(date),
            )
        }
    }

    /**
     * A route that does not exist yet: made through [city], put on the day, and opened so its
     * stops can be ticked. This is the "add new route" on a trip day, and it funnels into the same
     * route screen as everything else.
     */
    fun addNewRoute(
        date: LocalDate,
        city: CityOption,
        spotIds: List<String>,
        onCreated: (routeId: String) -> Unit,
    ) {
        viewModelScope.launch {
            val routeId = routeRepository.create(city.cityId, city.cityName, spotIds)
            repository.addItem(
                tripId = tripId,
                type = TripItemType.ROUTE,
                title = city.cityName,
                routeId = routeId,
                startLocal = LocalTimes.writeDate(date),
            )
            onCreated(routeId)
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch { repository.deleteItem(itemId) }
    }

    fun rename(name: String) = edit { it.copy(name = name.trim()) }

    fun setDates(startDate: String, endDate: String) =
        edit { it.copy(startDate = startDate, endDate = endDate) }

    fun setNotes(notes: String) = edit { it.copy(notes = notes) }

    fun setCountry(country: CountryEntity) = edit { it.copy(countryId = country.meta.id) }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(tripId)
            onDeleted()
        }
    }

    private fun edit(change: (TripEntity) -> TripEntity) {
        val current = uiState.value.trip ?: return
        viewModelScope.launch { repository.update(change(current)) }
    }
}
