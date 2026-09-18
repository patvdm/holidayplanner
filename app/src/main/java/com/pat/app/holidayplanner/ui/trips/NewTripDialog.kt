package com.pat.app.holidayplanner.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.CountryEntity
import com.pat.app.holidayplanner.domain.CountryCatalog
import com.pat.app.holidayplanner.domain.LocalTimes
import java.time.LocalDate

private const val COUNTRY_LIST_MAX_HEIGHT_DP = 240

/**
 * A new trip: where to, from when until when, and optionally a name.
 *
 * Only countries you have spots in are offered, because a trip exists to plan days out of them.
 * Somewhere with nothing saved yet has nothing to plan with.
 */
@Composable
fun NewTripDialog(
    countries: List<CountryEntity>,
    onConfirm: (name: String, country: CountryEntity, start: LocalDate, end: LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var country by remember { mutableStateOf<CountryEntity?>(null) }
    var start by remember { mutableStateOf<LocalDate?>(null) }
    var end by remember { mutableStateOf<LocalDate?>(null) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    val chosen = country
    val from = start
    val until = end
    val complete = chosen != null && from != null && until != null && !until.isBefore(from)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trips_add)) },
        text = {
            if (countries.isEmpty()) {
                Text(stringResource(R.string.trips_no_countries))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.trips_name_optional)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.trip_dates), style = MaterialTheme.typography.labelLarge)
                    DatesRow(
                        from = from,
                        until = until,
                        onPickStart = { pickingStart = true },
                        onPickEnd = { pickingEnd = true },
                    )
                    Text(stringResource(R.string.trips_country), style = MaterialTheme.typography.labelLarge)
                    CountryList(countries = countries, chosenId = chosen?.meta?.id, onChoose = { country = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (chosen != null && from != null && until != null) onConfirm(name, chosen, from, until)
                },
                enabled = complete,
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (pickingStart) {
        DatePickerSheet(
            initial = from,
            onPick = {
                pickingStart = false
                start = it
                // A one-day trip until told otherwise, so the end never has to be set twice.
                if (until == null || until.isBefore(it)) end = it
            },
            onDismiss = { pickingStart = false },
        )
    }

    if (pickingEnd) {
        DatePickerSheet(
            initial = until ?: from,
            onPick = {
                pickingEnd = false
                end = it
            },
            onDismiss = { pickingEnd = false },
        )
    }
}

@Composable
private fun DatesRow(from: LocalDate?, until: LocalDate?, onPickStart: () -> Unit, onPickEnd: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPickStart, modifier = Modifier.weight(1f)) {
            Text(from?.let(LocalTimes::formatDay) ?: stringResource(R.string.trip_start_date))
        }
        OutlinedButton(onClick = onPickEnd, modifier = Modifier.weight(1f)) {
            Text(until?.let(LocalTimes::formatDay) ?: stringResource(R.string.trip_end_date))
        }
    }
}

@Composable
private fun CountryList(countries: List<CountryEntity>, chosenId: String?, onChoose: (CountryEntity) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = COUNTRY_LIST_MAX_HEIGHT_DP.dp),
    ) {
        items(countries, key = { it.meta.id }) { option ->
            ListItem(
                headlineContent = { Text("${CountryCatalog.flag(option.isoCode)} ${option.name}") },
                trailingContent = { if (option.meta.id == chosenId) Text("\u2713") },
                modifier = Modifier.clickable { onChoose(option) },
            )
        }
    }
}
