package com.pat.app.holidayplanner.ui.search

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.ui.components.BackTopBar
import com.pat.app.holidayplanner.ui.components.PlaceholderBody

@Composable
fun SearchScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.search_title), onBack = onBack) },
    ) { padding ->
        PlaceholderBody(stringResource(R.string.search_placeholder), padding)
    }
}
