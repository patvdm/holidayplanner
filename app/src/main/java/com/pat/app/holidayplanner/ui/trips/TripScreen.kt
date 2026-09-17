package com.pat.app.holidayplanner.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.domain.Costs
import com.pat.app.holidayplanner.domain.LocalTimes
import com.pat.app.holidayplanner.domain.TripItemType
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(
    tripId: String,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onEditTrip: () -> Unit,
    viewModel: TripViewModel = koinViewModel { parametersOf(tripId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var addingItem by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.trip?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onEditTrip) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.trip_edit))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addingItem = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.trip_add_item))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.timeline.days.isEmpty() && state.timeline.undated.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.trip_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            state.timeline.days.forEach { day ->
                item(key = "day-${day.date}") {
                    Text(
                        text = LocalTimes.formatDay(day.date),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(day.entries, key = { "${day.date}-${it.item.meta.id}-${it.role}" }) { entry ->
                    ItemCard(entry = entry, onClick = { onOpenItem(entry.item.meta.id) })
                }
            }

            if (state.timeline.undated.isNotEmpty()) {
                item(key = "undated") {
                    Text(
                        text = stringResource(R.string.trip_undated),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(state.timeline.undated, key = { "undated-${it.meta.id}" }) { item ->
                    ItemCard(entry = TimelineEntry(item, ItemRole.START), onClick = { onOpenItem(item.meta.id) })
                }
            }

            if (state.totals.isNotEmpty()) {
                item(key = "totals") { TotalsSection(state) }
            }
        }
    }

    if (addingItem) {
        ItemTypeDialog(
            onPick = { type ->
                addingItem = false
                viewModel.addItem(type, onOpenItem)
            },
            onDismiss = { addingItem = false },
        )
    }
}

@Composable
private fun ItemCard(entry: TimelineEntry, onClick: () -> Unit) {
    val item = entry.item
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
                text = timeLabel(entry),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { stringResource(item.type.label()) },
                    style = MaterialTheme.typography.titleMedium,
                )
                subtitle(entry)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun timeLabel(entry: TimelineEntry): String {
    val local = if (entry.role == ItemRole.END) entry.item.endLocal else entry.item.startLocal
    return LocalTimes.timeOfDay(local).ifBlank { "—" }
}

/** What a card says under its title depends on why it is on this day. */
@Composable
private fun subtitle(entry: TimelineEntry): String? {
    val item = entry.item
    if (entry.role == ItemRole.END) return stringResource(R.string.trip_check_out)

    val route = listOf(item.origin, item.destination).filter { it.isNotBlank() }
    val elapsed = LocalTimes.elapsed(item.startLocal, item.startTimeZone, item.endLocal, item.endTimeZone)
    val zones = if (LocalTimes.differentZones(item.startTimeZone, item.endTimeZone)) {
        "${LocalTimes.zoneLabel(item.startTimeZone)} → ${LocalTimes.zoneLabel(item.endTimeZone)}"
    } else {
        null
    }

    val parts = listOfNotNull(
        route.takeIf { it.size == 2 }?.joinToString(" → "),
        zones,
        elapsed?.let(LocalTimes::formatDuration),
        item.address.takeIf { it.isNotBlank() },
    )
    return parts.joinToString(" · ").takeIf { it.isNotBlank() }
}

@Composable
private fun TotalsSection(state: TripUiState) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
        Text(
            text = stringResource(R.string.trip_totals),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        state.totals.forEach { total ->
            Text(text = Costs.format(total), style = MaterialTheme.typography.titleMedium)
        }
        state.totalsByType.forEach { (type, totals) ->
            Text(
                text = "${stringResource(type.label())}: ${totals.joinToString(", ") { Costs.format(it) }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ItemTypeDialog(onPick: (TripItemType) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trip_add_item)) },
        text = {
            Column {
                TripItemType.entries.forEach { type ->
                    Text(
                        text = stringResource(type.label()),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(type) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

fun TripItemType.label(): Int = when (this) {
    TripItemType.FLIGHT -> R.string.item_flight
    TripItemType.STAY -> R.string.item_stay
    TripItemType.TRANSPORT -> R.string.item_transport
    TripItemType.ACTIVITY -> R.string.item_activity
    TripItemType.DAY_ROUTE -> R.string.item_day_route
}
