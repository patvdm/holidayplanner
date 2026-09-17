package com.pat.app.holidayplanner.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.HeartRow
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.local.CityOption
import com.pat.app.holidayplanner.data.local.SpotContext
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.repo.LinkRepository
import com.pat.app.holidayplanner.data.repo.CityRepository
import com.pat.app.holidayplanner.data.repo.SpotRepository
import com.pat.app.holidayplanner.data.repo.UserRepository
import com.pat.app.holidayplanner.domain.LinkSource
import com.pat.app.holidayplanner.domain.MapsUrls
import com.pat.app.holidayplanner.domain.ShareParser
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

data class SpotDetailUiState(
    val spot: SpotEntity? = null,
    val context: SpotContext? = null,
    val hearts: List<HeartRow> = emptyList(),
    val links: List<LinkEntity> = emptyList(),
    val cities: List<CityOption> = emptyList(),
    val currentUserId: String = "",
) {
    val heartedByMe: Boolean get() = hearts.any { it.userId == currentUserId }

    /**
     * The link Open in Maps uses: the one that was shared if there is one, because it points at the
     * exact place Google meant, otherwise one built from whatever the spot knows.
     */
    val mapsUrl: String
        get() = spot?.mapsUrl?.takeIf { it.isNotBlank() }
            ?: spot?.let {
                MapsUrls.spot(
                    lat = it.lat,
                    lng = it.lng,
                    googlePlaceId = it.googlePlaceId,
                    name = it.name,
                    cityName = context?.cityName.orEmpty(),
                    countryName = context?.countryName.orEmpty(),
                )
            }.orEmpty()

    /** True when the spot carries a shared Maps link of its own, rather than a derived one. */
    val hasOwnMapsLink: Boolean get() = spot?.mapsUrl?.isNotBlank() == true
}

/** Everything the spot screen shows, combined separately to stay within combine's arity. */
private data class SpotBundle(
    val hearts: List<HeartRow>,
    val links: List<LinkEntity>,
    val cities: List<CityOption>,
)

class SpotDetailViewModel(
    private val spotId: String,
    private val spotRepository: SpotRepository,
    private val linkRepository: LinkRepository,
    cityRepository: CityRepository,
    userRepository: UserRepository,
) : ViewModel() {

    private val bundle: Flow<SpotBundle> = combine(
        spotRepository.observeHeartNames(spotId),
        linkRepository.observeLinks(spotId),
        cityRepository.observeOptions(),
        ::SpotBundle,
    )

    val uiState: StateFlow<SpotDetailUiState> = combine(
        spotRepository.observeSpot(spotId),
        spotRepository.observeContext(spotId),
        bundle,
        userRepository.currentUserId,
    ) { spot, context, parts, userId ->
        SpotDetailUiState(
            spot = spot,
            context = context,
            hearts = parts.hearts,
            links = parts.links,
            cities = parts.cities,
            currentUserId = userId.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SpotDetailUiState())

    fun rename(name: String) {
        viewModelScope.launch { spotRepository.rename(spotId, name) }
    }

    fun setNotes(notes: String) {
        viewModelScope.launch { spotRepository.setNotes(spotId, notes) }
    }

    fun setTags(tags: String) {
        viewModelScope.launch { spotRepository.setTags(spotId, tags.split(',')) }
    }

    fun setStatus(status: SpotStatus) {
        viewModelScope.launch { spotRepository.setStatus(spotId, status) }
    }

    /** Null clears it, because "no type" is a real answer rather than a missing one. */
    fun setType(type: SpotType?) {
        viewModelScope.launch { spotRepository.setType(spotId, type) }
    }

    fun setPriority(priority: Priority) {
        viewModelScope.launch { spotRepository.setPriority(spotId, priority) }
    }

    fun toggleHeart() {
        viewModelScope.launch { spotRepository.toggleHeart(spotId) }
    }

    fun moveToPlace(cityId: String) {
        viewModelScope.launch { spotRepository.moveToPlace(spotId, cityId) }
    }

    /**
     * Pasting a link attaches it to this spot. A Maps link is not a link in that sense: a spot has
     * one location, so it replaces the spot's Maps link rather than joining a list.
     */
    fun addLink(url: String, note: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        val source = ShareParser.sourceOf(trimmed)
        viewModelScope.launch {
            if (source == LinkSource.MAPS) {
                spotRepository.setMapsUrl(spotId, trimmed)
            } else {
                linkRepository.add(url = trimmed, source = source, note = note, spotId = spotId)
            }
        }
    }

    fun setMapsUrl(url: String) {
        viewModelScope.launch { spotRepository.setMapsUrl(spotId, url) }
    }

    fun clearMapsUrl() {
        viewModelScope.launch { spotRepository.setMapsUrl(spotId, "") }
    }

    fun setAddress(address: String) {
        viewModelScope.launch { spotRepository.setAddress(spotId, address) }
    }

    fun deleteLink(linkId: String) {
        viewModelScope.launch { linkRepository.delete(linkId) }
    }


    fun delete() {
        viewModelScope.launch { spotRepository.delete(spotId) }
    }
}
