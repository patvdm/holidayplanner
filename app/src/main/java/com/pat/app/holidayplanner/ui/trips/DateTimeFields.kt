package com.pat.app.holidayplanner.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.domain.LocalTimes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

private const val ZONE_LIST_MAX_HEIGHT_DP = 420

/**
 * A moment on a trip: a date, a time, and the clock it is written against.
 *
 * The zone sits next to the time on purpose. A flight lands at a local time in a place with its own
 * clock, and hiding which clock that is makes the number meaningless.
 */
@Composable
fun DateTimeField(
    label: String,
    local: String,
    zone: String,
    onChange: (local: String, zone: String) -> Unit,
) {
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var pickingZone by remember { mutableStateOf(false) }

    // A day may be known before its time is: an item put on a day starts as a bare date.
    val date = LocalTimes.dateOf(local)
    val moment = LocalTimes.parse(local)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickingDate = true }) {
                Text(date?.let(LocalTimes::formatDay) ?: stringResource(R.string.trip_pick_date))
            }
            OutlinedButton(onClick = { pickingTime = true }, enabled = date != null) {
                Text(moment?.let { LocalTimes.timeOfDay(local) } ?: stringResource(R.string.trip_pick_time))
            }
            OutlinedButton(onClick = { pickingZone = true }) {
                Text(LocalTimes.zoneLabel(zone).ifBlank { stringResource(R.string.trip_pick_zone) })
            }
        }
    }

    if (pickingDate) {
        DatePickerSheet(
            initial = date,
            onPick = { picked ->
                pickingDate = false
                val time = moment?.toLocalTime()
                val written = if (time == null) {
                    LocalTimes.writeDate(picked)
                } else {
                    LocalTimes.write(LocalDateTime.of(picked, time))
                }
                onChange(written, zone)
            },
            onDismiss = { pickingDate = false },
        )
    }

    if (pickingTime && date != null) {
        TimePickerDialog(
            initial = moment ?: date.atTime(DEFAULT_HOUR, 0),
            onPick = { hour, minute ->
                pickingTime = false
                onChange(LocalTimes.write(date.atTime(hour, minute)), zone)
            },
            onDismiss = { pickingTime = false },
        )
    }

    if (pickingZone) {
        ZonePickerSheet(
            onPick = {
                pickingZone = false
                onChange(local, it)
            },
            onDismiss = { pickingZone = false },
        )
    }
}

private const val DEFAULT_HOUR = 9

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The picker works in UTC millis, so the date is converted at that boundary and nowhere else.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    } else {
                        onDismiss()
                    }
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial: LocalDateTime,
    onPick: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onPick(state.hour, state.minute) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Every zone the platform knows, searchable, because a trip can go anywhere. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZonePickerSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zones = remember { ZoneId.getAvailableZoneIds().sorted() }
    val matches = remember(query, zones) {
        val needle = query.trim()
        if (needle.isEmpty()) zones else zones.filter { it.contains(needle, ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.trip_search_zone)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        LazyColumn(modifier = Modifier.heightIn(max = ZONE_LIST_MAX_HEIGHT_DP.dp)) {
            items(matches, key = { it }) { zone ->
                Text(
                    text = zone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(zone) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
    }
}
