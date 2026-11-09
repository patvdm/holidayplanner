package com.pat.app.holidayplanner.ui.map

import androidx.compose.material.icons.filled.Hotel
import com.pat.app.holidayplanner.ui.components.PriorityStar
import com.pat.app.holidayplanner.ui.components.WeekHours
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.foundation.clickable
import com.pat.app.holidayplanner.data.repo.galleryOf
import com.pat.app.holidayplanner.domain.pictureUrlOrNull
import com.pat.app.holidayplanner.ui.components.PictureViewer
import com.pat.app.holidayplanner.ui.components.Thumbnail
import com.pat.app.holidayplanner.ui.components.rememberFoundPictures
import java.time.LocalDate
import com.pat.app.holidayplanner.ui.components.dayHoursText
import com.pat.app.holidayplanner.domain.OpeningHours
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.size
import com.pat.app.holidayplanner.domain.SpotStatus
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.AddRoad
import androidx.compose.material3.Icon
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.ui.components.LinkList
import com.pat.app.holidayplanner.ui.components.openExternalUrl
import com.pat.app.holidayplanner.ui.routes.AddToRouteSheet
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.SpotPin
import com.pat.app.holidayplanner.domain.LocalTimes
import com.pat.app.holidayplanner.domain.MapPinFilter
import com.pat.app.holidayplanner.domain.MapView
import com.pat.app.holidayplanner.domain.MapsUrls
import com.pat.app.holidayplanner.domain.RoutePoint
import kotlinx.coroutines.flow.filter
import com.pat.app.holidayplanner.ui.components.BackTopBar
import com.pat.app.holidayplanner.ui.components.EmptyListMessage
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Every spot on one map. Each pin carries the start of its name and an icon for its kind; colour
 * says charcoal for a normal spot, orange for high priority, faded with a tick once visited
 * (see MapMarkers.kt).
 *
 * It always shows everything and only differs in where it opens, so a spot just over a city border
 * is visible rather than mysteriously missing. Your own position is drawn when location is allowed,
 * which is how you see what is near you.
 */
