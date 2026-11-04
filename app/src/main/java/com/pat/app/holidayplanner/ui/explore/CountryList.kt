package com.pat.app.holidayplanner.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.CityEntity
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.local.SpotRow
import com.pat.app.holidayplanner.data.repo.AttachmentOwner
import com.pat.app.holidayplanner.domain.CountryCatalog
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.ui.components.Attachments
import com.pat.app.holidayplanner.ui.components.EmptyListMessage
import com.pat.app.holidayplanner.ui.components.HeartCount
import com.pat.app.holidayplanner.ui.components.HeroImage
import com.pat.app.holidayplanner.ui.components.PriorityBadge
import com.pat.app.holidayplanner.ui.components.ReorderableColumn
import com.pat.app.holidayplanner.ui.components.MarkBadges
import com.pat.app.holidayplanner.ui.components.StatusChip
import com.pat.app.holidayplanner.ui.components.Thumbnail
import com.pat.app.holidayplanner.ui.components.TypeBadge
import com.pat.app.holidayplanner.ui.components.noteOrNull
import com.pat.app.holidayplanner.ui.components.label
import com.pat.app.holidayplanner.ui.components.rememberKeyboardDismiss
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

/** One line of the country screen, so the list can be built, keyed and searched in one place. */
private sealed interface Entry {
    val key: String

    data object Hero : Entry {
        override val key = "hero"
    }

    data object CountryOwn : Entry {
        override val key = "country-attachments"
    }

    data object Filters : Entry {
        override val key = "filters"
    }

    data object NoCities : Entry {
        override val key = "no-cities"
    }

    data object NoMatches : Entry {
        override val key = "no-matches"
    }

    data class City(val section: CitySection) : Entry {
        override val key = cityKey(section.city.meta.id)
    }

    data class Spot(val row: SpotRow) : Entry {
        override val key = spotKey(row.spot.meta.id)
    }
}

private fun cityKey(id: String) = "city:$id"

private fun spotKey(id: String) = "spot:$id"

/**
 * [orderOf] gives the spots of a group in the order to show them: a city's by its id, the country's
 * own by an empty id.
 */
private fun buildEntries(
    state: CountryUiState,
    orderOf: (String, List<SpotRow>) -> List<SpotRow>,
): List<Entry> = buildList {
    add(Entry.Hero)
    if (state.countryAttachments.isNotEmpty()) add(Entry.CountryOwn)
    if (state.isEmpty) add(Entry.NoCities) else add(Entry.Filters)
    // The country's own spots come first, above every city, with no heading of their own.
    orderOf("", state.countrySpots).forEach { add(Entry.Spot(it)) }
    // While filtering, a city with nothing that matches is left out rather than shown empty.
    val shown = if (state.filters.any) state.sections.filter { it.spots.isNotEmpty() } else state.sections
    if (state.filters.any && shown.isEmpty() && state.countrySpots.isEmpty()) add(Entry.NoMatches)
    // A city with no spots is just its heading; its "N spots" line already says there are none.
    shown.forEach { section ->
        add(Entry.City(section))
        if (section.expanded) orderOf(section.city.meta.id, section.spots).forEach { add(Entry.Spot(it)) }
    }
}

/**
 * A spot being dragged, and the order the spots of its city (or of the country itself, for an empty
 * [cityId]) are in while it is. The order is held on to
 * after the finger lifts until the database answers, or the row would jump back for a moment.
 */
private data class SpotDrag(
    val cityId: String,
    val priority: Priority,
    val order: List<SpotRow>,
    val movedId: String? = null,
)

/**
 * The whole country on one screen: its picture and its own photos and links, then every city with
 * what hangs on it and its spots underneath. Tapping a city folds it away. Spots are dragged only
 * within their own city; moving one to another city is a menu item, not a gesture.
 */
