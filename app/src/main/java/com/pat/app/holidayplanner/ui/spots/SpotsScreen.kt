package com.pat.app.holidayplanner.ui.spots

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.SpotListRow
import com.pat.app.holidayplanner.domain.CountryCatalog
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SortScope
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.ui.components.PriorityBadge
import com.pat.app.holidayplanner.ui.components.PullableEmptyState
import com.pat.app.holidayplanner.ui.components.SortMenu
import com.pat.app.holidayplanner.ui.components.StatusChip
import com.pat.app.holidayplanner.ui.components.SyncRefreshBox
import com.pat.app.holidayplanner.ui.components.SyncStatusIcon
import com.pat.app.holidayplanner.ui.components.Thumbnail
import com.pat.app.holidayplanner.ui.components.TypeBadge
import com.pat.app.holidayplanner.ui.components.label
import com.pat.app.holidayplanner.ui.components.firstLine
import com.pat.app.holidayplanner.ui.explore.label
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar

/** Every spot anywhere, filterable by what it is and where, sortable by name, priority or age. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotsScreen(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenSpot: (String) -> Unit,
    viewModel: SpotsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        // The tab bar below is already accounted for by the navigation Scaffold.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_spots)) },
                actions = {
                    SyncStatusIcon()
                    SortMenu(SortScope.ALL_SPOTS, state.sortMode, viewModel::setSortMode)
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
    ) { padding ->
        SyncRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                FilterRow(state, viewModel)
                when {
                    state.total == 0 -> PullableEmptyState(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.spots_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }

                    state.nothingMatches -> PullableEmptyState(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.spots_no_matches),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }

                    else -> LazyColumn(modifier = Modifier.weight(1f)) {
                        items(state.rows, key = { it.spot.meta.id }) { row ->
                            SpotListItem(row = row, onClick = { onOpenSpot(row.spot.meta.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(state: SpotsUiState, viewModel: SpotsViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.countries.forEach { country ->
            FilterChip(
                selected = state.filters.countryId == country.id,
                onClick = { viewModel.setCountryFilter(country.id.takeIf { it != state.filters.countryId }) },
                label = { Text("${CountryCatalog.flag(country.isoCode)} ${country.name}") },
            )
        }
        SpotType.entries.forEach { type ->
            FilterChip(
                selected = state.filters.type == type,
                onClick = { viewModel.setTypeFilter(type.takeIf { it != state.filters.type }) },
                label = { Text(stringResource(type.label())) },
            )
        }
        SpotStatus.entries.forEach { status ->
            FilterChip(
                selected = state.filters.status == status,
                onClick = { viewModel.setStatusFilter(status.takeIf { it != state.filters.status }) },
                label = { Text(stringResource(status.label())) },
            )
        }
        FilterChip(
            selected = state.filters.priority == Priority.HIGH,
            onClick = { viewModel.setPriorityFilter(Priority.HIGH.takeIf { it != state.filters.priority }) },
            label = { Text(stringResource(R.string.priority_high)) },
        )
    }
}

@Composable
private fun SpotListItem(row: SpotListRow, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Thumbnail(row.spot.imageUrl)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.spot.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.spots_where, row.cityName, row.countryName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PriorityBadge(row.spot.priority)
                    StatusChip(row.spot.status)
                    TypeBadge(row.spot.type)
                }
                row.spot.notes.firstLine()?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        HorizontalDivider()
    }
}
