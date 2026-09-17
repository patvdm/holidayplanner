package com.pat.app.holidayplanner.ui.explore

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.CityOption
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.ui.components.ConfirmDeleteDialog
import com.pat.app.holidayplanner.ui.components.TextInputDialog
import com.pat.app.holidayplanner.ui.components.label
import com.pat.app.holidayplanner.ui.components.openExternalUrl
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotDetailScreen(
    spotId: String,
    onBack: () -> Unit,
    viewModel: SpotDetailViewModel = koinViewModel { parametersOf(spotId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dialogs = remember { SpotDialogs() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.spot?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleHeart) {
                        Icon(
                            imageVector = if (state.heartedByMe) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Outlined.FavoriteBorder
                            },
                            contentDescription = stringResource(R.string.spot_heart),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    IconButton(onClick = { dialogs.confirmingDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.context?.let { where ->
                Text(
                    text = stringResource(R.string.spot_breadcrumb, where.countryName, where.cityName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            SectionLabel(stringResource(R.string.spot_location))
            state.spot?.address?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.hasOwnMapsLink) {
                Text(
                    text = state.spot?.mapsUrl.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { context.openExternalUrl(state.mapsUrl) }) {
                    Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.spot_open_in_maps))
                }
                if (state.hasOwnMapsLink) {
                    TextButton(onClick = viewModel::clearMapsUrl) {
                        Text(stringResource(R.string.spot_location_clear))
                    }
                } else {
                    TextButton(onClick = { dialogs.addingMapsLink = true }) {
                        Text(stringResource(R.string.spot_location_add))
                    }
                }
            }
            OutlinedButton(onClick = { dialogs.renaming = true }) { Text(stringResource(R.string.action_rename)) }

            HeartsSection(names = state.hearts.map { it.name }.filter { it.isNotBlank() })
            HorizontalDivider()

            SectionLabel(stringResource(R.string.spot_priority))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Priority.entries.forEach { priority ->
                    FilterChip(
                        selected = state.spot?.priority == priority,
                        onClick = { viewModel.setPriority(priority) },
                        label = { Text(stringResource(priority.label())) },
                    )
                }
            }

            SectionLabel(stringResource(R.string.spot_type))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.spot?.type == null,
                    onClick = { viewModel.setType(null) },
                    label = { Text(stringResource(R.string.type_clear)) },
                )
                SpotType.entries.forEach { type ->
                    FilterChip(
                        selected = state.spot?.type == type,
                        onClick = { viewModel.setType(type) },
                        label = { Text(stringResource(type.label())) },
                    )
                }
            }

            SectionLabel(stringResource(R.string.spot_status))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SpotStatus.entries.forEach { status ->
                    FilterChip(
                        selected = state.spot?.status == status,
                        onClick = { viewModel.setStatus(status) },
                        label = { Text(stringResource(status.label())) },
                    )
                }
            }

            NotesField(initial = state.spot?.notes.orEmpty(), onSave = viewModel::setNotes)
            TagsField(initial = state.spot?.tags.orEmpty().joinToString(", "), onSave = viewModel::setTags)

            HorizontalDivider()
            LinksSection(
                links = state.links,
                onOpen = { context.openExternalUrl(it) },
                onMoveToInbox = viewModel::moveLinkToInbox,
                onDelete = viewModel::deleteLink,
                onAdd = { dialogs.addingLink = true },
            )

            HorizontalDivider()
            OutlinedButton(
                onClick = { dialogs.addingToTrip = true },
                enabled = state.trips.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.trips.isEmpty()) R.string.spot_no_trips else R.string.spot_add_to_trip,
                    ),
                )
            }
            OutlinedButton(onClick = { dialogs.movingSpot = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.spot_move))
            }
        }
    }

    SpotDialogs(state = state, dialogs = dialogs, viewModel = viewModel, onBack = onBack)
}

/** Which dialog is open, kept together so the screen itself stays readable. */
private class SpotDialogs {
    var renaming by mutableStateOf(false)
    var addingLink by mutableStateOf(false)
    var addingMapsLink by mutableStateOf(false)
    var movingSpot by mutableStateOf(false)
    var addingToTrip by mutableStateOf(false)
    var confirmingDelete by mutableStateOf(false)
}