@Composable
fun CountryContent(
    state: CountryUiState,
    listState: LazyListState,
    actions: CountryActions,
    modifier: Modifier = Modifier,
) {
    var drag by remember { mutableStateOf<SpotDrag?>(null) }
    var dragging by remember { mutableStateOf(false) }

    // A fresh answer from the database replaces the held order, unless a finger is still on it.
    LaunchedEffect(state.sections) {
        if (!dragging) drag = null
    }

    val entries = buildEntries(state) { groupId, spots ->
        drag?.takeIf { it.cityId == groupId }?.order ?: spots
    }

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val current = drag ?: return@rememberReorderableLazyListState
        val fromIndex = current.order.indexOfFirst { spotKey(it.spot.meta.id) == from.key }
        val toIndex = current.order.indexOfFirst { spotKey(it.spot.meta.id) == to.key }
        // High-priority spots stay above the rest, so a spot only swaps places within its own group.
        if (fromIndex >= 0 && toIndex >= 0 && current.order[toIndex].spot.priority == current.priority) {
            val order = current.order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            drag = current.copy(order = order, movedId = order[toIndex].spot.meta.id)
        }
    }

    LazyColumn(state = listState, modifier = modifier) {
        items(entries, key = { it.key }) { entry ->
            if (entry !is Entry.Spot) {
                EntryContent(entry, state, actions)
                return@items
            }
            val cityId = entry.row.spot.parentCityId
            DraggableSpot(
                row = entry.row,
                reorderState = reorderState,
                state = state,
                // Only the spots of the group being dragged in make way, so a spot never changes city
                // or priority group by dragging.
                enabled = state.canReorderSpots &&
                    drag.let { it == null || (it.cityId == cityId && it.priority == entry.row.spot.priority) },
                actions = actions,
                onDragStarted = {
                    dragging = true
                    drag = SpotDrag(cityId, entry.row.spot.priority, state.spotsOf(cityId))
                },
                onDragStopped = {
                    dragging = false
                    drag?.let { persistSpotMove(it, actions.onSpotMoved) }
                },
            )
        }
    }
}

/** Every line of the list that is not a spot. */
@Composable
private fun EntryContent(entry: Entry, state: CountryUiState, actions: CountryActions) {
    when (entry) {
        Entry.Hero -> state.country?.let { country ->
            HeroImage(
                imageUrl = country.imageUrl,
                title = "${CountryCatalog.flag(country.isoCode)} ${country.name}".trim(),
            )
        }
        Entry.CountryOwn -> Attachments(
            attachments = state.countryAttachments,
            onOpenPhoto = actions.onOpenPhoto,
            onOpenLink = actions.onOpenLink,
            onMove = actions.onMoveAttachment,
            onDelete = actions.onDeleteAttachment,
            onUseAsPicture = actions.onUseAsPicture,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Entry.Filters -> FilterRow(state.filters, actions.onFilters)
        Entry.NoCities -> EmptyListMessage(stringResource(R.string.country_empty))
        Entry.NoMatches -> EmptyListMessage(stringResource(R.string.city_no_matches))
        is Entry.City -> CityHeader(section = entry.section, actions = actions)
        is Entry.Spot -> Unit
    }
}

/** A spot row that can be picked up by its handle and dropped elsewhere in its own city. */
@Composable
private fun LazyItemScope.DraggableSpot(
    row: SpotRow,
    reorderState: ReorderableLazyListState,
    state: CountryUiState,
    enabled: Boolean,
    actions: CountryActions,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
) {
    ReorderableItem(reorderState, key = spotKey(row.spot.meta.id), enabled = enabled) { isDragging ->
        val handle: (@Composable () -> Unit)? = if (!state.canReorderSpots) {
            null
        } else {
            {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.action_reorder),
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .draggableHandle(onDragStarted = { onDragStarted() }, onDragStopped = onDragStopped),
                )
            }
        }
        Surface(tonalElevation = if (isDragging) 4.dp else 0.dp) {
            SpotRowItem(
                row = row,
                everyone = state.userCount > 0 && row.heartCount >= state.userCount,
                attachments = state.spotAttachments[row.spot.meta.id].orEmpty(),
                handle = handle,
                actions = actions,
            )
        }
    }
}

/** Turns where the spot was dropped into a sort key between its two new neighbours in its city. */
private fun persistSpotMove(drag: SpotDrag, onMoved: (String, String?, String?) -> Unit) {
    val movedId = drag.movedId ?: return
    val (before, after) = drag.order.dropNeighbours(movedId) ?: return
    onMoved(movedId, before, after)
}

/**
 * A list row already shows its place's picture as the thumbnail. When that picture is one of the
 * place's own photos, repeating it in the row's photo roll beside it would just show it twice.
 */
private fun List<LinkEntity>.withoutPicture(pictureUrl: String): List<LinkEntity> =
    if (pictureUrl.isBlank()) this else filterNot { it.url == pictureUrl }

/** Where the list sits for [cityId], so a search result can open the country scrolled to its city. */
fun CountryUiState.indexOfCity(cityId: String): Int {
    if (cityId.isEmpty()) return -1
    return buildEntries(this) { _, spots -> spots }.indexOfFirst { it.key == cityKey(cityId) }
}

/** How far a city's spots sit in from the city above them, so the nesting reads at a glance. */
private val SPOT_INDENT = 12.dp

