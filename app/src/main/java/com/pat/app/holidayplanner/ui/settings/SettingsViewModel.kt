package com.pat.app.holidayplanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import com.pat.app.holidayplanner.data.prefs.BackendOverride
import com.pat.app.holidayplanner.data.remote.SheetsApi
import com.pat.app.holidayplanner.data.repo.UserRepository
import com.pat.app.holidayplanner.data.sync.SyncScheduler
import com.pat.app.holidayplanner.data.sync.SyncStatus
import com.pat.app.holidayplanner.data.sync.SyncStatusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

sealed interface PingState {
    data object Idle : PingState
    data object Running : PingState
    data class Success(val serverTime: Long) : PingState
    data class Failure(val message: String) : PingState
}

data class SettingsUiState(
    val userName: String = "",
    val sync: SyncStatus = SyncStatus(),
    val effectiveUrl: String = "",
    val overridden: Boolean = false,
    val draftUrl: String = "",
    val draftSecret: String = "",
    val ping: PingState = PingState.Idle,
)

class SettingsViewModel(
    private val prefs: AppPreferences,
    private val api: SheetsApi,
    private val userRepository: UserRepository,
    private val syncScheduler: SyncScheduler,
    syncStatusRepository: SyncStatusRepository,
) : ViewModel() {

    private val draft = MutableStateFlow(BackendOverride(url = "", secret = ""))
    private val ping = MutableStateFlow<PingState>(PingState.Idle)

    val uiState: StateFlow<SettingsUiState> = combine(
        api.config,
        draft,
        ping,
        userRepository.observeCurrentUser(),
        syncStatusRepository.status,
    ) { config, draft, ping, user, sync ->
        SettingsUiState(
            userName = user?.name.orEmpty(),
            sync = sync,
            effectiveUrl = config.url,
            overridden = config.overridden,
            draftUrl = draft.url,
            draftSecret = draft.secret,
            ping = ping,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    init {
        viewModelScope.launch { draft.value = prefs.backendOverride.first() }
    }

    fun onDraftUrlChange(value: String) = draft.update { it.copy(url = value) }

    fun onDraftSecretChange(value: String) = draft.update { it.copy(secret = value) }

    fun saveOverride() {
        val current = draft.value
        viewModelScope.launch { prefs.setBackendOverride(current.url, current.secret) }
    }

    fun clearOverride() {
        draft.value = BackendOverride(url = "", secret = "")
        viewModelScope.launch { prefs.setBackendOverride(url = "", secret = "") }
    }

    fun syncNow() = syncScheduler.syncNow()

    /** Returns to onboarding so another name can be picked on this device. */
    fun switchUser() {
        viewModelScope.launch { userRepository.signOut() }
    }

    fun pingBackend() {
        if (ping.value == PingState.Running) return
        viewModelScope.launch {
            ping.value = PingState.Running
            ping.value = api.ping().fold(
                onSuccess = { PingState.Success(it) },
                onFailure = { PingState.Failure(it.message ?: it.javaClass.simpleName) },
            )
        }
    }
}
