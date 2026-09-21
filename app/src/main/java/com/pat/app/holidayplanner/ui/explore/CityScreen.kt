package com.pat.app.holidayplanner.ui.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.local.SpotRow
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SortScope
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.ui.components.ConfirmDeleteDialog
import com.pat.app.holidayplanner.ui.components.EmptyListMessage
import com.pat.app.holidayplanner.ui.components.HeartCount
import com.pat.app.holidayplanner.ui.components.LinkList
import com.pat.app.holidayplanner.ui.components.PhotoRoll
import com.pat.app.holidayplanner.ui.components.PhotoViewer
import com.pat.app.holidayplanner.ui.components.openExternalUrl
import com.pat.app.holidayplanner.ui.components.HeroImage
import com.pat.app.holidayplanner.ui.components.PictureDialog
import com.pat.app.holidayplanner.ui.components.Thumbnail
import com.pat.app.holidayplanner.ui.components.PriorityBadge
import com.pat.app.holidayplanner.ui.components.ReorderableColumn
import com.pat.app.holidayplanner.ui.components.SortMenu
import com.pat.app.holidayplanner.ui.components.rememberKeyboardDismiss
import com.pat.app.holidayplanner.ui.components.StatusChip
import com.pat.app.holidayplanner.ui.components.TypeBadge
import com.pat.app.holidayplanner.ui.components.firstLine
import com.pat.app.holidayplanner.ui.components.label
import com.pat.app.holidayplanner.ui.components.TextInputDialog
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityScreen(
    cityId: String,
    onBack: () -> Unit,
    onOpenSpot: (String) -> Unit,
    onOpenRoute: (String) -> Unit,
    viewModel: CityViewModel = koinViewModel { parametersOf(cityId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var renamingPlace by remember { mutableStateOf(false) }
    var changingPicture by remember { mutableStateOf(false) }
    var viewingPhoto by remember { mutableStateOf<String?>(null) }
    var deletingPhoto by remember { mutableStateOf<LinkEntity?>(null) }
    var addingLink by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::addPhoto)
    }
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val putKeyboardAway = rememberKeyboardDismiss()
    val routeTitle = state.place?.name.orEmpty()

    Scaffold(
        // The tab bar below already keeps clear of the system bar; padding for it again left a dead strip.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.place?.name.orEmpty(),
                        modifier = Modifier.clickable { renamingPlace = true },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.savingPhoto) {
                        CircularProgressIndicator(modifier = Modifier.padding(horizontal = 16.dp))
                    } else {
                        IconButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ImageOnly)) }) {
                            Icon(
                                Icons.Outlined.AddPhotoAlternate,
                                contentDescription = stringResource(R.string.spot_add_photo),
                            )
                        }
                    }
                    SortMenu(SortScope.SPOTS, state.sortMode, viewModel::setSortMode)
                    IconButton(
                        onClick = {
                            putKeyboardAway()
                            menuOpen = true
                        },
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = {
                            menuOpen = false
                            putKeyboardAway()
                        },
                        properties = PopupProperties(focusable = false),
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.route_new)) },
                            onClick = {
                                menuOpen = false
                                viewModel.createRoute(routeTitle, onOpenRoute)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.spot_add_link)) },
                            onClick = {
                                menuOpen = false
                                addingLink = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.picture_change)) },
                            onClick = {
                                menuOpen = false
                                changingPicture = true
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            state.place?.let { HeroImage(imageUrl = it.imageUrl, title = it.name) }
            CityAttachments(
                state = state,
                onOpenPhoto = { viewingPhoto = it.url },
                onOpenLink = { context.openExternalUrl(it.url) },
                onDelete = { deletingPhoto = it },
            )
            FilterRow(state, viewModel)
            if (state.rows.isEmpty()) {
                EmptyListMessage(
                    text = if (state.filters.any) {
                        stringResource(R.string.city_no_matches)
                    } else {
                        stringResource(R.string.city_empty)
                    },
                )
            } else {
                ReorderableColumn(
                    items = state.rows,
                    idOf = { it.spot.meta.id },
                    sortKeyOf = { it.spot.sortKey },
                    reorderable = state.canReorder,
                    onMoved = viewModel::move,
                    modifier = Modifier.fillMaxSize(),
                ) { row, handle ->
                    SpotRowItem(
                        row = row,
                        everyone = state.userCount > 0 && row.heartCount >= state.userCount,
                        attachments = state.attachments[row.spot.meta.id].orEmpty(),
                        handle = handle,
                        onOpenPhoto = { viewingPhoto = it.url },
                        onDeletePhoto = { deletingPhoto = it },
                        onOpenLink = { context.openExternalUrl(it.url) },
                        onClick = { onOpenSpot(row.spot.meta.id) },
                        onHeart = { viewModel.toggleHeart(row.spot.meta.id) },
                        onPriority = { viewModel.setPriority(row.spot.meta.id, it) },
                        onStatus = { viewModel.setStatus(row.spot.meta.id, it) },
                        onDelete = { viewModel.requestDelete(row) },
                    )
                }
            }
        }
    }

    viewingPhoto?.let { url ->
        PhotoViewer(url = url, onDismiss = { viewingPhoto = null })
    }

    deletingPhoto?.let { photo ->
        ConfirmDeleteDialog(
            title = stringResource(
                if (photo.source.isPhoto) R.string.photo_delete_title else R.string.link_delete_title,
            ),
            message = stringResource(
                if (photo.source.isPhoto) R.string.photo_delete_message else R.string.link_delete_message,
            ),
            onConfirm = {
                viewModel.deleteAttachment(photo)
                deletingPhoto = null
            },
            onDismiss = { deletingPhoto = null },
        )
    }

    if (addingLink) {
        TextInputDialog(
            title = stringResource(R.string.spot_add_link),
            label = stringResource(R.string.label_url),
            confirmLabel = stringResource(R.string.action_add),
            onConfirm = {
                viewModel.addLink(it)
                addingLink = false
            },
            onDismiss = { addingLink = false },
        )
    }

    if (changingPicture) {
        PictureDialog(
            onSave = {
                viewModel.setImageUrl(it)
                changingPicture = false
            },
            onDismiss = { changingPicture = false },
        )
    }

    if (renamingPlace) {
        TextInputDialog(
            title = stringResource(R.string.country_rename_city),
            label = stringResource(R.string.label_name),
            initial = state.place?.name.orEmpty(),
            onConfirm = {
                viewModel.renamePlace(it)
                renamingPlace = false
            },
            onDismiss = { renamingPlace = false },
        )
    }

    state.pendingDelete?.let { pending ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_title, pending.name),
            message = stringResource(
                R.string.delete_spot_message,
                pending.impact.links,
                pending.impact.hearts,
            ),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun FilterRow(state: CityUiState, viewModel: CityViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SpotStatus.entries.forEach { status ->
            FilterChip(
                selected = state.filters.status == status,
                onClick = { viewModel.setStatusFilter(status.takeIf { it != state.filters.status }) },
                label = { Text(stringResource(status.label())) },
            )
        }
        Priority.entries.forEach { priority ->
            FilterChip(
                selected = state.filters.priority == priority,
                onClick = { viewModel.setPriorityFilter(priority.takeIf { it != state.filters.priority }) },
                label = { Text(stringResource(priority.label())) },
            )
        }
        SpotType.entries.forEach { type ->
            FilterChip(
                selected = state.filters.type == type,
                onClick = { viewModel.setTypeFilter(type.takeIf { it != state.filters.type }) },
                label = { Text(stringResource(type.label())) },
            )
        }
        if (state.filters.any) {
            FilterChip(
                selected = false,
                onClick = viewModel::clearFilters,
                label = { Text(stringResource(R.string.filter_clear)) },
            )
        }
    }
}

