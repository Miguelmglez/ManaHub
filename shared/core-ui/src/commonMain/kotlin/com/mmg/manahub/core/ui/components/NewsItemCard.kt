package com.mmg.manahub.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.news_content_article
import com.mmg.manahub.core.ui.news_content_video
import com.mmg.manahub.core.ui.news_item_more_a11y
import com.mmg.manahub.core.ui.news_item_save_a11y
import com.mmg.manahub.core.ui.news_item_unsave_a11y
import com.mmg.manahub.core.ui.theme.CardCornerRadius
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ExtraSmallCardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.onOverlayScrim
import com.mmg.manahub.core.ui.theme.overlayScrim
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.TimeAgoFormatter
import org.jetbrains.compose.resources.stringResource

/**
 * Unified component for News items (Articles and Videos).
 * Supports both [NewsItemOrientation.HORIZONTAL] (list style) and [NewsItemOrientation.VERTICAL] (grid/widget style).
 *
 * @param placeholderPainter Optional painter used as placeholder, error, and fallback for the
 *   thumbnail [AsyncImage]. On Android callers typically pass `painterResource(R.drawable.mtg_card_back)`;
 *   on web (or when `null`) the image simply shows nothing while loading.
 * @param titleMinLines minimum title lines; a row of equal-height cards passes the title's
 *   `maxLines` (2) so short titles reserve the same space as long ones.
 * @param isSaved bookmark state; the bookmark button shows only when [onToggleSave] is set (horizontal layout).
 * @param overflowMenu items of the "more" menu anchored to the card; the button shows only when set.
 * @param showContentType whether to include ARTICLE/VIDEO in the metadata footer.
 * @param compact reduces the horizontal layout's height requirement, padding, and title size.
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
    showContentType: Boolean = false,
    titleMinLines: Int = 1,
    compact: Boolean = false,
    isSaved: Boolean = false,
    onToggleSave: (() -> Unit)? = null,
    overflowMenu: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
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
            showContentType = showContentType,
            titleMinLines = titleMinLines,
            compact = compact,
            isSaved = isSaved,
            onToggleSave = onToggleSave,
            overflowMenu = overflowMenu,
            modifier = containerModifier
        )
    } else {
        VerticalNewsLayout(
            item = item,
            placeholderPainter = placeholderPainter,
            languageBadge = languageBadge,
            showContentType = showContentType,
            titleMinLines = titleMinLines,
            isSaved = isSaved,
            onToggleSave = onToggleSave,
            overflowMenu = overflowMenu,
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
    showContentType: Boolean,
    titleMinLines: Int,
    compact: Boolean,
    isSaved: Boolean,
    onToggleSave: (() -> Unit)?,
    overflowMenu: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography
    val typeLabel = if (showContentType) {
        stringResource(if (item is NewsItem.Video) Res.string.news_content_video else Res.string.news_content_article)
    } else {
        null
    }
    val timeStr = TimeAgoFormatter.format(item.publishedAt)

    Box(modifier = modifier.heightIn(min = if (compact) 0.dp else 180.dp)) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            placeholder = placeholderPainter,
            error = placeholderPainter,
            fallback = placeholderPainter,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            mc.overlayScrim.copy(alpha = 0.6f),
                            mc.overlayScrim.copy(alpha = 0.9f),
                            mc.overlayScrim.copy(alpha = 1f)
                        )
                    )
                )
        )
        if (item is NewsItem.Video) {
            VideoPlayIndicator(
                compact = compact,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(MaterialTheme.spacing.sm),
            )
        }
        NewsItemActions(
            isSaved = isSaved,
            onToggleSave = onToggleSave,
            overflowMenu = overflowMenu,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(if (compact) MaterialTheme.spacing.sm else MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
        ) {

            Text(
                text = item.title,
                style = if (compact) mt.bodyMedium else mt.titleMedium,
                color = mc.onOverlayScrim,
                minLines = titleMinLines.coerceAtMost(2),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (showDescription && item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = mt.bodySmall,
                    color = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs, Alignment.Start),
            ) {
                Text(
                    text = if (typeLabel == null) {
                        "${item.sourceName} · $timeStr"
                    } else {
                        "$typeLabel · ${item.sourceName} · $timeStr"
                    },
                    style = mt.labelSmall,
                    color = mc.secondaryAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (languageBadge != null) {
                    LanguageBadge(languageBadge)
                }
            }
        }
    }
}

@Composable
private fun NewsItemActions(
    isSaved: Boolean,
    onToggleSave: (() -> Unit)?,
    overflowMenu: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier = modifier.padding(MaterialTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onToggleSave != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(mc.overlayScrim.copy(alpha = 0.65f))
                    .clickable(
                        role = Role.Button,
                        onClick = onToggleSave,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = stringResource(if (isSaved) Res.string.news_item_unsave_a11y else Res.string.news_item_save_a11y),
                    tint = mc.onOverlayScrim,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (overflowMenu != null) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(mc.overlayScrim.copy(alpha = 0.65f))
                        .clickable(
                            role = Role.Button,
                            onClick = { expanded = true },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(Res.string.news_item_more_a11y),
                        tint = mc.onOverlayScrim,
                        modifier = Modifier.size(16.dp),
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = mc.surface,
                ) {
                    overflowMenu { expanded = false }
                }
            }
        }
    }
}

@Composable
private fun VerticalNewsLayout(
    item: NewsItem,
    placeholderPainter: Painter?,
    languageBadge: String?,
    showContentType: Boolean,
    titleMinLines: Int,
    modifier: Modifier = Modifier,
    isSaved: Boolean = false,
    onToggleSave: (() -> Unit)? = null,
    overflowMenu: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography
    val typeLabel = if (showContentType) {
        stringResource(if (item is NewsItem.Video) Res.string.news_content_video else Res.string.news_content_article)
    } else {
        null
    }
    val timeStr = TimeAgoFormatter.format(item.publishedAt)

    Column(modifier = modifier) {
        ThumbnailBox(
            imageUrl = item.imageUrl,
            isVideo = item is NewsItem.Video,
            duration = (item as? NewsItem.Video)?.duration,
            placeholderPainter = placeholderPainter,
            isSaved = isSaved,
            onToggleSave = onToggleSave,
            overflowMenu = overflowMenu,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = CardCornerRadius, topEnd = CardCornerRadius))
        )

        Column(modifier = Modifier.padding(MaterialTheme.spacing.sm)) {
            Text(
                text = item.title,
                style = mt.bodyMedium,
                color = mc.textPrimary,
                maxLines = 2,
                minLines = titleMinLines.coerceAtMost(2),
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs, Alignment.Start),
            ) {
                Text(
                    text = if (typeLabel == null) {
                        "${item.sourceName}  ·  $timeStr"
                    } else {
                        "$typeLabel  ·  ${item.sourceName}  ·  $timeStr"
                    },
                    style = mt.labelSmall,
                    color = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (languageBadge != null) {
                    LanguageBadge(languageBadge)
                }
            }
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
    isSaved: Boolean = false,
    onToggleSave: (() -> Unit)? = null,
    overflowMenu: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    compact: Boolean = true,
) {
    val mt = MaterialTheme.magicTypography
    val mc = MaterialTheme.magicColors
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            mc.overlayScrim.copy(alpha = 0.6f),
                            mc.overlayScrim.copy(alpha = 0.9f),
                            mc.overlayScrim.copy(alpha = 1f)
                        )
                    )
                )
        )
        if (isVideo) {
            VideoPlayIndicator(
                compact = compact,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(MaterialTheme.spacing.sm),
            )
        }

        NewsItemActions(
            isSaved = isSaved,
            onToggleSave = onToggleSave,
            overflowMenu = overflowMenu,
            modifier = Modifier.align(Alignment.TopEnd),
        )

        if (duration != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(ExtraSmallCardShape)
                    .background(mc.overlayScrim.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = duration,
                    style = mt.labelSmall,
                    color = mc.onOverlayScrim
                )
            }
        }
    }
}

@Composable
private fun VideoPlayIndicator(
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Box(
        modifier = modifier.size(if (compact) 32.dp else 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(6.dp)
                .background(mc.overlayScrim.copy(alpha = 0.65f), CircleShape),
        )
        Icon(
            imageVector = PlayArrowIcon,
            contentDescription = null,
            tint = mc.onOverlayScrim,
            modifier = Modifier.size(if (compact) 18.dp else 28.dp),
        )
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

/** Layout orientation for [NewsItemCard]. */
enum class NewsItemOrientation { HORIZONTAL, VERTICAL }
