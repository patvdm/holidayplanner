package com.pat.app.holidayplanner.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.TripEntity
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.data.local.CityOption
import com.pat.app.holidayplanner.data.repo.CityRepository
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
    val cities: List<CityOption> = emptyList(),
) {
    /** The city this trip is to, which is where its spots and its day routes come from. */
    val city: CityOption? get() = cities.firstOrNull { it.cityId == trip?.cityId }

    val cityName: String get() = city?.cityName.orEmpty()
}

class TripViewModel(
    private val tripId: String,
    private val repository: TripRepository,
    cityRepository: CityRepository,
) : ViewModel() {

    val uiState: StateFlow<TripUiState> = combine(
        repository.observeTrip(tripId),
        repository.observeItems(tripId),
        cityRepository.observeOptions(),
    ) { trip, items, cities ->
        TripUiState(
            trip = trip,
            timeline = items.toTimeline(),
            totals = Costs.byCurrency(items.mapNotNull(::costOf)),
            totalsByType = totalsByType(items),
            cities = cities,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TripUiState())

    fun addItem(type: TripItemType, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(repository.addItem(tripId = tripId, type = type)) }
    }

    /** A day route walks the trip's own city, so there is nothing to ask before making one. */
    fun addDayRoute(onCreated: (String) -> Unit) {
        val trip = uiState.value.trip ?: return
        viewModelScope.launch {
            onCreated(
                repository.addDayRoute(
                    tripId = tripId,
                    title = uiState.value.cityName,
                    cityId = trip.cityId,
                    date = null,
                ),
            )
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch { repository.deleteItem(itemId) }
    }

    fun rename(name: String) = edit { it.copy(name = name.trim()) }

    fun setDates(startDate: String, endDate: String) =
        edit { it.copy(startDate = startDate, endDate = endDate) }

    fun setNotes(notes: String) = edit { it.copy(notes = notes) }

    /** Moving a trip to another city takes its country with it, so the two never disagree. */
    fun setCity(city: CityOption) = edit { it.copy(cityId = city.cityId, countryIds = listOf(city.countryId)) }

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