@Composable
fun MapScreen(
    scope: MapScope,
    onBack: () -> Unit,
    onOpenSpot: (String) -> Unit,
    onOpenRoute: (String) -> Unit,
    viewModel: MapViewModel = koinViewModel { parametersOf(scope) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val detail by viewModel.selection.collectAsStateWithLifecycle()
    var addingToRoute by remember { mutableStateOf<SpotPin?>(null) }
    val context = LocalContext.current
    var locationAllowed by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> locationAllowed = granted }

    // Asked once on arrival. Refused is fine: the map works, it just has no blue dot.
    LaunchedEffect(Unit) {
        if (!locationAllowed) permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    Scaffold(
        topBar = {
            val title = state.day?.let { LocalTimes.formatDay(it) } ?: stringResource(R.string.map_title)
            BackTopBar(title, onBack = onBack)
        },
    ) { padding ->
        if (!state.hasPins) {
            EmptyListMessage(stringResource(R.string.map_no_pins), Modifier.padding(padding))
            return@Scaffold
        }
        SpotMap(
            pins = state.pins,
            badges = state.badges,
            focus = state.focus,
            showMyLocation = locationAllowed,
            detail = detail,
            routes = RouteLayerState(
                lines = state.lines,
                showing = state.showsRoutes,
                onShow = viewModel::setShowsRoutes,
                onOpen = onOpenRoute,
            ),
            filter = PinFilterState(current = state.filter, onChange = viewModel::setFilter),
            onSelect = viewModel::select,
            onOpenSpot = onOpenSpot,
            onAddToRoute = { addingToRoute = it },
            onSetVisited = viewModel::setVisited,
            onTogglePriority = viewModel::togglePriority,
            onOpenLink = { context.openExternalUrl(it.url) },
            onNavigate = { pin, placeId ->
                context.openExternalUrl(MapsUrls.navigateTo(RoutePoint(pin.name, pin.lat, pin.lng, placeId)))
            },
            onSettled = viewModel::rememberView,
            modifier = Modifier.padding(padding),
        )
    }

    addingToRoute?.let { pin ->
        AddToRouteSheet(
            spotId = pin.spotId,
            spotName = pin.name,
            onDismiss = { addingToRoute = null },
            onOpenRoute = onOpenRoute,
        )
    }
}

@Composable
private fun SpotMap(
    pins: List<SpotPin>,
    badges: Map<String, RouteBadge>,
    focus: MapFocus?,
    showMyLocation: Boolean,
    detail: PinDetail?,
    routes: RouteLayerState,
    filter: PinFilterState,
    onSelect: (String?) -> Unit,
    onOpenSpot: (String) -> Unit,
    onAddToRoute: (SpotPin) -> Unit,
    onSetVisited: (String, Boolean) -> Unit,
    onTogglePriority: (String) -> Unit,
    onOpenLink: (LinkEntity) -> Unit,
    onNavigate: (SpotPin, String) -> Unit,
    onSettled: (MapView) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<SpotPin?>(null) }
    // A tapped route line; a pin and a route are never both up at once.
    var chosenRoute by remember { mutableStateOf<RouteLine?>(null) }
    // The card's notes and links load for whichever pin is up.
    LaunchedEffect(selected?.spotId) { onSelect(selected?.spotId) }
    // A pin the filter has just hidden takes its card with it.
    LaunchedEffect(pins) {
        if (pins.none { it.spotId == selected?.spotId }) selected = null
    }
    // Both survive a trip to a spot and back, so the map is left where it was, not re-aimed.
    val camera = rememberCameraPositionState()
    var aimed by rememberSaveable { mutableStateOf(false) }

    // The pins arrive after the first composition, so the camera follows once they are known.
    LaunchedEffect(focus) {
        val target = focus ?: return@LaunchedEffect
        if (aimed) return@LaunchedEffect
        aimed = true
        camera.move(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.fromLatLngZoom(LatLng(target.centre.lat, target.centre.lng), target.zoom),
            ),
        )
        // Opened for one spot or route: its card is up too, so it is plain which one is meant.
        if (target.spotId.isNotEmpty()) selected = pins.firstOrNull { it.spotId == target.spotId }
        if (target.routeId.isNotEmpty()) chosenRoute = routes.lines.firstOrNull { it.routeId == target.routeId }
    }

    // Each time the map comes to rest after being aimed, remember the view for the next opening.
    LaunchedEffect(camera) {
        snapshotFlow { camera.isMoving }
            .filter { moving -> !moving && aimed }
            .collect {
                val at = camera.position
                onSettled(MapView(at.target.latitude, at.target.longitude, at.zoom))
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = MapProperties(isMyLocationEnabled = showMyLocation),
            // North stays up: a two-finger zoom turned the map too easily, and nothing here needs
            // it turned or tilted. The remembered view keeps no bearing, so it opens north-up too.
            uiSettings = MAP_UI,
            onMapClick = {
                selected = null
                chosenRoute = null
            },
        ) {
            RouteLinesLayer(lines = routes.lines, chosenId = chosenRoute?.routeId) {
                chosenRoute = it
                selected = null
            }
            pins.forEach { pin ->
                // Keyed on what the picture shows, so a pin is only redrawn when its look changes.
                MarkerComposable(
                    pin.name,
                    pin.pinStyle(),
                    pin.pinKind(),
                    badges[pin.spotId] ?: NO_BADGE,
                    state = rememberUpdatedMarkerState(position = LatLng(pin.lat, pin.lng)),
                    title = pin.name,
                    snippet = pin.cityName.ifBlank { pin.countryName },
                    // High priority draws over the rest where pins overlap.
                    zIndex = if (pin.pinStyle() == PinStyle.HIGH) 1f else 0f,
                    onClick = {
                        selected = pin
                        chosenRoute = null
                        // Consume it, so the map does not also recentre and hide the card.
                        true
                    },
                ) {
                    PinMarker(pin, badges[pin.spotId])
                }
            }
        }

        MapChips(
            routes = routes,
            filter = filter,
            onRoutesOff = { chosenRoute = null },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        )

        chosenRoute?.let { route ->
            RouteCard(
                route = route,
                onOpen = { routes.onOpen(route.routeId) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }

        // The live pin rather than the one tapped, so the card follows a change made from it.
        selected?.let { tapped -> pins.firstOrNull { it.spotId == tapped.spotId } ?: tapped }?.let { pin ->
            SpotCard(
                pin = pin,
                onSetVisited = { onSetVisited(pin.spotId, it) },
                onTogglePriority = { onTogglePriority(pin.spotId) },
                detail = detail?.takeIf { it.spotId == pin.spotId },
                onOpen = { onOpenSpot(pin.spotId) },
                onAddToRoute = { onAddToRoute(pin) },
                onNavigate = { onNavigate(pin, detail?.takeIf { it.spotId == pin.spotId }?.googlePlaceId.orEmpty()) },
                onOpenLink = onOpenLink,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }
}

/**
 * The round switches over the map's top left corner: route lines, then the pin filters. Icons
 * only, to keep the map clear; a filled button is on. The filters take turns, so picking one
 * drops the other, and tapping the one that is on shows everything again.
 */
@Composable
private fun MapChips(
    routes: RouteLayerState,
    filter: PinFilterState,
    onRoutesOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Shown while there is something to switch, and always while switched off, to switch back.
        if (routes.lines.isNotEmpty() || !routes.showing) {
            MapToggle(
                checked = routes.showing,
                onChange = {
                    routes.onShow(it)
                    if (!it) onRoutesOff()
                },
                icon = Icons.Filled.Route,
                description = stringResource(R.string.map_routes),
            )
        }
        PinFilterOptions.forEach { (option, icon, label) ->
            MapToggle(
                checked = filter.current == option,
                onChange = { on -> filter.onChange(if (on) option else MapPinFilter.ALL) },
                icon = icon,
                description = stringResource(label),
            )
        }
    }
}

/** Each pin filter's button: what it picks, its icon, and what a screen reader calls it. */
private val PinFilterOptions = listOf(
    Triple(MapPinFilter.FOOD, Icons.Filled.Restaurant, R.string.map_food_only),
    Triple(MapPinFilter.STAY, Icons.Filled.Hotel, R.string.map_stay_only),
    Triple(MapPinFilter.DRONE, DroneIcon, R.string.map_drone_only),
)

/** Which pins are showing, and how to change that. */
private class PinFilterState(val current: MapPinFilter, val onChange: (MapPinFilter) -> Unit)

/** The route lines and what they can do, handed to the map as one. */
private class RouteLayerState(
    val lines: List<RouteLine>,
    val showing: Boolean,
    val onShow: (Boolean) -> Unit,
    val onOpen: (String) -> Unit,
)

/**
 * The tapped pin: its name and town, everything written about it, and its saved links, which open
 * straight from here. Long notes scroll inside the card so it never covers the whole map.
 */
@Composable
private fun SpotCard(
    pin: SpotPin,
    detail: PinDetail?,
    onSetVisited: (Boolean) -> Unit,
    onTogglePriority: () -> Unit,
    onOpen: () -> Unit,
    onAddToRoute: () -> Unit,
    onNavigate: () -> Unit,
    onOpenLink: (LinkEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(modifier = Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (detail != null && detail.hasPictures) {
                    CardPicture(detail, modifier = Modifier.padding(end = 12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(pin.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = pin.cityName.ifBlank { pin.countryName },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OpeningHours.parse(pin.openingHours)?.let { CardHours(it) }
                }
                PriorityStar(pin.priority, onToggle = onTogglePriority)
                // Standing in front of it is when you know, so it can be ticked off right here.
                val visited = pin.status == SpotStatus.VISITED
                FilterChip(
                    selected = visited,
                    onClick = { onSetVisited(!visited) },
                    label = { Text(stringResource(R.string.route_mark_visited)) },
                    leadingIcon = if (visited) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
            val hasBody = detail != null && (detail.notes.isNotEmpty() || detail.links.isNotEmpty())
            if (detail != null && hasBody) {
                Column(
                    modifier = Modifier
                        .heightIn(max = CARD_BODY_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState())
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (detail.notes.isNotEmpty()) {
                        Text(detail.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                    LinkList(links = detail.links, onOpen = onOpenLink)
                }
            }
            // Wraps rather than squeezing a label when the screen is narrow.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onAddToRoute) {
                    Icon(Icons.Outlined.AddRoad, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.add_to_route))
                }
                TextButton(onClick = onNavigate) {
                    Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.map_navigate))
                }
                TextButton(onClick = onOpen) { Text(stringResource(R.string.map_open_spot)) }
            }
        }
    }
}

/**
 * The spot's main picture, small beside its name. Tapping it opens all its pictures full size:
 * the main one, its photos, then any found for it, which are only looked for at that moment.
 */
@Composable
private fun CardPicture(detail: PinDetail, modifier: Modifier = Modifier) {
    var viewing by remember(detail.spotId) { mutableStateOf(false) }
    Thumbnail(
        imageUrl = detail.imageUrl.pictureUrlOrNull() ?: detail.photos.first().url,
        size = CARD_PICTURE_SIZE,
        modifier = modifier.clickable { viewing = true },
    )
    if (viewing) {
        val found = rememberFoundPictures(detail.pictureQuery)
        val gallery = remember(detail.imageUrl, detail.photos, found) {
            galleryOf(detail.imageUrl, detail.photos, found)
        }
        PictureViewer(urls = gallery.map { it.url }, start = 0, onDismiss = { viewing = false })
    }
}

/**
 * Today's hours on a pin's card, which open into the whole week when tapped. Nothing while the
 * hours are unknown or not looked up yet.
 */
@Composable
private fun CardHours(hours: OpeningHours) {
    val today = dayHoursText(hours, LocalDate.now().dayOfWeek) ?: return
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier.clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.hours_today, today),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(R.string.hours_show_week),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        if (open) WeekHours(hours)
    }
}

/** About a third of a phone screen: room for a good note, with the pin still in view above it. */
private val CARD_BODY_MAX_HEIGHT = 220.dp

private val MAP_UI = MapUiSettings(rotationGesturesEnabled = false, tiltGesturesEnabled = false)

/** Big enough to tell what it is, small enough that the card barely grows. */
private val CARD_PICTURE_SIZE = 52.dp

/** Stands in for "no route sign" among a marker's keys, which cannot be null. */
private const val NO_BADGE = "none"
