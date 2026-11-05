package com.pat.app.holidayplanner.ui.routes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.ui.components.DropFocusWhileSheetCloses
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val LIST_MAX_HEIGHT_DP = 420

/**
 * Puts one spot on a route, or takes it off, a tap per route. The routes through the spot's city
 * are listed with a tick on those it is already on, and the sheet stays open so several can be
 * set at once. "New route" starts a walk through the city with this spot as its first stop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToRouteSheet(
    spotId: String,
    spotName: String,
    onDismiss: () -> Unit,
    onOpenRoute: (String) -> Unit,
    // Keyed by spot: the map opens this for one pin after another within the same screen.
    viewModel: AddToRouteViewModel = koinViewModel(key = "add-to-route:$spotId") { parametersOf(spotId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        DropFocusWhileSheetCloses(sheetState)
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.add_to_route_title, spotName), style = MaterialTheme.typography.titleMedium)
            if (state.loaded && state.routes.isEmpty()) {
                Text(
                    text = stringResource(R.string.add_to_route_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            LazyColumn(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT_DP.dp)) {
                items(state.routes, key = { it.routeId }) { choice ->
                    RouteChoiceRow(
                        choice = choice,
                        onToggle = { viewModel.toggle(choice) },
                        // Closed first, so coming back from the route does not find it still up.
                        onOpen = {
                            onDismiss()
                            onOpenRoute(choice.routeId)
                        },
                    )
                }
            }
            if (state.canCreate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = viewModel::createRoute)
                        .padding(vertical = 12.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.add_to_route_new, state.cityName),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteChoiceRow(choice: RouteChoice, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = choice.holdsSpot, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(choice.name, style = MaterialTheme.typography.bodyLarge)
            val detail = stringResource(
                R.string.route_row_detail_country,
                choice.cityName,
                pluralStringResource(R.plurals.count_stops, choice.stopCount, choice.stopCount),
            )
            Text(
                text = if (choice.walked) stringResource(R.string.add_to_route_walked, detail) else detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpen) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.map_open_route),
            )
        }
    }
}
