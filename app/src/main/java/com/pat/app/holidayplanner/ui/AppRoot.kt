package com.pat.app.holidayplanner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.ui.navigation.AppNavigation
import com.pat.app.holidayplanner.ui.onboarding.OnboardingScreen
import org.koin.compose.viewmodel.koinViewModel

/** Shows onboarding until somebody is signed in, then the tabs. */
@Composable
fun AppRoot(viewModel: SessionViewModel = koinViewModel()) {
    val session by viewModel.state.collectAsStateWithLifecycle()

    when (session) {
        SessionState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        SessionState.SignedOut -> OnboardingScreen()
        is SessionState.SignedIn -> AppNavigation()
    }
    UpdatePrompt()
}
