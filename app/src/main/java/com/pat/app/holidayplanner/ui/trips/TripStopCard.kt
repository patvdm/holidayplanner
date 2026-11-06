package com.pat.app.holidayplanner.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.domain.LocalTimes
import com.pat.app.holidayplanner.ui.components.PlaceSearchSheet
import com.pat.app.holidayplanner.ui.components.SpotPickerSheet
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Typing pauses this long before a field is written, so one edit is one sync. */
private const val SAVE_DELAY_MS = 800L

/**
 * What a spot is on the trip it was opened from: its day and time, a booking reference and the
 * trip's own notes about it, plus changing the spot or taking it off the trip. Nothing at all when
 * the spot was not opened from a trip, which is [itemId] blank.
 *
 * [onSpotChanged] gets the new spot's id, so the screen can show that spot instead.
 */
@Composable
fun TripStopCard(
    itemId: String,
    onSpotChanged: (String) -> Unit,
    onRemoved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (itemId.isBlank()) return
    val viewModel: TripStopViewModel = koinViewModel(key = "trip-stop-$itemId") { parametersOf(itemId) }
    val stop by viewModel.stop.collectAsStateWithLifecycle()
    val current = stop ?: return
    var changingSpot by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Luggage, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.trip_stop_on, current.tripName),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            StopWhen(current.item, viewModel)
            OptionalField(
                label = stringResource(R.string.item_booking_ref),
                addLabel = stringResource(R.string.trip_stop_add_booking),
                saved = current.item.bookingRef,
                onSave = viewModel::setBookingRef,
            )
            OptionalField(
                label = stringResource(R.string.trip_stop_notes),
                addLabel = stringResource(R.string.trip_stop_add_notes),
                saved = current.item.notes,
                onSave = viewModel::setNotes,
                multiline = true,
            )
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { changingSpot = true }) {
                    Text(stringResource(R.string.item_change_spot))
                }
                TextButton(
                    onClick = { viewModel.remove(onRemoved) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.trip_remove_item))
                }
            }
        }
    }

    if (changingSpot) {
        ChangeSpotSheets(
            onPicked = { spotId ->
                changingSpot = false
                viewModel.setSpot(spotId, onSpotChanged)
            },
            onDismiss = { changingSpot = false },
        )
    }
}

/** The day and time of the stop, in the zone the trip already uses when it has none of its own. */
@Composable
private fun StopWhen(item: TripItemEntity, viewModel: TripStopViewModel) {
    val zone by produceState(item.startTimeZone.ifBlank { LocalTimes.deviceZone() }, item.meta.id) {
        value = viewModel.zoneFor(item)
    }
    DateTimeField(
        label = stringResource(R.string.item_date),
        local = item.startLocal,
        zone = zone,
        onChange = viewModel::setStart,
    )
}

/**
 * A field that only takes room once it has something in it: until then a small link opens it.
 * Writes a moment after typing stops.
 */
@Composable
private fun OptionalField(
    label: String,
    addLabel: String,
    saved: String,
    onSave: (String) -> Unit,
    multiline: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    if (saved.isBlank() && !open) {
        TextButton(onClick = { open = true }, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
            Text(addLabel)
        }
        return
    }
    var value by remember(saved) { mutableStateOf(saved) }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(label) },
        singleLine = !multiline,
        minLines = if (multiline) 2 else 1,
        keyboardOptions = KeyboardOptions(
            capitalization = if (multiline) KeyboardCapitalization.Sentences else KeyboardCapitalization.Characters,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp),
    )
    LaunchedEffect(value) {
        if (value.trim() != saved) {
            delay(SAVE_DELAY_MS)
            onSave(value)
        }
    }
}

/** Picks another saved spot, or adds a new one from Google, for the stop to point at. */
@Composable
private fun ChangeSpotSheets(onPicked: (String) -> Unit, onDismiss: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    if (adding) {
        PlaceSearchSheet(
            title = stringResource(R.string.spot_add),
            onDismiss = onDismiss,
            onSaved = onPicked,
        )
    } else {
        SpotPickerSheet(
            allowCreate = true,
            onCreateNew = { adding = true },
            onPick = { onPicked(it.spotId) },
            onDismiss = onDismiss,
        )
    }
}
