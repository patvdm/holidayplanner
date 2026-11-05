package com.pat.app.holidayplanner.ui.explore

import com.pat.app.holidayplanner.domain.PictureOrigin
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.CityEntity
import com.pat.app.holidayplanner.data.local.SpotHome
import com.pat.app.holidayplanner.data.local.CityRow
import com.pat.app.holidayplanner.data.local.CountryEntity
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.local.SpotRow
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import com.pat.app.holidayplanner.data.repo.AttachmentOwner
import com.pat.app.holidayplanner.data.repo.AttachmentRepository
import com.pat.app.holidayplanner.data.repo.AttachmentTarget
import com.pat.app.holidayplanner.data.repo.CityRepository
import com.pat.app.holidayplanner.data.repo.CountryAttachments
import com.pat.app.holidayplanner.data.repo.CountryRepository
import com.pat.app.holidayplanner.data.repo.DeleteImpact
import com.pat.app.holidayplanner.data.repo.RouteRepository
import com.pat.app.holidayplanner.data.repo.SpotRepository
import com.pat.app.holidayplanner.data.repo.UserRepository
import com.pat.app.holidayplanner.data.repo.attachmentTargets
import com.pat.app.holidayplanner.domain.CountryCatalog
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SortMode
import com.pat.app.holidayplanner.domain.SortScope
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

/** The filters on the spot lists. Null or false means "everything". */
data class SpotFilters(
    val status: SpotStatus? = null,
    val priority: Priority? = null,
    val type: SpotType? = null,
    /** Somewhere with a link saved on it. Pictures do not count: nearly everywhere has one. */
    val hasLinks: Boolean = false,
) {
    val any: Boolean get() = status != null || priority != null || type != null || hasLinks

    fun matches(row: SpotRow): Boolean =
        (status == null || row.spot.status == status) &&
            (priority == null || row.spot.priority == priority) &&
            (type == null || row.spot.type == type) &&
            (!hasLinks || row.webLinkCount > 0)
}

/** One city on the country screen: its own row, what hangs on it, and the spots under it. */
data class CitySection(
    val city: CityEntity,
    /** The spots on show, filtered and sorted. */
    val spots: List<SpotRow>,
    /** Every spot in the city, whatever the filters hide. */
    val spotCount: Int,
    val attachments: List<LinkEntity>,
    val expanded: Boolean,
)

data class CountryUiState(
    val country: CountryEntity? = null,
    /** Spots that belong to the country itself rather than to a city, filtered and sorted. */
    val countrySpots: List<SpotRow> = emptyList(),
    /** Every spot of the country itself, whatever the filters hide. */
    val countrySpotCount: Int = 0,
    /** Always in the order the cities were dragged into. */
    val sections: List<CitySection> = emptyList(),
    /** What was shared to the country itself rather than to anything in it. */
    val countryAttachments: List<LinkEntity> = emptyList(),
    /** The photos and links of each spot, so a row can show them without a query of its own. */
    val spotAttachments: Map<String, List<LinkEntity>> = emptyMap(),
    /** Everywhere in this country a photo or a link could be moved to. */
    val targets: List<AttachmentTarget> = emptyList(),
    /** Every country and city anywhere, for moving a spot. */
    val homes: List<SpotHome> = emptyList(),
    val filters: SpotFilters = SpotFilters(),
    val sortMode: SortMode = SortMode.MANUAL,
    val savingPhoto: Boolean = false,
    val pendingCityDelete: PendingDelete? = null,
    val pendingSpotDelete: PendingDelete? = null,
) {
    /** Dragging spots only makes sense in manual order and with nothing filtered out. */
    val canReorderSpots: Boolean get() = sortMode == SortMode.MANUAL && !filters.any

    val anyExpanded: Boolean get() = sections.any { it.expanded }

    /** True when there is nothing in the country at all, not even a spot of its own. */
    val isEmpty: Boolean get() = sections.isEmpty() && countrySpotCount == 0

    /** The spots of one drag group: a city's, or the country's own for an empty id. */
    fun spotsOf(cityId: String): List<SpotRow> =
        if (cityId.isEmpty()) countrySpots else sections.firstOrNull { it.city.meta.id == cityId }?.spots.orEmpty()
}

/**
 * What the country screen reads and writes. Grouped because the screen shows a country, its
 * cities, their spots and everything hanging on all three at once, and a constructor listing all
 * of them says nothing useful.
 */
