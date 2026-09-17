package com.pat.app.holidayplanner.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.CountryEntity
import com.pat.app.holidayplanner.data.local.TripEntity
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.data.local.CityOption
import com.pat.app.holidayplanner.data.repo.CityRepository
import com.pat.app.holidayplanner.data.repo.CountryRepository
import com.pat.app.holidayplanner.data.repo.TripRepository
import com.pat.app.holidayplanner.domain.Cost
import com.pat.app.holidayplanner.domain.Costs
import com.pat.app.holidayplanner.domain.CurrencyTotal
import com.pat.app.holidayplanner.domain.TripItemType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

data class TripUiState(
    val trip: TripEntity? = null,
    val timeline: Timeline = Timeline(emptyList(), emptyList()),
    val totals: List<CurrencyTotal> = emptyList(),
    val totalsByType: List<Pair<TripItemType, List<CurrencyTotal>>> = emptyList(),
    val countries: List<CountryEntity> = emptyList(),
) {
    val tripCountries: List<CountryEntity>
        get() = countries.filter { it.meta.id in trip?.countryIds.orEmpty() }
}

class TripViewModel(
    private val tripId: String,
    private val repository: TripRepository,
    countryRepository: CountryRepository,
    cityRepository: CityRepository,
) : ViewModel() {

    /** Offered when adding a day route, which cannot exist without a city to walk. */
    val cityOptions = cityRepository.observeOptions()

    val uiState: StateFlow<TripUiState> = combine(
        repository.observeTrip(tripId),
        repository.observeItems(tripId),
        countryRepository.observeCountries(),
    ) { trip, items, countries ->
        TripUiState(
            trip = trip,
            timeline = items.toTimeline(),
            totals = Costs.byCurrency(items.mapNotNull(::costOf)),
            totalsByType = totalsByType(items),
            countries = countries,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TripUiState())

    fun addItem(type: TripItemType, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(repository.addItem(tripId = tripId, type = type)) }
    }

    /** A day route is a walk through one city, so it is created with that city already set. */
    fun addDayRoute(city: CityOption, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            onCreated(repository.addDayRoute(tripId, city.cityName, city.cityId, date = null))
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch { repository.deleteItem(itemId) }
    }

    fun rename(name: String) = edit { it.copy(name = name.trim()) }

    fun setDates(startDate: String, endDate: String) =
        edit { it.copy(startDate = startDate, endDate = endDate) }

    fun setNotes(notes: String) = edit { it.copy(notes = notes) }

    fun toggleCountry(countryId: String) = edit { trip ->
        val ids = trip.countryIds.toMutableList()
        if (!ids.remove(countryId)) ids.add(countryId)
        trip.copy(countryIds = ids)
    }

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

    /** Per type, so it is clear whether the flights or the hotels are what cost the money. */
    private fun totalsByType(items: List<TripItemEntity>): List<Pair<TripItemType, List<CurrencyTotal>>> =
        TripItemType.entries.mapNotNull { type ->
            val costs = items.filter { it.type == type }.mapNotNull(::costOf)
            val totals = Costs.byCurrency(costs)
            if (totals.isEmpty()) null else type to totals
        }
}

private fun costOf(item: TripItemEntity): Cost? {
    val amount = item.costMinor ?: return null
    return Cost(amountMinor = amount, currency = item.currency)
}
