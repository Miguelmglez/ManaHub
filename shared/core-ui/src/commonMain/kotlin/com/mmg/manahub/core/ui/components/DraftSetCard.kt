package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import kotlinx.datetime.LocalDate

/**
 * Shared component representing an MTG set in a draft context.
 * Used in both the full Draft screen and the Home dashboard widget.
 *
 * SVG set icons are decoded by the global ImageLoader configuration
 * (SvgDecoder on Android, native browser rendering on web).
 */
@Composable
fun DraftSetCard(
    set: DraftSet,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = set.iconSvgUri,
                contentDescription = set.name,
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(colors.textPrimary),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = set.name,
                style = typography.labelLarge,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatReleaseDate(set.releasedAt),
                    style = typography.labelSmall,
                    color = colors.textSecondary,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.primaryAccent.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = set.code.uppercase(),
                        style = typography.labelSmall,
                        color = colors.primaryAccent,
                    )
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