class CountryRepositories(
    val countries: CountryRepository,
    val cities: CityRepository,
    val spots: SpotRepository,
    val users: UserRepository,
    val routes: RouteRepository,
    val attachments: AttachmentRepository,
)

/** Everything stored, before the screen's own choices shape it. */
private data class CountryData(
    val country: CountryEntity?,
    val cities: List<CityRow>,
    val spots: List<SpotRow>,
    val attachments: CountryAttachments,
    val homes: List<SpotHome>,
)

/** How the screen chose to look at that. */
private data class CountryView(
    val sortMode: SortMode,
    val filters: SpotFilters,
    val collapsed: Set<String>,
)

/** What is under way right now: deletes waiting for a yes, photos on their way up. */
private data class CountryPending(
    val cityDelete: PendingDelete?,
    val spotDelete: PendingDelete?,
    val uploads: Int,
)

class CountryViewModel(
    private val countryId: String,
    private val repos: CountryRepositories,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val filters = MutableStateFlow(SpotFilters())

    /** Cities start open; this remembers the ones closed since the screen opened. */
    private val collapsed = MutableStateFlow<Set<String>>(emptySet())
    private val pendingCityDelete = MutableStateFlow<PendingDelete?>(null)
    private val pendingSpotDelete = MutableStateFlow<PendingDelete?>(null)
    private val uploads = MutableStateFlow(0)

    /** What the photo being picked right now is of, since the picker only answers with a picture. */
    private var photoOwner: AttachmentOwner? = null

    private val spots: Flow<List<SpotRow>> = repos.spots.observeRowsInCountry(countryId)

    private val data: Flow<CountryData> = combine(
        repos.countries.observeCountry(countryId),
        repos.cities.observeRows(countryId),
        spots,
        repos.attachments.observeInCountry(countryId),
        repos.cities.observeHomes(),
        ::CountryData,
    )

    private val view: Flow<CountryView> = combine(
        prefs.sortMode(SortScope.SPOTS),
        filters,
        collapsed,
        ::CountryView,
    )

    private val pending: Flow<CountryPending> =
        combine(pendingCityDelete, pendingSpotDelete, uploads, ::CountryPending)

    val uiState: StateFlow<CountryUiState> = combine(data, view, pending, ::countryUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CountryUiState())

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch { prefs.setSortMode(SortScope.SPOTS, mode) }
    }

    fun setFilters(value: SpotFilters) {
        filters.value = value
    }

    fun toggleExpanded(cityId: String) =
        collapsed.update { if (cityId in it) it - cityId else it + cityId }

    fun setAllExpanded(expanded: Boolean) {
        collapsed.value = if (expanded) emptySet() else uiState.value.sections.map { it.city.meta.id }.toSet()
    }

    /** The picture at the top of the country, or on a city's row when [cityId] is given. Blank searches again. */
    fun setPicture(cityId: String, url: String) {
        viewModelScope.launch {
            if (cityId.isEmpty()) {
                repos.countries.setPicture(countryId, url, PictureOrigin.CHOSEN)
            } else {
                repos.cities.setPicture(cityId, url, PictureOrigin.CHOSEN)
            }
        }
    }

    /** The priority of a city or a spot. */
    fun setPriority(place: AttachmentOwner, priority: Priority) {
        viewModelScope.launch {
            if (place.cityId.isNotEmpty()) repos.cities.setPriority(place.cityId, priority)
            if (place.spotId.isNotEmpty()) repos.spots.setPriority(place.spotId, priority)
        }
    }

    /** The note of a city or a spot. Blank removes it. */
    fun setNote(place: AttachmentOwner, note: String) {
        viewModelScope.launch {
            if (place.cityId.isNotEmpty()) repos.cities.setNotes(place.cityId, note)
            if (place.spotId.isNotEmpty()) repos.spots.setNotes(place.spotId, note)
        }
    }

    fun renameCity(id: String, name: String) {
        viewModelScope.launch { repos.cities.rename(id, name) }
    }

    fun moveCity(id: String, previousKey: String?, nextKey: String?) {
        viewModelScope.launch { repos.cities.move(id, previousKey, nextKey) }
    }

    fun requestCityDelete(id: String, name: String) {
        viewModelScope.launch {
            pendingCityDelete.value = PendingDelete(id, name, repos.cities.deleteImpact(id))
        }
    }

    /** The row already carries the counts, so confirming a delete needs no extra query. */
    fun requestSpotDelete(row: SpotRow) {
        pendingSpotDelete.value = PendingDelete(
            id = row.spot.meta.id,
            name = row.spot.name,
            impact = DeleteImpact(links = row.linkCount),
        )
    }

    fun cancelDelete() {
        pendingCityDelete.value = null
        pendingSpotDelete.value = null
    }

    fun confirmDelete() {
        val city = pendingCityDelete.value
        val spot = pendingSpotDelete.value
        cancelDelete()
        viewModelScope.launch {
            city?.let { repos.cities.delete(it.id) }
            spot?.let { repos.spots.delete(it.id) }
        }
    }

    /** Remembers what the next picked photo is of. Called just before the picker opens. */
    fun choosePhotoFor(owner: AttachmentOwner) {
        photoOwner = owner
    }

    /** Unlike every other edit here, this one needs the network. */
    fun addPhoto(uri: Uri) {
        val owner = photoOwner ?: return
        photoOwner = null
        viewModelScope.launch {
            uploads.update { it + 1 }
            repos.attachments.attach(uri, owner)
            uploads.update { it - 1 }
        }
    }

    fun addLink(owner: AttachmentOwner, url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repos.attachments.attachLink(trimmed, owner) }
    }

    fun moveAttachment(attachment: LinkEntity, owner: AttachmentOwner) {
        viewModelScope.launch { repos.attachments.moveTo(attachment, owner) }
    }

    /** Shows a photo as the main picture of whatever it hangs on. */
    fun useAsPicture(photo: LinkEntity) {
        viewModelScope.launch { repos.attachments.useAsPicture(photo) }
    }

    /** A photo or a link, of the country, a city or a spot. */
    fun deleteAttachment(attachment: LinkEntity) {
        viewModelScope.launch { repos.attachments.remove(attachment) }
    }


    fun setSpotStatus(id: String, status: SpotStatus) {
        viewModelScope.launch { repos.spots.setStatus(id, status) }
    }

    /** A new place within the spot's own city; the list never drags a spot across cities. */
    fun moveSpot(id: String, previousKey: String?, nextKey: String?) {
        viewModelScope.launch { repos.spots.move(id, previousKey, nextKey) }
    }

    /** Files a spot under another city or straight under a country, at the end of the spots there. */
    fun moveSpotTo(spotId: String, home: SpotHome) {
        viewModelScope.launch { repos.spots.moveTo(spotId, home) }
    }

    /**
     * Starts a route through a city with every spot nobody has visited yet already on it,
     * ordered so it opens as a sensible walk with no extra tap.
     */
    /** A new route through one of this country's cities, holding the spots chosen for it. */
    fun createRoute(cityId: String, title: String, spotIds: List<String>, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(repos.routes.create(cityId, title, spotIds)) }
    }
}

