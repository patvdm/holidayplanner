package com.pat.app.holidayplanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.domain.pictureUrlOrNull

private val THUMBNAIL_SHAPE = RoundedCornerShape(12.dp)
internal const val HERO_HEIGHT_DP = 200
private const val HERO_SCRIM_START = 0.35f

/**
 * A square picture beside a list row. With nothing to show it falls back to [fallback], which is
 * a flag for a country and a quiet icon for everything else, so the rows keep their shape while
 * pictures are still being found.
 */
@Composable
fun Thumbnail(
    imageUrl: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    fallback: @Composable BoxScope.() -> Unit = { PictureFallbackIcon() },
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(THUMBNAIL_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val url = imageUrl.pictureUrlOrNull()
        if (url == null) {
            fallback()
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun BoxScope.PictureFallbackIcon() {
    Icon(
        imageVector = Icons.Outlined.Image,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.Center),
    )
}

/** A flag as the stand-in for a country's picture. */
@Composable
fun BoxScope.FlagFallback(flag: String) {
    Text(
        text = flag,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.align(Alignment.Center),
    )
}

/**
 * The wide picture at the top of a country, city or spot screen, with the name written over it.
 * Without a picture it is a plain band in the container colour, so the screen reads the same.
 * With [onClick], tapping the picture opens it full size.
 */
@Composable
fun HeroImage(
    imageUrl: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val url = imageUrl.pictureUrlOrNull()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT_DP.dp)
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = onClick?.let { stringResource(R.string.picture_open) },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            )
            HeroScrim()
        }
        HeroTitle(title = title, subtitle = subtitle, onPicture = url != null)
    }
}

/** Darkens the bottom of a picture so the name over it can be read. Taps go through to the picture. */
@Composable
internal fun BoxScope.HeroScrim() {
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(
                    HERO_SCRIM_START to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.75f),
                ),
            ),
    )
}

/**
 * The name and where it is, bottom left over the banner. With [onEdit], a pencil beside the name
 * renames it.
 */
@Composable
internal fun BoxScope.HeroTitle(
    title: String,
    subtitle: String?,
    onPicture: Boolean,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colour = if (onPicture) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
    Column(
        modifier = modifier
            .align(Alignment.BottomStart)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colour,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onEdit != null) {
                CompactIconButton(onClick = onEdit) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.action_rename),
                        tint = colour,
                        modifier = Modifier.size(CompactIconSize),
                    )
                }
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colour.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
