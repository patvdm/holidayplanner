package com.pat.app.holidayplanner.ui.explore

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.CityEntity
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.local.SpotRow
import com.pat.app.holidayplanner.data.repo.AttachmentOwner
import com.pat.app.holidayplanner.domain.CountryCatalog
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SortMode
import com.pat.app.holidayplanner.domain.SortScope
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.ui.components.ConfirmDeleteDialog
import com.pat.app.holidayplanner.ui.components.MoveAttachmentDialog
import com.pat.app.holidayplanner.ui.components.NoteDialog
import com.pat.app.holidayplanner.ui.components.PhotoViewer
import com.pat.app.holidayplanner.ui.components.PickSpotsSheet
import com.pat.app.holidayplanner.ui.components.PlaceSearchSheet
import com.pat.app.holidayplanner.ui.components.PictureDialog
import com.pat.app.holidayplanner.ui.components.SortMenu
import com.pat.app.holidayplanner.ui.components.TextInputDialog
import com.pat.app.holidayplanner.ui.components.openExternalUrl
import com.pat.app.holidayplanner.ui.components.rememberKeyboardDismiss
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A country and everything in it on one screen. Each city is a section heading carrying its own
 * photos, links and menu, with its spots listed underneath; there is no separate city screen.
 * [focusCityId] scrolls to one city on opening, for a search result that named it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryScreen(
    countryId: String,
    focusCityId: String,
    onBack: () -> Unit,
    onOpenSpot: (String) -> Unit,
    onOpenRoute: (String) -> Unit,
    viewModel: CountryViewModel = koinViewModel { parametersOf(countryId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dialogs = remember { CountryDialogs() }
    var arranging by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::addPhoto)
    }
    val openSpot by rememberUpdatedState(onOpenSpot)
    val openRoute by rememberUpdatedState(onOpenRoute)

    val actions = remember(viewModel, dialogs) {
        object : CountryActions {
            override val onOpenSpot: (String) -> Unit = { openSpot(it) }
            override val onToggleCity: (String) -> Unit = viewModel::toggleExpanded
            override val onFilters: (SpotFilters) -> Unit = viewModel::setFilters
            override val onOpenPhoto: (LinkEntity) -> Unit = { dialogs.viewingPhoto = it.url }
            override val onOpenLink: (LinkEntity) -> Unit = { context.openExternalUrl(it.url) }
            override val onMoveAttachment: (LinkEntity) -> Unit = { dialogs.movingAttachment = it }
            override val onDeleteAttachment: (LinkEntity) -> Unit = { dialogs.deletingAttachment = it }
            override val onUseAsPicture: (LinkEntity) -> Unit = viewModel::useAsPicture
            override val onAddPhoto: (AttachmentOwner) -> Unit = {
                viewModel.choosePhotoFor(it)
                photoPicker.launch(PickVisualMediaRequest(ImageOnly))
            }
            override val onAddLink: (AttachmentOwner) -> Unit = { dialogs.addingLinkTo = it }
            override val onEditNote: (AttachmentOwner, String) -> Unit = { owner, note ->
                dialogs.editingNote = owner to note
            }
            override val onChangePicture: (String) -> Unit = { dialogs.changingPictureOf = it }
            override val onNewRoute: (CityEntity) -> Unit = { dialogs.newRouteIn = it }
            override val onAddSpot: (CityEntity) -> Unit = { dialogs.addingSpotIn = it.meta.id }
            override val onCityPriority: (String, Priority) -> Unit = { id, priority ->
                viewModel.setPriority(AttachmentOwner(cityId = id), priority)
            }
            override val onRenameCity: (CityEntity) -> Unit = { dialogs.renamingCity = it }
            override val onDeleteCity: (CityEntity) -> Unit = { viewModel.requestCityDelete(it.meta.id, it.name) }
            override val onHeart: (String) -> Unit = viewModel::toggleHeart
            override val onSpotPriority: (String, Priority) -> Unit = { id, priority ->
                viewModel.setPriority(AttachmentOwner(spotId = id), priority)
            }
            override val onSpotStatus: (String, SpotStatus) -> Unit = viewModel::setSpotStatus
            override val onSpotMoved: (String, String?, String?) -> Unit = viewModel::moveSpot
            override val onMoveSpot: (SpotRow) -> Unit = { dialogs.movingSpot = it }
            override val onDeleteSpot: (SpotRow) -> Unit = viewModel::requestSpotDelete
        }
    }

    // Once, when the cities first arrive: a search result for a city lands on that city.
    var focused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.sections.isNotEmpty()) {
        if (focused || state.sections.isEmpty()) return@LaunchedEffect
        focused = true
        val index = state.indexOfCity(focusCityId)
        if (index >= 0) listState.scrollToItem(index)
    }

    BackHandler(enabled = arranging) { arranging = false }

    Scaffold(
        // The tab bar below already keeps clear of the system bar; padding for it again left a dead strip.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (arranging) {
                            stringResource(R.string.country_arrange_cities)
                        } else {
                            state.country?.let { "${CountryCatalog.flag(it.isoCode)} ${it.name}".trim() }.orEmpty()
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (arranging) arranging = false else onBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (arranging) {
                        IconButton(onClick = { arranging = false }) {
                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_done))
                        }
                    } else {
                        CountryTopActions(
                            state = state,
                            onAddPhoto = { actions.onAddPhoto(AttachmentOwner(countryId = countryId)) },
                            onArrange = { arranging = true },
                            onSortMode = viewModel::setSortMode,
                            menu = { close ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.spot_add_link)) },
                                    onClick = {
                                        close()
                                        dialogs.addingLinkTo = AttachmentOwner(countryId = countryId)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.picture_change)) },
                                    onClick = {
                                        close()
                                        dialogs.changingPictureOf = ""
                                    },
                                )
                                if (state.sections.isNotEmpty()) {
                                    val expand = !state.anyExpanded
                                    val label = if (expand) R.string.city_expand_all else R.string.city_collapse_all
                                    DropdownMenuItem(
                                        text = { Text(stringResource(label)) },
                                        onClick = {
                                            close()
                                            viewModel.setAllExpanded(expand)
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            // Hidden while the cities are being dragged about: nothing else is tappable then either.
            if (!arranging) {
                FloatingActionButton(onClick = { dialogs.addingSpotIn = "" }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.spot_add))
                }
            }
        },
    ) { padding ->
        if (arranging) {
            ArrangeCities(
                sections = state.sections,
                onMoved = viewModel::moveCity,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
        } else {
            CountryContent(
                state = state,
                listState = listState,
                actions = actions,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
        }
    }

    CountryDialogs(state = state, dialogs = dialogs, viewModel = viewModel, onOpenRoute = { openRoute(it) })
}

/** The country's own buttons: a photo of it, the spot order, arranging the cities, and the rest. */
@Composable
private fun CountryTopActions(
    state: CountryUiState,
    onAddPhoto: () -> Unit,
    onArrange: () -> Unit,
    onSortMode: (SortMode) -> Unit,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val putKeyboardAway = rememberKeyboardDismiss()

    if (state.savingPhoto) {
        CircularProgressIndicator(modifier = Modifier.padding(horizontal = 16.dp))
    } else {
        IconButton(onClick = onAddPhoto) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = stringResource(R.string.spot_add_photo))
        }
    }
    SortMenu(SortScope.SPOTS, state.sortMode, onSortMode)
    if (state.sections.size > 1) {
        IconButton(onClick = onArrange) {
            Icon(Icons.Filled.SwapVert, contentDescription = stringResource(R.string.country_arrange_cities))
        }
    }
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
        menu { menuOpen = false }
    }
}

