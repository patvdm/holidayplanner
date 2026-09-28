package com.pat.app.holidayplanner.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.local.LinkEntity

private val PHOTO_SHAPE = RoundedCornerShape(12.dp)
private const val VIEWER_DIM = 0.95f

/**
 * The photos of a place, side by side. Tapping one opens it; holding one offers to move it to
 * something else or to remove it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoRoll(
    photos: List<LinkEntity>,
    onOpen: (LinkEntity) -> Unit,
    onDelete: (LinkEntity) -> Unit,
    modifier: Modifier = Modifier,
    onMove: ((LinkEntity) -> Unit)? = null,
    size: Dp = 96.dp,
) {
    if (photos.isEmpty()) return
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photos, key = { it.meta.id }) { photo ->
            var menuOpen by remember(photo.meta.id) { mutableStateOf(false) }
            Box {
                AsyncImage(
                    model = photo.url,
                    contentDescription = stringResource(R.string.photo_open),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(size)
                        .clip(PHOTO_SHAPE)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .combinedClickable(onClick = { onOpen(photo) }, onLongClick = { menuOpen = true }),
                )
                AttachmentMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onMove = onMove?.let { move -> { move(photo) } },
                    onDelete = { onDelete(photo) },
                )
            }
        }
    }
}

/** What holding a photo or a link offers: send it somewhere else, or get rid of it. */
@Composable
private fun AttachmentMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onMove: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        if (onMove != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.attachment_move)) },
                onClick = {
                    onDismiss()
                    onMove()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            onClick = {
                onDismiss()
                onDelete()
            },
        )
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

/**
 * The links of a place, one line each. A link is shown as itself: there is nothing to say about it
 * that the address does not, and a row has space for one line. Holding one offers to move or
 * remove it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkList(
    links: List<LinkEntity>,
    onOpen: (LinkEntity) -> Unit,
    modifier: Modifier = Modifier,
    onMove: ((LinkEntity) -> Unit)? = null,
    onDelete: ((LinkEntity) -> Unit)? = null,
) {
    if (links.isEmpty()) return
    Column(modifier = modifier) {
        links.forEach { link ->
            var menuOpen by remember(link.meta.id) { mutableStateOf(false) }
            Box {
                Text(
                    text = link.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onOpen(link) },
                            onLongClick = { if (onDelete != null || onMove != null) menuOpen = true },
                        )
                        .padding(vertical = 6.dp),
                )
                AttachmentMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onMove = onMove?.let { move -> { move(link) } },
                    onDelete = { onDelete?.invoke(link) },
                )
            }
        }
    }
}

/** Small enough that a row with photos is still a row rather than a card. */
private val ROW_PHOTO_SIZE = 64.dp

/**
 * The photos of a place, then its links, the same shape whether they hang on a spot, a city or a
 * country. Nothing at all is drawn when there is nothing to show.
 */
@Composable
fun Attachments(
    attachments: List<LinkEntity>,
    onOpenPhoto: (LinkEntity) -> Unit,
    onOpenLink: (LinkEntity) -> Unit,
    onMove: (LinkEntity) -> Unit,
    onDelete: (LinkEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PhotoRoll(
            photos = attachments.filter { it.source.isPhoto },
            onOpen = onOpenPhoto,
            onDelete = onDelete,
            onMove = onMove,
            size = ROW_PHOTO_SIZE,
        )
        LinkList(
            links = attachments.filterNot { it.source.isPhoto },
            onOpen = onOpenLink,
            onMove = onMove,
            onDelete = onDelete,
        )
    }
}
