package com.pat.app.holidayplanner.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.Polyline
import com.pat.app.holidayplanner.R

/**
 * Colours for the route lines, picked to stand apart from each other, from the pins (charcoal and
 * orange) and from the map itself (green parks, blue water, yellow roads).
 */
private val RouteColours = listOf(
    Color(0xFF1A73E8),
    Color(0xFF9C27B0),
    Color(0xFFE91E63),
    Color(0xFF00ACC1),
    Color(0xFF795548),
)
private val HaloColour = Color.White
private val LineWidth = 3.dp
private val HaloWidth = 6.dp

/** A walked route: there to see where you have been, quieter than every walk still ahead. */
private val WalkedColour = Color(0xFF80868B)
private val WalkedLineWidth = 2.dp
private val WalkedHaloWidth = 4.dp
private const val WALKED_HALO_ALPHA = 0.6f
private val DashLength = 10.dp
private val GapLength = 6.dp
private const val DIMMED_ALPHA = 0.25f
private const val HALO_ALPHA = 0.85f
private const val CHOSEN_WIDTH_FACTOR = 1.6f

/**
 * Every route as a thin straight line through its stops in walking order. Straight, because the
 * real walking path would need Google's paid directions; this is for seeing at a glance where each
 * walk goes. A white halo keeps a line readable over busy streets, and once one route is picked the
 * others fade so it stands out where walks cross. Walked routes are thinner, grey and underneath
 * the rest.
 */
@Composable
@GoogleMapComposable
fun RouteLinesLayer(lines: List<RouteLine>, chosenId: String?, onPick: (RouteLine) -> Unit) {
    val density = LocalDensity.current
    val dashed = remember(density) {
        with(density) { listOf(Dash(DashLength.toPx()), Gap(GapLength.toPx())) }
    }
    lines.forEach { route ->
        val look = route.drawing(chosenId)
        val points = remember(route.points) { route.points.map { LatLng(it.lat, it.lng) } }
        // The halo is the wider, easier target for a finger, so it answers taps too.
        Polyline(
            points = points,
            color = look.halo,
            width = with(density) { look.haloWidth.toPx() },
            jointType = JointType.ROUND,
            startCap = RoundCap(),
            endCap = RoundCap(),
            zIndex = look.layer,
            clickable = true,
            onClick = { onPick(route) },
        )
        Polyline(
            points = points,
            color = look.colour,
            width = with(density) { look.lineWidth.toPx() },
            pattern = if (look.dashed) dashed else null,
            jointType = JointType.ROUND,
            startCap = RoundCap(),
            endCap = RoundCap(),
            zIndex = look.layer + 1f,
            clickable = true,
            onClick = { onPick(route) },
        )
    }
}

/** Everything about how one line is drawn, worked out apart from the drawing. */
private data class LineDrawing(
    val colour: Color,
    val halo: Color,
    val lineWidth: Dp,
    val haloWidth: Dp,
    val dashed: Boolean,
    val layer: Float,
)

private fun RouteLine.drawing(chosenId: String?): LineDrawing {
    val look = RouteLineStyle.of(style, RouteColours.size)
    val chosen = routeId == chosenId
    val faded = chosenId != null && !chosen
    val widen = if (chosen) CHOSEN_WIDTH_FACTOR else 1f
    return LineDrawing(
        colour = colour(look).copy(alpha = if (faded) DIMMED_ALPHA else 1f),
        halo = HaloColour.copy(
            alpha = when {
                faded -> DIMMED_ALPHA
                walked -> WALKED_HALO_ALPHA
                else -> HALO_ALPHA
            },
        ),
        lineWidth = (if (walked) WalkedLineWidth else LineWidth) * widen,
        haloWidth = (if (walked) WalkedHaloWidth else HaloWidth) * widen,
        dashed = look.dashed && !walked,
        // Chosen over everything, then walks ahead, then walked ones at the bottom.
        layer = when {
            chosen -> CHOSEN_LAYER
            walked -> WALKED_LAYER
            else -> AHEAD_LAYER
        },
    )
}

private const val WALKED_LAYER = 0f
private const val AHEAD_LAYER = 2f
private const val CHOSEN_LAYER = 4f

private fun RouteLine.colour(look: RouteLineStyle): Color =
    if (walked) WalkedColour else RouteColours[look.colourIndex]

/** Shows only places to eat, beside the routes switch. */
@Composable
fun FoodOnlyChip(only: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = only,
        onClick = { onChange(!only) },
        label = { Text(stringResource(R.string.map_food_only)) },
        leadingIcon = { Icon(Icons.Filled.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
    )
}

/** Switches the route lines on and off, over the map's top left corner. */
@Composable
fun RoutesChip(showing: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = showing,
        onClick = { onChange(!showing) },
        label = { Text(stringResource(R.string.map_routes)) },
        leadingIcon = { Icon(Icons.Filled.Route, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
    )
}

/** The route whose line was tapped: what it is called and a way into it. */
@Composable
fun RouteCard(route: RouteLine, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val look = RouteLineStyle.of(route.style, RouteColours.size)
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Route,
                contentDescription = null,
                tint = route.colour(look),
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(route.name, style = MaterialTheme.typography.titleMedium)
                val stops = pluralStringResource(R.plurals.map_route_stops, route.points.size, route.points.size)
                Text(
                    text = if (route.walked) "${stringResource(R.string.map_route_walked)} · $stops" else stops,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen) { Text(stringResource(R.string.map_open_route)) }
        }
    }
}
