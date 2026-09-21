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

/**
 * A spot's or a city's links, one line each. A link is shown as itself: there is nothing to say
 * about it that the address does not, and a row has space for one line.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkList(
    links: List<LinkEntity>,
    onOpen: (LinkEntity) -> Unit,
    modifier: Modifier = Modifier,
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
                            onLongClick = { if (onDelete != null) menuOpen = true },
                        )
                        .padding(vertical = 6.dp),
                )
                if (onDelete != null) {
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        properties = PopupProperties(focusable = false),
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            onClick = {
                                menuOpen = false
                                onDelete(link)
                            },
                        )
                    }
                }
            }
        }
    }
}
