package com.pat.app.holidayplanner.ui.share

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.SpotContext
import com.pat.app.holidayplanner.data.location.ReverseGeocoder
import com.pat.app.holidayplanner.data.remote.LinkResolver
import com.pat.app.holidayplanner.data.repo.LinkRepository
import com.pat.app.holidayplanner.data.repo.AttachmentRepository
import com.pat.app.holidayplanner.data.repo.SpotRepository
import com.pat.app.holidayplanner.domain.AddressText
import com.pat.app.holidayplanner.domain.MapsLinkParser
import com.pat.app.holidayplanner.domain.ShareParser
import com.pat.app.holidayplanner.domain.SharedLink
import com.pat.app.holidayplanner.ui.components.PlacePick
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "ShareViewModel"

/**
 * A place shared from Maps, ready to become a spot.
 *
 * The name is the place's own name rather than its address: Maps puts the name on the first line of
 * what it shares, and the address on the second, which is exactly the split a spot wants.
 */
data class SharedSpot(
    val name: String,
    val address: String,
    /** Guessed from the address, so the city field arrives filled in. */
    val cityName: String,
    /** ISO code of the country, so a place abroad does not land under home. */
    val isoCode: String,
    val lat: Double?,
    val lng: Double?,
    val mapsUrl: String,
)

/** A pick, as the two repositories want it: one id set, the other empty. */
private val PlacePick.spotId: String get() = if (isCity) "" else id

private val PlacePick.cityId: String get() = if (isCity) id else ""

/** Where a link that was already shared ended up, so the dialog can say so. */
data class ExistingLink(val linkId: String, val url: String, val where: String?)

sealed interface ShareState {
    data object Reading : ShareState

    /** The share had no link in it at all. */
    data object NoLink : ShareState

    /** The same link is already saved, so offer to open it rather than saving it twice. */
    data class Duplicate(val existing: ExistingLink) : ShareState

    /** Following a shortened Maps link to find out which place it is. */
    data object Resolving : ShareState

    /** A Maps link becomes a spot, named after the place, holding the link and the address. */
    data class CreatingSpot(val spot: SharedSpot) : ShareState

    /** A photo waiting to be hung on a spot. Nothing is stored until one is chosen. */
    data class FilingPhoto(val uri: Uri) : ShareState

    /** The one thing here that cannot be done offline: the picture itself has to reach Drive. */
    data object Uploading : ShareState

    /** Something went wrong that is worth saying out loud rather than closing over. */
    data class Problem(val message: String) : ShareState

    /**
     * Asking which spot the link belongs to, without leaving the other app. This is the first and
     * only question for anything that is not a Maps link: pick a spot, or find a new one.
     *
     * Nothing is written yet: a link with nowhere to live is not worth keeping, so backing out here
     * saves nothing rather than leaving something to tidy up later.
     */
    data class Filing(val link: SharedLink) : ShareState

    data object Saved : ShareState
}

/**
 * Handles one incoming share.
 *
 * The dialog has to be quick, so nothing here waits on the network: the link is parsed, checked
 * against what is already saved, and written locally. Sync picks it up a few seconds later.
 */
