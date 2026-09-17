package com.pat.app.holidayplanner.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.domain.TripItemType
import com.pat.app.holidayplanner.ui.components.SpotPickerSheet
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One trip item. Which fields appear depends on what it is: a flight has two airports and two
 * clocks, a stay has an address and a check-out, and an activity may simply point at a spot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripItemEditorScreen(
    itemId: String,
    onBack: () -> Unit,
    onOpenRoute: () -> Unit,
    viewModel: TripItemViewModel = koinViewModel { parametersOf(itemId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val item = state.item
    var pickingSpot by remember { mutableStateOf(false) }

    Scaffold(
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
            TextField(
                label = stringResource(titleLabelFor(item.type)),
                value = item.title,
                capitalize = true,
            ) { text -> viewModel.edit { it.copy(title = text) } }

            if (item.type == TripItemType.FLIGHT || item.type == TripItemType.TRANSPORT) {
                TextField(stringResource(R.string.item_origin), item.origin, capitalize = true) { text ->
                    viewModel.edit { it.copy(origin = text) }
                }
                TextField(stringResource(R.string.item_destination), item.destination, capitalize = true) { text ->
                    viewModel.edit { it.copy(destination = text) }
                }
            }

            if (item.type == TripItemType.STAY || item.type == TripItemType.ACTIVITY) {
                TextField(stringResource(R.string.item_address), item.address, capitalize = true) { text ->
                    viewModel.edit { it.copy(address = text) }
                }
            }

            if (item.type == TripItemType.ACTIVITY) {
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

            HorizontalDivider()
            DateTimeField(
                label = stringResource(startLabelFor(item.type)),
                local = item.startLocal,
                zone = item.startTimeZone,
                onChange = viewModel::setStart,
            )
            DateTimeField(
                label = stringResource(endLabelFor(item.type)),
                local = item.endLocal,
                zone = item.endTimeZone,
                onChange = viewModel::setEnd,
            )

            if (item.type == TripItemType.DAY_ROUTE) {
                // Saved on the way out, so edits made here are not lost to the builder.
                OutlinedButton(
                    onClick = { viewModel.save(onOpenRoute) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.item_route_stops))
                }
            }

            HorizontalDivider()
            if (item.type != TripItemType.DAY_ROUTE) {
                TextField(stringResource(R.string.item_booking_ref), item.bookingRef) { text ->
                    viewModel.edit { it.copy(bookingRef = text) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.costText,
                    onValueChange = viewModel::onCostChange,
                    label = { Text(stringResource(R.string.item_cost)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = item.currency,
                    onValueChange = { text -> viewModel.edit { it.copy(currency = text.uppercase()) } },
                    label = { Text(stringResource(R.string.item_currency)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            TextField(stringResource(R.string.item_doc_url), item.docUrl) { text ->
                viewModel.edit { it.copy(docUrl = text) }
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
            onPick = {
                pickingSpot = false
                viewModel.setSpot(it)
            },
            onDismiss = { pickingSpot = false },
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
    TripItemType.STAY -> R.string.item_check_in
    TripItemType.DAY_ROUTE -> R.string.item_date
    else -> R.string.item_departs
}

private fun endLabelFor(type: TripItemType): Int = when (type) {
    TripItemType.STAY -> R.string.item_check_out
    else -> R.string.item_arrives
}

