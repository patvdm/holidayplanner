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
import com.pat.app.holidayplanner.data.local.CityOption

private const val CITY_LIST_MAX_HEIGHT_DP = 300

/**
 * A new trip: a name and the city it is to.
 *
 * Only cities you have already saved are offered, because a trip exists to plan days out of the
 * spots in one place. Somewhere with nothing saved yet has nothing to plan with.
 */
@Composable
fun NewTripDialog(
    cities: List<CityOption>,
    onConfirm: (name: String, city: CityOption) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var city by remember { mutableStateOf<CityOption?>(null) }
    val chosen = city

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trips_add)) },
        text = {
            if (cities.isEmpty()) {
                Text(stringResource(R.string.trips_no_cities))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.label_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.trips_city))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = CITY_LIST_MAX_HEIGHT_DP.dp),
                    ) {
                        items(cities, key = { it.cityId }) { option ->
                            ListItem(
                                headlineContent = { Text(option.cityName) },
                                supportingContent = { Text(option.countryName) },
                                trailingContent = {
                                    if (option.cityId == chosen?.cityId) Text("✓")
                                },
                                modifier = Modifier.clickable { city = option },
                            )
                        }
                    }
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
}
