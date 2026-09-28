package com.pat.app.holidayplanner.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.remote.PlaceDetails
import com.pat.app.holidayplanner.data.remote.PlacePrediction
import com.pat.app.holidayplanner.data.remote.PlacesSearch
import com.pat.app.holidayplanner.data.remote.PlacesSession
import com.pat.app.holidayplanner.data.repo.CountryRepository
import com.pat.app.holidayplanner.data.repo.HierarchyResolver
import com.pat.app.holidayplanner.data.repo.CityRepository
import com.pat.app.holidayplanner.data.repo.SpotRepository
import com.pat.app.holidayplanner.domain.AddressComponents
import com.pat.app.holidayplanner.domain.CountryOption
import com.pat.app.holidayplanner.domain.GENERAL_CITY_NAME
import com.pat.app.holidayplanner.domain.MapsUrls
import com.pat.app.holidayplanner.domain.countryOrDefault
import com.pat.app.holidayplanner.domain.PlaceHierarchy
import com.pat.app.holidayplanner.domain.SpotType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DEBOUNCE_MS = 300L
private const val MIN_QUERY_CHARS = 2

/**
 * Where the spot being added goes. [cityId] is the place being viewed; when it is empty the spot
 * has no home yet and the sheet has to ask for one, or the spot would belong nowhere and never
 * appear in any list.
 */
data class PlaceSearchTarget(
    val cityId: String = "",
    /** Starting text, used when a Maps link already told us the name. */
    val prefillName: String = "",
    /** Where to look first, and the coordinates a manually saved spot keeps. */
    val knownLat: Double? = null,
    val knownLng: Double? = null,
    /** Carried straight onto the spot when a Maps link was shared. */
    val knownAddress: String = "",
    val knownMapsUrl: String = "",
    /** Prefills the city field, so a shared place needs no typing at all. */
    val knownCityName: String = "",
    /** ISO code of the country a shared place sits in, preselected over the default. */
    val knownIsoCode: String = "",
)

/** Where the sheet will file what was picked, shown for confirmation before anything is saved. */
data class PendingPick(
    val name: String,
    val details: PlaceDetails,
    val hierarchy: PlaceHierarchy?,
    val cityName: String,
) {
    val breadcrumb: String get() = listOfNotNull(hierarchy?.countryName, cityName.ifBlank { null })
        .joinToString(" › ")
}

/** The city a new spot goes in, or the country when there is no city. */
private data class SpotHomeIds(val cityId: String = "", val countryId: String = "")

/** Why the sheet dropped to typing a name. The screen turns this into wording. */
enum class ManualReason {
    NO_KEY,
    SEARCH_FAILED,
}

data class PlaceSearchUiState(
    val query: String = "",
    val predictions: List<PlacePrediction> = emptyList(),
    val searching: Boolean = false,
    /** True when there is no key, or the API refused. The person types a name instead. */
    val manual: Boolean = false,
    val manualReason: ManualReason? = null,
    /** The raw failure from Google, shown as a detail line. Not user-facing wording. */
    val errorDetail: String? = null,
    val pending: PendingPick? = null,
    val saving: Boolean = false,
    /** Set when what is being added has no parent yet, so a country has to be chosen. */
    val needsHome: Boolean = false,
    val country: CountryOption? = null,
    val homeCityName: String = "",
    /** Cities already known in the chosen country, offered so a second spot reuses the first's city. */
    val knownCities: List<String> = emptyList(),
    /** What kind of spot is being added, chosen with one tap before saving. */
    val type: SpotType = SpotType.POI,
) {
    /** Only the country is needed: a blank city files the spot under the country itself. */
    private val homeChosen: Boolean get() = country != null

    /** A blank name becomes the catch-all too, so something can always be filed and renamed later. */
    val canSaveManually: Boolean get() = !needsHome || homeChosen

    /** A Google pick still needs a home when Google's own address carried no country. */
    fun canConfirm(pick: PendingPick): Boolean = pick.hierarchy != null || !needsHome || homeChosen
}

