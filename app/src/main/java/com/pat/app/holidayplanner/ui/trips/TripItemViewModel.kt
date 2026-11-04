package com.pat.app.holidayplanner.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.SpotPickerRow
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.data.local.SpotContext
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.repo.SpotRepository
import com.pat.app.holidayplanner.data.repo.TripRepository
import com.pat.app.holidayplanner.domain.LocalTimes
import com.pat.app.holidayplanner.domain.MapsUrls
import com.pat.app.holidayplanner.domain.RoutePoint
import com.pat.app.holidayplanner.domain.TripItemType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The preview is the spot's own shared Maps link when it has one, since that is exactly the place
 * Google meant; otherwise one built from what the spot knows, as the spot screen does.
 */
private fun placeOf(spot: SpotEntity, context: SpotContext?): ItemSpotPlace {
    val city = context?.cityName.orEmpty()
    val preview = spot.mapsUrl.ifBlank {
        MapsUrls.spot(
            lat = spot.lat,
            lng = spot.lng,
            googlePlaceId = spot.googlePlaceId,
            name = spot.name,
            cityName = city,
            countryName = context?.countryName.orEmpty(),
        )
    }
    val label = listOf(spot.name, city).filter { it.isNotBlank() }.joinToString(", ")
    return ItemSpotPlace(
        spotId = spot.meta.id,
        onOurMap = spot.lat != null && spot.lng != null,
        previewUrl = preview,
        navigateUrl = MapsUrls.navigateTo(RoutePoint(label, spot.lat, spot.lng, spot.googlePlaceId)),
    )
}

/**
 * Where the spot a trip item points at is, as the three map buttons need it: whether our map has
 * a pin for it, the Google Maps page to preview, and the link that navigates there from here.
 */
data class ItemSpotPlace(val spotId: String, val onOurMap: Boolean, val previewUrl: String, val navigateUrl: String)

data class ItemEditorState(
    val item: TripItemEntity? = null,
    /** The trip's country, so a spot searched for from here is looked for in the right place. */
    val countryIso: String = "",
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
    spots: SpotRepository,
) : ViewModel() {

    private val state = MutableStateFlow(ItemEditorState())
    val uiState = state.asStateFlow()

    /** The spot this item points at, followed as it is picked or changed. Null for anything else. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val spotPlace: StateFlow<ItemSpotPlace?> = state
        .map { it.item?.takeIf { item -> item.type == TripItemType.SPOT }?.spotId.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { spotId ->
            if (spotId.isBlank()) {
                flowOf(null)
            } else {
                combine(spots.observeSpot(spotId), spots.observeContext(spotId)) { spot, context ->
                    spot?.let { placeOf(it, context) }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

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
                countryIso = repository.countryIsoOf(item.tripId),
            )
        }
    }

    fun edit(change: (TripItemEntity) -> TripItemEntity) {
        state.value = state.value.copy(item = state.value.item?.let(change))
    }

    fun setStart(local: String, zone: String) = edit { it.copy(startLocal = local, startTimeZone = zone) }

    fun setEnd(local: String, zone: String) = edit { it.copy(endLocal = local, endTimeZone = zone) }

    fun setSpot(spot: SpotPickerRow) = edit {
        it.copy(spotId = spot.spotId, title = it.title.ifBlank { spot.spotName })
    }

    /** A spot just found through Google; the row reads its name from the spot itself. */
    fun setSpot(spotId: String) = edit { it.copy(spotId = spotId) }

    fun clearSpot() = edit { it.copy(spotId = "") }

    fun save(onSaved: () -> Unit) {
        val item = state.value.item ?: return
        state.value = state.value.copy(saving = true)
        viewModelScope.launch {
            repository.updateItem(item)
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
