package com.pat.app.holidayplanner.ui.explore

import com.pat.app.holidayplanner.data.remote.SpotPictureQuery
import com.pat.app.holidayplanner.domain.PictureOrigin
import com.pat.app.holidayplanner.data.repo.GalleryPicture
import com.pat.app.holidayplanner.data.repo.spotPictureQuery
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.local.SpotHome
import com.pat.app.holidayplanner.data.local.SpotContext
import com.pat.app.holidayplanner.data.local.SpotEntity
import android.net.Uri
import com.pat.app.holidayplanner.data.repo.LinkRepository
import com.pat.app.holidayplanner.data.repo.AttachmentOwner
import com.pat.app.holidayplanner.data.repo.AttachmentRepository
import com.pat.app.holidayplanner.data.repo.AttachmentTarget
import com.pat.app.holidayplanner.data.repo.CityRepository
import com.pat.app.holidayplanner.data.repo.SpotRepository
import com.pat.app.holidayplanner.data.repo.UserRepository
import com.pat.app.holidayplanner.domain.LinkSource
import com.pat.app.holidayplanner.domain.MapsUrls
import com.pat.app.holidayplanner.domain.ShareParser
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotMark
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

data class SpotDetailUiState(
    val spot: SpotEntity? = null,
    val context: SpotContext? = null,
    val links: List<LinkEntity> = emptyList(),
    /** Every country and city this spot could be moved to. */
    val homes: List<SpotHome> = emptyList(),
    /** Everywhere in this spot's country a photo or link of it could be moved to. */
    val targets: List<AttachmentTarget> = emptyList(),
    val currentUserId: String = "",
    /** True while a picture is on its way to the backend, which is the one thing here that waits. */
    val savingPhoto: Boolean = false,
) {
    /** Photos are links too, but they belong in the roll at the top rather than the list below. */
    val photos: List<LinkEntity> get() = links.filter { it.source.isPhoto }

    val references: List<LinkEntity> get() = links.filterNot { it.source.isPhoto }

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

    /** What the picture chooser and the strip search for: the spot, where it is, and its own websites. */
    fun pictureQuery(): SpotPictureQuery = spotPictureQuery(spot, context, references)
}

/** Everything the spot screen shows, combined separately to stay within combine's arity. */
private data class SpotBundle(
    val links: List<LinkEntity>,
    val homes: List<SpotHome>,
    val targets: List<AttachmentTarget>,
)

class SpotDetailViewModel(
    private val spotId: String,
    private val spotRepository: SpotRepository,
    private val linkRepository: LinkRepository,
    private val attachments: AttachmentRepository,
    cityRepository: CityRepository,
    userRepository: UserRepository,
) : ViewModel() {

    private val savingPhoto = MutableStateFlow(false)

    private val context: Flow<SpotContext?> = spotRepository.observeContext(spotId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val targets: Flow<List<AttachmentTarget>> = context
        .map { it?.countryId }
        .distinctUntilChanged()
        .flatMapLatest(attachments::observeTargetsOrNone)

    private val bundle: Flow<SpotBundle> = combine(
        linkRepository.observeLinks(spotId),
        cityRepository.observeHomes(),
        targets,
        ::SpotBundle,
    )

    val uiState: StateFlow<SpotDetailUiState> = combine(
        spotRepository.observeSpot(spotId),
        context,
        bundle,
        userRepository.currentUserId,
        savingPhoto,
    ) { spot, context, parts, userId, saving ->
        SpotDetailUiState(
            spot = spot,
            context = context,
            links = parts.links,
            homes = parts.homes,
            targets = parts.targets,
            currentUserId = userId.orEmpty(),
            savingPhoto = saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SpotDetailUiState())

    fun rename(name: String) {
        viewModelScope.launch { spotRepository.rename(spotId, name) }
    }

    fun setNotes(notes: String) {
        viewModelScope.launch { spotRepository.setNotes(spotId, notes) }
    }

    fun setMark(mark: SpotMark, on: Boolean) {
        viewModelScope.launch { spotRepository.setMark(spotId, mark, on) }
    }

    fun setStatus(status: SpotStatus) {
        viewModelScope.launch { spotRepository.setStatus(spotId, status) }
    }

    /** Null clears it, because "no type" is a real answer rather than a missing one. */
    fun setType(type: SpotType) {
        viewModelScope.launch { spotRepository.setType(spotId, type) }
    }

    fun setPriority(priority: Priority) {
        viewModelScope.launch { spotRepository.setPriority(spotId, priority) }
    }


    fun moveTo(home: SpotHome) {
        viewModelScope.launch { spotRepository.moveTo(spotId, home) }
    }

    /**
     * Pasting a link attaches it to this spot. A Maps link is not a link in that sense: a spot has
     * one location, so it replaces the spot's Maps link rather than joining a list.
     */
    fun addLink(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        val source = ShareParser.sourceOf(trimmed)
        viewModelScope.launch {
            if (source == LinkSource.MAPS) {
                spotRepository.setMapsUrl(spotId, trimmed)
            } else {
                linkRepository.add(url = trimmed, source = source, owner = AttachmentOwner(spotId = spotId))
            }
        }
    }

    /** Blank lets the automatic search have another go. */
    fun setImageUrl(url: String) {
        viewModelScope.launch { spotRepository.setPicture(spotId, url, PictureOrigin.CHOSEN) }
    }

    fun setAddress(address: String) {
        viewModelScope.launch { spotRepository.setAddress(spotId, address) }
    }

    /** A picture from the gallery. Unlike every other edit this one needs the network. */
    fun addPhoto(uri: Uri) {
        viewModelScope.launch {
            savingPhoto.value = true
            attachments.attach(uri, AttachmentOwner(spotId = spotId))
            savingPhoto.value = false
        }
    }

    /** Hangs a photo or a link of this spot on something else in its country. */
    fun moveAttachment(attachment: LinkEntity, owner: AttachmentOwner) {
        viewModelScope.launch { attachments.moveTo(attachment, owner) }
    }

    /** Makes a picture of the strip the main one: a photo of this spot, or one that was found for it. */
    fun useAsCover(picture: GalleryPicture) {
        val photo = picture.photo
        if (photo != null) useAsPicture(photo) else setImageUrl(picture.url)
    }

    /** Shows a photo as this spot's main picture. */
    fun useAsPicture(photo: LinkEntity) {
        viewModelScope.launch { attachments.useAsPicture(photo) }
    }

    /** A photo or a link: a photo goes from the backend's folder too. */
    fun deleteAttachment(attachment: LinkEntity) {
        viewModelScope.launch { attachments.remove(attachment) }
    }


    fun delete() {
        viewModelScope.launch { spotRepository.delete(spotId) }
    }
}
