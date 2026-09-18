package com.pat.app.holidayplanner.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
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

private const val COUNTRY_LIST_MAX_HEIGHT_DP = 300

/**
 * A new trip: a name and the country it is to.
 *
 * Only countries you have spots in are offered, because a trip exists to plan days out of them.
 * Somewhere with nothing saved yet has nothing to plan with.
 */
@Composable
fun NewTripDialog(
    countries: List<CountryEntity>,
    onConfirm: (name: String, country: CountryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var country by remember { mutableStateOf<CountryEntity?>(null) }
    val chosen = country

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
                    Text(stringResource(R.string.trips_country))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = COUNTRY_LIST_MAX_HEIGHT_DP.dp),
                    ) {
                        items(countries, key = { it.meta.id }) { option ->
                            ListItem(
                                headlineContent = { Text("${CountryCatalog.flag(option.isoCode)} ${option.name}") },
                                trailingContent = {
                                    if (option.meta.id == chosen?.meta?.id) Text("\u2713")
                                },
                                modifier = Modifier.clickable { country = option },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (chosen != null) onConfirm(name, chosen) },
                enabled = chosen != null,
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
