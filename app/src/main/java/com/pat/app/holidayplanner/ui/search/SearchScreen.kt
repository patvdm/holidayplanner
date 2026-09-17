package com.pat.app.holidayplanner.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.SearchHit
import com.pat.app.holidayplanner.domain.SearchHitType
import com.pat.app.holidayplanner.ui.components.BackTopBar
import com.pat.app.holidayplanner.ui.components.EmptyListMessage
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import org.koin.compose.viewmodel.koinViewModel

/** The screen animates in, so the field can only take focus once it has settled. */
private const val FOCUS_DELAY_MS = 250L

/**
 * Search across everything, offline.
 *
 * Tapping a result hands its kind and id to the caller, which knows where each kind lives. A link
 * reports its spot, since a link has no screen of its own and never exists without one.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpen: (SearchHitType, navId: String) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(FOCUS_DELAY_MS)
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.search_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Search,
                ),
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = viewModel::clear) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.search_clear),
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
            )

            when {
                !state.searched -> EmptyListMessage(stringResource(R.string.search_prompt))
                state.nothingFound -> EmptyListMessage(stringResource(R.string.search_empty))
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    state.sections.forEach { section ->
                        item(key = "header-${section.type}") {
                            SectionHeader(stringResource(section.type.label()))
                        }
                        items(section.hits, key = { "${section.type}-${it.id}" }) { hit ->
                            HitRow(hit) { onOpen(section.type, hit.navId) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        HorizontalDivider()
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun HitRow(hit: SearchHit, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(hit.title) },
        supportingContent = {
            if (hit.subtitle.isNotBlank()) {
                Text(hit.subtitle, style = MaterialTheme.typography.bodySmall)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}


private fun SearchHitType.label(): Int = when (this) {
    SearchHitType.COUNTRY -> R.string.search_group_country
    SearchHitType.CITY -> R.string.search_group_city
    SearchHitType.SPOT -> R.string.search_group_spot
    SearchHitType.LINK -> R.string.search_group_link
    SearchHitType.TRIP -> R.string.search_group_trip
    SearchHitType.TRIP_ITEM -> R.string.search_group_trip_item
}
