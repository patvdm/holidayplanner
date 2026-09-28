package com.pat.app.holidayplanner.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.BuildConfig
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import com.pat.app.holidayplanner.data.sync.SyncStatus
import com.pat.app.holidayplanner.domain.ThemeMode
import com.pat.app.holidayplanner.data.update.AppUpdater
import com.pat.app.holidayplanner.data.update.UpdateCheck
import com.pat.app.holidayplanner.ui.components.BackTopBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.settings_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_section_user))
            Text(stringResource(R.string.settings_current_user, state.userName))
            OutlinedButton(onClick = viewModel::switchUser) {
                Text(stringResource(R.string.settings_switch_user))
            }
            HorizontalDivider()

            SectionTitle(stringResource(R.string.settings_section_sync))
            SyncSection(state.sync, viewModel::syncNow)
            HorizontalDivider()

            SectionTitle(stringResource(R.string.settings_section_advanced))
            AdvancedSection(state, viewModel)
            HorizontalDivider()

            SectionTitle(stringResource(R.string.settings_section_appearance))
            ThemeChoice()
            HorizontalDivider()

            SectionTitle(stringResource(R.string.settings_section_about))
            Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
            UpdateCheckRow()
        }
    }
}

@Composable
private fun SyncSection(sync: SyncStatus, onSyncNow: () -> Unit) {
    val lastSync = sync.lastSuccessAt?.let(::formatTimestamp)
    Text(
        text = if (lastSync == null) {
            stringResource(R.string.settings_sync_never)
        } else {
            stringResource(R.string.settings_sync_last, lastSync)
        },
    )
    Text(stringResource(R.string.settings_sync_pending, sync.pendingCount))
    sync.lastError?.let {
        Text(
            text = stringResource(R.string.settings_sync_error, it),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Button(onClick = onSyncNow, enabled = !sync.running) {
        Text(
            stringResource(
                if (sync.running) R.string.settings_sync_running else R.string.settings_sync_now,
            ),
        )
    }
}

@Composable
private fun AdvancedSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    if (state.effectiveUrl.isBlank()) {
        Text(stringResource(R.string.settings_backend_not_configured))
    } else {
        Text(stringResource(R.string.settings_backend_effective_url, state.effectiveUrl))
        val sourceText = if (state.overridden) {
            R.string.settings_backend_source_override
        } else {
            R.string.settings_backend_source_build
        }
        Text(text = stringResource(sourceText), style = MaterialTheme.typography.bodySmall)
    }

    OutlinedTextField(
        value = state.draftUrl,
        onValueChange = viewModel::onDraftUrlChange,
        label = { Text(stringResource(R.string.settings_backend_url_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.draftSecret,
        onValueChange = viewModel::onDraftSecretChange,
        label = { Text(stringResource(R.string.settings_backend_secret_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::saveOverride) { Text(stringResource(R.string.settings_backend_save)) }
        TextButton(onClick = viewModel::clearOverride) { Text(stringResource(R.string.settings_backend_clear)) }
    }

    OutlinedButton(onClick = viewModel::pingBackend, enabled = state.ping != PingState.Running) {
        Text(stringResource(R.string.settings_backend_ping))
    }
    PingResult(state.ping)
}

@Composable
private fun PingResult(ping: PingState) {
    when (ping) {
        PingState.Idle -> Unit
        PingState.Running -> Text(stringResource(R.string.settings_backend_pinging))
        is PingState.Success -> Text(
            text = stringResource(R.string.settings_backend_ping_ok, formatTimestamp(ping.serverTime)),
            color = MaterialTheme.colorScheme.primary,
        )
        is PingState.Failure -> Text(
            text = stringResource(R.string.settings_backend_ping_failed, ping.message),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

private fun formatTimestamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(timestampFormat)

/**
 * Checks for a new version now, downloading it even on mobile data since it was asked for. A
 * version found this way is offered by the same prompt the background check uses.
 */
@Composable
private fun UpdateCheckRow(updater: AppUpdater = koinInject()) {
    var checking by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<UpdateCheck?>(null) }
    val scope = rememberCoroutineScope()

    OutlinedButton(
        enabled = !checking,
        onClick = {
            checking = true
            scope.launch {
                outcome = updater.check(allowDownload = true)
                checking = false
            }
        },
    ) {
        Text(stringResource(if (checking) R.string.update_checking else R.string.update_check))
    }
    outcome?.let {
        Text(
            text = stringResource(
                when (it) {
                    UpdateCheck.UP_TO_DATE -> R.string.update_up_to_date
                    UpdateCheck.READY -> R.string.update_found
                    UpdateCheck.WAITING, UpdateCheck.FAILED -> R.string.update_failed
                    UpdateCheck.NOT_SET_UP -> R.string.update_not_set_up
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Dark, light, or whatever the phone is set to. Applies at once, on this phone only. */
@Composable
private fun ThemeChoice(prefs: AppPreferences = koinInject()) {
    val mode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.DEFAULT)
    val scope = rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { option ->
            FilterChip(
                selected = mode == option,
                onClick = { scope.launch { prefs.setThemeMode(option) } },
                label = {
                    Text(
                        stringResource(
                            when (option) {
                                ThemeMode.DARK -> R.string.theme_dark
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.SYSTEM -> R.string.theme_system
                            },
                        ),
                    )
                },
            )
        }
    }
}
