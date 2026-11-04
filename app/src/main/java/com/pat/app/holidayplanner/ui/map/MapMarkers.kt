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
 * What a pin looks like. Visited is green whatever else is true, since done is done; otherwise a
 * high-priority spot is red, like the High badge in the lists, and everything else is the app's teal.
 */
enum class PinColour { VISITED, HIGH, NORMAL }

fun SpotPin.pinColour(): PinColour = when {
    status == SpotStatus.VISITED -> PinColour.VISITED
    priority == Priority.HIGH -> PinColour.HIGH
    else -> PinColour.NORMAL
}

private val PinGreen = Color(0xFF2E7D32)
private val PinRed = Color(0xFFD32F2F)
private val PinTeal = Color(0xFF006A69)
private val LabelBackground = Color(0xEBFFFFFF)
private val PinHead = 26.dp
private val HighPinHead = 32.dp
private const val ICON_SHARE_OF_HEAD = 0.6f

private val PinColour.fill: Color
    get() = when (this) {
        PinColour.VISITED -> PinGreen
        PinColour.HIGH -> PinRed
        PinColour.NORMAL -> PinTeal
    }

private val SpotType.icon: ImageVector
    get() = when (this) {
        SpotType.RESTAURANT -> Icons.Filled.Restaurant
        SpotType.ACCOMMODATION -> Icons.Filled.Hotel
        SpotType.POI -> Icons.Filled.Place
    }

private val LabelText = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1C1B17))

/**
 * One pin as drawn on the map: the start of the name in a white tag, a round head carrying the
 * spot's kind, and a point underneath whose tip is the exact position. A high-priority pin is a
 * little larger so it still stands out where pins crowd together.
 */
@Composable
fun PinMarker(pin: SpotPin) {
    val colour = pin.pinColour()
    val fill = colour.fill
    val head = if (colour == PinColour.HIGH) HighPinHead else PinHead
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = markerLabel(pin.name),
            style = LabelText,
            maxLines = 1,
            modifier = Modifier
                .background(LabelBackground, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(head)
                .background(fill, CircleShape)
                .border(2.dp, Color.White, CircleShape),
        ) {
            Icon(
                imageVector = pin.type.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(head * ICON_SHARE_OF_HEAD),
            )
        }
        Canvas(modifier = Modifier.size(width = 10.dp, height = 7.dp)) {
            val point = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2, size.height)
                close()
            }
            drawPath(point, fill)
        }
    }
}
