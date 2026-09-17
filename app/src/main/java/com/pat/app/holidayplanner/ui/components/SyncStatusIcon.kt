package com.pat.app.holidayplanner.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.ui.sync.SyncRefreshViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Matches an IconButton, so the slot is the same size whether or not anything is in it. */
private const val SLOT_SIZE_DP = 48
private const val SPINNER_SIZE_DP = 16
private const val SPINNER_STROKE_DP = 2

/**
 * A quiet corner of the top bar saying a sync is running, or that the last one failed.
 *
 * It keeps its space whether or not it is showing anything, so a sync starting or finishing never
 * shifts the icons beside it, and it is never in the list's way. A failure is tappable to retry.
 */
@Composable
fun SyncStatusIcon(viewModel: SyncRefreshViewModel = koinViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()

    Box(modifier = Modifier.size(SLOT_SIZE_DP.dp), contentAlignment = Alignment.Center) {
        when {
            status.running -> CircularProgressIndicator(
                strokeWidth = SPINNER_STROKE_DP.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SPINNER_SIZE_DP.dp),
            )

            status.lastError != null -> IconButton(onClick = viewModel::refresh) {
                Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = stringResource(R.string.sync_failed_retry),
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            else -> Unit
        }
    }
}
