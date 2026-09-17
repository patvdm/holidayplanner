package com.pat.app.holidayplanner.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.PopupProperties
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.domain.SortMode
import com.pat.app.holidayplanner.domain.SortScope

/** Picks the order of a list. Only the modes that make sense for this list are offered. */
@Composable
fun SortMenu(scope: SortScope, selected: SortMode, onSelect: (SortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val putKeyboardAway = rememberKeyboardDismiss()

    IconButton(
        onClick = {
            // Dropped before the popup takes window focus, so there is nothing to hand back to.
            putKeyboardAway()
            expanded = true
        },
    ) {
        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.action_sort))
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            expanded = false
            putKeyboardAway()
        },
        properties = PopupProperties(focusable = false),
    ) {
        scope.options.forEach { mode ->
            DropdownMenuItem(
                text = { Text(stringResource(mode.label())) },
                leadingIcon = { RadioButton(selected = mode == selected, onClick = null) },
                onClick = {
                    expanded = false
                    onSelect(mode)
                },
            )
        }
    }
}

fun SortMode.label(): Int = when (this) {
    SortMode.MANUAL -> R.string.sort_manual
    SortMode.PRIORITY -> R.string.sort_priority
    SortMode.HEARTS -> R.string.sort_hearts
    SortMode.RECENT -> R.string.sort_recent
    SortMode.ALPHABETICAL -> R.string.sort_alphabetical
}
