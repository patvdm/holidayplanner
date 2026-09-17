package com.pat.app.holidayplanner.ui.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.ui.components.DropFocusWhileSheetCloses
import com.pat.app.holidayplanner.domain.CountryOption
import kotlinx.coroutines.delay

private const val SHEET_MAX_HEIGHT_DP = 480
private const val SHEET_SETTLE_MS = 250L

/** Picks a country to add. Countries already on the list are not offered again. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(
    options: List<CountryOption>,
    onPick: (CountryOption) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    // The sheet animates in, so the field is only ready to take focus a moment later.
    LaunchedEffect(Unit) {
        delay(SHEET_SETTLE_MS)
        focusRequester.requestFocus()
    }
    val filtered = remember(query, options) {
        if (query.isBlank()) options else options.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        DropFocusWhileSheetCloses(sheetState)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.countries_search)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .focusRequester(focusRequester),
        )
        LazyColumn(modifier = Modifier.heightIn(max = SHEET_MAX_HEIGHT_DP.dp)) {
            items(filtered, key = { it.isoCode }) { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(option) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(option.flag, style = MaterialTheme.typography.titleLarge)
                    Text(option.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
