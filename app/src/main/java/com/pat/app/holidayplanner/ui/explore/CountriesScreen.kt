package com.pat.app.holidayplanner.ui.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import com.pat.app.holidayplanner.data.local.CountryRow
import com.pat.app.holidayplanner.domain.CountryCatalog
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.ui.components.ConfirmDeleteDialog
import com.pat.app.holidayplanner.data.remote.PlaceSearchMode
import com.pat.app.holidayplanner.ui.components.EmptyListMessage
import com.pat.app.holidayplanner.ui.components.PlaceSearchSheet
import com.pat.app.holidayplanner.ui.feed.ThisWeekStrip
import com.pat.app.holidayplanner.ui.components.PullableEmptyState
import com.pat.app.holidayplanner.ui.components.SyncRefreshBox
import com.pat.app.holidayplanner.ui.components.SyncStatusBanner
import com.pat.app.holidayplanner.ui.components.PriorityBadge
import com.pat.app.holidayplanner.ui.components.ReorderableColumn
import com.pat.app.holidayplanner.ui.components.TextInputDialog
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountriesScreen(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenCountry: (String) -> Unit,
    onOpenFeed: () -> Unit,
    onOpenMap: () -> Unit,
    viewModel: CountriesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    var addingPlace by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<CountryRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_explore)) },
                actions = {
                    IconButton(onClick = onOpenMap) {
                        Icon(Icons.Filled.Map, contentDescription = stringResource(R.string.map_show))
                    }
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addingPlace = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.countries_add_place))
            }
        },
    ) { padding ->
        SyncRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SyncStatusBanner()
                ThisWeekStrip(onOpenFeed = onOpenFeed)
                if (state.rows.isEmpty()) {
                    PullableEmptyState(modifier = Modifier.weight(1f)) {
                        EmptyListMessage(stringResource(R.string.countries_empty))
                        AddCountryButton { showPicker = true }
                    }
                } else {
                    ReorderableColumn(
                        items = state.rows,
                        idOf = { it.country.meta.id },
                        sortKeyOf = { it.country.sortKey },
                        reorderable = true,
                        onMoved = viewModel::move,
                        modifier = Modifier.weight(1f),
                        footer = { AddCountryButton { showPicker = true } },
                    ) { row, handle ->
                        CountryRowItem(
                            row = row,
                            handle = handle,
                            onClick = { onOpenCountry(row.country.meta.id) },
                            onPriority = { viewModel.setPriority(row.country.meta.id, it) },
                            onRename = { renaming = row },
                            onDelete = { viewModel.requestDelete(row.country.meta.id, row.country.name) },
                        )
                    }
                }
            }
        }
    }

    if (addingPlace) {
        PlaceSearchSheet(
            mode = PlaceSearchMode.SPOT,
            onDismiss = { addingPlace = false },
            onSaved = { addingPlace = false },
        )
    }

    if (showPicker) {
        CountryPickerSheet(
            options = viewModel.availableCountries(state.rows),
            onPick = {
                showPicker = false
                viewModel.addCountry(it)
            },
            onDismiss = { showPicker = false },
        )
    }

    renaming?.let { row ->
        TextInputDialog(
            title = stringResource(R.string.countries_rename),
            label = stringResource(R.string.label_name),
            initial = row.country.name,
            onConfirm = {
                viewModel.rename(row.country.meta.id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    state.pendingDelete?.let { pending ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_title, pending.name),
            message = stringResource(
                R.string.delete_country_message,
                pending.impact.cities,
                pending.impact.spots,
            ),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun CountryRowItem(
    row: CountryRow,
    handle: (@Composable () -> Unit)?,
    onClick: () -> Unit,
    onPriority: (Priority) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
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
                text = CountryCatalog.flag(row.country.isoCode),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.size(32.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(row.country.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.countries_counts, row.cityCount, row.spotCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PriorityBadge(row.country.priority)
            RowMenu(
                currentPriority = row.country.priority,
                onPriority = onPriority,
                onRename = onRename,
                onDelete = onDelete,
            )
            handle?.invoke()
        }
        HorizontalDivider()
    }
}

/** The per-row overflow: priority, rename, delete. */
@Composable
fun RowMenu(
    currentPriority: Priority,
    onPriority: (Priority) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        Priority.entries.forEach { priority ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_set_priority, stringResource(priority.label()))) },
                enabled = priority != currentPriority,
                onClick = {
                    expanded = false
                    onPriority(priority)
                },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_rename)) },
            onClick = {
                expanded = false
                onRename()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            onClick = {
                expanded = false
                onDelete()
            },
        )
    }
}

fun Priority.label(): Int = when (this) {
    Priority.MUST -> R.string.priority_must
    Priority.LIKE -> R.string.priority_like
    Priority.MAYBE -> R.string.priority_maybe
}

/**
 * Adding a country with nothing in it, which is the rarer case now that adding a place brings its
 * country along. It sits under the list rather than on the button, where the common action lives.
 */
@Composable
private fun AddCountryButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.countries_add_country))
    }
}
