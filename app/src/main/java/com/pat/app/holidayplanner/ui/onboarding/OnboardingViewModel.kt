package com.pat.app.holidayplanner.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.UserEntity
import com.pat.app.holidayplanner.data.repo.UserRepository
import com.pat.app.holidayplanner.data.sync.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

data class OnboardingUiState(
    val syncing: Boolean = true,
    val users: List<UserEntity> = emptyList(),
    val error: String? = null,
    val name: String = "",
) {
    val canContinue: Boolean get() = name.isNotBlank()
}

/**
 * First run. Pulls the sheet so the people already using the app can be listed, then either signs
 * in as one of them or creates a new one. When the pull fails the same screen still works: the new
 * user is written locally and pushed once there is a connection.
 */
class OnboardingViewModel(
    private val userRepository: UserRepository,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    private val syncing = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val name = MutableStateFlow("")

    val uiState: StateFlow<OnboardingUiState> =
        combine(syncing, error, name, userRepository.observeUsers()) { isSyncing, lastError, typedName, users ->
            OnboardingUiState(syncing = isSyncing, users = users, error = lastError, name = typedName)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), OnboardingUiState())

    init {
        initialSync()
    }

    fun initialSync() {
        viewModelScope.launch {
            syncing.value = true
            error.value = null
            syncRepository.sync().onFailure { error.value = it.message ?: it.javaClass.simpleName }
            syncing.value = false
        }
    }

    fun onNameChange(value: String) {
        name.value = value
    }

    fun selectUser(userId: String) {
        viewModelScope.launch { userRepository.select(userId) }
    }

    fun createUser() {
        val chosen = name.value.trim()
        if (chosen.isEmpty()) return
        viewModelScope.launch { userRepository.createAndSelect(chosen) }
    }
}
