package com.pat.app.holidayplanner.ui.trips

import com.pat.app.holidayplanner.ui.components.rememberHoursBulk
import com.pat.app.holidayplanner.ui.components.HoursRunBanner
import com.pat.app.holidayplanner.ui.components.HoursBulkDialog
import androidx.compose.foundation.layout.size
import java.util.Locale
import java.time.format.TextStyle
import java.time.DayOfWeek
import com.pat.app.holidayplanner.ui.components.label
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.CityOption
import com.pat.app.holidayplanner.data.local.RouteRow
import com.pat.app.holidayplanner.data.local.TripItemRow
import com.pat.app.holidayplanner.domain.LocalTimes
import com.pat.app.holidayplanner.domain.TripItemType
import com.pat.app.holidayplanner.ui.components.PickFromListDialog
import com.pat.app.holidayplanner.ui.components.PickSpotsSheet
import com.pat.app.holidayplanner.ui.components.PlaceSearchSheet
import com.pat.app.holidayplanner.ui.components.PriorityBadge
import com.pat.app.holidayplanner.ui.components.SpotPickerSheet
import com.pat.app.holidayplanner.ui.components.MarkBadges
import com.pat.app.holidayplanner.ui.components.StatusChip
import com.pat.app.holidayplanner.ui.components.Thumbnail
import com.pat.app.holidayplanner.ui.components.TypeBadge
import com.pat.app.holidayplanner.ui.components.WalkedBadge
import com.pat.app.holidayplanner.ui.components.rememberKeyboardDismiss
import java.time.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** What is being added to which day, while the person is still choosing. */
private sealed interface Adding {
    data class Kind(val date: LocalDate) : Adding
    data class Spot(val date: LocalDate) : Adding
    data class NewSpot(val date: LocalDate) : Adding
    data class Route(val date: LocalDate) : Adding
    data class RouteCity(val date: LocalDate) : Adding
    data class RouteSpots(val date: LocalDate, val city: CityOption) : Adding
}

/**
 * A trip, day by day. Each day is a heading with a plus beside it, and what is under the heading
 * is what happens that day: flights, spots and routes, in that order of importance to the eye.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(
    tripId: String,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenStop: (spotId: String, itemId: String) -> Unit,
    onOpenRoute: (String) -> Unit,
    onEditTrip: () -> Unit,
    onShowDayOnMap: (LocalDate) -> Unit,
    viewModel: TripViewModel = koinViewModel { parametersOf(tripId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf<Adding?>(null) }
    val hours = rememberHoursBulk()
    val runId = "trip:$tripId"

    Scaffold(
        // The tab bar below already keeps clear of the system bar; padding for it again left a dead strip.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                    if (hours.lookup.isAvailable) {
                        IconButton(
                            onClick = {
                                hours.ask(runId, state.trip?.name.orEmpty()) { hours.lookup.dueOnTrip(tripId) }
                            },
                        ) {
                            Icon(
                                Icons.Outlined.Schedule,
                                contentDescription = stringResource(R.string.hours_bulk_action),
                            )
                        }
                    }
                    IconButton(onClick = onEditTrip) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.trip_edit))
                    }
                },
            )
        },
        snackbarHost = { HoursRunBanner(shows = { it.runId == runId }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!state.hasDates) {
                item(key = "no-dates") { NoDatesCard(onEditTrip) }
            } else if (state.isEmpty) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.trip_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }

            state.plan.days.forEach { day ->
                item(key = "day-${day.date}") {
                    DayHeader(
                        date = day.date,
                        onAdd = { adding = Adding.Kind(day.date) },
                        // Only once something is planned, since an empty day has nothing to show.
                        onShowOnMap = if (day.entries.isEmpty()) null else ({ onShowDayOnMap(day.date) }),
                    )
                }
                items(day.entries, key = { it.item.meta.id }) { row ->
                    TripItemCard(
                        row = row,
                        warning = hoursWarning(row, state.stopHours),
                        onClick = { open(row, onOpenItem, onOpenStop, onOpenRoute) },
                        onRemove = { viewModel.deleteItem(row.item.meta.id) },
                    )
                }
            }

            if (state.plan.undated.isNotEmpty()) {
                item(key = "undated") {
                    Text(
                        text = stringResource(R.string.trip_undated),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                items(state.plan.undated, key = { "undated-${it.item.meta.id}" }) { row ->
                    TripItemCard(
                        row = row,
                        warning = hoursWarning(row, state.stopHours),
                        onClick = { open(row, onOpenItem, onOpenStop, onOpenRoute) },
                        onRemove = { viewModel.deleteItem(row.item.meta.id) },
                    )
                }
            }
        }
    }

    HoursBulkDialog(hours)

    AddingDialogs(
        adding = adding,
        state = state,
        viewModel = viewModel,
        onChange = { adding = it },
        onOpenItem = onOpenItem,
        onOpenRoute = onOpenRoute,
    )
}

/**
 * A route opens its own screen, and a spot opens as the spot itself with the trip's side of it on
 * top. Anything else, a flight or a stop still waiting for its spot, opens the item.
 */
