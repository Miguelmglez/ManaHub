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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mmg.manahub.core.domain.model.news.NewsItem
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.TimeAgoFormatter

/**
 * Unified component for News items (Articles and Videos).
 * Supports both [NewsItemOrientation.HORIZONTAL] (list style) and [NewsItemOrientation.VERTICAL] (grid/widget style).
 */
@Composable
fun NewsItemCard(
    item: NewsItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    orientation: NewsItemOrientation = NewsItemOrientation.HORIZONTAL,
    languageBadge: String? = null,
    showDescription: Boolean = true,
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
        .clip(RoundedCornerShape(12.dp))
        .background(mc.surface)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )

    if (orientation == NewsItemOrientation.HORIZONTAL) {
        HorizontalNewsLayout(
            item = item,
            languageBadge = languageBadge,
            showDescription = showDescription,
            modifier = containerModifier.padding(12.dp)
        )
    } else {
        VerticalNewsLayout(
            item = item,
            languageBadge = languageBadge,
            modifier = containerModifier
        )
    }
}

@Composable
private fun HorizontalNewsLayout(
    item: NewsItem,
    languageBadge: String?,
    showDescription: Boolean,
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
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
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
    languageBadge: String?,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography

    Column(modifier = modifier) {
        ThumbnailBox(
            imageUrl = item.imageUrl,
            isVideo = item is NewsItem.Video,
            duration = (item as? NewsItem.Video)?.duration,
            languageBadge = languageBadge,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
        )

        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = item.title,
                style = mt.bodyLarge,
                color = mc.textPrimary,
                maxLines = 2,
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
    duration: String? = null,
    languageBadge: String? = null,
) {
    val mt = MaterialTheme.magicTypography
    Box(modifier = modifier) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(if (modifier.toString().contains("96.dp")) 32.dp else 48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(if (modifier.toString().contains("96.dp")) 20.dp else 28.dp),
                )
            }
        }
        
        if (languageBadge != null) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                LanguageBadge(languageBadge)
            }
        }

        if (duration != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = duration,
                    style = mt.labelSmall,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun LanguageBadge(code: String) {
    val mc = MaterialTheme.magicColors
    Surface(
        shape = RoundedCornerShape(4.dp),
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

enum class NewsItemOrientation { HORIZONTAL, VERTICAL }
