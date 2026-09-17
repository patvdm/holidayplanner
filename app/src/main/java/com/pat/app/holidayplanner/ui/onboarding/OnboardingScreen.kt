package com.pat.app.holidayplanner.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.onboarding_subtitle), style = MaterialTheme.typography.bodyMedium)

            when {
                state.syncing -> SyncingRow()
                state.error != null -> OfflineNotice(state.error.orEmpty(), viewModel::initialSync)
            }

            if (state.users.isNotEmpty()) {
                Text(stringResource(R.string.onboarding_existing), style = MaterialTheme.typography.titleMedium)
                state.users.forEach { user ->
                    Card(
                        onClick = { viewModel.selectUser(user.meta.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = user.name, modifier = Modifier.padding(16.dp))
                    }
                }
                HorizontalDivider()
            }

            Text(stringResource(R.string.onboarding_new), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.onboarding_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::createUser,
                enabled = state.canContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_continue))
            }
        }
    }
}

@Composable
private fun SyncingRow() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.onboarding_syncing), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun OfflineNotice(message: String, onRetry: () -> Unit) {
    Text(
        text = stringResource(R.string.onboarding_offline, message),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.onboarding_retry)) }
}
