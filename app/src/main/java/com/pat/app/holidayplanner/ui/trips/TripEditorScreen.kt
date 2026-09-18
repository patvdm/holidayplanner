package com.pat.app.holidayplanner.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.domain.CountryCatalog
import com.pat.app.holidayplanner.domain.LocalTimes
import com.pat.app.holidayplanner.ui.components.BackTopBar
import com.pat.app.holidayplanner.ui.components.ConfirmDeleteDialog
import com.pat.app.holidayplanner.ui.components.PickFromListDialog
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripEditorScreen(
    tripId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: TripViewModel = koinViewModel { parametersOf(tripId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var changingCountry by remember { mutableStateOf(false) }
    val trip = state.trip

    Scaffold(
        // The tab bar below already keeps clear of the system bar; padding for it again left a dead strip.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar(stringResource(R.string.trip_edit), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            var name by remember(trip?.name) { mutableStateOf(trip?.name.orEmpty()) }
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    viewModel.rename(it)
                },
                label = { Text(stringResource(R.string.label_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.trip_dates), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickingStart = true }) {
                    Text(dateLabel(trip?.startDate, R.string.trip_start_date))
                }
                OutlinedButton(onClick = { pickingEnd = true }) {
                    Text(dateLabel(trip?.endDate, R.string.trip_end_date))
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.trips_country), style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { changingCountry = true }) {
                Text(
                    state.country?.let { "${CountryCatalog.flag(it.isoCode)} ${it.name}" }
                        ?: stringResource(R.string.trips_country),
                )
            }

            HorizontalDivider()
            var notes by remember(trip?.notes) { mutableStateOf(trip?.notes.orEmpty()) }
            OutlinedTextField(
                value = notes,
                onValueChange = {
                    notes = it
                    viewModel.setNotes(it)
                },
                label = { Text(stringResource(R.string.spot_notes)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            TextButton(onClick = { confirmingDelete = true }) {
                Text(
                    text = stringResource(R.string.trip_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (pickingStart) {
        DatePickerSheet(
            initial = LocalTimes.parseDate(trip?.startDate.orEmpty()),
            onPick = {
                pickingStart = false
                viewModel.setDates(LocalTimes.writeDate(it), trip?.endDate.orEmpty())
            },
            onDismiss = { pickingStart = false },
        )
    }

    if (pickingEnd) {
        DatePickerSheet(
            initial = LocalTimes.parseDate(trip?.endDate.orEmpty()),
            onPick = {
                pickingEnd = false
                viewModel.setDates(trip?.startDate.orEmpty(), LocalTimes.writeDate(it))
            },
            onDismiss = { pickingEnd = false },
        )
    }

    if (changingCountry) {
        PickFromListDialog(
            title = stringResource(R.string.trips_country),
            options = state.countries.map { it.meta.id to "${CountryCatalog.flag(it.isoCode)} ${it.name}" },
            onPick = { countryId ->
                changingCountry = false
                state.countries.firstOrNull { it.meta.id == countryId }?.let(viewModel::setCountry)
            },
            onDismiss = { changingCountry = false },
        )
    }

    if (confirmingDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_title, trip?.name.orEmpty()),
            message = stringResource(R.string.delete_trip_message),
            onConfirm = {
                confirmingDelete = false
                viewModel.delete(onDeleted)
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun dateLabel(date: String?, fallback: Int): String =
    LocalTimes.parseDate(date.orEmpty())?.let(LocalTimes::formatDay) ?: stringResource(fallback)
