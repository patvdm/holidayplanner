package com.pat.app.holidayplanner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.SpotPickerRow
import com.pat.app.holidayplanner.data.repo.SpotRepository
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

private const val LIST_MAX_HEIGHT_DP = 400
private const val SHEET_SETTLE_MS = 250L

/**
 * Picks one of the spots that already exist, grouped by where it is.
 *
 * Used wherever something has to be attached to a spot: assigning an inbox link, and later adding a
 * spot to a trip or a day route. When [allowCreate] is set, the sheet also offers to make a new one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotPickerSheet(
    onPick: (SpotPickerRow) -> Unit,
    onDismiss: () -> Unit,
    allowCreate: Boolean = false,
    onCreateNew: () -> Unit = {},
    filter: (SpotPickerRow) -> Boolean = { true },
    repository: SpotRepository = koinInject(),
) {
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    // The sheet animates in, so the field is only ready to take focus a moment later.
    LaunchedEffect(Unit) {
        delay(SHEET_SETTLE_MS)
        focusRequester.requestFocus()
    }
    val rows by remember(repository) { repository.observePickerRows() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val matches = remember(rows, query) {
        val needle = query.trim()
        rows.filter(filter).filter { row ->
            needle.isEmpty() ||
                row.spotName.contains(needle, ignoreCase = true) ||
                row.cityName.contains(needle, ignoreCase = true) ||
                row.countryName.contains(needle, ignoreCase = true)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.spot_picker_search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )

            if (allowCreate) {
                TextButton(onClick = onCreateNew, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.spot_picker_new))
                }
            }

            if (matches.isEmpty()) {
                Text(
                    text = stringResource(R.string.spot_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }

            LazyColumn(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT_DP.dp)) {
                items(matches, key = { it.spotId }) { row ->
                    SpotPickerItem(row = row, onPick = { onPick(row) })
                }
            }
        }
    }
}

@Composable
private fun SpotPickerItem(row: SpotPickerRow, onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(vertical = 12.dp),
    ) {
        Text(row.spotName, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = row.group,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}