/** A city as a section heading: tap to fold, its menu for everything about the city itself. */
@Composable
private fun CityHeader(section: CitySection, actions: CountryActions) {
    val city = section.city
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { actions.onToggleCity(city.meta.id) }
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Thumbnail(city.imageUrl)
            Column(modifier = Modifier.weight(1f)) {
                Text(city.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.country_spot_count, section.spotCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                city.notes.noteOrNull()?.let { note ->
                    Text(text = note, style = MaterialTheme.typography.bodySmall)
                }
            }
            PriorityBadge(city.priority)
            IconButton(onClick = { actions.onShowCityOnMap(city.meta.id) }) {
                Icon(Icons.Outlined.Map, contentDescription = stringResource(R.string.city_show_on_map))
            }
            CityMenu(city = city, actions = actions)
            Icon(
                imageVector = if (section.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    if (section.expanded) R.string.city_collapse else R.string.city_expand,
                ),
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        if (section.expanded) {
            Attachments(
                // Its picture is already the row's thumbnail, so it is not repeated beside it.
                attachments = section.attachments.withoutPicture(city.imageUrl),
                onOpenPhoto = actions.onOpenPhoto,
                onOpenLink = actions.onOpenLink,
                onMove = actions.onMoveAttachment,
                onDelete = actions.onDeleteAttachment,
                onUseAsPicture = actions.onUseAsPicture,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
        HorizontalDivider()
    }
}

/** Everything about one city: its photos, links and picture, a walk through it, its priority. */
@Composable
private fun CityMenu(city: CityEntity, actions: CountryActions) {
    var expanded by remember { mutableStateOf(false) }
    val putKeyboardAway = rememberKeyboardDismiss()
    val owner = AttachmentOwner(cityId = city.meta.id)
    val entries = listOf(
        (if (city.notes.isBlank()) R.string.note_add else R.string.note_edit) to
            { actions.onEditNote(owner, city.notes) },
        R.string.spot_add to { actions.onAddSpot(city) },
        R.string.spot_add_photo to { actions.onAddPhoto(owner) },
        R.string.spot_add_link to { actions.onAddLink(owner) },
        R.string.picture_change to { actions.onChangePicture(city.meta.id) },
        R.string.route_new to { actions.onNewRoute(city) },
    )

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
        entries.forEach { (text, onClick) ->
            DropdownMenuItem(
                text = { Text(stringResource(text)) },
                onClick = {
                    expanded = false
                    onClick()
                },
            )
        }
        HorizontalDivider()
        Priority.entries.forEach { priority ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_set_priority, stringResource(priority.label()))) },
                enabled = priority != city.priority,
                onClick = {
                    expanded = false
                    actions.onCityPriority(city.meta.id, priority)
                },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_rename)) },
            onClick = {
                expanded = false
                actions.onRenameCity(city)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            onClick = {
                expanded = false
                actions.onDeleteCity(city)
            },
        )
    }
}

