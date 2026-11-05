package com.pat.app.holidayplanner.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Room a compact button takes, against the 48dp a standard one reserves. */
val CompactButtonSize = 36.dp

/** The icon inside a compact button. */
val CompactIconSize = 20.dp

/**
 * An icon button for crowded rows: 36dp instead of the usual 48dp, so a row of three leaves its
 * title the room it needs. Material pads every icon button out to 48dp for touch, which is lifted
 * here; 36dp is still comfortably tappable.
 */
@Composable
fun CompactIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        IconButton(onClick = onClick, modifier = modifier.size(CompactButtonSize)) { content() }
    }
}
