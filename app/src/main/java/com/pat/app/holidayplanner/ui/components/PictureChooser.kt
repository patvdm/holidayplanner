package com.pat.app.holidayplanner.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.remote.PictureCandidate
import com.pat.app.holidayplanner.data.remote.PictureFrom

private val CaptionBackground = Color(0x99000000)

/**
 * Choosing a place's main picture: a grid of candidates found for it, tapped to take one, with a
 * link to paste for anything else and a way back to letting the app choose.
 *
 * [onChoose] gets the picture chosen or pasted; [onFindAgain] clears the picture so the automatic
 * search has another go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PictureChooser(
    load: suspend () -> Result<List<PictureCandidate>>,
    onChoose: (String) -> Unit,
    onFindAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Null while the search runs.
    val candidates by produceState<List<PictureCandidate>?>(initialValue = null, load) {
        value = load().getOrDefault(emptyList())
    }
    var pasted by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.picture_choose_title), style = MaterialTheme.typography.titleLarge)
            when (val found = candidates) {
                null -> Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.picture_loading), style = MaterialTheme.typography.bodyMedium)
                }
                emptyList<PictureCandidate>() -> Text(
                    text = stringResource(R.string.picture_none_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(GRID_COLUMNS),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = GRID_MAX_HEIGHT),
                ) {
                    items(found, key = { it.url }) { candidate ->
                        CandidateTile(candidate, onClick = { onChoose(candidate.url) })
                    }
                }
            }
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it },
                label = { Text(stringResource(R.string.picture_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onFindAgain) { Text(stringResource(R.string.picture_find_again)) }
                TextButton(onClick = { onChoose(pasted.trim()) }, enabled = pasted.isNotBlank()) {
                    Text(stringResource(R.string.picture_use_link))
                }
            }
        }
    }
}

/** One candidate, square, with where it came from along its foot. */
@Composable
private fun CandidateTile(candidate: PictureCandidate, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = candidate.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Text(
            text = caption(candidate),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(CaptionBackground)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun caption(candidate: PictureCandidate): String {
    val from = stringResource(candidate.from.label())
    return candidate.metres?.let { stringResource(R.string.picture_from_near, from, it) } ?: from
}

/** Where a picture was found, as said under or over it. */
@StringRes
internal fun PictureFrom.label(): Int = when (this) {
    PictureFrom.WIKIVOYAGE -> R.string.picture_from_wikivoyage
    PictureFrom.WIKIPEDIA -> R.string.picture_from_wikipedia
    PictureFrom.COMMONS -> R.string.picture_from_commons
    PictureFrom.WEBSITE -> R.string.picture_from_website
}

private const val GRID_COLUMNS = 3
private val GRID_MAX_HEIGHT = 420.dp
