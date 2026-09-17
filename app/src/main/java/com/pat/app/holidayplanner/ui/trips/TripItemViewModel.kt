package com.pat.app.holidayplanner.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.SpotPickerRow
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.data.repo.TripRepository
import com.pat.app.holidayplanner.domain.LocalTimes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Money is typed in major units and stored in minor ones, so the text stays as the person wrote it. */
data class ItemEditorState(
    val item: TripItemEntity? = null,
    val costText: String = "",
    val saving: Boolean = false,
) {
    val type get() = item?.type
}

/**
 * Edits one trip item.
 *
 * Changes are held here until Save rather than written on each keystroke: a form with a dozen
 * fields would otherwise push a dozen rows through sync for one edit.
 */
class TripItemViewModel(
    private val itemId: String,
    private val repository: TripRepository,
) : ViewModel() {

    private val state = MutableStateFlow(ItemEditorState())
    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch {
            val item = repository.findItem(itemId) ?: return@launch
            val zone = item.startTimeZone.ifBlank {
                repository.suggestedTimeZone(item.tripId).ifBlank { LocalTimes.deviceZone() }
            }
            state.value = ItemEditorState(
                item = item.copy(
                    startTimeZone = zone,
                    endTimeZone = item.endTimeZone.ifBlank { zone },
                ),
                costText = item.costMinor.toMajorUnits(),
            )
        }
    }

    fun edit(change: (TripItemEntity) -> TripItemEntity) {
        state.value = state.value.copy(item = state.value.item?.let(change))
    }

    fun onCostChange(text: String) {
        state.value = state.value.copy(costText = text)
    }

    fun setStart(local: String, zone: String) = edit { it.copy(startLocal = local, startTimeZone = zone) }

    fun setEnd(local: String, zone: String) = edit { it.copy(endLocal = local, endTimeZone = zone) }

    fun setSpot(spot: SpotPickerRow) = edit {
        it.copy(spotId = spot.spotId, title = it.title.ifBlank { spot.spotName })
    }

    fun clearSpot() = edit { it.copy(spotId = "") }

    fun save(onSaved: () -> Unit) {
        val item = state.value.item ?: return
        state.value = state.value.copy(saving = true)
        viewModelScope.launch {
            repository.updateItem(item.copy(costMinor = state.value.costText.toMinorUnits()))
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteItem(itemId)
            onDeleted()
        }
    }
}

private const val MINOR_UNITS_PER_MAJOR = 100

/** "249.50" becomes 24950. Anything unreadable becomes no cost rather than a wrong one. */
private fun String.toMinorUnits(): Long? {
    val cleaned = trim().replace(',', '.')
    if (cleaned.isEmpty()) return null
    val amount = cleaned.toDoubleOrNull() ?: return null
    return Math.round(amount * MINOR_UNITS_PER_MAJOR)
}

private fun Long?.toMajorUnits(): String {
    val minor = this ?: return ""
    return (minor.toDouble() / MINOR_UNITS_PER_MAJOR).toString()
}
