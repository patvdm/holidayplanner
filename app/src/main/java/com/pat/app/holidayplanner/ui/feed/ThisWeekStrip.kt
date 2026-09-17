package com.pat.app.holidayplanner.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.domain.FeedEntryType
import com.pat.app.holidayplanner.domain.FeedGroup
import org.koin.compose.viewmodel.koinViewModel

/** At most this many lines before the strip stops listing and points at the full feed instead. */
private const val MAX_LINES = 3

/**
 * The strip above the country list: what the other person added this week, in a sentence or three.
 *
 * It renders nothing at all when there is no news, rather than an empty box saying so, because a
 * permanent placeholder on the home screen is worse than silence.
 */
@Composable
fun ThisWeekStrip(
    onOpenFeed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.hasNews) return

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenFeed),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.feed_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            state.groups.take(MAX_LINES).forEach { group ->
                Text(
                    text = group.sentence(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (state.groups.size > MAX_LINES) {
                Text(
                    text = stringResource(R.string.feed_see_all),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** One group as the line someone reads, for example "3 new links in Lisbon". */
@Composable
fun FeedGroup.sentence(): String = when (type) {
    FeedEntryType.SPOT -> pluralStringResource(R.plurals.feed_spots_in, count, count, place)
    FeedEntryType.HEART -> pluralStringResource(R.plurals.feed_hearts_in, count, count, place)
    FeedEntryType.TRIP -> pluralStringResource(R.plurals.feed_trips, count, count)
    FeedEntryType.LINK -> if (place.isBlank()) {
        pluralStringResource(R.plurals.feed_links_loose, count, count)
    } else {
        pluralStringResource(R.plurals.feed_links_in, count, count, place)
    }
}