/**
 * Drives the place search sheet.
 *
 * Typing is debounced and short queries are ignored, because every call costs money. A pick fetches
 * the details with the same session token, which is what makes Google bill one session rather than
 * one request per keystroke. Any refusal switches the sheet to manual entry rather than failing,
 * so a spot can always be added.
 */
class PlaceSearchViewModel(
    private val target: PlaceSearchTarget,
    private val search: PlacesSearch,
    private val countryRepository: CountryRepository,
    private val cityRepository: CityRepository,
    private val spotRepository: SpotRepository,
    private val resolver: HierarchyResolver,
) : ViewModel() {

    /** A spot saved without a city is invisible everywhere, so the sheet collects one first. */
    private val needsHome = target.cityId.isEmpty()

    private val state = MutableStateFlow(
        PlaceSearchUiState(
            query = target.prefillName,
            manual = !search.isConfigured,
            needsHome = needsHome,
            // Preselected so a spot can be filed without a decision when nothing is known about it.
            country = if (needsHome) countryOrDefault(target.knownIsoCode) else null,
            homeCityName = target.knownCityName,
        ),
    )
    val uiState = state.asStateFlow()

    private val queryFlow = MutableStateFlow(target.prefillName)
    private val session: PlacesSession = search.newSession()
    private var biasLat: Double? = target.knownLat
    private var biasLng: Double? = target.knownLng

    init {
        if (!search.isConfigured) {
            state.update { it.copy(manualReason = ManualReason.NO_KEY) }
        }
        state.value.country?.let { loadKnownPlaces(it.isoCode) }
        loadBias()
        observeQuery()
    }

    fun onQueryChange(value: String) {
        state.update { it.copy(query = value) }
        queryFlow.value = value
    }

    /** Lets the person correct where a spot will be filed before it is saved. */
    fun onCityNameChange(value: String) {
        state.update { current -> current.copy(pending = current.pending?.copy(cityName = value)) }
    }

    fun onCountryChange(option: CountryOption) {
        state.update { it.copy(country = option) }
        loadKnownPlaces(option.isoCode)
    }

    fun onHomeCityNameChange(value: String) {
        state.update { it.copy(homeCityName = value) }
    }

    fun onTypeChange(value: SpotType) {
        state.update { it.copy(type = value) }
    }

    fun pick(prediction: PlacePrediction) {
        viewModelScope.launch {
            state.update { it.copy(searching = true) }
            search.details(prediction.googlePlaceId, session).fold(
                onSuccess = { details ->
                    state.update { it.copy(searching = false, pending = pendingFor(prediction, details)) }
                },
                onFailure = { error ->
                    state.update {
                        it.copy(
                            searching = false,
                            manual = true,
                            manualReason = ManualReason.SEARCH_FAILED,
                            errorDetail = error.message,
                        )
                    }
                },
            )
        }
    }

    fun cancelPick() {
        state.update { it.copy(pending = null) }
    }

    /** Saves what was picked and returns the new row's id. */
    fun confirm(onSaved: (String) -> Unit) {
        val pending = state.value.pending ?: return
        if (!state.value.canConfirm(pending)) return
        viewModelScope.launch {
            state.update { it.copy(saving = true) }
            onSaved(save(pending))
        }
    }

    /** Saves a typed name with no coordinates, which the Maps link handles as a text query. */
    fun saveManually(onSaved: (String) -> Unit) {
        val current = state.value
        if (!current.canSaveManually) return
        viewModelScope.launch {
            state.update { it.copy(saving = true) }
            val name = current.query.trim().ifEmpty { GENERAL_CITY_NAME }
            // Coordinates from a Maps link survive even when the name was typed by hand.
            val home = homeOf(current)
            val id = spotRepository.add(
                cityId = home.cityId,
                countryId = home.countryId,
                name = name,
                lat = target.knownLat,
                lng = target.knownLng,
                address = target.knownAddress,
                mapsUrl = target.knownMapsUrl,
                type = current.type,
            )
            onSaved(id)
        }
    }

    private suspend fun save(pending: PendingPick): String {
        // Google decides which country and city this belongs to, not the screen it was opened from.
        val home = pending.hierarchy
            ?.let { resolver.resolve(it, pending.cityName) }
            ?.let { SpotHomeIds(cityId = it.cityId, countryId = it.countryId) }
            ?: homeOf(state.value)
        return spotRepository.add(
            cityId = home.cityId,
            countryId = home.countryId,
            name = pending.name,
            type = state.value.type,
            googlePlaceId = pending.details.googlePlaceId,
            lat = pending.details.lat,
            lng = pending.details.lng,
            address = pending.details.address,
            // Every spot carries a Maps link. Google gave us the place, so build the link to it.
            mapsUrl = MapsUrls.spot(
                lat = pending.details.lat,
                lng = pending.details.lng,
                googlePlaceId = pending.details.googlePlaceId,
                name = pending.name,
            ),
        )
    }

    /**
     * Where a spot will live. A city passed in wins. Otherwise the chosen country and city are found
     * or created, which is how a brand new spot can bring a new country along with it; with no city
     * named it lives on the country itself.
     */
    private suspend fun homeOf(current: PlaceSearchUiState): SpotHomeIds {
        if (target.cityId.isNotEmpty()) return SpotHomeIds(cityId = target.cityId)
        val country = current.country ?: return SpotHomeIds()
        val resolved = resolver.resolve(
            PlaceHierarchy(
                isoCode = country.isoCode,
                countryName = country.name,
                cityName = current.homeCityName.trim(),
            ),
        )
        return SpotHomeIds(cityId = resolved.cityId, countryId = resolved.countryId)
    }

    private fun pendingFor(prediction: PlacePrediction, details: PlaceDetails): PendingPick {
        val hierarchy = AddressComponents.resolve(details.components)
        return PendingPick(
            name = prediction.primaryText,
            details = details,
            hierarchy = hierarchy,
            cityName = hierarchy?.cityName.orEmpty(),
        )
    }

    private fun loadKnownPlaces(isoCode: String) {
        viewModelScope.launch {
            val country = countryRepository.findByIsoCode(isoCode)
            val names = country
                ?.let { cityRepository.observeRows(it.meta.id).map { rows -> rows.map { row -> row.place.name } } }
                ?.first()
                .orEmpty()
            state.update { it.copy(knownCities = names) }
        }
    }

    private fun loadBias() {
        if (target.knownLat != null && target.knownLng != null) return
        val id = target.cityId.ifEmpty { return }
        viewModelScope.launch {
            val place = cityRepository.find(id) ?: return@launch
            biasLat = place.lat
            biasLng = place.lng
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeQuery() {
        viewModelScope.launch {
            queryFlow.debounce(DEBOUNCE_MS)
                .map(String::trim)
                .distinctUntilChanged()
                .collectLatest { query -> runSearch(query) }
        }
    }

    private suspend fun runSearch(query: String) {
        if (state.value.manual) return
        if (query.length < MIN_QUERY_CHARS) {
            state.update { it.copy(predictions = emptyList(), searching = false) }
            return
        }
        state.update { it.copy(searching = true) }
        search.predictions(query, session, biasLat, biasLng).fold(
            onSuccess = { found ->
                state.update { it.copy(predictions = found, searching = false, errorDetail = null) }
            },
            onFailure = { error ->
                state.update {
                    it.copy(
                        searching = false,
                        manual = true,
                        manualReason = ManualReason.SEARCH_FAILED,
                        errorDetail = error.message,
                        predictions = emptyList(),
                    )
                }
            },
        )
    }
}
