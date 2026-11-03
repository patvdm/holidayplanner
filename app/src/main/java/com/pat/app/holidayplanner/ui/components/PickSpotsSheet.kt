package com.pat.app.holidayplanner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.repo.SpotRepository
import org.koin.compose.koinInject

private const val LIST_MAX_HEIGHT_DP = 420

/**
 * Picks any number of a city's spots at once, which is how a route starts: the city first, then
 * what the walk takes in. Confirming with none chosen is allowed — an empty route is a plan you
 * have not made yet, and spots can be added to it afterwards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickSpotsSheet(
    cityId: String,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.route_pick_spots),
    repository: SpotRepository = koinInject(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val spots by remember(repository, cityId) { repository.observeSpots(cityId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var chosen by remember(cityId) { mutableStateOf(emptySet<String>()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        DropFocusWhileSheetCloses(sheetState)
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)

            if (spots.isEmpty()) {
                Text(
                    text = stringResource(R.string.route_pick_spots_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }

            LazyColumn(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT_DP.dp)) {
                items(spots, key = { it.meta.id }) { spot ->
                    val id = spot.meta.id
                    SpotChoice(
                        spot = spot,
                        chosen = id in chosen,
                        onToggle = { chosen = if (id in chosen) chosen - id else chosen + id },
                    )
                }
            }

            Button(
                onClick = { onConfirm(spots.map { it.meta.id }.filter { it in chosen }) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (chosen.isEmpty()) {
                        stringResource(R.string.route_create_empty)
                    } else {
                        stringResource(R.string.route_create_with, chosen.size)
                    },
                )
            }
        }
    }
}

@Composable
private fun SpotChoice(spot: SpotEntity, chosen: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = chosen, onCheckedChange = { onToggle() })
        Thumbnail(spot.imageUrl, size = 40.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(spot.name, style = MaterialTheme.typography.bodyLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PriorityBadge(spot.priority)
                StatusChip(spot.status)
                MarkBadges(spot.marks)
                TypeBadge(spot.type)
            }
        }
    }
    HorizontalDivider()
}
