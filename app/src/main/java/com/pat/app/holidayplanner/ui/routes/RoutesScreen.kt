package com.pat.app.holidayplanner.ui.routes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.CityOption
import com.pat.app.holidayplanner.data.local.RouteRow
import com.pat.app.holidayplanner.ui.components.PickFromListDialog
import com.pat.app.holidayplanner.ui.components.PickSpotsSheet
import com.pat.app.holidayplanner.ui.components.PullableEmptyState
import com.pat.app.holidayplanner.ui.components.SyncRefreshBox
import com.pat.app.holidayplanner.ui.components.TabTopBar
import com.pat.app.holidayplanner.ui.components.WalkedBadge
import org.koin.compose.viewmodel.koinViewModel

/** Every route, wherever it is: the ones still to walk, then the ones already walked. */
@Composable
fun RoutesScreen(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenRoute: (String) -> Unit,
    viewModel: RoutesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pickingCity by remember { mutableStateOf(false) }
    var pickingSpotsIn by remember { mutableStateOf<CityOption?>(null) }

    Scaffold(
        // The tab bar below is already accounted for by the navigation Scaffold.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TabTopBar(stringResource(R.string.tab_routes), onSearch = onSearch, onSettings = onSettings) },
        floatingActionButton = {
            FloatingActionButton(onClick = { pickingCity = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.route_new))
            }
        },
    ) { padding ->
        SyncRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.rows.isEmpty()) {
                PullableEmptyState(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(
                            if (state.cities.isEmpty()) R.string.routes_no_cities else R.string.routes_empty,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.toWalk, key = { it.route.meta.id }) { row ->
                        RouteRowItem(row = row, onClick = { onOpenRoute(row.route.meta.id) })
                    }
                    if (state.walked.isNotEmpty()) {
                        item(key = "walked-header") {
                            Text(
                                text = stringResource(R.string.routes_walked_header),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                        items(state.walked, key = { it.route.meta.id }) { row ->
                            RouteRowItem(row = row, onClick = { onOpenRoute(row.route.meta.id) })
                        }
                    }
                }
            }
        }
    }

    if (pickingCity) {
        PickFromListDialog(
            title = stringResource(R.string.route_pick_city),
            options = state.cities.map { it.cityId to "${it.countryName} \u203a ${it.cityName}" },
            onPick = { cityId ->
                pickingCity = false
                pickingSpotsIn = state.cities.firstOrNull { it.cityId == cityId }
            },
            onDismiss = { pickingCity = false },
        )
    }

    // The city first, then what the walk takes in.
    pickingSpotsIn?.let { city ->
        PickSpotsSheet(
            cityId = city.cityId,
            onConfirm = { spotIds ->
                pickingSpotsIn = null
                viewModel.create(city, spotIds, onOpenRoute)
            },
            onDismiss = { pickingSpotsIn = null },
        )
    }
}

@Composable
fun RouteRowItem(row: RouteRow, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = row.route.name.ifBlank { row.cityName },
                    style = MaterialTheme.typography.titleMedium,
                )
                // A route named after its city says so once.
                Text(
                    text = if (row.route.name.isBlank() || row.route.name == row.cityName) {
                        stringResource(R.string.route_row_detail_country, row.countryName, row.stopCount)
                    } else {
                        stringResource(R.string.route_row_detail, row.cityName, row.countryName, row.stopCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WalkedBadge(row.route.completed)
        }
        HorizontalDivider()
    }
}
