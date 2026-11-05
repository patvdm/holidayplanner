package com.pat.app.holidayplanner.ui

import com.pat.app.holidayplanner.data.sync.SyncRepository
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.update.AppUpdater
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Offers a downloaded update, once per version per app start. Later is fine: the background check
 * installs it by itself when the app is closed, once Android allows that.
 */
@Composable
fun UpdatePrompt(updater: AppUpdater = koinInject(), sync: SyncRepository = koinInject()) {
    val update by updater.state.collectAsStateWithLifecycle()
    var dismissedCode by rememberSaveable { mutableLongStateOf(0L) }
    // Changes that could not be sent when Install was tapped; null until then.
    var unsent by rememberSaveable { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val ready = update ?: return

    unsent?.let { count ->
        UnsentChangesDialog(
            count = count,
            onInstall = {
                unsent = null
                scope.launch { updater.install(interactive = true) }
            },
            onLater = { unsent = null },
        )
        return
    }
    if (ready.versionCode == dismissedCode) return

    AlertDialog(
        onDismissRequest = { dismissedCode = ready.versionCode },
        title = { Text(stringResource(R.string.update_title)) },
        text = { Text(stringResource(R.string.update_message, ready.versionName)) },
        confirmButton = {
            TextButton(
                onClick = {
                    dismissedCode = ready.versionCode
                    scope.launch {
                        // Sends whatever is waiting first; an update can rebuild the database.
                        val left = sync.sendPending()
                        if (left == 0) updater.install(interactive = true) else unsent = left
                    }
                },
            ) { Text(stringResource(R.string.update_install)) }
        },
        dismissButton = {
            TextButton(onClick = { dismissedCode = ready.versionCode }) { Text(stringResource(R.string.update_later)) }
        },
    )
}

/**
 * Install was tapped while some changes could not reach the sheet, most likely for want of a
 * connection. Installing anyway risks them, so it is asked rather than assumed.
 */
@Composable
private fun UnsentChangesDialog(count: Int, onInstall: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.update_unsent_title)) },
        text = { Text(pluralStringResource(R.plurals.update_unsent_message, count, count)) },
        confirmButton = { TextButton(onClick = onInstall) { Text(stringResource(R.string.update_install_anyway)) } },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) } },
    )
}
