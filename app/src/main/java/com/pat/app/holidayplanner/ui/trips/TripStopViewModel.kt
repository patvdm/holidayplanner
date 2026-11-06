package com.pat.app.holidayplanner.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.data.repo.TripRepository
import com.pat.app.holidayplanner.domain.LocalTimes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

/** A spot's place on a trip, as the card on the spot screen shows it. */
data class TripStop(val item: TripItemEntity, val tripName: String)

/**
 * The trip side of a spot opened from a trip: its day, booking and the trip's own notes about it.
 * Each change is written as it is made, read fresh first so two quick edits do not undo each other.
 */
class TripStopViewModel(
    private val itemId: String,
    private val repository: TripRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val stop: StateFlow<TripStop?> = repository.observeItem(itemId)
        .flatMapLatest { item ->
            if (item == null) {
                flowOf(null)
            } else {
                repository.observeTrip(item.tripId).map { trip -> TripStop(item, trip?.name.orEmpty()) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun setStart(local: String, zone: String) = change { it.copy(startLocal = local, startTimeZone = zone) }

    fun setBookingRef(value: String) = change { it.copy(bookingRef = value.trim()) }

    fun setNotes(value: String) = change { it.copy(notes = value.trim()) }

    /** Points this stop at another spot, then hands its id back so the screen can show that one. */
    fun setSpot(spotId: String, onChanged: (String) -> Unit) {
        viewModelScope.launch {
            write { it.copy(spotId = spotId) }
            onChanged(spotId)
        }
    }

    fun remove(onRemoved: () -> Unit) {
        viewModelScope.launch {
            repository.deleteItem(itemId)
            onRemoved()
        }
    }

    /** The zone a stop with no time yet should start in, as the item editor picks it. */
    suspend fun zoneFor(item: TripItemEntity): String = item.startTimeZone.ifBlank {
        repository.suggestedTimeZone(item.tripId).ifBlank { LocalTimes.deviceZone() }
    }

    private fun change(edit: (TripItemEntity) -> TripItemEntity) {
        viewModelScope.launch { write(edit) }
    }

    private suspend fun write(edit: (TripItemEntity) -> TripItemEntity) {
        val current = repository.findItem(itemId)?.takeUnless { it.meta.deleted } ?: return
        val changed = edit(current)
        if (changed != current) repository.updateItem(changed)
    }
}
