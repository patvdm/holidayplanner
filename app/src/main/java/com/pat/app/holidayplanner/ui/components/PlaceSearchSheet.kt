package com.pat.app.holidayplanner.ui.components

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.pat.app.holidayplanner.data.remote.PlaceSearchMode
import com.pat.app.holidayplanner.domain.CountryCatalog
import com.pat.app.holidayplanner.domain.GENERAL_CITY_NAME
import com.pat.app.holidayplanner.ui.explore.CountryPickerSheet
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val LIST_MAX_HEIGHT_DP = 360
private const val SHEET_SETTLE_MS = 250L

/**
 * Adds a place or a spot, by searching Google or by typing a name.
 *
 * The search is the fast path, but it is never the only path: with no API key, no connection, or a
 * spent quota, the same sheet lets the name be typed and saves a row without coordinates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSearchSheet(
    mode: PlaceSearchMode,
    countryId: String = "",
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
    viewModel: PlaceSearchViewModel = koinViewModel {
        parametersOf(
            PlaceSearchTarget(
                mode, countryId, cityId, prefillName, knownLat, knownLng, knownAddress, knownMapsUrl,
                knownCityName, knownIsoCode,
            ),
        )
    },
) {
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
                SearchStep(mode, state, viewModel, focusRequester, onSaved)
            } else {
                ConfirmStep(mode, state, pending, viewModel, onSaved)
            }
        }
    }
}

@Composable
private fun SearchStep(
    mode: PlaceSearchMode,
    state: PlaceSearchUiState,
    viewModel: PlaceSearchViewModel,
    focusRequester: FocusRequester,
    onSaved: (String) -> Unit,
) {
    val title = if (mode == PlaceSearchMode.CITY) R.string.country_add_city else R.string.city_add_spot
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)

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

    state.manualReason?.let { reason ->
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

    if (state.searching) CircularProgressIndicator()

    if (state.needsHome) HomeFields(state, viewModel)

    if (state.manual) {
        Button(
            onClick = { viewModel.saveManually(onSaved) },
            enabled = state.canSaveManually && !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_add))
        }
    } else {
        LazyColumn(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT_DP.dp)) {
            items(state.predictions, key = { it.googlePlaceId }) { prediction ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.pick(prediction) }
                        .padding(vertical = 12.dp),
                ) {
                    Text(prediction.primaryText, style = MaterialTheme.typography.bodyLarge)
                    if (prediction.secondaryText.isNotBlank()) {
                        Text(
                            text = prediction.secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
        TextButton(onClick = { viewModel.saveManually(onSaved) }, enabled = state.canSaveManually) {
            Text(stringResource(R.string.place_search_use_typed))
        }
    }
}

/**
 * The parent for something that has no home yet, so it is never saved orphaned. A spot needs a
 * country and a city; a city needs only a country, since it is the city. Each is found or created
 * by name, which is how adding one can bring a new country along with it.
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

    if (state.country != null && state.asksForCity) {
        OutlinedTextField(
            value = state.homeCityName,
            onValueChange = viewModel::onHomeCityNameChange,
            label = { Text(stringResource(R.string.place_search_city)) },
            placeholder = { Text(GENERAL_CITY_NAME) },
            supportingText = { Text(stringResource(R.string.place_search_city_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
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
    mode: PlaceSearchMode,
    state: PlaceSearchUiState,
    pending: PendingPick,
    viewModel: PlaceSearchViewModel,
    onSaved: (String) -> Unit,
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
    } else if (mode == PlaceSearchMode.SPOT) {
        Text(
            text = stringResource(R.string.place_search_filed_under, pending.breadcrumb),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = pending.cityName,
            onValueChange = viewModel::onCityNameChange,
            label = { Text(stringResource(R.string.place_search_change_place)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { viewModel.confirm(onSaved) }, enabled = !saving && state.canConfirm(pending)) {
            Text(stringResource(R.string.action_save))
        }
        TextButton(onClick = viewModel::cancelPick) { Text(stringResource(R.string.action_back)) }
    }
}