@Composable
private fun SpotDialogs(
    state: SpotDetailUiState,
    dialogs: SpotDialogs,
    viewModel: SpotDetailViewModel,
    onBack: () -> Unit,
) {
    if (dialogs.renaming) {
        TextInputDialog(
            title = stringResource(R.string.action_rename),
            label = stringResource(R.string.label_name),
            initial = state.spot?.name.orEmpty(),
            onConfirm = {
                viewModel.rename(it)
                dialogs.renaming = false
            },
            onDismiss = { dialogs.renaming = false },
        )
    }

    if (dialogs.addingMapsLink) {
        TextInputDialog(
            title = stringResource(R.string.spot_location_add),
            label = stringResource(R.string.label_url),
            onConfirm = {
                viewModel.setMapsUrl(it)
                dialogs.addingMapsLink = false
            },
            onDismiss = { dialogs.addingMapsLink = false },
        )
    }

    if (dialogs.addingLink) {
        AddLinkDialog(
            onConfirm = { url, note ->
                viewModel.addLink(url, note)
                dialogs.addingLink = false
            },
            onDismiss = { dialogs.addingLink = false },
        )
    }

    if (dialogs.addingToTrip) {
        PickFromListDialog(
            title = stringResource(R.string.spot_add_to_trip),
            options = state.trips.map { it.meta.id to it.name },
            onPick = {
                dialogs.addingToTrip = false
                viewModel.addToTrip(it)
            },
            onDismiss = { dialogs.addingToTrip = false },
        )
    }

    if (dialogs.movingSpot) {
        PickFromListDialog(
            title = stringResource(R.string.spot_move),
            options = state.cities
                .filter { it.cityId != state.context?.cityId }
                .map { it.cityId to "${it.countryName} › ${it.cityName}" },
            onPick = {
                dialogs.movingSpot = false
                viewModel.moveToPlace(it)
            },
            onDismiss = { dialogs.movingSpot = false },
        )
    }

    if (dialogs.confirmingDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_title, state.spot?.name.orEmpty()),
            message = stringResource(R.string.delete_spot_message, state.links.size, state.hearts.size),
            onConfirm = {
                dialogs.confirmingDelete = false
                viewModel.delete()
                onBack()
            },
            onDismiss = { dialogs.confirmingDelete = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun HeartsSection(names: List<String>) {
    Text(
        text = if (names.isEmpty()) {
            stringResource(R.string.spot_hearts_none)
        } else {
            stringResource(R.string.spot_hearts_by, names.joinToString(", "))
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Edits in place and writes when the field loses focus, so every keystroke is not a sync. */
@Composable
private fun NotesField(initial: String, onSave: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(stringResource(R.string.spot_notes)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
    SaveIfChanged(value, initial, onSave)
}

@Composable
private fun TagsField(initial: String, onSave: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(stringResource(R.string.spot_tags)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    SaveIfChanged(value, initial, onSave)
}

/** Saves a text field a moment after typing stops, which keeps one edit out of the sync queue. */
@Composable
private fun SaveIfChanged(value: String, initial: String, onSave: (String) -> Unit) {
    LaunchedEffect(value) {
        if (value != initial) {
            delay(SAVE_DELAY_MS)
            onSave(value)
        }
    }
}

private const val SAVE_DELAY_MS = 800L

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinksSection(
    links: List<LinkEntity>,
    onOpen: (String) -> Unit,
    onMoveToInbox: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SectionLabel(stringResource(R.string.spot_links))
        TextButton(onClick = onAdd) { Text(stringResource(R.string.spot_add_link)) }
    }

    if (links.isEmpty()) {
        Text(
            text = stringResource(R.string.spot_links_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    links.forEach { link ->
        var expanded by remember(link.meta.id) { mutableStateOf(false) }
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpen(link.url) },
                        onLongClick = { expanded = true },
                    )
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = link.note.ifBlank { link.source.name },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = link.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.spot_link_to_inbox)) },
                    onClick = {
                        expanded = false
                        onMoveToInbox(link.meta.id)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = {
                        expanded = false
                        onDelete(link.meta.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun AddLinkDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spot_add_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.label_url)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.label_note)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(url, note) }, enabled = url.isNotBlank()) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A plain list to choose one thing from, used for both moving a spot and putting it on a trip. */
@Composable
private fun PickFromListDialog(
    title: String,
    options: List<Pair<String, String>>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (id, label) ->
                    TextButton(onClick = { onPick(id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
