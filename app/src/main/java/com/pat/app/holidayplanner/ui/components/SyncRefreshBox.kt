package com.pat.app.holidayplanner.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.ui.sync.SyncRefreshViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Wraps a screen's list so pulling down runs a sync.
 *
 * One sync refreshes every table at once, so this belongs on the three tab roots rather than on
 * every list: pulling on any of them brings the whole database up to date.
 *
 * The content must be scrollable, including its empty state, or there is no gesture to detect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncRefreshBox(
    modifier: Modifier = Modifier,
    viewModel: SyncRefreshViewModel = koinViewModel(),
    content: @Composable BoxScope.() -> Unit,
) {
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier,
        content = content,
    )
}