/** Which dialog is open, kept together so the screen itself stays readable. */
private class CountryDialogs {
    /** The city a route is being made for, while its spots are being chosen. */
    var newRouteIn by mutableStateOf<CityEntity?>(null)

    /** The city a new spot is being searched for: empty means anywhere in the country. */
    var addingSpotIn by mutableStateOf<String?>(null)

    var viewingPhoto by mutableStateOf<String?>(null)
    var deletingAttachment by mutableStateOf<LinkEntity?>(null)
    var movingAttachment by mutableStateOf<LinkEntity?>(null)
    var addingLinkTo by mutableStateOf<AttachmentOwner?>(null)

    /** Empty for the country itself, else the city whose picture is being changed. */
    var changingPictureOf by mutableStateOf<String?>(null)
    var renamingCity by mutableStateOf<CityEntity?>(null)
    var movingSpot by mutableStateOf<SpotRow?>(null)

    /** The city or spot whose note is open, with the note it had when the dialog opened. */
    var editingNote by mutableStateOf<Pair<AttachmentOwner, String>?>(null)
}

/** The two sheets that make something new: a route through a city, and a spot found on Google. */
@Composable
private fun CountryAddSheets(
    state: CountryUiState,
    dialogs: CountryDialogs,
    viewModel: CountryViewModel,
    onOpenRoute: (String) -> Unit,
) {
    // A route starts with the city it walks, then the spots it takes in.
    dialogs.newRouteIn?.let { city ->
        PickSpotsSheet(
            cityId = city.meta.id,
            onConfirm = { spotIds ->
                dialogs.newRouteIn = null
                viewModel.createRoute(city.meta.id, city.name, spotIds, onOpenRoute)
            },
            onDismiss = { dialogs.newRouteIn = null },
        )
    }

    dialogs.addingSpotIn?.let { cityId ->
        // From a city the search leans on its coordinates; from the country, on the country itself,
        // and Google's answer decides which city the spot ends up in.
        PlaceSearchSheet(
            title = stringResource(R.string.spot_add),
            cityId = cityId,
            knownIsoCode = state.country?.isoCode.orEmpty(),
            onDismiss = { dialogs.addingSpotIn = null },
            onSaved = { dialogs.addingSpotIn = null },
        )
    }
}

