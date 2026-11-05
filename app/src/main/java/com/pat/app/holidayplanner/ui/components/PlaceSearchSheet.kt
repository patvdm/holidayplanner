package com.pat.app.holidayplanner.ui.components

import com.pat.app.holidayplanner.data.repo.OpeningHoursLookup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.repo.CityRepository
import com.pat.app.holidayplanner.data.repo.SpotRepository
import com.pat.app.holidayplanner.domain.CountryCatalog
import com.pat.app.holidayplanner.domain.GENERAL_CITY_NAME
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.ui.explore.CountryPickerSheet
import java.util.UUID
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val LIST_MAX_HEIGHT_DP = 360
private const val SHEET_SETTLE_MS = 250L

/** Enough of your own places to find the one you meant, without pushing Google's off the screen. */
private const val MAX_EXISTING_MATCHES = 6

/** Somewhere already saved that a share can be filed under: a spot, or a city itself. */
data class PlacePick(val id: String, val isCity: Boolean)

/** One of those, with the two lines its row shows. */
private data class Match(val pick: PlacePick, val primary: String, val secondary: String)

/**
 * Adds a spot by searching Google, or picks one that already exists.
 *
 * With [onPickExisting] set, the one search field looks through what is already saved as well,
 * spots and cities alike, and those are listed above Google's suggestions: a photo of somewhere
 * you already have is filed with one tap, and somewhere new is one tap further.
 *
 * The search is the fast path, but it is never the only path: with no API key, no connection, or a
 * spent quota, the same sheet lets the name be typed and saves a row without coordinates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSearchSheet(
    title: String = stringResource(R.string.place_search_title),
    onPickExisting: ((PlacePick) -> Unit)? = null,
    cityId: String = "",
    prefillName: String = "",
    knownLat: Double? = null,
    knownLng: Double? = null,
    knownAddress: String = "",
    knownMapsUrl: String = "",
    knownCityName: String = "",
    knownIsoCode: String = "",
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    // A view model of its own per opening. The sheet sits inside a screen, so one shared with that
    // screen would hand back the last search, and the city that search was opened for; it also
    // gives Google a fresh session token, which is what it bills a search as.
    val opening = remember { UUID.randomUUID().toString() }
    val viewModel: PlaceSearchViewModel = koinViewModel(key = opening) {
        parametersOf(
            PlaceSearchTarget(
                cityId, prefillName, knownLat, knownLng, knownAddress, knownMapsUrl, knownCityName, knownIsoCode,
            ),
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    // The sheet animates in, so the field is only ready to take focus a moment later.
    LaunchedEffect(Unit) {
        delay(SHEET_SETTLE_MS)
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        DropFocusWhileSheetCloses(sheetState)
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val pending = state.pending
            if (pending == null) {
                SearchStep(title, state, viewModel, focusRequester, onPickExisting, onSaved)
            } else {
                ConfirmStep(state, pending, viewModel, onSaved)
            }
        }
    }
}

@Composable
private fun SearchStep(
    title: String,
    state: PlaceSearchUiState,
    viewModel: PlaceSearchViewModel,
    focusRequester: FocusRequester,
    onPickExisting: ((PlacePick) -> Unit)?,
    onSaved: (String) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleMedium)

    OutlinedTextField(
        value = state.query,
        onValueChange = viewModel::onQueryChange,
        label = {
            Text(
                stringResource(
                    if (state.manual) R.string.label_name else R.string.place_search_label,
                ),
            )
        },
        placeholder = { if (state.manual) Text(GENERAL_CITY_NAME) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )

    ManualNotice(state)

    if (state.searching) CircularProgressIndicator()

    // Google says where a picked place belongs, so a home is only asked for when typing a name.
    if (state.needsHome && state.manual) HomeFields(state, viewModel)

    if (state.manual) {
        TypeChips(state.type, viewModel::onTypeChange)
        Button(
            onClick = { viewModel.saveManually(onSaved) },
            enabled = state.canSaveManually && !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_add))
        }
    } else {
        Results(state, viewModel, onPickExisting)
        if (state.query.isNotBlank()) {
            TextButton(onClick = { viewModel.saveManually(onSaved) }, enabled = state.canSaveManually) {
                Text(stringResource(R.string.place_search_use_typed))
            }
        }
    }
}

/** Why the search is off, when it is. */
@Composable
private fun ManualNotice(state: PlaceSearchUiState) {
    val reason = state.manualReason ?: return
    Text(
        text = stringResource(
            when (reason) {
                ManualReason.NO_KEY -> R.string.place_search_no_key
                ManualReason.SEARCH_FAILED -> R.string.place_search_failed
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    state.errorDetail?.let {
        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

/** Your own matching spots first, then what Google suggests. */
@Composable
private fun Results(
    state: PlaceSearchUiState,
    viewModel: PlaceSearchViewModel,
    onPickExisting: ((PlacePick) -> Unit)?,
) {
    val existing = if (onPickExisting == null) emptyList() else rememberExistingMatches(state.query)
    LazyColumn(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT_DP.dp)) {
        if (existing.isNotEmpty()) {
            item(key = "yours") { ListHeader(stringResource(R.string.place_search_yours)) }
            items(existing, key = { "saved-${it.pick.id}" }) { match ->
                ResultRow(primary = match.primary, secondary = match.secondary) {
                    onPickExisting?.invoke(match.pick)
                }
            }
        }
        if (existing.isNotEmpty() && state.predictions.isNotEmpty()) {
            item(key = "new") { ListHeader(stringResource(R.string.place_search_new)) }
        }
        items(state.predictions, key = { it.googlePlaceId }) { prediction ->
            ResultRow(primary = prediction.primaryText, secondary = prediction.secondaryText) {
                viewModel.pick(prediction)
            }
        }
    }
}

/**
 * The saved places whose name or whereabouts contain what was typed, a few at most. Spots lead,
 * because most things are about one; a city follows for the things that are about the place itself.
 */
@Composable
private fun rememberExistingMatches(
    query: String,
    spots: SpotRepository = koinInject(),
    cities: CityRepository = koinInject(),
): List<Match> {
    val spotRows by remember(spots) { spots.observePickerRows() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val cityRows by remember(cities) { cities.observeOptions() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    return remember(spotRows, cityRows, query) {
        val needle = query.trim()
        if (needle.isEmpty()) return@remember emptyList()
        val matchedSpots = spotRows
            .filter { row ->
                row.spotName.contains(needle, ignoreCase = true) ||
                    row.cityName.contains(needle, ignoreCase = true) ||
                    row.countryName.contains(needle, ignoreCase = true)
            }
            .map { Match(PlacePick(it.spotId, isCity = false), it.spotName, it.group) }
        val matchedCities = cityRows
            .filter { row ->
                row.cityName.contains(needle, ignoreCase = true) ||
                    row.countryName.contains(needle, ignoreCase = true)
            }
            .map { Match(PlacePick(it.cityId, isCity = true), it.cityName, it.countryName) }
        (matchedSpots + matchedCities).take(MAX_EXISTING_MATCHES)
    }
}

@Composable
private fun ListHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun ResultRow(primary: String, secondary: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(primary, style = MaterialTheme.typography.bodyLarge)
        if (secondary.isNotBlank()) {
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
}

/**
 * The parent for something that has no home yet, so it is never saved orphaned. A spot needs a
 * country, and a city only if one is named. Each is found or created by name, which is how adding
 * one can bring a new country along with it.
 */
@Composable
private fun HomeFields(state: PlaceSearchUiState, viewModel: PlaceSearchViewModel) {
    var pickingCountry by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { pickingCountry = true }, modifier = Modifier.fillMaxWidth()) {
        Text(
            state.country?.let { "${it.flag} ${it.name}" }
                ?: stringResource(R.string.place_search_choose_country),
        )
    }

    if (state.country != null) {
        CityField(
            value = state.homeCityName,
            onValueChange = viewModel::onHomeCityNameChange,
            label = stringResource(R.string.place_search_city),
        )
        if (state.knownCities.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.knownCities.forEach { name ->
                    SuggestionChip(
                        onClick = { viewModel.onHomeCityNameChange(name) },
                        label = { Text(name) },
                    )
                }
            }
        }
    }

    if (pickingCountry) {
        CountryPickerSheet(
            options = CountryCatalog.all(),
            onPick = {
                pickingCountry = false
                viewModel.onCountryChange(it)
            },
            onDismiss = { pickingCountry = false },
        )
    }
}

/** Shows where the pick will be filed, with a chance to correct the city before saving. */
@Composable
private fun ConfirmStep(
    state: PlaceSearchUiState,
    pending: PendingPick,
    viewModel: PlaceSearchViewModel,
    onSaved: (String) -> Unit,
    hours: OpeningHoursLookup = koinInject(),
) {
    val saving = state.saving
    Text(pending.name, style = MaterialTheme.typography.titleMedium)
    if (pending.details.address.isNotBlank()) {
        Text(
            text = pending.details.address,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (pending.hierarchy == null && state.needsHome) {
        // Google gave no usable address, so the spot still needs somewhere to live.
        HomeFields(state, viewModel)
    } else {
        Text(
            text = stringResource(R.string.place_search_filed_under, pending.breadcrumb),
            style = MaterialTheme.typography.bodyMedium,
        )
        CityField(
            value = pending.cityName,
            onValueChange = viewModel::onCityNameChange,
            label = stringResource(R.string.place_search_change_place),
        )
    }

    TypeChips(state.type, viewModel::onTypeChange)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                viewModel.confirm { id ->
                    // A place just picked from Google is one somebody chose, so its hours are worth one request now.
                    if (hours.isAvailable) hours.lookUp(id)
                    onSaved(id)
                }
            },
            enabled = !saving && state.canConfirm(pending),
        ) {
            Text(stringResource(R.string.action_save))
        }
        TextButton(onClick = viewModel::cancelPick) { Text(stringResource(R.string.action_back)) }
    }
}

/**
 * Where in the country a new spot goes. Blank keeps it on the country itself, so the cross that
 * empties it in one tap is how a spot is filed with no city.
 */
@Composable
private fun CityField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.place_search_whole_country)) },
        supportingText = { Text(stringResource(R.string.place_search_city_hint)) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** POI, food or a bed, picked with one tap while the spot is being added. */
@Composable
private fun TypeChips(selected: SpotType, onSelect: (SpotType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpotType.entries.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = { Text(stringResource(type.label())) },
            )
        }
    }
}
