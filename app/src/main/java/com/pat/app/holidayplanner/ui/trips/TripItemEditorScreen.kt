package com.pat.app.holidayplanner.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.domain.TripItemType
import com.pat.app.holidayplanner.ui.components.PlaceSearchSheet
import com.pat.app.holidayplanner.ui.components.SpotPickerSheet
import androidx.compose.ui.platform.LocalContext
import com.pat.app.holidayplanner.ui.components.MapButtonRow
import com.pat.app.holidayplanner.ui.components.openExternalUrl
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One trip item. Which fields appear depends on what it is: a flight has two airports and two
 * clocks, and a spot points at the spot it is, whose own screen is a tap away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripItemEditorScreen(
    itemId: String,
    onBack: () -> Unit,
    onOpenSpot: (String) -> Unit,
    onShowSpotOnMap: (String) -> Unit,
    viewModel: TripItemViewModel = koinViewModel { parametersOf(itemId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val place by viewModel.spotPlace.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val item = state.item
    var pickingSpot by remember { mutableStateOf(false) }
    var addingSpot by remember { mutableStateOf(false) }

    Scaffold(
        // The tab bar below already keeps clear of the system bar; padding for it again left a dead strip.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(item?.type?.label() ?: R.string.trip_add_item)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.delete(onBack) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        if (item == null) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // When and where first, since that is what a flight is; its number and booking come after.
            DateTimeField(
                label = stringResource(startLabelFor(item.type)),
                local = item.startLocal,
                zone = item.startTimeZone,
                onChange = viewModel::setStart,
            )
            // Only a flight has a second clock; a spot or a route simply belongs to a day.
            if (item.type == TripItemType.FLIGHT) {
                DateTimeField(
                    label = stringResource(R.string.item_arrives),
                    local = item.endLocal,
                    zone = item.endTimeZone,
                    onChange = viewModel::setEnd,
                )
                HorizontalDivider()
                TextField(stringResource(R.string.item_origin), item.origin, capitalize = true) { text ->
                    viewModel.edit { it.copy(origin = text) }
                }
                TextField(stringResource(R.string.item_destination), item.destination, capitalize = true) { text ->
                    viewModel.edit { it.copy(destination = text) }
                }
            }

            HorizontalDivider()
            if (item.type == TripItemType.SPOT) {
                if (item.spotId.isNotBlank()) {
                    FilledTonalButton(onClick = { onOpenSpot(item.spotId) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.item_open_spot))
                    }
                }
                // The same three as on a route: our map, a look in Google Maps, and going there now.
                place?.let { spot ->
                    MapButtonRow(
                        onOurMap = if (spot.onOurMap) ({ onShowSpotOnMap(spot.spotId) }) else null,
                        onPreview = { context.openExternalUrl(spot.previewUrl) },
                        onNavigate = { context.openExternalUrl(spot.navigateUrl) },
                    )
                }
                OutlinedButton(onClick = { pickingSpot = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (item.spotId.isBlank()) {
                            stringResource(R.string.item_pick_spot)
                        } else {
                            stringResource(R.string.item_change_spot)
                        },
                    )
                }
            }

            TextField(
                label = stringResource(titleLabelFor(item.type)),
                value = item.title,
                capitalize = true,
            ) { text -> viewModel.edit { it.copy(title = text) } }
            TextField(stringResource(R.string.item_booking_ref), item.bookingRef) { text ->
                viewModel.edit { it.copy(bookingRef = text) }
            }
            TextField(stringResource(R.string.spot_notes), item.notes, capitalize = true, singleLine = false) { text ->
                viewModel.edit { it.copy(notes = text) }
            }

            Button(
                onClick = { viewModel.save(onBack) },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (pickingSpot) {
        SpotPickerSheet(
            allowCreate = true,
            onCreateNew = {
                pickingSpot = false
                addingSpot = true
            },
            onPick = {
                pickingSpot = false
                viewModel.setSpot(it)
            },
            onDismiss = { pickingSpot = false },
        )
    }

    if (addingSpot) {
        PlaceSearchSheet(
            title = stringResource(R.string.spot_add),
            knownIsoCode = state.countryIso,
            onDismiss = { addingSpot = false },
            onSaved = { spotId ->
                addingSpot = false
                viewModel.setSpot(spotId)
            },
        )
    }
}

@Composable
private fun TextField(
    label: String,
    value: String,
    capitalize: Boolean = false,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(
            capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun titleLabelFor(type: TripItemType): Int = when (type) {
    TripItemType.FLIGHT -> R.string.item_flight_number
    else -> R.string.label_name
}

private fun startLabelFor(type: TripItemType): Int = when (type) {
    TripItemType.FLIGHT -> R.string.item_departs
    else -> R.string.item_date
}

