package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.core.ui.theme.CardCornerRadius
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import kotlinx.datetime.LocalDate

/**
 * Shared component representing an MTG set in a draft context.
 * Supports standard List Mode (isCompact = false) and Compact Grid/Widget Mode (isCompact = true).
 *
 * SVG set icons are decoded by the global ImageLoader configuration
 * (SvgDecoder on Android, native browser rendering on web).
 */
@Composable
fun DraftSetCard(
    set: DraftSet,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val fallbackPainter = rememberVectorPainter(SetSymbolFallbackIcon)

    if (isCompact) {
        Surface(
            onClick = onClick,
            shape = CardShape,
            color = mc.surface,
            modifier = modifier
                .width(160.dp)
                .height(110.dp)
                .border(1.dp, mc.surfaceVariant.copy(alpha = 0.5f), CardShape),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Background: Set symbol SVG watermark
                AsyncImage(
                    model = set.iconSvgUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .align(Alignment.CenterEnd)
                        .offset(x = 12.dp)
                        .alpha(0.08f),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(mc.textPrimary),
                    error = fallbackPainter,
                    fallback = fallbackPainter,
                )

                // Content Layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Top Row: Left small set symbol (24.dp), Right draft star icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = set.iconSvgUri,
                            contentDescription = set.name,
                            modifier = Modifier.size(24.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(mc.textPrimary),
                            error = fallbackPainter,
                            fallback = fallbackPainter,
                        )
                        if (set.boosterVersion != null) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = mc.goldMtg,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    // Middle: Full set name
                    Text(
                        text = set.name,
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Bottom Row: Release date on left, Set code on right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatReleaseDate(set.releasedAt),
                            style = ty.labelSmall,
                            color = mc.textSecondary,
                            maxLines = 1,
                        )
                        Text(
                            text = set.code.uppercase(),
                            style = ty.labelSmall,
                            color = mc.primaryAccent,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    } else {
        Surface(
            onClick = onClick,
            shape = CardShape,
            color = mc.surface,
            modifier = modifier
                .fillMaxWidth()
                .border(1.dp, mc.surfaceVariant.copy(alpha = 0.5f), CardShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
            ) {
                val setIconPainter = rememberAsyncImagePainter(
                    model = set.iconSvgUri,
                    error = fallbackPainter,
                    fallback = fallbackPainter,
                )

                if (!set.setImageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(set.setImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.35f),
                        contentScale = ContentScale.Crop,
                        error = setIconPainter,
                        fallback = setIconPainter,
                    )
                } else {
                    // Background set symbol backdrop (watermark fallback)
                    AsyncImage(
                        model = set.iconSvgUri,
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .padding(12.dp)
                            .alpha(0.12f),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(mc.textPrimary),
                        error = fallbackPainter,
                        fallback = fallbackPainter,
                    )
                }

                // Gradient overlay
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    mc.surface.copy(alpha = 0.88f),
                                    mc.surface.copy(alpha = 0.35f),
                                ),
                            ),
                        ),
                )

                // Left accent bar
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(
                            mc.primaryAccent,
                            shape = RoundedCornerShape(
                                topStart = CardCornerRadius,
                                bottomStart = CardCornerRadius,
                            ),
                        ),
                )

                // Main Row content
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left: Set symbol SVG AsyncImage (38.dp x 38.dp), tinted with mc.textPrimary
                    AsyncImage(
                        model = set.iconSvgUri,
                        contentDescription = set.name,
                        modifier = Modifier.size(38.dp),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(mc.textPrimary),
                        error = fallbackPainter,
                        fallback = fallbackPainter,
                    )

                    // Middle Column
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, end = 4.dp),
                    ) {
                        // Top Line (Full Width for Set Name)
                        Text(
                            text = set.name,
                            style = ty.titleMedium,
                            color = mc.textPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Bottom Line
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            // Left: Release Date
                            Text(
                                text = formatReleaseDate(set.releasedAt),
                                style = ty.bodySmall,
                                color = mc.textSecondary,
                            )

                            // Right: Draft star icon + Set Code Pill Chip
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (set.boosterVersion != null) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = mc.goldMtg,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(ChipShape)
                                        .background(mc.primaryAccent.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = set.code.uppercase(),
                                        style = ty.labelSmall,
                                        color = mc.primaryAccent,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formats an ISO-8601 date string (e.g. "2025-07-25") into "Jul 2025" for display.
 * Falls back to the raw string on any parse failure.
 */
private fun formatReleaseDate(dateStr: String): String {
    return try {
        val date = LocalDate.parse(dateStr)
        val month = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        "$month ${date.year}"
    } catch (_: Exception) {
        dateStr
    }
}
