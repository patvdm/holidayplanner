package com.pat.app.holidayplanner.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.ui.components.PlaceSearchSheet
import com.pat.app.holidayplanner.ui.components.SpotPickerSheet
import org.koin.compose.viewmodel.koinViewModel

/**
 * The dialog that pops over the sharing app: a preview of the link, a note, and one Save, which
 * files it straight away. The spot picker opens over the same dialog, so filing a reel never takes
 * you out of Instagram. Backing out of the picker saves nothing: a link with no spot is not kept.
 */
@Composable
fun ShareDialog(
    sharedText: String?,
    onClose: () -> Unit,
    onOpenLink: (String) -> Unit,
    viewModel: ShareViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var creatingSpot by remember { mutableStateOf(false) }

    LaunchedEffect(sharedText) { viewModel.onShared(sharedText) }
    LaunchedEffect(state) { if (state is ShareState.Saved) onClose() }

    when (val current = state) {
        ShareState.Reading, ShareState.Saved -> Unit

        ShareState.Resolving -> MessageDialog(
            title = stringResource(R.string.share_resolving_title),
            message = stringResource(R.string.share_resolving_message),
            onClose = onClose,
        )

        // A Maps link goes straight to creating a spot; no link row is written for it.
        is ShareState.CreatingSpot -> PlaceSearchSheet(
            prefillName = current.spot.name,
            knownLat = current.spot.lat,
            knownLng = current.spot.lng,
            knownAddress = current.spot.address,
            knownMapsUrl = current.spot.mapsUrl,
            knownCityName = current.spot.cityName,
            knownIsoCode = current.spot.isoCode,
            onDismiss = onClose,
            onSaved = { viewModel.spotCreated() },
        )

        ShareState.NoLink -> MessageDialog(
            title = stringResource(R.string.share_no_link_title),
            message = stringResource(R.string.share_no_link_message),
            onClose = onClose,
        )

        is ShareState.Duplicate -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.share_duplicate_title)) },
            text = {
                Text(
                    current.existing.where
                        ?.let { stringResource(R.string.share_duplicate_on_spot, it) }
                        ?: stringResource(R.string.share_duplicate_unknown),
                )
            },
            confirmButton = {
                TextButton(onClick = { onOpenLink(current.existing.url) }) {
                    Text(stringResource(R.string.share_open))
                }
            },
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) } },
        )

        is ShareState.Ready -> SaveDialog(
            url = current.link.url,
            note = current.note,
            hint = stringResource(R.string.share_maps_fallback).takeIf { current.link.isMapsLink },
            detail = current.mapsIssue,
            viewModel = viewModel,
            onClose = onClose,
        )

        // The picker opens over the app that shared the link, so nothing takes you away from it.
        is ShareState.Filing -> if (creatingSpot) {
            PlaceSearchSheet(
                onDismiss = { creatingSpot = false },
                onSaved = { spotId ->
                    creatingSpot = false
                    viewModel.fileUnder(spotId)
                },
            )
        } else {
            SpotPickerSheet(
                onPick = { viewModel.fileUnder(it.spotId) },
                onDismiss = viewModel::cancelFiling,
                allowCreate = true,
                onCreateNew = { creatingSpot = true },
            )
        }
    }
}

@Composable
private fun SaveDialog(
    url: String,
    note: String,
    hint: String?,
    detail: String? = null,
    viewModel: ShareViewModel,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.share_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                hint?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                detail?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 3,
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = viewModel::onNoteChange,
                    label = { Text(stringResource(R.string.share_note_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = viewModel::save) { Text(stringResource(R.string.share_save)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun MessageDialog(title: String, message: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = { Text(message, modifier = Modifier.padding(top = 4.dp)) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) } },
    )
}