private fun countryUiState(data: CountryData, view: CountryView, pending: CountryPending): CountryUiState {
    val spotsByCity = data.spots.groupBy { it.spot.parentCityId }
    val sections = data.cities
        // High priority first, then each group in its own order: a city rises when its spots change.
        .sortedWith(
            compareBy<CityRow> { it.place.priority.ordinal }.thenBy { it.place.sortKey }.thenBy { it.place.meta.id },
        )
        .map { row ->
            val all = spotsByCity[row.place.meta.id].orEmpty()
            CitySection(
                city = row.place,
                spots = all.filter(view.filters::matches).sortedSpots(view.sortMode),
                spotCount = all.size,
                attachments = data.attachments.byCity[row.place.meta.id].orEmpty(),
                expanded = row.place.meta.id !in view.collapsed,
            )
        }
    val country = data.country
    val ownSpots = spotsByCity[""].orEmpty()
    return CountryUiState(
        country = country,
        countrySpots = ownSpots.filter(view.filters::matches).sortedSpots(view.sortMode),
        countrySpotCount = ownSpots.size,
        sections = sections,
        countryAttachments = data.attachments.country,
        spotAttachments = data.attachments.bySpot,
        targets = if (country == null) {
            emptyList()
        } else {
            // The flag says "the country itself" at a glance in a list of place names.
            val named = country.copy(name = "${CountryCatalog.flag(country.isoCode)} ${country.name}".trim())
            attachmentTargets(named, data.cities.map { it.place }, data.spots.map { it.spot })
        },
        homes = data.homes,
        filters = view.filters,
        sortMode = view.sortMode,
        savingPhoto = pending.uploads > 0,
        pendingCityDelete = pending.cityDelete,
        pendingSpotDelete = pending.spotDelete,
    )
}