private fun open(
    row: TripItemRow,
    onOpenItem: (String) -> Unit,
    onOpenStop: (spotId: String, itemId: String) -> Unit,
    onOpenRoute: (String) -> Unit,
) {
    val item = row.item
    when {
        item.type == TripItemType.ROUTE && item.routeId.isNotBlank() -> onOpenRoute(item.routeId)
        item.type == TripItemType.SPOT && item.spotId.isNotBlank() -> onOpenStop(item.spotId, item.meta.id)
        else -> onOpenItem(item.meta.id)
    }
}

@Composable
private fun AddingDialogs(
    adding: Adding?,
    state: TripUiState,
    viewModel: TripViewModel,
    onChange: (Adding?) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenRoute: (String) -> Unit,
) {
    val countryId = state.trip?.countryId.orEmpty()
    when (adding) {
        null -> Unit

        is Adding.Kind -> KindDialog(
            date = adding.date,
            onPick = { type ->
                when (type) {
                    TripItemType.FLIGHT -> {
                        onChange(null)
                        viewModel.addFlight(adding.date, onOpenItem)
                    }
                    TripItemType.SPOT -> onChange(Adding.Spot(adding.date))
                    TripItemType.ROUTE -> onChange(Adding.Route(adding.date))
                }
            },
            onDismiss = { onChange(null) },
        )

        is Adding.Spot -> SpotPickerSheet(
            filter = { it.countryId == countryId },
            allowCreate = true,
            onCreateNew = { onChange(Adding.NewSpot(adding.date)) },
            onPick = { spot ->
                onChange(null)
                viewModel.addSpot(adding.date, spot)
            },
            onDismiss = { onChange(null) },
        )

        // Somewhere in this country that nobody has saved yet, straight onto the day.
        is Adding.NewSpot -> PlaceSearchSheet(
            title = stringResource(R.string.spot_add),
            knownIsoCode = state.country?.isoCode.orEmpty(),
            onDismiss = { onChange(null) },
            onSaved = { spotId ->
                onChange(null)
                viewModel.addSpot(adding.date, spotId)
            },
        )

        is Adding.Route -> RoutePickerDialog(
            state = state,
            onPick = { route ->
                onChange(null)
                viewModel.addRoute(adding.date, route)
            },
            onNew = { onChange(Adding.RouteCity(adding.date)) },
            onDismiss = { onChange(null) },
        )

        is Adding.RouteCity -> PickFromListDialog(
            title = stringResource(R.string.route_pick_city),
            options = state.cities.map { it.cityId to it.cityName },
            onPick = { cityId ->
                val city = state.cities.firstOrNull { it.cityId == cityId }
                onChange(city?.let { Adding.RouteSpots(adding.date, it) })
            },
            onDismiss = { onChange(null) },
        )

        // The city first, then what the walk takes in, then it opens to be ordered.
        is Adding.RouteSpots -> PickSpotsSheet(
            cityId = adding.city.cityId,
            onConfirm = { spotIds ->
                onChange(null)
                viewModel.addNewRoute(adding.date, adding.city, spotIds, onOpenRoute)
            },
            onDismiss = { onChange(null) },
        )
    }
}

