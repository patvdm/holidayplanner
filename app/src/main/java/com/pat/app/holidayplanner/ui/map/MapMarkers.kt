package com.pat.app.holidayplanner.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pat.app.holidayplanner.data.local.SpotPin
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotMark
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.domain.hasMark

/** How many characters of a spot's name ride above its pin before the rest is cut off. */
const val MARKER_LABEL_CHARS = 12

/**
 * The few characters of [name] shown above a pin: the whole name when it is short, otherwise its
 * start with an ellipsis, so neighbouring pins stay readable instead of burying each other in text.
 */
fun markerLabel(name: String, max: Int = MARKER_LABEL_CHARS): String {
    val trimmed = name.trim()
    if (trimmed.length <= max) return trimmed
    return trimmed.take(max - 1).trimEnd() + "…"
}

/**
 * What a pin looks like. One quiet colour for the ordinary, one loud one for what matters, and
 * done pins fade out rather than taking a third colour, so nothing competes with the map's own
 * greens and blues: charcoal for a normal spot, orange for high priority, white with a tick once
 * visited. The icon inside always says what kind of place it is.
 */
enum class PinStyle { VISITED, HIGH, NORMAL }

fun SpotPin.pinStyle(): PinStyle = when {
    status == SpotStatus.VISITED -> PinStyle.VISITED
    priority == Priority.HIGH -> PinStyle.HIGH
    else -> PinStyle.NORMAL
}

/**
 * What the icon on a pin's head says the place is for. A drone spot is still a viewpoint or a
 * beach, but flying is why it was saved, so it wears the drone; a restaurant or a hotel keeps its
 * own icon even if marked, since eating or sleeping there is the point.
 */
enum class PinKind { FOOD, STAY, DRONE, PLACE }

fun SpotPin.pinKind(): PinKind = when {
    type == SpotType.RESTAURANT -> PinKind.FOOD
    type == SpotType.ACCOMMODATION -> PinKind.STAY
    marks.hasMark(SpotMark.DRONE) -> PinKind.DRONE
    else -> PinKind.PLACE
}

private val Charcoal = Color(0xFF3C4043)
private val Orange = Color(0xFFF4511E)
private val Faded = Color(0xFF9AA0A6)
private val LabelBackground = Color(0xEBFFFFFF)
private val LabelInk = Color(0xFF1C1B17)
private val PinHead = 26.dp
private val HighPinHead = 32.dp
private val TickSize = 14.dp
private val BadgeSize = 14.dp

/** The start and end of a walk are what you look for, so their signs are a touch larger. */
private val EndBadgeSize = 16.dp

/** Badges sit half over the head's rim, so they read as attached rather than inside it. */
private val BadgeNudge = 4.dp
private val RouteBlue = Color(0xFF1A73E8)
private val StartGreen = Color(0xFF188038)
private val EndRed = Color(0xFFC5221F)
private val WalkedGrey = Color(0xFF80868B)
private const val ICON_SHARE_OF_HEAD = 0.6f

/** Head colour, what is drawn on it, and its rim, in that order. */
private data class PinColours(val head: Color, val ink: Color, val rim: Color)

private val PinStyle.colours: PinColours
    get() = when (this) {
        PinStyle.VISITED -> PinColours(head = Color.White, ink = Faded, rim = Faded)
        PinStyle.HIGH -> PinColours(head = Orange, ink = Color.White, rim = Color.White)
        PinStyle.NORMAL -> PinColours(head = Charcoal, ink = Color.White, rim = Color.White)
    }

private val PinKind.icon: ImageVector
    get() = when (this) {
        PinKind.FOOD -> Icons.Filled.Restaurant
        PinKind.STAY -> Icons.Filled.Hotel
        PinKind.DRONE -> DroneIcon
        PinKind.PLACE -> Icons.Filled.Place
    }

/**
 * A route sign's icon and colour. The shape says where on the walk the spot is: a play arrow where
 * it sets off, a flag where it ends, the road sign in between. The colour says start green, end
 * red, in between blue, and all of them grey once the walk is done, so a finished walk still shows
 * its shape without competing with the ones ahead.
 */
private val RouteBadge.icon: ImageVector
    get() = when (role) {
        RouteRole.START -> Icons.Filled.PlayArrow
        RouteRole.END -> Icons.Filled.Flag
        RouteRole.STOP -> Icons.Filled.Route
    }

private val RouteBadge.colour: Color
    get() = when {
        walked -> WalkedGrey
        role == RouteRole.START -> StartGreen
        role == RouteRole.END -> EndRed
        else -> RouteBlue
    }

private val LabelText = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)

/**
 * One pin as drawn on the map: the start of the name in a white tag, a round head carrying the
 * spot's kind, and a point underneath whose tip is the exact position. A high-priority pin is a
 * little larger so it still stands out where pins crowd together; a visited one wears a tick, and
 * one on a route wears a small route sign saying where on the walk it is (see [RouteBadge.icon]).
 */
@Composable
fun PinMarker(pin: SpotPin, route: RouteBadge?) {
    val style = pin.pinStyle()
    val colours = style.colours
    val head = if (style == PinStyle.HIGH) HighPinHead else PinHead
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = markerLabel(pin.name),
            style = LabelText,
            color = if (style == PinStyle.VISITED) Faded else LabelInk,
            maxLines = 1,
            modifier = Modifier
                .background(LabelBackground, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
        // Room around the head for the badges that overhang it.
        Box(modifier = Modifier.padding(top = 2.dp + BadgeNudge, start = BadgeNudge, end = BadgeNudge)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(head)
                    .background(colours.head, CircleShape)
                    .border(2.dp, colours.rim, CircleShape),
            ) {
                Icon(
                    imageVector = pin.pinKind().icon,
                    contentDescription = null,
                    tint = colours.ink,
                    modifier = Modifier.size(head * ICON_SHARE_OF_HEAD),
                )
            }
            if (route != null) {
                // On a walk: a small route sign at the head's top left.
                Icon(
                    imageVector = route.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = -BadgeNudge, y = -BadgeNudge)
                        .size(if (route.role == RouteRole.STOP) BadgeSize else EndBadgeSize)
                        .background(route.colour, CircleShape)
                        .border(1.dp, Color.White, CircleShape)
                        .padding(2.dp),
                )
            }
            if (style == PinStyle.VISITED) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Charcoal,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = BadgeNudge, y = -BadgeNudge)
                        .size(TickSize)
                        .background(Color.White, CircleShape),
                )
            }
        }
        Canvas(modifier = Modifier.size(width = 10.dp, height = 7.dp)) {
            val point = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2, size.height)
                close()
            }
            drawPath(point, colours.rim.takeIf { style == PinStyle.VISITED } ?: colours.head)
        }
    }
}