/** Wraps onto as many lines as it needs, so every chip is in view without scrolling sideways. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(filters: SpotFilters, onChange: (SpotFilters) -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The type first, then the two questions worth asking: is it a priority, and is it still to do.
        SpotType.entries.forEach { type ->
            FilterChip(
                selected = filters.type == type,
                onClick = { onChange(filters.copy(type = type.takeIf { it != filters.type })) },
                label = { Text(stringResource(type.label())) },
            )
        }
        FilterChip(
            selected = filters.hasLinks,
            onClick = { onChange(filters.copy(hasLinks = !filters.hasLinks)) },
            label = { Text(stringResource(R.string.filter_has_links)) },
        )
        FilterChip(
            selected = filters.priority == Priority.HIGH,
            onClick = { onChange(filters.copy(priority = Priority.HIGH.takeIf { filters.priority == null })) },
            label = { Text(stringResource(Priority.HIGH.label())) },
        )
        FilterChip(
            selected = filters.status == SpotStatus.UNVISITED,
            onClick = {
                onChange(filters.copy(status = SpotStatus.UNVISITED.takeIf { filters.status == null }))
            },
            label = { Text(stringResource(SpotStatus.UNVISITED.label())) },
        )
        if (filters.any) {
            FilterChip(
                selected = false,
                onClick = { onChange(SpotFilters()) },
                label = { Text(stringResource(R.string.filter_clear)) },
            )
        }
    }
}

@Composable
private fun SpotRowItem(
    row: SpotRow,
    everyone: Boolean,
    attachments: List<LinkEntity>,
    handle: (@Composable () -> Unit)?,
    actions: CountryActions,
) {
    val id = row.spot.meta.id
    Column(modifier = Modifier.padding(start = SPOT_INDENT)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { actions.onOpenSpot(id) }
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Thumbnail(row.spot.imageUrl, size = 48.dp)
            // A little air between the name and its badges, or they read as one block.
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.spot.name, style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PriorityBadge(row.spot.priority)
                    StatusChip(row.spot.status)
                    MarkBadges(row.spot.marks)
                    TypeBadge(row.spot.type)
                }
                // The reason a place was saved is usually the first thing written about it.
                row.spot.notes.noteOrNull()?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            IconButton(onClick = { actions.onHeart(id) }) {
                HeartCount(count = row.heartCount, heartedByMe = row.heartedByMe, everyone = everyone)
            }
            SpotRowMenu(row = row, actions = actions)
            handle?.invoke()
        }
        // Everything saved about the place, on lines of their own so the row above stays readable.
        // The photo already showing as the row's picture is left out; the spot's own screen has it.
        Attachments(
            attachments = attachments.withoutPicture(row.spot.imageUrl),
            onOpenPhoto = actions.onOpenPhoto,
            onOpenLink = actions.onOpenLink,
            onMove = actions.onMoveAttachment,
            onDelete = actions.onDeleteAttachment,
            onUseAsPicture = actions.onUseAsPicture,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )
        HorizontalDivider()
    }
}

@Composable
private fun SpotRowMenu(row: SpotRow, actions: CountryActions) {
    var expanded by remember { mutableStateOf(false) }
    val putKeyboardAway = rememberKeyboardDismiss()
    val id = row.spot.meta.id

    Box {
        IconButton(
            onClick = {
                // Dropped before the popup takes window focus, so there is nothing to hand back to.
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
            val noteLabel = if (row.spot.notes.isBlank()) R.string.note_add else R.string.note_edit
            DropdownMenuItem(
                text = { Text(stringResource(noteLabel)) },
                onClick = {
                    expanded = false
                    actions.onEditNote(AttachmentOwner(spotId = id), row.spot.notes)
                },
            )
            HorizontalDivider()
            Priority.entries.forEach { priority ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_set_priority, stringResource(priority.label()))) },
                    enabled = priority != row.spot.priority,
                    onClick = {
                        expanded = false
                        actions.onSpotPriority(id, priority)
                    },
                )
            }
            HorizontalDivider()
            SpotStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_set_status, stringResource(status.label()))) },
                    enabled = status != row.spot.status,
                    onClick = {
                        expanded = false
                        actions.onSpotStatus(id, status)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.spot_move)) },
                onClick = {
                    expanded = false
                    actions.onMoveSpot(row)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = {
                    expanded = false
                    actions.onDeleteSpot(row)
                },
            )
        }
    }
}

fun SpotStatus.label(): Int = when (this) {
    SpotStatus.UNVISITED -> R.string.status_unvisited
    SpotStatus.VISITED -> R.string.status_visited
}

/**
 * The cities alone, folded down to one line each with a handle, for putting them in order. The
 * spots are left out so a city never has to be dragged past a screenful of them.
 */
@Composable
fun ArrangeCities(
    sections: List<CitySection>,
    onMoved: (id: String, previousKey: String?, nextKey: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    ReorderableColumn(
        items = sections.map { it.city },
        idOf = { it.meta.id },
        sortKeyOf = { it.sortKey },
        reorderable = true,
        onMoved = onMoved,
        modifier = modifier,
    ) { city, handle ->
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Thumbnail(city.imageUrl, size = 40.dp)
                Text(
                    text = city.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                handle?.invoke()
            }
            HorizontalDivider()
        }
    }
}

/** Everything the country list can ask for, so its rows need no view model of their own. */
interface CountryActions {
    val onOpenSpot: (String) -> Unit
    val onToggleCity: (String) -> Unit

    /** Opens our own map on one city, by its id. */
    val onShowCityOnMap: (String) -> Unit
    val onFilters: (SpotFilters) -> Unit
    val onOpenPhoto: (LinkEntity) -> Unit
    val onOpenLink: (LinkEntity) -> Unit
    val onMoveAttachment: (LinkEntity) -> Unit
    val onDeleteAttachment: (LinkEntity) -> Unit
    val onUseAsPicture: (LinkEntity) -> Unit
    val onAddPhoto: (AttachmentOwner) -> Unit
    val onAddLink: (AttachmentOwner) -> Unit

    /** Opens the note of a city or a spot, with what it says now. */
    val onEditNote: (AttachmentOwner, String) -> Unit

    /** Empty for the country's own picture, a city id for that city's. */
    val onChangePicture: (String) -> Unit
    val onNewRoute: (CityEntity) -> Unit

    /** Adds a spot to one city, searching Google from inside it. */
    val onAddSpot: (CityEntity) -> Unit
    val onCityPriority: (String, Priority) -> Unit
    val onRenameCity: (CityEntity) -> Unit
    val onDeleteCity: (CityEntity) -> Unit
    val onHeart: (String) -> Unit
    val onSpotPriority: (String, Priority) -> Unit
    val onSpotStatus: (String, SpotStatus) -> Unit
    val onSpotMoved: (String, String?, String?) -> Unit
    val onMoveSpot: (SpotRow) -> Unit
    val onDeleteSpot: (SpotRow) -> Unit
}
