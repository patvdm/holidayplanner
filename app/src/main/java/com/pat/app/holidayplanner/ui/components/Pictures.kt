package com.pat.app.holidayplanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pat.app.holidayplanner.domain.pictureUrlOrNull

private val THUMBNAIL_SHAPE = RoundedCornerShape(12.dp)
private const val HERO_HEIGHT_DP = 200
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
 */
@Composable
fun HeroImage(
    imageUrl: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
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
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            HERO_SCRIM_START to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.75f),
                        ),
                    ),
            )
        }
        val onPicture = if (url != null) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = onPicture,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onPicture.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
