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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudUpload
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
import com.pat.app.holidayplanner.data.image.PhotoQueue
import com.pat.app.holidayplanner.data.local.LinkEntity

private val PHOTO_SHAPE = RoundedCornerShape(12.dp)
private const val VIEWER_DIM = 0.95f
private const val WAITING_BADGE_DIM = 0.55f

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
    onUseAsPicture: ((LinkEntity) -> Unit)? = null,
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
                if (PhotoQueue.isWaiting(photo.url)) WaitingBadge(Modifier.align(Alignment.BottomEnd))
                AttachmentMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onMove = onMove?.let { move -> { move(photo) } },
                    onDelete = { onDelete(photo) },
                    // Only once it has arrived: until then the other phone could not show it.
                    onUseAsPicture = onUseAsPicture
                        ?.takeUnless { PhotoQueue.isWaiting(photo.url) }
                        ?.let { use -> { use(photo) } },
                )
            }
        }
    }
}

/** Marks a photo that is still on this phone only, so nobody wonders why the other phone lacks it. */
@Composable
internal fun WaitingBadge(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Outlined.CloudUpload,
        contentDescription = stringResource(R.string.photo_waiting),
        tint = Color.White,
        modifier = modifier
            .padding(4.dp)
            .background(Color.Black.copy(alpha = WAITING_BADGE_DIM), CircleShape)
            .padding(3.dp)
            .size(14.dp),
    )
}

/**
 * What holding a photo or a link offers: make it the main picture, send it somewhere else, or get
 * rid of it. On a banner it also offers to change the main picture, from the chooser.
 */
@Composable
internal fun AttachmentMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onMove: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onUseAsPicture: (() -> Unit)? = null,
    onChangePicture: (() -> Unit)? = null,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        if (onChangePicture != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.picture_change)) },
                onClick = {
                    onDismiss()
                    onChangePicture()
                },
            )
        }
        if (onUseAsPicture != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.photo_use_as_picture)) },
                onClick = {
                    onDismiss()
                    onUseAsPicture()
                },
            )
        }
        if (onMove != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.attachment_move)) },
                onClick = {
                    onDismiss()
                    onMove()
                },
            )
        }
        if (onDelete != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = {
                    onDismiss()
                    onDelete()
                },
            )
        }
    }
}

/** One photo, as big as the screen allows. A tap anywhere closes it again. */
@Composable
fun PhotoViewer(url: String, onDismiss: () -> Unit) {
    PictureViewer(urls = listOf(url), start = 0, onDismiss = onDismiss)
}

/**
 * Pictures as big as the screen allows, swiped through from [start]. A tap anywhere closes it.
 * The list may grow while it is open, as found pictures arrive.
 */
@Composable
fun PictureViewer(urls: List<String>, start: Int, onDismiss: () -> Unit) {
    if (urls.isEmpty()) return
    val pager = rememberPagerState(initialPage = start.coerceIn(0, urls.lastIndex)) { urls.size }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = VIEWER_DIM)),
            contentAlignment = Alignment.Center,
        ) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = urls[page],
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (urls.size > 1) {
                PageCount(
                    page = pager.currentPage,
                    count = urls.size,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                )
            }
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

/** "2 / 7" on a dark pill, readable over any picture. */
@Composable
internal fun PageCount(page: Int, count: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.picture_count, page + 1, count),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = modifier
            .background(Color.Black.copy(alpha = WAITING_BADGE_DIM), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
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
    onUseAsPicture: ((LinkEntity) -> Unit)? = null,
) {
    if (attachments.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PhotoRoll(
            photos = attachments.filter { it.source.isPhoto },
            onOpen = onOpenPhoto,
            onDelete = onDelete,
            onMove = onMove,
            onUseAsPicture = onUseAsPicture,
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
