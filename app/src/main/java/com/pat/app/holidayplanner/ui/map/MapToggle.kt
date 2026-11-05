package com.pat.app.holidayplanner.ui.map

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A round on-off switch floating over the map: white with a grey icon while off, filled with the
 * theme colour while on. A shadow lifts it off the map so it reads as a button over any street.
 * [description] is what a screen reader says, since there is no label to read.
 */
@Composable
fun MapToggle(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
) {
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = onChange,
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier
            .size(40.dp)
            .shadow(3.dp, CircleShape),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}
