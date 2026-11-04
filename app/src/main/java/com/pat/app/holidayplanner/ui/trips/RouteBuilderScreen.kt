package com.pat.app.holidayplanner.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.domain.MapsUrls
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.ui.components.MapButtonRow
import com.pat.app.holidayplanner.ui.components.MapsNavigateButton
import com.pat.app.holidayplanner.ui.components.MapsPreviewButton
import com.pat.app.holidayplanner.ui.components.OurMapButton
import com.pat.app.holidayplanner.ui.components.BackTopBar
import com.pat.app.holidayplanner.ui.components.ConfirmDeleteDialog
import com.pat.app.holidayplanner.ui.components.DARK_TEXT
import com.pat.app.holidayplanner.ui.components.EmptyListMessage
import com.pat.app.holidayplanner.ui.components.MarkBadges
import com.pat.app.holidayplanner.ui.components.PlaceSearchSheet
import com.pat.app.holidayplanner.ui.components.PriorityBadge
import com.pat.app.holidayplanner.ui.components.ReorderableColumn
import com.pat.app.holidayplanner.ui.components.SpotPickerSheet
import com.pat.app.holidayplanner.ui.components.StatusChip
import com.pat.app.holidayplanner.ui.components.Thumbnail
import com.pat.app.holidayplanner.ui.components.TypeBadge
import com.pat.app.holidayplanner.ui.components.VISITED_GREEN
import com.pat.app.holidayplanner.ui.components.noteOrNull
import com.pat.app.holidayplanner.ui.components.openExternalUrl
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A route: a name, whether it has been walked, and the spots put on it in the order they are walked.
 *
 * Only this route's stops are listed. The tick against each one says whether Google is asked to
 * walk you past it, so a stop can sit on the plan without being in today's directions. Everything
 * written here takes effect at once, so there is no Save button: the list is the plan.
 */
