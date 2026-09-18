package com.pat.app.holidayplanner.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.ui.components.PullableEmptyState
import com.pat.app.holidayplanner.ui.components.SyncRefreshBox
import com.pat.app.holidayplanner.data.local.TripRow
import com.pat.app.holidayplanner.domain.CountryCatalog
import com.pat.app.holidayplanner.domain.LocalTimes
import com.pat.app.holidayplanner.ui.components.TabTopBar
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TripsScreen(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenTrip: (String) -> Unit,
    viewModel: TripsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var showPast by remember { mutableStateOf(false) }

    Scaffold(
        // The tab bar below is already accounted for by the navigation Scaffold, so adding the
        // navigation inset again here would float the button a bar's height too high.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TabTopBar(stringResource(R.string.tab_trips), onSearch = onSearch, onSettings = onSettings) },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.trips_add))
            }
        },
    ) { padding ->
        SyncRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isEmpty) {
                PullableEmptyState(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.trips_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.upcoming, key = { it.trip.meta.id }) { row ->
                        TripRowItem(row = row, onClick = { onOpenTrip(row.trip.meta.id) })
                    }
                    if (state.past.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(
                                    if (showPast) R.string.trips_past_hide else R.string.trips_past_show,
                                    state.past.size,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showPast = !showPast }
                                    .padding(16.dp),
                            )
                        }
                        if (showPast) {
                            items(state.past, key = { it.trip.meta.id }) { row ->
                                TripRowItem(row = row, onClick = { onOpenTrip(row.trip.meta.id) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        val countries by viewModel.countryOptions.collectAsStateWithLifecycle(initialValue = emptyList())
        NewTripDialog(
            countries = countries,
            onConfirm = { name, country, start, end ->
                adding = false
                viewModel.addTrip(name, country, start, end, onOpenTrip)
            },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun TripRowItem(row: TripRow, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = CountryCatalog.flag(row.isoCode.orEmpty()),
                style = MaterialTheme.typography.headlineSmall,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(row.trip.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = listOfNotNull(row.countryName?.takeIf { it.isNotBlank() }, tripDates(row))
                        .joinToString(" \u00b7 "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun tripDates(row: TripRow): String {
    val start = LocalTimes.parseDate(row.trip.startDate)
    val end = LocalTimes.parseDate(row.trip.endDate)
    return when {
        start != null && end != null -> "${LocalTimes.formatDay(start)} – ${LocalTimes.formatDay(end)}"
        start != null -> LocalTimes.formatDay(start)
        else -> stringResource(R.string.trips_no_dates)
    }
}
