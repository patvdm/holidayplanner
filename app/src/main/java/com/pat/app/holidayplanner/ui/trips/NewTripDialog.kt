package com.pat.app.holidayplanner.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import com.pat.app.holidayplanner.domain.CountryCatalog
import com.pat.app.holidayplanner.domain.CountryOption
import com.pat.app.holidayplanner.ui.explore.CountryPickerSheet

/**
 * A new trip: a name and the country it is to.
 *
 * The country is asked for up front because almost everything later hangs off it, and a trip
 * belonging to nowhere cannot suggest spots, a time zone, or anything else useful.
 */
@Composable
fun NewTripDialog(
    onConfirm: (name: String, country: CountryOption) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var country by remember { mutableStateOf<CountryOption?>(null) }
    var pickingCountry by remember { mutableStateOf(false) }
    val chosen = country

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trips_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.trips_country))
                OutlinedButton(
                    onClick = { pickingCountry = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        chosen?.let { "${it.flag} ${it.name}" }
                            ?: stringResource(R.string.trips_pick_country),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (chosen != null) onConfirm(name, chosen) },
                enabled = name.isNotBlank() && chosen != null,
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (pickingCountry) {
        CountryPickerSheet(
            options = CountryCatalog.all(),
            onPick = {
                pickingCountry = false
                country = it
            },
            onDismiss = { pickingCountry = false },
        )
    }
}
