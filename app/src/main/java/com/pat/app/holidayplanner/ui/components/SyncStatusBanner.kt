package com.pat.app.holidayplanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

private const val SPINNER_SIZE_DP = 14
private const val SPINNER_STROKE_DP = 2

/**
 * A thin line above a list saying that a sync is running, or that the last one failed.
 *
 * Without it a failed sync is silent everywhere except Settings, so the app looks like it simply
 * has nothing new rather than like it could not reach the sheet. It shows nothing when the last
 * sync worked, because a permanent "all fine" line is noise.
 */
@Composable
fun SyncStatusBanner(
    modifier: Modifier = Modifier,
    viewModel: SyncRefreshViewModel = koinViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val failed = status.lastError

    when {
        status.running -> Banner(
            colour = MaterialTheme.colorScheme.surfaceVariant,
            textColour = MaterialTheme.colorScheme.onSurfaceVariant,
            text = stringResource(R.string.sync_banner_running),
            showSpinner = true,
            modifier = modifier,
        )

        failed != null -> Banner(
            colour = MaterialTheme.colorScheme.errorContainer,
            textColour = MaterialTheme.colorScheme.onErrorContainer,
            text = stringResource(R.string.sync_banner_failed),
            showSpinner = false,
            modifier = modifier,
            action = { TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.sync_banner_retry)) } },
        )

        else -> Unit
    }
}

@Composable
private fun Banner(
    colour: androidx.compose.ui.graphics.Color,
    textColour: androidx.compose.ui.graphics.Color,
    text: String,
    showSpinner: Boolean,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(color = colour, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showSpinner) {
                CircularProgressIndicator(
                    strokeWidth = SPINNER_STROKE_DP.dp,
                    color = textColour,
                    modifier = Modifier.size(SPINNER_SIZE_DP.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = textColour,
                modifier = Modifier.weight(1f),
            )
            action?.invoke()
        }
    }
}