@Composable
private fun CountryDialogs(
    state: CountryUiState,
    dialogs: CountryDialogs,
    viewModel: CountryViewModel,
    onOpenRoute: (String) -> Unit,
) {
    CountryAddSheets(state, dialogs, viewModel, onOpenRoute)

    dialogs.viewingPhoto?.let { url ->
        PhotoViewer(url = url, onDismiss = { dialogs.viewingPhoto = null })
    }

    dialogs.deletingAttachment?.let { attachment ->
        ConfirmDeleteDialog(
            title = stringResource(
                if (attachment.source.isPhoto) R.string.photo_delete_title else R.string.link_delete_title,
            ),
            message = stringResource(
                if (attachment.source.isPhoto) R.string.photo_delete_message else R.string.link_delete_message,
            ),
            onConfirm = {
                viewModel.deleteAttachment(attachment)
                dialogs.deletingAttachment = null
            },
            onDismiss = { dialogs.deletingAttachment = null },
        )
    }

    dialogs.movingAttachment?.let { attachment ->
        MoveAttachmentDialog(
            attachment = attachment,
            targets = state.targets,
            onPick = {
                viewModel.moveAttachment(attachment, it)
                dialogs.movingAttachment = null
            },
            onDismiss = { dialogs.movingAttachment = null },
        )
    }

    dialogs.addingLinkTo?.let { owner ->
        TextInputDialog(
            title = stringResource(R.string.spot_add_link),
            label = stringResource(R.string.label_url),
            confirmLabel = stringResource(R.string.action_add),
            onConfirm = {
                viewModel.addLink(owner, it)
                dialogs.addingLinkTo = null
            },
            onDismiss = { dialogs.addingLinkTo = null },
        )
    }

    dialogs.changingPictureOf?.let { cityId ->
        PictureDialog(
            onSave = {
                viewModel.setPicture(cityId, it)
                dialogs.changingPictureOf = null
            },
            onDismiss = { dialogs.changingPictureOf = null },
        )
    }

    dialogs.editingNote?.let { (owner, note) ->
        NoteDialog(
            initial = note,
            onSave = {
                viewModel.setNote(owner, it)
                dialogs.editingNote = null
            },
            onDismiss = { dialogs.editingNote = null },
        )
    }

    dialogs.renamingCity?.let { city ->
        TextInputDialog(
            title = stringResource(R.string.country_rename_city),
            label = stringResource(R.string.label_name),
            initial = city.name,
            onConfirm = {
                viewModel.renameCity(city.meta.id, it)
                dialogs.renamingCity = null
            },
            onDismiss = { dialogs.renamingCity = null },
        )
    }

    dialogs.movingSpot?.let { row ->
        MoveSpotDialog(
            spot = row.spot,
            homes = state.homes,
            hereFirst = state.country?.meta?.id,
            onPick = {
                viewModel.moveSpotTo(row.spot.meta.id, it)
                dialogs.movingSpot = null
            },
            onDismiss = { dialogs.movingSpot = null },
        )
    }

    state.pendingCityDelete?.let { pending ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_title, pending.name),
            message = stringResource(R.string.delete_place_message, pending.impact.spots),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }

    state.pendingSpotDelete?.let { pending ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_title, pending.name),
            message = stringResource(R.string.delete_spot_message, pending.impact.links, pending.impact.hearts),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}
