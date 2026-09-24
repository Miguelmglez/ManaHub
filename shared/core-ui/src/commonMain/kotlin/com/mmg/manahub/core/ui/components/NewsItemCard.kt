package com.mmg.manahub.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.ui.theme.CardCornerRadius
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.ExtraSmallCardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.TimeAgoFormatter

/**
 * Unified component for News items (Articles and Videos).
 * Supports both [NewsItemOrientation.HORIZONTAL] (list style) and [NewsItemOrientation.VERTICAL] (grid/widget style).
 *
 * @param placeholderPainter Optional painter used as placeholder, error, and fallback for the
 *   thumbnail [AsyncImage]. On Android callers typically pass `painterResource(R.drawable.mtg_card_back)`;
 *   on web (or when `null`) the image simply shows nothing while loading.
 * @param titleMinLines minimum title lines; a row of equal-height cards passes the title's
 *   `maxLines` (2) so short titles reserve the same space as long ones.
 */
@Composable
fun NewsItemCard(
    item: NewsItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    orientation: NewsItemOrientation = NewsItemOrientation.HORIZONTAL,
    placeholderPainter: Painter? = null,
    languageBadge: String? = null,
    showDescription: Boolean = true,
    titleMinLines: Int = 1,
) {
    val mc = MaterialTheme.magicColors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "Scale")

    val containerModifier = modifier
        .fillMaxWidth()
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(CardShape)
        .background(mc.surface)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )

    if (orientation == NewsItemOrientation.HORIZONTAL) {
        HorizontalNewsLayout(
            item = item,
            placeholderPainter = placeholderPainter,
            languageBadge = languageBadge,
            showDescription = showDescription,
            titleMinLines = titleMinLines,
            modifier = containerModifier.padding(MaterialTheme.spacing.md)
        )
    } else {
        VerticalNewsLayout(
            item = item,
            placeholderPainter = placeholderPainter,
            languageBadge = languageBadge,
            titleMinLines = titleMinLines,
            modifier = containerModifier
        )
    }
}

@Composable
private fun HorizontalNewsLayout(
    item: NewsItem,
    placeholderPainter: Painter?,
    languageBadge: String?,
    showDescription: Boolean,
    titleMinLines: Int,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography

    Row(modifier = modifier) {
        if (item.imageUrl != null) {
            ThumbnailBox(
                imageUrl = item.imageUrl,
                isVideo = item is NewsItem.Video,
                duration = (item as? NewsItem.Video)?.duration,
                placeholderPainter = placeholderPainter,
                compact = true,
                modifier = Modifier
                    .size(96.dp)
                    .clip(ChipShape)
            )
            Spacer(Modifier.width(MaterialTheme.spacing.md))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.title,
                    style = mt.bodyLarge,
                    color = mc.textPrimary,
                    maxLines = 2,
                    minLines = titleMinLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (languageBadge != null) {
                    LanguageBadge(languageBadge)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${item.sourceName}  ·  ${TimeAgoFormatter.format(item.publishedAt)}",
                style = mt.labelSmall,
                color = mc.textSecondary,
                maxLines = 1,
            )
            if (showDescription && item.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.description,
                    style = mt.bodySmall,
                    color = mc.textDisabled,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun VerticalNewsLayout(
    item: NewsItem,
    placeholderPainter: Painter?,
    languageBadge: String?,
    titleMinLines: Int,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography

    Column(modifier = modifier) {
        ThumbnailBox(
            imageUrl = item.imageUrl,
            isVideo = item is NewsItem.Video,
            duration = (item as? NewsItem.Video)?.duration,
            placeholderPainter = placeholderPainter,
            languageBadge = languageBadge,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = CardCornerRadius, topEnd = CardCornerRadius))
        )

        Column(modifier = Modifier.padding(MaterialTheme.spacing.md)) {
            Text(
                text = item.title,
                style = mt.bodyLarge,
                color = mc.textPrimary,
                maxLines = 2,
                minLines = titleMinLines,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${item.sourceName}  ·  ${TimeAgoFormatter.format(item.publishedAt)}",
                style = mt.labelSmall,
                color = mc.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ThumbnailBox(
    imageUrl: String?,
    isVideo: Boolean,
    modifier: Modifier = Modifier,
    placeholderPainter: Painter? = null,
    duration: String? = null,
    languageBadge: String? = null,
    compact: Boolean = false,
) {
    val mt = MaterialTheme.magicTypography
    Box(modifier = modifier) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            placeholder = placeholderPainter,
            error = placeholderPainter,
            fallback = placeholderPainter,
            modifier = Modifier.fillMaxSize(),
        )
        if (isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(if (compact) 32.dp else 48.dp)
                    .clip(CircleShape)
                    .background(VideoScrim.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = PlayArrowIcon,
                    contentDescription = null,
                    tint = VideoScrimContent,
                    modifier = Modifier.size(if (compact) 20.dp else 28.dp),
                )
            }
        }

        if (languageBadge != null) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(MaterialTheme.spacing.sm)) {
                LanguageBadge(languageBadge)
            }
        }

        if (duration != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(ExtraSmallCardShape)
                    .background(VideoScrim.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = duration,
                    style = mt.labelSmall,
                    color = VideoScrimContent
                )
            }
        }
    }
}

@Composable
private fun LanguageBadge(code: String) {
    val flag = com.mmg.manahub.core.util.CardConstants.getFlag(code)
    if (flag.isNotEmpty()) {
        Text(
            text = flag,
            style = MaterialTheme.magicTypography.labelLarge.copy(fontSize = 14.sp)
        )
    } else {
        val mc = MaterialTheme.magicColors
        Surface(
            shape = ExtraSmallCardShape,
            color = mc.primaryAccent.copy(alpha = 0.15f),
        ) {
            Text(
                text = code.uppercase(),
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.primaryAccent,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

// Scrims sit on arbitrary thumbnail art, so they stay theme-independent on purpose (not tokens).
private val VideoScrim = Color.Black
private val VideoScrimContent = Color.White

/** Layout orientation for [NewsItemCard]. */
enum class NewsItemOrientation { HORIZONTAL, VERTICAL }
