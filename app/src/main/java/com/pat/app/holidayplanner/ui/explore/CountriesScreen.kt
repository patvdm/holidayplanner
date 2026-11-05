package com.pat.app.holidayplanner.ui.explore

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.CountryRow
import com.pat.app.holidayplanner.domain.CountryCatalog
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.ui.components.ConfirmDeleteDialog
import com.pat.app.holidayplanner.ui.components.FlagFallback
import com.pat.app.holidayplanner.ui.components.Thumbnail
import com.pat.app.holidayplanner.ui.components.EmptyListMessage
import com.pat.app.holidayplanner.ui.components.rememberKeyboardDismiss
import com.pat.app.holidayplanner.ui.components.PullableEmptyState
import com.pat.app.holidayplanner.ui.components.SyncRefreshBox
import com.pat.app.holidayplanner.ui.components.SyncStatusIcon
import com.pat.app.holidayplanner.ui.components.PriorityStar
import com.pat.app.holidayplanner.ui.components.ReorderableColumn
import com.pat.app.holidayplanner.ui.components.TextInputDialog
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountriesScreen(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenCountry: (String) -> Unit,
    viewModel: CountriesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<CountryRow?>(null) }

    Scaffold(
        // The tab bar below is already accounted for by the navigation Scaffold, so adding the
        // navigation inset again here would float the button a bar's height too high.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_explore)) },
                actions = {
                    SyncStatusIcon()
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
                if (state.rows.isEmpty()) {
                    PullableEmptyState(modifier = Modifier.weight(1f)) {
                        EmptyListMessage(stringResource(R.string.countries_empty))
                    }
                } else {
                    ReorderableColumn(
                        items = state.rows,
                        idOf = { it.country.meta.id },
                        sortKeyOf = { it.country.sortKey },
                        groupOf = { it.country.priority },
                        reorderable = true,
                        onMoved = viewModel::move,
                        modifier = Modifier.weight(1f),
                    ) { row, handle ->
                        CountryRowItem(
                            row = row,
                            handle = handle,
                            onClick = { onOpenCountry(row.country.meta.id) },
                            onPriority = { viewModel.setPriority(row.country.meta.id, it) },
                            onRename = { renaming = row },
                            onDelete = { viewModel.requestDelete(row.country.meta.id, row.country.name) },
                        )
                    }
                }
            }
        }
    }

    renaming?.let { row ->
        TextInputDialog(
            title = stringResource(R.string.countries_rename),
            label = stringResource(R.string.label_name),
            initial = row.country.name,
            onConfirm = {
                viewModel.rename(row.country.meta.id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    state.pendingDelete?.let { pending ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_title, pending.name),
            message = stringResource(
                R.string.delete_country_message,
                pluralStringResource(R.plurals.count_cities, pending.impact.cities, pending.impact.cities),
                pluralStringResource(R.plurals.count_spots, pending.impact.spots, pending.impact.spots),
            ),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun CountryRowItem(
    row: CountryRow,
    handle: (@Composable () -> Unit)?,
    onClick: () -> Unit,
    onPriority: (Priority) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Thumbnail(
                imageUrl = row.country.imageUrl,
                fallback = { FlagFallback(CountryCatalog.flag(row.country.isoCode)) },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(row.country.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        R.string.countries_counts,
                        pluralStringResource(R.plurals.count_cities, row.cityCount, row.cityCount),
                        pluralStringResource(R.plurals.count_spots, row.spotCount, row.spotCount),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PriorityStar(row.country.priority, onToggle = { onPriority(row.country.priority.toggled()) })
            RowMenu(
                onRename = onRename,
                onDelete = onDelete,
            )
            handle?.invoke()
        }
        HorizontalDivider()
    }
}

/** The per-row overflow: rename, delete. */
@Composable
fun RowMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val putKeyboardAway = rememberKeyboardDismiss()

    IconButton(
        onClick = {
            // Dropped before the popup takes window focus, so there is nothing to hand back to.
            putKeyboardAway()
            expanded = true
        },
    ) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            expanded = false
            putKeyboardAway()
        },
        properties = PopupProperties(focusable = false),
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_rename)) },
            onClick = {
                expanded = false
                onRename()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            onClick = {
                expanded = false
                onDelete()
            },
        )
    }
}

fun Priority.label(): Int = when (this) {
    Priority.HIGH -> R.string.priority_high
    Priority.NORMAL -> R.string.priority_normal
}

