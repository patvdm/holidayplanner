package com.pat.app.holidayplanner.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
fun UpdatePrompt(updater: AppUpdater = koinInject()) {
    val update by updater.state.collectAsStateWithLifecycle()
    var dismissedCode by rememberSaveable { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    val ready = update ?: return
    if (ready.versionCode == dismissedCode) return

    AlertDialog(
        onDismissRequest = { dismissedCode = ready.versionCode },
        title = { Text(stringResource(R.string.update_title)) },
        text = { Text(stringResource(R.string.update_message, ready.versionName)) },
        confirmButton = {
            TextButton(
                onClick = {
                    dismissedCode = ready.versionCode
                    scope.launch { updater.install(interactive = true) }
                },
            ) { Text(stringResource(R.string.update_install)) }
        },
        dismissButton = {
            TextButton(onClick = { dismissedCode = ready.versionCode }) { Text(stringResource(R.string.update_later)) }
        },
    )
}
