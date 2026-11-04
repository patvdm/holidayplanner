package com.pat.app.holidayplanner.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
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
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType

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

private val Charcoal = Color(0xFF3C4043)
private val Orange = Color(0xFFF4511E)
private val Faded = Color(0xFF9AA0A6)
private val LabelBackground = Color(0xEBFFFFFF)
private val LabelInk = Color(0xFF1C1B17)
private val PinHead = 26.dp
private val HighPinHead = 32.dp
private val TickSize = 12.dp
private const val ICON_SHARE_OF_HEAD = 0.6f

/** Head colour, what is drawn on it, and its rim, in that order. */
private data class PinColours(val head: Color, val ink: Color, val rim: Color)

private val PinStyle.colours: PinColours
    get() = when (this) {
        PinStyle.VISITED -> PinColours(head = Color.White, ink = Faded, rim = Faded)
        PinStyle.HIGH -> PinColours(head = Orange, ink = Color.White, rim = Color.White)
        PinStyle.NORMAL -> PinColours(head = Charcoal, ink = Color.White, rim = Color.White)
    }

private val SpotType.icon: ImageVector
    get() = when (this) {
        SpotType.RESTAURANT -> Icons.Filled.Restaurant
        SpotType.ACCOMMODATION -> Icons.Filled.Hotel
        SpotType.POI -> Icons.Filled.Place
    }

private val LabelText = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)

/**
 * One pin as drawn on the map: the start of the name in a white tag, a round head carrying the
 * spot's kind, and a point underneath whose tip is the exact position. A high-priority pin is a
 * little larger so it still stands out where pins crowd together; a visited one wears a tick.
 */
@Composable
fun PinMarker(pin: SpotPin) {
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
        Box(modifier = Modifier.padding(top = 2.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(head)
                    .background(colours.head, CircleShape)
                    .border(2.dp, colours.rim, CircleShape),
            ) {
                Icon(
                    imageVector = pin.type.icon,
                    contentDescription = null,
                    tint = colours.ink,
                    modifier = Modifier.size(head * ICON_SHARE_OF_HEAD),
                )
            }
            if (style == PinStyle.VISITED) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Charcoal,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
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
