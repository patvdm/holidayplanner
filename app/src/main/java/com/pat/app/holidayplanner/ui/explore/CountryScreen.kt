package com.pat.app.holidayplanner.ui.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.pat.app.holidayplanner.data.local.CityRow
import com.pat.app.holidayplanner.domain.CountryCatalog
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SortScope
import com.pat.app.holidayplanner.data.remote.PlaceSearchMode
import com.pat.app.holidayplanner.ui.components.ConfirmDeleteDialog
import com.pat.app.holidayplanner.ui.components.EmptyListMessage
import com.pat.app.holidayplanner.ui.components.PlaceSearchSheet
import com.pat.app.holidayplanner.ui.components.PriorityBadge
import com.pat.app.holidayplanner.ui.components.ReorderableColumn
import com.pat.app.holidayplanner.ui.map.MapScope
import com.pat.app.holidayplanner.ui.map.SpotMap
import com.pat.app.holidayplanner.ui.components.SortMenu
import com.pat.app.holidayplanner.ui.components.TextInputDialog
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryScreen(
    countryId: String,
    onBack: () -> Unit,
    onOpenPlace: (String) -> Unit,
    onOpenSpot: (String) -> Unit,
    viewModel: CountryViewModel = koinViewModel { parametersOf(countryId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var showingMap by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<CityRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val country = state.country
                    Text(
                        text = if (country == null) {
                            ""
                        } else {
                            "${CountryCatalog.flag(country.isoCode)} ${country.name}".trim()
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    SortMenu(SortScope.CITIES, state.sortMode, viewModel::setSortMode)
                    IconButton(onClick = { showingMap = !showingMap }) {
                        Icon(
                            imageVector = if (showingMap) Icons.AutoMirrored.Filled.List else Icons.Filled.Map,
                            contentDescription = stringResource(
                                if (showingMap) R.string.map_show_list else R.string.map_show,
                            ),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.country_add_city))
            }
        },
    ) { padding ->
        if (showingMap) {
            SpotMap(
                scope = MapScope(countryId = countryId),
                onOpenSpot = onOpenSpot,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        if (state.rows.isEmpty()) {
            EmptyListMessage(stringResource(R.string.country_empty), Modifier.padding(padding))
        } else {
            ReorderableColumn(
                items = state.rows,
                idOf = { it.place.meta.id },
                sortKeyOf = { it.place.sortKey },
                reorderable = state.canReorder,
                onMoved = viewModel::move,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) { row, handle ->
                CityRowItem(
                    row = row,
                    handle = handle,
                    onClick = { onOpenPlace(row.place.meta.id) },
                    onPriority = { viewModel.setPriority(row.place.meta.id, it) },
                    onRename = { renaming = row },
                    onDelete = { viewModel.requestDelete(row.place.meta.id, row.place.name) },
                )
            }
        }
    }

    if (adding) {
        PlaceSearchSheet(
            mode = PlaceSearchMode.CITY,
            countryId = countryId,
            onDismiss = { adding = false },
            onSaved = { adding = false },
        )
    }

    renaming?.let { row ->
        TextInputDialog(
            title = stringResource(R.string.country_rename_city),
            label = stringResource(R.string.label_name),
            initial = row.place.name,
            onConfirm = {
                viewModel.rename(row.place.meta.id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    state.pendingDelete?.let { pending ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_title, pending.name),
            message = stringResource(R.string.delete_place_message, pending.impact.spots),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun CityRowItem(
    row: CityRow,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(row.place.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.country_spot_count, row.spotCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PriorityBadge(row.place.priority)
            RowMenu(
                currentPriority = row.place.priority,
                onPriority = onPriority,
                onRename = onRename,
                onDelete = onDelete,
            )
            handle?.invoke()
        }
        HorizontalDivider()
    }
}
