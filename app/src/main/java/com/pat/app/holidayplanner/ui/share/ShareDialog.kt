package com.pat.app.holidayplanner.ui.share

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.ui.components.PlaceSearchSheet
import org.koin.compose.viewmodel.koinViewModel

/**
 * What pops over the sharing app. A Maps link becomes a spot on its own. Any other link asks one
 * question, which spot it is about, in a single sheet that searches your spots and Google at once,
 * so a spot that does not exist yet is one tap further than one that does. Nothing takes you out
 * of Instagram, and backing out saves nothing: a link with no spot is not kept.
 */
@Composable
fun ShareDialog(
    sharedText: String?,
    sharedImage: android.net.Uri? = null,
    onClose: () -> Unit,
    onOpenLink: (String) -> Unit,
    viewModel: ShareViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(sharedText, sharedImage) { viewModel.onShared(sharedText, sharedImage) }
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

        // A photo asks the same one question a link does, in the same sheet.
        is ShareState.FilingPhoto -> PlaceSearchSheet(
            title = stringResource(R.string.share_pick_spot_photo),
            onPickExisting = { viewModel.attachPhoto(it.spotId) },
            onDismiss = viewModel::cancelFiling,
            onSaved = viewModel::attachPhoto,
        )

        ShareState.Uploading -> BusyDialog(
            title = stringResource(R.string.share_uploading_title),
            message = stringResource(R.string.share_uploading_message),
        )

        is ShareState.Problem -> MessageDialog(
            title = stringResource(R.string.share_photo_failed_title),
            message = current.message.ifBlank { stringResource(R.string.share_photo_failed_message) },
            onClose = onClose,
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

        // One sheet over the app that shared the link: your spots and Google's, in one search.
        is ShareState.Filing -> PlaceSearchSheet(
            title = stringResource(R.string.share_pick_spot),
            onPickExisting = { viewModel.fileUnder(it.spotId) },
            onDismiss = viewModel::cancelFiling,
            onSaved = viewModel::fileUnder,
        )
    }
}

/** Waiting on the network, with nothing useful to offer until it answers. */
@Composable
private fun BusyDialog(title: String, message: String) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp))
                Text(message)
            }
        },
        confirmButton = {},
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
