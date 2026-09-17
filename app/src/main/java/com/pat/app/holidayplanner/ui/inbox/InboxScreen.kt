package com.pat.app.holidayplanner.ui.inbox

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.ui.components.PullableEmptyState
import com.pat.app.holidayplanner.ui.components.SyncRefreshBox
import com.pat.app.holidayplanner.ui.feed.ThisWeekStrip
import com.pat.app.holidayplanner.data.local.InboxRow
import com.pat.app.holidayplanner.data.remote.PlaceSearchMode
import com.pat.app.holidayplanner.ui.components.PlaceSearchSheet
import com.pat.app.holidayplanner.ui.components.SpotPickerSheet
import com.pat.app.holidayplanner.ui.components.TabTopBar
import com.pat.app.holidayplanner.ui.components.openExternalUrl
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenFeed: () -> Unit,
    viewModel: InboxViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var creatingSpot by remember { mutableStateOf(false) }

    // Coming back from Instagram is the cue to offer filing the link that was just opened.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }

    Scaffold(
        topBar = {
            if (state.selectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.inbox_selected, state.selected.size)) },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                        }
                    },
                    actions = {
                        TextButton(onClick = viewModel::assignSelected) {
                            Text(stringResource(R.string.inbox_assign_selected))
                        }
                    },
                )
            } else {
                TabTopBar(stringResource(R.string.tab_inbox), onSearch = onSearch, onSettings = onSettings)
            }
        },
    ) { padding ->
        SyncRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ThisWeekStrip(onOpenFeed = onOpenFeed)
                if (state.rows.isEmpty()) {
                    PullableEmptyState(modifier = Modifier.weight(1f).padding(24.dp)) {
                        Text(
                            text = stringResource(R.string.inbox_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(state.rows, key = { it.link.meta.id }) { row ->
                            InboxItem(
                                row = row,
                                selected = row.link.meta.id in state.selected,
                                selectionMode = state.selectionMode,
                                onOpen = {
                                    // Only an unfiled link needs the picker waiting on the way back.
                                    if (!row.filed) viewModel.rememberPendingAssign(row.link.meta.id)
                                    context.openExternalUrl(row.link.url)
                                },
                                onToggleSelect = { viewModel.toggleSelected(row.link.meta.id) },
                                onDelete = { viewModel.delete(row.link.meta.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.assigning.isNotEmpty() && !creatingSpot) {
        SpotPickerSheet(
            onPick = { viewModel.assignTo(it.spotId) },
            onDismiss = viewModel::cancelAssign,
            allowCreate = true,
            onCreateNew = { creatingSpot = true },
        )
    }

    if (creatingSpot) {
        PlaceSearchSheet(
            mode = PlaceSearchMode.SPOT,
            onDismiss = { creatingSpot = false },
            onSaved = { spotId ->
                creatingSpot = false
                viewModel.assignTo(spotId)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun InboxItem(
    row: InboxRow,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val delete by rememberUpdatedState(onDelete)

    // React once the swipe has settled on a dismissed anchor, rather than vetoing the gesture.
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) delete()
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.inbox_delete))
            }
        },
    ) {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                        onLongClick = onToggleSelect,
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.link.note.ifBlank { row.link.source.name },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = row.link.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    // Where it ended up, or a nudge when a share was backed out of before filing.
                    Text(
                        text = if (row.filed) {
                            stringResource(R.string.inbox_filed_under, row.spotName, row.cityName)
                        } else {
                            stringResource(R.string.inbox_unfiled)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (row.filed) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(
                        text = stringResource(
                            R.string.inbox_added_by,
                            row.authorName,
                            relativeTime(row.link.meta.createdAt),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

private fun relativeTime(epochMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(epochMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        .toString()