@Composable
fun RouteBuilderScreen(
    routeId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onOpenSpot: (String) -> Unit,
    onShowOnMap: () -> Unit,
    viewModel: RouteViewModel = koinViewModel { parametersOf(routeId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmingDelete by remember { mutableStateOf(false) }
    var pickingSpot by remember { mutableStateOf(false) }
    var searchingSpot by remember { mutableStateOf(false) }

    val noMapsApp = stringResource(R.string.route_no_maps_app)
    val open: (String?) -> Unit = { url ->
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
                onAddStop = { pickingSpot = true },
            )
            HorizontalDivider()

            if (state.isEmpty) {
                EmptyListMessage(stringResource(R.string.route_empty))
            } else {
                StopList(
                    state = state,
                    viewModel = viewModel,
                    onDirections = { open(viewModel.directionsTo(it)) },
                    onOpenSpot = onOpenSpot,
                    modifier = Modifier.weight(1f),
                )
                HorizontalDivider()
                RouteFooter(
                    state = state,
                    onShowOnMap = onShowOnMap,
                    onPreview = { stops -> open(viewModel.mapsUrl(stops, fromHere = false)) },
                    onNavigate = { stops -> open(viewModel.mapsUrl(stops, fromHere = true, navigate = true)) },
                )
            }
        }
    }

    // One sheet for both ways of adding: the city's other spots, and a search for somewhere new.
    if (pickingSpot) {
        val alreadyOn = state.stops.map { it.spotId }.toSet()
        SpotPickerSheet(
            filter = { it.cityId == state.cityId && it.spotId !in alreadyOn },
            allowCreate = true,
            onCreateNew = {
                pickingSpot = false
                searchingSpot = true
            },
            onPick = {
                pickingSpot = false
                viewModel.addSpot(it.spotId)
            },
            onDismiss = { pickingSpot = false },
        )
    }

    if (searchingSpot) {
        // Filed in the route's own city, and on the walk before the sheet has closed.
        PlaceSearchSheet(
            title = stringResource(R.string.spot_add),
            cityId = state.cityId,
            onDismiss = { searchingSpot = false },
            onSaved = { spotId ->
                searchingSpot = false
                viewModel.addSpot(spotId)
            },
        )
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
    onAddStop: () -> Unit,
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
            AssistChip(onClick = onAddStop, label = { Text(stringResource(R.string.route_add_stop)) })
            if (state.walked.size > 1) {
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
private fun StopList(
    state: RouteUiState,
    viewModel: RouteViewModel,
    onDirections: (RouteStop) -> Unit,
    onOpenSpot: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ReorderableColumn(
        items = state.stops,
        idOf = { it.stopId },
        sortKeyOf = { it.sortKey },
        reorderable = true,
        onMoved = viewModel::move,
        modifier = modifier,
    ) { stop, handle ->
        SwipeToMarkVisited(
            visited = stop.status == SpotStatus.VISITED,
            onVisited = { viewModel.setVisited(stop, it) },
        ) {
            StopRow(
                stop = stop,
                position = state.walked.indexOf(stop).takeIf { it >= 0 }?.plus(1),
                handle = handle,
                onToggle = { viewModel.setOnMaps(stop, it) },
                onOpen = { onOpenSpot(stop.spotId) },
                onDirections = { onDirections(stop) },
                onRemove = { viewModel.remove(stop) },
            )
        }
    }
}

/**
 * Swipe a stop one way to say you have been there, the other way to take it back.
 *
 * The box never dismisses anything. The swipe is read once it has settled, the change is written,
 * and the row is sent back to where it started, because a stop you have walked still belongs on
 * the list and flying it off the screen would say the wrong thing.
 */
@Composable
private fun SwipeToMarkVisited(
    visited: Boolean,
    onVisited: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> onVisited(true)
            SwipeToDismissBoxValue.EndToStart -> onVisited(false)
            SwipeToDismissBoxValue.Settled -> return@LaunchedEffect
        }
        state.reset()
    }
    SwipeToDismissBox(
        state = state,
        backgroundContent = { SwipeHint(state.dismissDirection) },
        // Only the swipe that would change something answers the finger.
        enableDismissFromStartToEnd = !visited,
        enableDismissFromEndToStart = visited,
    ) {
        // The row has to be opaque, or the hint behind it shows through as it slides.
        Surface(color = MaterialTheme.colorScheme.surface) { content() }
    }
}

/** What is about to happen, showing from the edge the finger came from. */
@Composable
private fun SwipeHint(direction: SwipeToDismissBoxValue) {
    if (direction == SwipeToDismissBoxValue.Settled) return
    val marking = direction == SwipeToDismissBoxValue.StartToEnd
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(if (marking) VISITED_GREEN else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 20.dp),
        horizontalArrangement = if (marking) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (marking) DARK_TEXT else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(
            imageVector = if (marking) Icons.Filled.Check else Icons.AutoMirrored.Filled.Undo,
            contentDescription = null,
            tint = tint,
        )
        Text(
            text = stringResource(
                if (marking) R.string.route_mark_visited else R.string.route_mark_unvisited,
            ),
            color = tint,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * A stop as a spot looks everywhere else, with the tick that puts it in the directions, a way to
 * walk there on its own, and a way off the route. Tapping the row opens the spot itself, the same
 * as tapping it in any other list.
 */
@Composable
private fun StopRow(
    stop: RouteStop,
    position: Int?,
    handle: (@Composable () -> Unit)?,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onDirections: () -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(checked = stop.onMaps, onCheckedChange = onToggle)
            Thumbnail(stop.imageUrl, size = 44.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    // The number is where this stop comes in the directions, so it skips the unticked.
                    text = position?.let { "$it. ${stop.name}" } ?: stop.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PriorityBadge(stop.priority)
                    StatusChip(stop.status)
                    MarkBadges(stop.marks)
                    TypeBadge(stop.type)
                }
                stop.notes.noteOrNull()?.let { note ->
                    Text(text = note, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onDirections, modifier = Modifier.size(ICON_BUTTON_DP.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = stringResource(R.string.route_directions_to),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(ICON_BUTTON_DP.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.route_remove_stop),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            handle?.invoke()
        }
        HorizontalDivider()
    }
}

/** Tighter than a standard icon button, so a row still has room for the spot itself. */
private const val ICON_BUTTON_DP = 40

/**
 * The three ways to look at or set off on a walk: our own map, zoomed to the route with its line
 * picked out; a preview in Google Maps of the stops on their own, first to last, for looking a day
 * over; and turn-by-turn in Google Maps from where you are standing, for walking it now.
 *
 * Google takes ten stops at a time, so a longer walk keeps one button for our map, which shows all
 * of it, and gets Preview and Navigate per part.
 */
@Composable
private fun RouteFooter(
    state: RouteUiState,
    onShowOnMap: () -> Unit,
    onPreview: (List<RouteStop>) -> Unit,
    onNavigate: (List<RouteStop>) -> Unit,
) {
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
            !state.canOpen -> {
                Note(stringResource(R.string.route_needs_a_stop))
                OurMapButton(onShowOnMap, Modifier.fillMaxWidth())
            }

            state.tooLongForOneRoute -> {
                Note(stringResource(R.string.route_split_warning, MapsUrls.MAX_ROUTE_STOPS, state.parts.size))
                OurMapButton(onShowOnMap, Modifier.fillMaxWidth())
                state.parts.forEachIndexed { index, part ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.route_open_part, index + 1, state.parts.size),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        MapsPreviewButton(
                            onClick = if (part.size >= 2) ({ onPreview(part) }) else null,
                            modifier = Modifier.weight(1f),
                        )
                        MapsNavigateButton(onClick = { onNavigate(part) }, Modifier.weight(1f))
                    }
                }
            }

            // One stop has no walk to show without you at the other end of it.
            else -> MapButtonRow(
                onOurMap = onShowOnMap,
                onPreview = if (state.walked.size >= 2) ({ onPreview(state.walked) }) else null,
                onNavigate = { onNavigate(state.walked) },
            )
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
