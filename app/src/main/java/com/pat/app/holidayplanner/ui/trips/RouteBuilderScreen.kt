package com.pat.app.holidayplanner.ui.trips

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.domain.MapsUrls
import com.pat.app.holidayplanner.ui.components.BackTopBar
import com.pat.app.holidayplanner.ui.components.ConfirmDeleteDialog
import com.pat.app.holidayplanner.ui.components.EmptyListMessage
import com.pat.app.holidayplanner.ui.components.PriorityBadge
import com.pat.app.holidayplanner.ui.components.StatusChip
import com.pat.app.holidayplanner.ui.components.Thumbnail
import com.pat.app.holidayplanner.ui.components.TypeBadge
import com.pat.app.holidayplanner.ui.components.firstLine
import com.pat.app.holidayplanner.ui.components.ReorderableColumn
import com.pat.app.holidayplanner.ui.components.openExternalUrl
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A route: a name, whether it has been walked, and which of the city's spots are on it.
 *
 * Every spot in the city is listed and ticking one puts it on the walk, so choosing a day is a
 * matter of looking at what is nearby rather than searching for things one at a time. Everything
 * written here takes effect at once, so there is no Save button: the list is the plan.
 */
@Composable
fun RouteBuilderScreen(
    routeId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: RouteViewModel = koinViewModel { parametersOf(routeId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmingDelete by remember { mutableStateOf(false) }

    val noMapsApp = stringResource(R.string.route_no_maps_app)
    val openInMaps: (List<RouteCandidate>) -> Unit = { part ->
        val url = viewModel.mapsUrl(part)
        if (url == null || !context.openExternalUrl(url)) {
            scope.launch { snackbars.showSnackbar(noMapsApp) }
        }
    }

    Scaffold(
        // The tab bar below already keeps clear of the system bar; padding for it again left a dead strip.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            BackTopBar(
                title = state.cityName.ifBlank { stringResource(R.string.route_title) },
                onBack = onBack,
                actions = {
                    IconButton(onClick = { confirmingDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            RouteHeader(
                state = state,
                onRename = viewModel::rename,
                onCompleted = viewModel::setCompleted,
                onSortByProximity = viewModel::sortByProximity,
            )
            HorizontalDivider()

            if (state.candidates.isEmpty()) {
                EmptyListMessage(stringResource(R.string.route_city_empty))
            } else {
                StopList(state = state, viewModel = viewModel, modifier = Modifier.weight(1f))
                HorizontalDivider()
                RouteFooter(state = state, onOpen = openInMaps)
            }
        }
    }

    if (confirmingDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_title, state.name),
            message = stringResource(R.string.route_delete_message),
            onConfirm = {
                confirmingDelete = false
                viewModel.delete(onDeleted)
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun RouteHeader(
    state: RouteUiState,
    onRename: (String) -> Unit,
    onCompleted: (Boolean) -> Unit,
    onSortByProximity: (fromHere: Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var name by remember(state.route?.meta?.id) { mutableStateOf(state.name) }
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                onRename(it)
            },
            label = { Text(stringResource(R.string.label_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.completed,
                onClick = { onCompleted(!state.completed) },
                label = { Text(stringResource(R.string.route_completed)) },
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
    }
}

@Composable
private fun StopList(state: RouteUiState, viewModel: RouteViewModel, modifier: Modifier = Modifier) {
    ReorderableColumn(
        items = state.candidates,
        idOf = { it.spotId },
        sortKeyOf = { it.sortKey },
        // Only the walk has an order; the rest are just spots that happen to be in this city.
        reorderable = true,
        onMoved = { id, previous, next ->
            state.candidates.firstOrNull { it.spotId == id }?.stopId?.let { viewModel.move(it, previous, next) }
        },
        modifier = modifier,
    ) { candidate, handle ->
        CandidateRow(
            candidate = candidate,
            position = if (candidate.selected) state.stops.indexOf(candidate) + 1 else null,
            handle = handle.takeIf { candidate.selected },
            onToggle = { viewModel.setOnRoute(candidate, it) },
        )
    }
}

/** A spot as it looks everywhere else in the app, with a tick and its place on the walk in front. */
@Composable
private fun CandidateRow(
    candidate: RouteCandidate,
    position: Int?,
    handle: (@Composable () -> Unit)?,
    onToggle: (Boolean) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!candidate.selected) }
                .padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(checked = candidate.selected, onCheckedChange = onToggle)
            Text(
                text = position?.toString().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(POSITION_WIDTH_DP.dp),
            )
            Thumbnail(candidate.imageUrl, size = 48.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(candidate.name, style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PriorityBadge(candidate.priority)
                    StatusChip(candidate.status)
                    TypeBadge(candidate.type)
                }
                candidate.notes.firstLine()?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            handle?.invoke()
        }
        HorizontalDivider()
    }
}

/** Room for a two-digit stop number, so the rows line up whether or not a spot is on the walk. */
private const val POSITION_WIDTH_DP = 20

@Composable
private fun RouteFooter(state: RouteUiState, onOpen: (List<RouteCandidate>) -> Unit) {
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
