package com.pat.app.holidayplanner.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.FeedRow
import com.pat.app.holidayplanner.domain.FeedEntryType
import com.pat.app.holidayplanner.domain.LocalTimes
import com.pat.app.holidayplanner.ui.components.BackTopBar
import com.pat.app.holidayplanner.ui.components.EmptyListMessage
import java.time.Instant
import java.time.ZoneId
import org.koin.compose.viewmodel.koinViewModel

/**
 * Everything the other person added this week, newest first.
 *
 * A heart and a link both point at the spot they belong to, since neither has a screen of its own.
 */
@Composable
fun FeedScreen(
    onBack: () -> Unit,
    onOpenSpot: (String) -> Unit,
    onOpenTrip: (String) -> Unit,
    onOpenInbox: () -> Unit,
    viewModel: FeedViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.feed_title), onBack = onBack) },
    ) { padding ->
        if (state.rows.isEmpty()) {
            EmptyListMessage(stringResource(R.string.feed_empty), Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(state.rows, key = { "${it.type}-${it.id}" }) { row ->
                    FeedRowItem(row) { open(row, onOpenSpot, onOpenTrip, onOpenInbox) }
                }
            }
        }
    }
}

@Composable
private fun FeedRowItem(row: FeedRow, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(row.title) },
        supportingContent = {
            Text(
                text = stringResource(R.string.feed_by, row.authorName, row.createdAt.asDay()),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        overlineContent = { if (row.place.isNotBlank()) Text(row.place) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun open(
    row: FeedRow,
    onOpenSpot: (String) -> Unit,
    onOpenTrip: (String) -> Unit,
    onOpenInbox: () -> Unit,
) {
    when (FeedEntryType.fromRow(row.type)) {
        FeedEntryType.TRIP -> onOpenTrip(row.navId)
        // A link with no spot is still sitting in the inbox, which is where it can be dealt with.
        FeedEntryType.LINK -> if (row.navId.isBlank()) onOpenInbox() else onOpenSpot(row.navId)
        FeedEntryType.SPOT, FeedEntryType.HEART -> onOpenSpot(row.navId)
        null -> Unit
    }
}

private fun Long.asDay(): String =
    LocalTimes.formatDay(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate())
