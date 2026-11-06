package com.pat.app.holidayplanner.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.data.image.PhotoQueue
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.remote.PictureCandidate
import com.pat.app.holidayplanner.data.remote.PictureFrom
import com.pat.app.holidayplanner.data.remote.SpotPictureQuery
import com.pat.app.holidayplanner.data.repo.FoundPictures
import com.pat.app.holidayplanner.data.repo.GalleryPicture
import org.koin.compose.koinInject

private const val LABEL_DIM = 0.55f

/** What the "2 / 7" in the corner needs, so a long name stops short of it. */
private val PAGE_COUNT_ROOM = 56.dp

/**
 * Pictures found for a spot, fetched once per spot while the app runs. Empty while they load, when
 * [query] is null because the spot is not loaded yet, and when there are none.
 */
@Composable
fun rememberFoundPictures(query: SpotPictureQuery?): List<PictureCandidate> {
    val found: FoundPictures = koinInject()
    val pictures by produceState(emptyList<PictureCandidate>(), query) {
        value = query?.let { found.forSpot(it) }.orEmpty()
    }
    return pictures
}

/** What the strip does: open a picture, what holding one offers, and the pencil by the name. */
class PictureStripActions(
    val onOpen: (Int) -> Unit,
    val onUseAsCover: (GalleryPicture) -> Unit,
    val onMove: (LinkEntity) -> Unit,
    val onDelete: (LinkEntity) -> Unit,
    val onChangePicture: () -> Unit,
    val onRename: () -> Unit,
)

/**
 * The banner of a spot, swiped through its pictures: the main one, your photos, then found ones.
 * Tapping opens the picture full size. Holding offers to change the main picture from the chooser,
 * to make this one the main picture, and for a photo of your own to move or remove it. With no
 * pictures at all it is the plain banner, and holding it opens the chooser straight away.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PictureStrip(
    pictures: List<GalleryPicture>,
    title: String,
    subtitle: String?,
    actions: PictureStripActions,
    modifier: Modifier = Modifier,
) {
    if (pictures.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(HERO_HEIGHT_DP.dp)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .combinedClickable(onClick = {}, onLongClick = actions.onChangePicture),
        ) {
            HeroTitle(title = title, subtitle = subtitle, onPicture = false, onEdit = actions.onRename)
        }
        return
    }
    val pager = rememberPagerState { pictures.size }
    // A new main picture moves to the front, so follow it there rather than show what took its place.
    LaunchedEffect(pictures.first().url) { pager.scrollToPage(0) }
    var menuFor by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT_DP.dp)
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val picture = pictures[page]
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = picture.url,
                    contentDescription = stringResource(R.string.picture_open),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .combinedClickable(onClick = { actions.onOpen(page) }, onLongClick = { menuFor = page }),
                )
                if (PhotoQueue.isWaiting(picture.url)) WaitingBadge(Modifier.align(Alignment.TopEnd))
                PictureMenu(
                    picture = picture,
                    expanded = menuFor == page,
                    onDismiss = { menuFor = null },
                    actions = actions,
                )
            }
        }
        HeroScrim()
        // Clear of the page count, which sits in the same bottom corner.
        HeroTitle(
            title = title,
            subtitle = subtitle,
            onPicture = true,
            onEdit = actions.onRename,
            modifier = Modifier.padding(end = if (pictures.size > 1) PAGE_COUNT_ROOM else 0.dp),
        )
        pictures.getOrNull(pager.currentPage)?.from?.let { from ->
            SourceLabel(from, Modifier.align(Alignment.TopStart).padding(12.dp))
        }
        if (pictures.size > 1) {
            PageCount(
                page = pager.currentPage,
                count = pictures.size,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun PictureMenu(
    picture: GalleryPicture,
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: PictureStripActions,
) {
    val photo = picture.photo
    // Only once a photo has arrived: until then the other phone could not show it.
    val canBeCover = !picture.isCover && !PhotoQueue.isWaiting(picture.url)
    AttachmentMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        onMove = photo?.let { { actions.onMove(it) } },
        onDelete = photo?.let { { actions.onDelete(it) } },
        onUseAsPicture = if (canBeCover) ({ actions.onUseAsCover(picture) }) else null,
        onChangePicture = actions.onChangePicture,
    )
}

/** Where a found picture came from, so it is not taken for one of your own. */
@Composable
private fun SourceLabel(from: PictureFrom, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(from.label()),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = modifier
            .background(Color.Black.copy(alpha = LABEL_DIM), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
