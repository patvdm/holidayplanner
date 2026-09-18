package com.pat.app.holidayplanner.ui.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.CityRow
import com.pat.app.holidayplanner.domain.CountryCatalog
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SortScope
import com.pat.app.holidayplanner.ui.components.ConfirmDeleteDialog
import com.pat.app.holidayplanner.ui.components.EmptyListMessage
import com.pat.app.holidayplanner.ui.components.HeroImage
import com.pat.app.holidayplanner.ui.components.PictureDialog
import com.pat.app.holidayplanner.ui.components.Thumbnail
import com.pat.app.holidayplanner.ui.components.rememberKeyboardDismiss
import com.pat.app.holidayplanner.ui.components.PriorityBadge
import com.pat.app.holidayplanner.ui.components.ReorderableColumn
import com.pat.app.holidayplanner.ui.components.SortMenu
import com.pat.app.holidayplanner.ui.components.TextInputDialog
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryScreen(
    countryId: String,
    onBack: () -> Unit,
    onOpenPlace: (String) -> Unit,
    viewModel: CountryViewModel = koinViewModel { parametersOf(countryId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<CityRow?>(null) }
    var changingPicture by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val putKeyboardAway = rememberKeyboardDismiss()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val country = state.country
                    Text(
                        text = if (country == null) {
                            ""
                        } else {
                            "${CountryCatalog.flag(country.isoCode)} ${country.name}".trim()
                        },
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
                    SortMenu(SortScope.CITIES, state.sortMode, viewModel::setSortMode)
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
            state.country?.let { country ->
                HeroImage(
                    imageUrl = country.imageUrl,
                    title = "${CountryCatalog.flag(country.isoCode)} ${country.name}".trim(),
                )
            }
            if (state.rows.isEmpty()) {
                EmptyListMessage(stringResource(R.string.country_empty))
            } else {
                ReorderableColumn(
                    items = state.rows,
                    idOf = { it.place.meta.id },
                    sortKeyOf = { it.place.sortKey },
                    reorderable = state.canReorder,
                    onMoved = viewModel::move,
                    modifier = Modifier.fillMaxSize(),
                ) { row, handle ->
                    CityRowItem(
                        row = row,
                        handle = handle,
                        onClick = { onOpenPlace(row.place.meta.id) },
                        onPriority = { viewModel.setPriority(row.place.meta.id, it) },
                        onRename = { renaming = row },
                        onDelete = { viewModel.requestDelete(row.place.meta.id, row.place.name) },
                    )
                }
            }
        }
    }

    if (changingPicture) {
        PictureDialog(
            current = state.country?.imageUrl.orEmpty(),
            onSave = {
                viewModel.setImageUrl(it)
                changingPicture = false
            },
            onDismiss = { changingPicture = false },
        )
    }

    renaming?.let { row ->
        TextInputDialog(
            title = stringResource(R.string.country_rename_city),
            label = stringResource(R.string.label_name),
            initial = row.place.name,
            onConfirm = {
                viewModel.rename(row.place.meta.id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    state.pendingDelete?.let { pending ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_title, pending.name),
            message = stringResource(R.string.delete_place_message, pending.impact.spots),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun CityRowItem(
    row: CityRow,
    handle: (@Composable () -> Unit)?,
    onClick: () -> Unit,
    onPriority: (Priority) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Thumbnail(row.place.imageUrl)
            Column(modifier = Modifier.weight(1f)) {
                Text(row.place.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.country_spot_count, row.spotCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PriorityBadge(row.place.priority)
            RowMenu(
                currentPriority = row.place.priority,
                onPriority = onPriority,
                onRename = onRename,
                onDelete = onDelete,
            )
            handle?.invoke()
        }
        HorizontalDivider()
    }
}
