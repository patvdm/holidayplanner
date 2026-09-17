package com.pat.app.holidayplanner.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.SpotContext
import com.pat.app.holidayplanner.data.location.ReverseGeocoder
import com.pat.app.holidayplanner.data.remote.LinkResolver
import com.pat.app.holidayplanner.data.repo.LinkRepository
import com.pat.app.holidayplanner.data.repo.SpotRepository
import com.pat.app.holidayplanner.domain.AddressText
import com.pat.app.holidayplanner.domain.MapsLinkParser
import com.pat.app.holidayplanner.domain.ShareParser
import com.pat.app.holidayplanner.domain.SharedLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

/** Where a link that was already shared ended up, so the dialog can say so. */
data class ExistingLink(val linkId: String, val url: String, val where: String?)

sealed interface ShareState {
    data object Reading : ShareState

    /** The share had no link in it at all. */
    data object NoLink : ShareState

    /**
     * Ready to save, with a note. Maps links are the same state: [SharedLink.isMapsLink] decides
     * the wording, so there is one path through the dialog rather than two that can drift apart.
     */
    data class Ready(
        val link: SharedLink,
        val note: String = "",
        /** Why a Maps link did not become a spot, shown so a failure is diagnosable rather than mute. */
        val mapsIssue: String? = null,
    ) : ShareState

    /** The same link is already saved, so offer to open it rather than saving it twice. */
    data class Duplicate(val existing: ExistingLink) : ShareState

    /** Following a shortened Maps link to find out which place it is. */
    data object Resolving : ShareState

    /** A Maps link becomes a spot, named after the place, holding the link and the address. */
    data class CreatingSpot(val spot: SharedSpot) : ShareState

    /**
     * Asking which spot the link belongs to, without leaving the other app.
     *
     * Nothing is written yet: a link with nowhere to live is not worth keeping, so backing out here
     * saves nothing rather than leaving something to tidy up later.
     */
    data class Filing(val link: SharedLink, val note: String) : ShareState

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
    private val spotRepository: SpotRepository,
    private val linkResolver: LinkResolver,
    private val reverseGeocoder: ReverseGeocoder,
) : ViewModel() {

    private val state = MutableStateFlow<ShareState>(ShareState.Reading)
    val uiState: StateFlow<ShareState> = state.asStateFlow()

    private var sharedText: String? = null

    fun onShared(text: String?) {
        sharedText = text
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
                else -> ShareState.Ready(shared)
            }
            if (state.value == ShareState.Resolving) resolveMapsLink(shared)
        }
    }

    /**
     * Follows the short link and reads the place out of the real address. If that fails, the link
     * is offered as an ordinary one rather than lost.
     */
    private suspend fun resolveMapsLink(shared: SharedLink) {
        val outcome = linkResolver.resolve(shared.url)
        val resolved = outcome.getOrNull()
        if (resolved == null) {
            val reason = outcome.exceptionOrNull()?.message.orEmpty()
            state.value = ShareState.Ready(shared, mapsIssue = reason)
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
                "No place in ${resolved.url}"
            } else {
                "Did not redirect: HTTP ${resolved.status} after ${resolved.hops} hops"
            }
            ShareState.Ready(shared, mapsIssue = detail)
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

    fun onNoteChange(value: String) {
        state.update { current -> if (current is ShareState.Ready) current.copy(note = value) else current }
    }

    /** Moves on to the only question that matters: which spot is this link about. */
    fun save() {
        val ready = state.value as? ShareState.Ready ?: return
        state.value = ShareState.Filing(ready.link, ready.note)
    }

    /** Writes the link against the spot it belongs to, which is the only way it gets written. */
    fun fileUnder(spotId: String) {
        val filing = state.value as? ShareState.Filing ?: return
        state.value = ShareState.Saved
        viewModelScope.launch {
            linkRepository.add(
                url = filing.link.url,
                source = filing.link.source,
                note = filing.note,
                spotId = spotId,
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