@Composable
private fun SpotRowItem(
    row: SpotRow,
    everyone: Boolean,
    attachments: List<LinkEntity>,
    handle: (@Composable () -> Unit)?,
    onOpenPhoto: (LinkEntity) -> Unit,
    onDeletePhoto: (LinkEntity) -> Unit,
    onOpenLink: (LinkEntity) -> Unit,
    onClick: () -> Unit,
    onHeart: () -> Unit,
    onPriority: (Priority) -> Unit,
    onStatus: (SpotStatus) -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Thumbnail(row.spot.imageUrl, size = 48.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(row.spot.name, style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PriorityBadge(row.spot.priority)
                    StatusChip(row.spot.status)
                    TypeBadge(row.spot.type)
                }
                // The reason a place was saved is usually the first thing written about it.
                row.spot.notes.firstLine()?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onHeart) {
                HeartCount(count = row.heartCount, heartedByMe = row.heartedByMe, everyone = everyone)
            }
            SpotRowMenu(row = row, onPriority = onPriority, onStatus = onStatus, onDelete = onDelete)
            handle?.invoke()
        }
        // Everything saved about the place, on lines of their own so the row above stays readable.
        Attachments(
            attachments = attachments,
            onOpenPhoto = onOpenPhoto,
            onOpenLink = onOpenLink,
            onDelete = onDeletePhoto,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )
        HorizontalDivider()
    }
}

