package com.pat.app.holidayplanner.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.domain.LinkSource

private val PHOTO_SHAPE = RoundedCornerShape(12.dp)
private const val VIEWER_DIM = 0.95f

/**
 * The photos of one spot, side by side. Tapping one opens it; holding one offers to remove it,
 * which is the only way a photo goes, so it asks first.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoRoll(
    photos: List<LinkEntity>,
    onOpen: (LinkEntity) -> Unit,
    onDelete: (LinkEntity) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    if (photos.isEmpty()) return
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photos, key = { it.meta.id }) { photo ->
            AsyncImage(
                model = photo.url,
                contentDescription = stringResource(R.string.photo_open),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(PHOTO_SHAPE)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .combinedClickable(onClick = { onOpen(photo) }, onLongClick = { onDelete(photo) }),
            )
        }
    }
}

/** One photo, as big as the screen allows. A tap anywhere closes it again. */
@Composable
fun PhotoViewer(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = VIEWER_DIM))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
            }
        }
    }
}

/** A spot's links as chips that open them, for a row with no space for a list. */
@Composable
fun LinkChips(links: List<LinkEntity>, onOpen: (LinkEntity) -> Unit, modifier: Modifier = Modifier) {
    if (links.isEmpty()) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        links.forEach { link ->
            AssistChip(
                onClick = { onOpen(link) },
                label = { Text(link.label()) },
                colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** What a link is called: whatever was written about it, else where it came from. */
@Composable
fun LinkEntity.label(): String = note.ifBlank { stringResource(source.label()) }

fun LinkSource.label(): Int = when (this) {
    LinkSource.INSTAGRAM -> R.string.link_instagram
    LinkSource.TIKTOK -> R.string.link_tiktok
    LinkSource.YOUTUBE -> R.string.link_youtube
    LinkSource.MAPS -> R.string.link_maps
    LinkSource.WEB -> R.string.link_web
    LinkSource.PHOTO -> R.string.link_photo
}
