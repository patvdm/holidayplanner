package com.pat.app.holidayplanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType

/** How badly we want to go. Colour carries the same meaning as the label, never on its own. */
@Composable
fun PriorityBadge(priority: Priority, modifier: Modifier = Modifier) {
    val (label, color) = when (priority) {
        Priority.MUST -> R.string.priority_must to MaterialTheme.colorScheme.errorContainer
        Priority.LIKE -> R.string.priority_like to MaterialTheme.colorScheme.tertiaryContainer
        Priority.MAYBE -> R.string.priority_maybe to MaterialTheme.colorScheme.surfaceVariant
    }
    SmallBadge(text = stringResource(label), background = color, modifier = modifier)
}

/** Where a spot stands: still a wish, planned into a trip, or already seen. */
@Composable
fun StatusChip(status: SpotStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        SpotStatus.UNVISITED -> R.string.status_unvisited to MaterialTheme.colorScheme.surfaceVariant
        SpotStatus.VISITED -> R.string.status_visited to MaterialTheme.colorScheme.secondaryContainer
    }
    SmallBadge(text = stringResource(label), background = color, modifier = modifier)
}

/** What kind of place this is, shown only when somebody said. */
@Composable
fun TypeBadge(type: SpotType?, modifier: Modifier = Modifier) {
    if (type == null) return
    SmallBadge(
        text = stringResource(type.label()),
        background = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier,
    )
}

fun SpotType.label(): Int = when (this) {
    SpotType.POI -> R.string.type_poi
    SpotType.ACCOMMODATION -> R.string.type_accommodation
    SpotType.RESTAURANT -> R.string.type_restaurant
}

/**
 * Heart count. A filled heart means the signed-in person hearted it, and the count is emphasised
 * once everybody has.
 */
@Composable
fun HeartCount(count: Int, heartedByMe: Boolean, everyone: Boolean, modifier: Modifier = Modifier) {
    val description = if (everyone) {
        stringResource(R.string.hearts_everyone_description, count)
    } else {
        stringResource(R.string.hearts_description, count)
    }
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (heartedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            tint = if (everyone) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (everyone) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SmallBadge(text: String, background: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