/** Small enough that a row with photos is still a row rather than a card. */
private const val ROW_PHOTO_SIZE_DP = 64

/** The photos of a place, then its links: the same shape whether they hang on a spot or a city. */
@Composable
private fun Attachments(
    attachments: List<LinkEntity>,
    onOpenPhoto: (LinkEntity) -> Unit,
    onOpenLink: (LinkEntity) -> Unit,
    onDelete: (LinkEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PhotoRoll(
            photos = attachments.filter { it.source.isPhoto },
            onOpen = onOpenPhoto,
            onDelete = onDelete,
            size = ROW_PHOTO_SIZE_DP.dp,
        )
        LinkList(
            links = attachments.filterNot { it.source.isPhoto },
            onOpen = onOpenLink,
            onDelete = onDelete,
        )
    }
}

/** What was shared to the city itself, above everything that is only about one spot in it. */
@Composable
private fun CityAttachments(
    state: CityUiState,
    onOpenPhoto: (LinkEntity) -> Unit,
    onOpenLink: (LinkEntity) -> Unit,
    onDelete: (LinkEntity) -> Unit,
) {
    Attachments(
        attachments = state.own,
        onOpenPhoto = onOpenPhoto,
        onOpenLink = onOpenLink,
        onDelete = onDelete,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SpotRowMenu(
    row: SpotRow,
    onPriority: (Priority) -> Unit,
    onStatus: (SpotStatus) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val putKeyboardAway = rememberKeyboardDismiss()

    IconButton(
        onClick = {
            // Dropped before the popup takes window focus, so there is nothing to hand back to.
            putKeyboardAway()
            expanded = true
        },
    ) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            expanded = false
            putKeyboardAway()
        },
        properties = PopupProperties(focusable = false),
    ) {
        Priority.entries.forEach { priority ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_set_priority, stringResource(priority.label()))) },
                enabled = priority != row.spot.priority,
                onClick = {
                    expanded = false
                    onPriority(priority)
                },
            )
        }
        HorizontalDivider()
        SpotStatus.entries.forEach { status ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_set_status, stringResource(status.label()))) },
                enabled = status != row.spot.status,
                onClick = {
                    expanded = false
                    onStatus(status)
                },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            onClick = {
                expanded = false
                onDelete()
            },
        )
    }
}

fun SpotStatus.label(): Int = when (this) {
    SpotStatus.UNVISITED -> R.string.status_unvisited
    SpotStatus.VISITED -> R.string.status_visited
}