class ShareViewModel(
    private val linkRepository: LinkRepository,
    private val attachments: AttachmentRepository,
    private val spotRepository: SpotRepository,
    private val linkResolver: LinkResolver,
    private val reverseGeocoder: ReverseGeocoder,
) : ViewModel() {

    private val state = MutableStateFlow<ShareState>(ShareState.Reading)
    val uiState: StateFlow<ShareState> = state.asStateFlow()

    private var sharedText: String? = null

    fun onShared(text: String?, imageUri: Uri? = null) {
        sharedText = text
        if (imageUri != null) {
            state.value = ShareState.FilingPhoto(imageUri)
            return
        }
        val shared = ShareParser.parse(text)
        if (shared == null) {
            state.value = ShareState.NoLink
            return
        }
        viewModelScope.launch {
            val existing = linkRepository.findByUrl(shared.url)
            state.value = when {
                existing != null -> ShareState.Duplicate(describe(existing.meta.id, existing.url, existing.spotId))
                shared.isMapsLink -> ShareState.Resolving
                else -> ShareState.Filing(shared)
            }
            if (state.value == ShareState.Resolving) resolveMapsLink(shared)
        }
    }

    /**
     * Follows the short link and reads the place out of the real address. If that fails, the link
     * is filed under a spot like any other rather than lost; the reason goes to the log.
     */
    private suspend fun resolveMapsLink(shared: SharedLink) {
        val outcome = linkResolver.resolve(shared.url)
        val resolved = outcome.getOrNull()
        if (resolved == null) {
            Log.w(TAG, "could not follow ${shared.url}: ${outcome.exceptionOrNull()?.message}")
            state.value = ShareState.Filing(shared)
            return
        }
        val place = MapsLinkParser.parse(resolved.url)
        val lines = ShareParser.textLines(sharedText)

        // Maps writes a name then an address for a business, but sharing a dropped pin gives only
        // the address, and in that case the URL's own name is the address too. So take whichever
        // candidate is not an address as the name, and cut a short one out of the address otherwise.
        val candidates = listOfNotNull(lines.firstOrNull(), place.name).filter { it.isNotBlank() }
        val address = candidates.firstOrNull(AddressText::looksLikeAddress)
            ?: lines.getOrNull(1).orEmpty()
        val name = candidates.firstOrNull { !AddressText.looksLikeAddress(it) }
            ?: AddressText.nameFrom(address)
        // Coordinates know which country this is. An address only says so when Google felt like it.
        val located = place.takeIf { it.hasCoordinates }
            ?.let { reverseGeocoder.lookup(it.lat!!, it.lng!!) }
        val isoCode = located?.isoCode?.takeIf { it.isNotBlank() }
            ?: AddressText.countryFrom(address)
        val cityName = AddressText.cityFrom(address)
            .ifBlank { located?.cityName.orEmpty() }

        state.value = if (place.isEmpty && name.isBlank()) {
            // Either it never redirected, or the address shape is one the parser has not met.
            val detail = if (resolved.movedFrom(shared.url)) {
                "no place in ${resolved.url}"
            } else {
                "did not redirect: HTTP ${resolved.status} after ${resolved.hops} hops"
            }
            Log.w(TAG, "Maps link kept as a plain link: $detail")
            ShareState.Filing(shared)
        } else {
            ShareState.CreatingSpot(
                SharedSpot(
                    name = name,
                    address = address,
                    cityName = cityName,
                    isoCode = isoCode,
                    lat = place.lat,
                    lng = place.lng,
                    // The link people actually shared, kept whole rather than rebuilt from coordinates.
                    mapsUrl = resolved.url,
                ),
            )
        }
    }

    /** The spot made from a Maps link needs nothing else saved, so the dialog is done. */
    fun spotCreated() {
        state.value = ShareState.Saved
    }

    /** Sends the shared photo to the backend and hangs it on the spot that was chosen. */
    fun attachPhoto(pick: PlacePick) {
        val filing = state.value as? ShareState.FilingPhoto ?: return
        state.value = ShareState.Uploading
        viewModelScope.launch {
            attachments.attach(filing.uri, spotId = pick.spotId, cityId = pick.cityId)
                .onSuccess { state.value = ShareState.Saved }
                .onFailure { state.value = ShareState.Problem(it.message.orEmpty()) }
        }
    }

    /** Writes the link against the spot it belongs to, which is the only way it gets written. */
    fun fileUnder(pick: PlacePick) {
        val filing = state.value as? ShareState.Filing ?: return
        state.value = ShareState.Saved
        viewModelScope.launch {
            linkRepository.add(
                url = filing.link.url,
                source = filing.link.source,
                spotId = pick.spotId,
                cityId = pick.cityId,
            )
        }
    }

    /** Backing out before a spot was chosen. Nothing was written, so nothing is left behind. */
    fun cancelFiling() {
        state.value = ShareState.Saved
    }

    /** A duplicate is worth locating: "already on Mauritshuis" beats a bare "already saved". */
    private suspend fun describe(linkId: String, url: String, spotId: String): ExistingLink {
        if (spotId.isEmpty()) return ExistingLink(linkId = linkId, url = url, where = null)
        val context = spotRepository.observeContext(spotId).first()
        val spotName = spotRepository.observeSpot(spotId).first()?.name
        return ExistingLink(linkId = linkId, url = url, where = breadcrumb(context, spotName))
    }

    private fun breadcrumb(context: SpotContext?, spotName: String?): String? =
        listOfNotNull(context?.countryName, context?.cityName, spotName)
            .filter { it.isNotBlank() }
            .joinToString(" › ")
            .takeIf { it.isNotBlank() }
}
