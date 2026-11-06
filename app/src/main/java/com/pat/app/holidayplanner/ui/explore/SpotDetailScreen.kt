package com.pat.app.holidayplanner.ui.explore

import com.pat.app.holidayplanner.data.remote.PictureSource
import com.pat.app.holidayplanner.ui.components.PictureChooser
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.pat.app.holidayplanner.ui.components.PriorityStar
import com.pat.app.holidayplanner.data.local.SpotEntity
import org.koin.compose.koinInject
import com.pat.app.holidayplanner.ui.components.SpotHoursSection
import com.pat.app.holidayplanner.data.repo.OpeningHoursLookup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.domain.SpotMark
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.hasMark
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.ui.components.ConfirmDeleteDialog
import com.pat.app.holidayplanner.ui.components.LinkList
import com.pat.app.holidayplanner.ui.components.PictureStrip
import com.pat.app.holidayplanner.ui.components.PictureStripActions
import com.pat.app.holidayplanner.ui.components.PictureViewer
import com.pat.app.holidayplanner.ui.components.rememberFoundPictures
import com.pat.app.holidayplanner.data.repo.GalleryPicture
import com.pat.app.holidayplanner.data.repo.galleryOf
import com.pat.app.holidayplanner.ui.components.MoveAttachmentDialog
import com.pat.app.holidayplanner.ui.components.TextInputDialog
import com.pat.app.holidayplanner.ui.components.label
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AddRoad
import com.pat.app.holidayplanner.ui.routes.AddToRouteSheet
import androidx.compose.material3.CircularProgressIndicator
import com.pat.app.holidayplanner.ui.components.openExternalUrl
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotDetailScreen(
    spotId: String,
    onBack: () -> Unit,
    onShowOnMap: () -> Unit,
    onOpenRoute: (String) -> Unit,
    viewModel: SpotDetailViewModel = koinViewModel { parametersOf(spotId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dialogs = remember { SpotDialogs() }
    // Only once the spot and where it is are known, so the search runs once with everything it needs.
    val found = rememberFoundPictures(state.spot?.takeIf { state.context != null }?.let { state.pictureQuery() })
    val gallery = remember(state.spot?.imageUrl, state.photos, found) {
        galleryOf(state.spot?.imageUrl.orEmpty(), state.photos, found)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::addPhoto)
    }

    Scaffold(
        // The tab bar below already keeps clear of the system bar; padding for it again left a dead strip.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                    // First in the bar: putting a spot on a walk is the quick thing to do here.
                    IconButton(onClick = { dialogs.addingToRoute = true }) {
                        Icon(Icons.Outlined.AddRoad, contentDescription = stringResource(R.string.add_to_route))
                    }
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
                    state.spot?.let { spot ->
                        PriorityStar(spot.priority, onToggle = { viewModel.setPriority(spot.priority.toggled()) })
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
                .verticalScroll(rememberScrollState()),
        ) {
            state.spot?.let { spot ->
                PictureStrip(
                    pictures = gallery,
                    title = spot.name,
                    subtitle = state.context?.let {
                        if (it.cityName.isBlank()) {
                            it.countryName
                        } else {
                            stringResource(R.string.spot_breadcrumb, it.countryName, it.cityName)
                        }
                    },
                    actions = PictureStripActions(
                        onOpen = { dialogs.viewing = it },
                        onUseAsCover = viewModel::useAsCover,
                        onMove = { dialogs.moving = it },
                        onDelete = { dialogs.deletingPhoto = it },
                        onChangePicture = { dialogs.changingPicture = true },
                        onRename = { dialogs.renaming = true },
                    ),
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LinksSection(
                    links = state.references,
                    onOpen = { context.openExternalUrl(it.url) },
                    onMove = { dialogs.moving = it },
                    onDelete = viewModel::deleteAttachment,
                    onAdd = { dialogs.addingLink = true },
                )

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
                // Wraps on a narrow screen rather than squeezing either label.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilledTonalButton(onClick = { context.openExternalUrl(state.mapsUrl) }) {
                        Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.spot_open_in_maps))
                    }
                    // Our own map, opened on this spot. A spot with no position has no pin to show.
                    OutlinedButton(
                        onClick = onShowOnMap,
                        enabled = state.spot?.let { it.lat != null && it.lng != null } == true,
                    ) {
                        Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.spot_show_on_map))
                    }
                }
                HoursSection(state.spot)
                HorizontalDivider()

                SectionLabel(stringResource(R.string.spot_type))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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

                SectionLabel(stringResource(R.string.spot_marks))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SpotMark.entries.forEach { mark ->
                        val on = state.spot?.marks?.hasMark(mark) == true
                        FilterChip(
                            selected = on,
                            onClick = { viewModel.setMark(mark, !on) },
                            label = { Text(stringResource(mark.label())) },
                        )
                    }
                }

                NotesField(initial = state.spot?.notes.orEmpty(), onSave = viewModel::setNotes)

                HorizontalDivider()
                OutlinedButton(onClick = { dialogs.movingSpot = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.spot_move))
                }
            }
        }
    }

    SpotDialogs(
        state = state,
        gallery = gallery,
        dialogs = dialogs,
        viewModel = viewModel,
        onBack = onBack,
        onOpenRoute = onOpenRoute,
    )
}

