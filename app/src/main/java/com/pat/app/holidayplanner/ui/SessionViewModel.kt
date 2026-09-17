package com.pat.app.holidayplanner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.repo.UserRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val STOP_TIMEOUT_MS = 5_000L

sealed interface SessionState {
    /** Preferences have not been read yet; showing onboarding now would flash the wrong screen. */
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val userId: String) : SessionState
}

class SessionViewModel(userRepository: UserRepository) : ViewModel() {

    val state: StateFlow<SessionState> = userRepository.currentUserId
        .map { userId -> if (userId == null) SessionState.SignedOut else SessionState.SignedIn(userId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SessionState.Loading)
}
