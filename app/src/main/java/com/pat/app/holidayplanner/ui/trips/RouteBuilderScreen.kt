package com.pat.app.holidayplanner.ui.trips

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.RouteStopRow
import com.pat.app.holidayplanner.domain.MapsUrls
import com.pat.app.holidayplanner.ui.components.BackTopBar
import com.pat.app.holidayplanner.ui.components.EmptyListMessage
import com.pat.app.holidayplanner.ui.components.ReorderableColumn
import com.pat.app.holidayplanner.ui.components.SpotPickerSheet
import com.pat.app.holidayplanner.ui.components.openExternalUrl
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The stops of one day, in the order they will be walked.
 *
 * Everything written here takes effect at once, so there is no Save button: the list is the plan.
 */
@Composable
fun RouteBuilderScreen(
    itemId: String,
    onBack: () -> Unit,
    viewModel: RouteViewModel = koinViewModel { parametersOf(itemId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var addingStop by remember { mutableStateOf(false) }

    val noMapsApp = stringResource(R.string.route_no_maps_app)
    val openInMaps: (List<RouteStopRow>) -> Unit = { part ->
        val url = viewModel.mapsUrl(part)
        if (url == null || !context.openExternalUrl(url)) {
            scope.launch { snackbars.showSnackbar(noMapsApp) }
        }
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = state.item?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.route_title),
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            RouteControls(
                state = state,
                onAddStop = { addingStop = true },
                onSortByProximity = viewModel::sortByProximity,
            )
            HorizontalDivider()

            if (state.stops.isEmpty()) {
                EmptyListMessage(stringResource(R.string.route_empty))
            } else {
                ReorderableColumn(
                    items = state.stops,
                    idOf = { it.stopId },
                    sortKeyOf = { it.sortKey },
                    reorderable = true,
                    onMoved = viewModel::move,
                    modifier = Modifier.weight(1f),
                ) { stop, handle ->
                    StopRow(
                        stop = stop,
                        position = state.stops.indexOfFirst { it.stopId == stop.stopId } + 1,
                        handle = handle,
                        onRemove = { viewModel.removeStop(stop.stopId) },
                    )
                }
                HorizontalDivider()
                RouteFooter(state = state, onOpen = openInMaps)
            }
        }
    }

    if (addingStop) {
        val alreadyOn = state.stops.map { it.spotId }.toSet()
        val city = state.cityId
        SpotPickerSheet(
            onPick = {
                addingStop = false
                viewModel.addStop(it.spotId)
            },
            onDismiss = { addingStop = false },
            // A walk stays in one city, so once the first stop is on, only its neighbours are offered.
            filter = { it.spotId !in alreadyOn && (city.isEmpty() || it.cityId == city) },
        )
    }
}

@Composable
private fun RouteControls(
    state: RouteUiState,
    onAddStop: () -> Unit,
    onSortByProximity: (fromHere: Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = onAddStop,
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                label = { Text(stringResource(R.string.route_add_stop)) },
            )
            if (state.stops.size > 1) {
                AssistChip(
                    enabled = !state.sorting,
                    onClick = { onSortByProximity(false) },
                    label = { Text(stringResource(R.string.route_sort_proximity)) },
                )
                AssistChip(
                    enabled = !state.sorting,
                    onClick = { onSortByProximity(true) },
                    label = { Text(stringResource(R.string.route_sort_from_here)) },
                )
            }
        }
        if (state.cityName.isNotBlank()) {
            Text(
                text = stringResource(R.string.route_city_locked, state.cityName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StopRow(
    stop: RouteStopRow,
    position: Int,
    handle: (@Composable () -> Unit)?,
    onRemove: () -> Unit,
) {
    ListItem(
        leadingContent = { Text(position.toString(), style = MaterialTheme.typography.titleMedium) },
        headlineContent = { Text(stop.name) },
        supportingContent = {
            Text(stop.address.ifBlank { stop.cityName }, style = MaterialTheme.typography.bodySmall)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.route_remove_stop))
                }
                handle?.invoke()
            }
        },
    )
}

@Composable
private fun RouteFooter(state: RouteUiState, onOpen: (List<RouteStopRow>) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.stopsWithoutCoordinates > 0) {
            Note(
                pluralStringResource(
                    R.plurals.route_without_location,
                    state.stopsWithoutCoordinates,
                    state.stopsWithoutCoordinates,
                ),
            )
        }

        when {
            !state.canOpen -> Note(stringResource(R.string.route_needs_two))

            state.tooLongForOneRoute -> {
                Note(stringResource(R.string.route_split_warning, MapsUrls.MAX_ROUTE_STOPS, state.parts.size))
                state.parts.forEachIndexed { index, part ->
                    FilledTonalButton(onClick = { onOpen(part) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.route_open_part, index + 1, state.parts.size))
                    }
                }
            }

            else -> Button(onClick = { onOpen(state.stops) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Map, contentDescription = null)
                Text(
                    text = stringResource(R.string.route_open_in_maps),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