/** Which dialog is open, kept together so the screen itself stays readable. */
private class SpotDialogs {
    var renaming by mutableStateOf(false)
    var addingLink by mutableStateOf(false)
    var movingSpot by mutableStateOf(false)
    var changingPicture by mutableStateOf(false)
    var confirmingDelete by mutableStateOf(false)
    /** Which picture of the strip is open full size. */
    var viewing by mutableStateOf<Int?>(null)
    var deletingPhoto by mutableStateOf<LinkEntity?>(null)
    var moving by mutableStateOf<LinkEntity?>(null)
    var addingToRoute by mutableStateOf(false)
}

@Composable
private fun SpotDialogs(
    state: SpotDetailUiState,
    gallery: List<GalleryPicture>,
    dialogs: SpotDialogs,
    viewModel: SpotDetailViewModel,
    onBack: () -> Unit,
    onOpenRoute: (String) -> Unit,
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

    dialogs.viewing?.let { start ->
        PictureViewer(urls = gallery.map { it.url }, start = start, onDismiss = { dialogs.viewing = null })
    }

    dialogs.deletingPhoto?.let { photo ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.photo_delete_title),
            message = stringResource(R.string.photo_delete_message),
            onConfirm = {
                viewModel.deleteAttachment(photo)
                dialogs.deletingPhoto = null
            },
            onDismiss = { dialogs.deletingPhoto = null },
        )
    }

    dialogs.moving?.let { attachment ->
        MoveAttachmentDialog(
            attachment = attachment,
            targets = state.targets,
            onPick = {
                viewModel.moveAttachment(attachment, it)
                dialogs.moving = null
            },
            onDismiss = { dialogs.moving = null },
        )
    }

    if (dialogs.changingPicture) {
        val pictures: PictureSource = koinInject()
        PictureChooser(
            load = { pictures.candidatesForSpot(state.pictureQuery()) },
            onChoose = {
                viewModel.setImageUrl(it)
                dialogs.changingPicture = false
            },
            onFindAgain = {
                viewModel.setImageUrl("")
                dialogs.changingPicture = false
            },
            onDismiss = { dialogs.changingPicture = false },
        )
    }

    if (dialogs.addingLink) {
        TextInputDialog(
            title = stringResource(R.string.spot_add_link),
            label = stringResource(R.string.label_url),
            confirmLabel = stringResource(R.string.action_add),
            onConfirm = {
                viewModel.addLink(it)
                dialogs.addingLink = false
            },
            onDismiss = { dialogs.addingLink = false },
        )
    }

    val spot = state.spot
    if (dialogs.addingToRoute && spot != null) {
        AddToRouteSheet(
            spotId = spot.meta.id,
            spotName = spot.name,
            onDismiss = { dialogs.addingToRoute = false },
            onOpenRoute = onOpenRoute,
        )
    }

    if (dialogs.movingSpot && spot != null) {
        MoveSpotDialog(
            spot = spot,
            homes = state.homes,
            hereFirst = state.context?.countryId,
            onPick = {
                dialogs.movingSpot = false
                viewModel.moveTo(it)
            },
            onDismiss = { dialogs.movingSpot = false },
        )
    }

    if (dialogs.confirmingDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_title, state.spot?.name.orEmpty()),
            message = stringResource(
                R.string.delete_spot_message,
                pluralStringResource(R.plurals.count_links, state.links.size, state.links.size),
            ),
            onConfirm = {
                dialogs.confirmingDelete = false
                viewModel.delete()
                onBack()
            },
            onDismiss = { dialogs.confirmingDelete = false },
        )
    }
}

/** The week's hours and the button that fetches them; nothing without a Places key. */
@Composable
private fun HoursSection(spot: SpotEntity?, lookup: OpeningHoursLookup = koinInject()) {
    if (spot == null || !lookup.isAvailable) return
    HorizontalDivider()
    SpotHoursSection(spot, lookup)
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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

@Composable
private fun LinksSection(
    links: List<LinkEntity>,
    onOpen: (LinkEntity) -> Unit,
    onMove: (LinkEntity) -> Unit,
    onDelete: (LinkEntity) -> Unit,
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

    LinkList(links = links, onOpen = onOpen, onMove = onMove, onDelete = onDelete)
}


