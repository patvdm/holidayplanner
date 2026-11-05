package com.pat.app.holidayplanner.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotMark
import com.pat.app.holidayplanner.domain.hasMark
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType

/**
 * Priority, in one colour across the app: the orange of a high-priority pin on the map. Where the
 * star can be tapped it is drawn by [PriorityStar]; elsewhere [PriorityBadge] shows it, small and
 * filled, only for high priority.
 */
val PriorityOrange = Color(0xFFF4511E)

/** High priority as a small filled star; Normal shows nothing. Read only. */
@Composable
fun PriorityBadge(priority: Priority, modifier: Modifier = Modifier) {
    if (priority != Priority.HIGH) return
    Icon(
        imageVector = Icons.Filled.Star,
        contentDescription = stringResource(R.string.priority_high),
        tint = PriorityOrange,
        modifier = modifier.size(16.dp),
    )
}

/** The one-tap priority switch: a filled orange star when it is priority, an outline when not. */
@Composable
fun PriorityStar(priority: Priority, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val high = priority == Priority.HIGH
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = if (high) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = stringResource(if (high) R.string.priority_remove else R.string.priority_add),
            tint = if (high) PriorityOrange else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Visited earns a green tag; unvisited is the default and shows nothing. */
@Composable
fun StatusChip(status: SpotStatus, modifier: Modifier = Modifier) {
    if (status != SpotStatus.VISITED) return
    SmallBadge(
        text = stringResource(R.string.status_visited),
        background = VISITED_GREEN,
        foreground = DARK_TEXT,
        modifier = modifier,
    )
}

/** Restaurants and accommodation are tagged so they stand out; a plain place of interest is not. */
@Composable
fun TypeBadge(type: SpotType, modifier: Modifier = Modifier) {
    val background = when (type) {
        SpotType.POI -> return
        SpotType.RESTAURANT -> RESTAURANT_ORANGE
        SpotType.ACCOMMODATION -> ACCOMMODATION_BLUE
    }
    SmallBadge(
        text = stringResource(type.label()),
        background = background,
        foreground = DARK_TEXT,
        modifier = modifier,
    )
}

/** A route that has been walked wears the same green as a visited spot. */
@Composable
fun WalkedBadge(completed: Boolean, modifier: Modifier = Modifier) {
    if (!completed) return
    SmallBadge(
        text = stringResource(R.string.route_completed),
        background = VISITED_GREEN,
        foreground = DARK_TEXT,
        modifier = modifier,
    )
}

/** What a spot is good for, worn beside what it is. Nothing shows for a spot with no marks. */
@Composable
fun MarkBadges(marks: String, modifier: Modifier = Modifier) {
    SpotMark.entries.filter { marks.hasMark(it) }.forEach { mark ->
        SmallBadge(
            text = stringResource(mark.label()),
            background = MARK_VIOLET,
            foreground = DARK_TEXT,
            modifier = modifier,
        )
    }
}

fun SpotMark.label(): Int = when (this) {
    SpotMark.DRONE -> R.string.mark_drone
}

fun SpotType.label(): Int = when (this) {
    SpotType.POI -> R.string.type_poi
    SpotType.ACCOMMODATION -> R.string.type_accommodation
    SpotType.RESTAURANT -> R.string.type_restaurant
}

/** Fixed tag colours, the same in both themes so a tag means the same thing everywhere. */
private val MARK_VIOLET = Color(0xFFD7C4F0)
internal val VISITED_GREEN = Color(0xFFB9E4C0)
private val RESTAURANT_ORANGE = Color(0xFFFFD199)
private val ACCOMMODATION_BLUE = Color(0xFFB7D4F5)
internal val DARK_TEXT = Color(0xFF1B1B1B)

@Composable
private fun SmallBadge(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = foreground,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
