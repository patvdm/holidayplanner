package com.pat.app.holidayplanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pat.app.holidayplanner.R
import androidx.compose.ui.res.stringResource

/** Room inside each of the three buttons, tighter than standard so all three fit across a phone. */
private val ButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

/**
 * The three ways to look at somewhere or set off for it, the same wherever they appear: our own
 * map; a preview in Google Maps, for looking it over; and turn-by-turn in Google Maps from where
 * you are standing, for going now. A null action shows its button greyed out, so the row keeps its
 * shape when one of them means nothing here.
 */
@Composable
fun MapButtonRow(
    onOurMap: (() -> Unit)?,
    onPreview: (() -> Unit)?,
    onNavigate: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OurMapButton(onOurMap, Modifier.weight(1f))
        MapsPreviewButton(onPreview, Modifier.weight(1f))
        MapsNavigateButton(onNavigate, Modifier.weight(1f))
    }
}

@Composable
fun OurMapButton(onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = modifier,
        contentPadding = ButtonPadding,
    ) {
        Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.route_our_map), modifier = Modifier.padding(start = 6.dp), maxLines = 1)
    }
}

@Composable
fun MapsPreviewButton(onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = modifier,
        contentPadding = ButtonPadding,
    ) {
        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.route_preview), modifier = Modifier.padding(start = 6.dp), maxLines = 1)
    }
}

@Composable
fun MapsNavigateButton(onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Button(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = modifier,
        contentPadding = ButtonPadding,
    ) {
        Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.route_navigate), modifier = Modifier.padding(start = 6.dp), maxLines = 1)
    }
}