@Composable
private fun NoDatesCard(onEditTrip: () -> Unit) {
    Card(modifier = Modifier.padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.trip_no_dates_yet), style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = onEditTrip) { Text(stringResource(R.string.trip_set_dates)) }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, onAdd: () -> Unit, onShowOnMap: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = LocalTimes.formatDay(date),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        onShowOnMap?.let {
            IconButton(onClick = it) {
                Icon(
                    Icons.Filled.Map,
                    contentDescription = stringResource(R.string.trip_day_on_map, LocalTimes.formatDay(date)),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onAdd) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.trip_add_to_day, LocalTimes.formatDay(date)),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

/** One thing on a day. A flight and a route say what they are; a spot shows its picture and tags. */
@Composable
private fun TripItemCard(row: TripItemRow, warning: HoursWarning?, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (row.item.type) {
            TripItemType.SPOT -> Thumbnail(row.spotImageUrl.orEmpty(), size = 48.dp)
            TripItemType.FLIGHT -> KindIcon(Icons.Outlined.Flight)
            TripItemType.ROUTE -> KindIcon(Icons.Outlined.Route)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (row.item.type != TripItemType.SPOT) {
                Text(
                    text = stringResource(row.item.type.label()).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                text = row.title.ifBlank { stringResource(row.item.type.label()) },
                style = MaterialTheme.typography.titleMedium,
            )
            subtitle(row)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                row.spotPriority?.let { PriorityBadge(it) }
                row.spotStatus?.let { StatusChip(it) }
                row.spotMarks?.let { MarkBadges(it) }
                row.spotType?.let { TypeBadge(it) }
                row.routeCompleted?.let { WalkedBadge(it) }
            }
            warning?.let { HoursWarningLine(it) }
        }
        RemoveMenu(onRemove)
    }
}

@Composable
private fun KindIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun RemoveMenu(onRemove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val putKeyboardAway = rememberKeyboardDismiss()
    IconButton(
        onClick = {
            putKeyboardAway()
            expanded = true
        },
    ) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            expanded = false
            putKeyboardAway()
        },
        properties = PopupProperties(focusable = false),
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.trip_remove_item)) },
            onClick = {
                expanded = false
                onRemove()
            },
        )
    }
}

/** A plan that meets a closed door, in the error colour so it is seen before the day, not on it. */
@Composable
private fun HoursWarningLine(warning: HoursWarning) {
    val text = when (warning) {
        is HoursWarning.ClosedAt -> stringResource(
            R.string.hours_warn_closed_at,
            warning.time,
            warning.thatDay.label().orEmpty(),
        )
        is HoursWarning.ClosedThatDay -> stringResource(R.string.hours_warn_closed_day, warning.day.fullName())
        is HoursWarning.StopsClosed -> stringResource(
            R.string.hours_warn_stops_closed,
            warning.day.fullName(),
            warning.names.joinToString(", "),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

private fun DayOfWeek.fullName(): String = getDisplayName(TextStyle.FULL, Locale.getDefault())

/** What a row says under its name: a flight its number and length, a route its city and stops. */
@Composable
private fun subtitle(row: TripItemRow): String? {
    val item = row.item
    return when (item.type) {
        // The legs are the name, so the line under it holds the flight number, the time and how long.
        TripItemType.FLIGHT -> {
            val elapsed = LocalTimes.elapsed(item.startLocal, item.startTimeZone, item.endLocal, item.endTimeZone)
            listOfNotNull(
                item.title.takeIf { it.isNotBlank() && row.title != it },
                LocalTimes.timeOfDay(item.startLocal).takeIf { it.isNotBlank() },
                elapsed?.let(LocalTimes::formatDuration),
            ).joinToString(" \u00b7 ").takeIf { it.isNotBlank() }
        }

        TripItemType.ROUTE -> routeDetail(row.title, row.routeCityName, row.routeStopCount)

        TripItemType.SPOT -> LocalTimes.timeOfDay(item.startLocal).takeIf { it.isNotBlank() }
    }
}

/** A route named after its city says so once: then only the stop count is worth adding. */
@Composable
private fun routeDetail(name: String, cityName: String?, stops: Int): String =
    if (cityName.isNullOrBlank() || cityName == name) {
        pluralStringResource(R.plurals.route_stop_count, stops, stops)
    } else {
        stringResource(R.string.trip_route_stops, cityName, pluralStringResource(R.plurals.count_stops, stops, stops))
    }

@Composable
private fun KindDialog(date: LocalDate, onPick: (TripItemType) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trip_add_to_day, LocalTimes.formatDay(date))) },
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

/** The routes through the trip's country, and the way to start one that is not there yet. */
@Composable
private fun RoutePickerDialog(
    state: TripUiState,
    onPick: (RouteRow) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trip_pick_route)) },
        text = {
            Column {
                state.routes.forEach { route ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(route) }
                            .padding(vertical = 10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = route.route.name.ifBlank { route.cityName },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            WalkedBadge(route.route.completed)
                        }
                        Text(
                            text = routeDetail(route.route.name, route.cityName, route.stopCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FilledTonalButton(
                    onClick = onNew,
                    enabled = state.cities.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.trip_new_route), modifier = Modifier.padding(start = 8.dp))
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
    TripItemType.SPOT -> R.string.item_spot
    TripItemType.ROUTE -> R.string.item_route
}
