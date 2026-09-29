package com.mmg.manahub.feature.friends.presentation.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.mmg.manahub.R
import com.mmg.manahub.core.model.FriendStats
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.core.util.TimeAgoFormatter

private const val MAX_COLOR_SYMBOLS = 3

/** Stats tab of [FriendDetailScreen]: loading, error with retry, "no stats yet", or the dashboard. */
@Composable
fun FriendStatsTab(
    uiState: FriendDetailViewModel.UiState,
    onRetry: () -> Unit,
) {
    val stats = uiState.friendStats
    when {
        uiState.isLoadingStats -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MagicLoadingSpinner()
        }

        uiState.statsError -> FullErrorState(
            message = stringResource(R.string.friend_stats_error),
            retryLabel = stringResource(R.string.action_retry),
            onRetry = onRetry,
        )

        stats == null -> EmptyState(
            title = stringResource(R.string.friend_stats_no_data_title),
            subtitle = stringResource(R.string.friend_stats_no_data, uiState.friend?.nickname.orEmpty()),
            icon = Icons.Default.BarChart,
        )

        else -> StatsContent(stats = stats, currency = uiState.preferredCurrency)
    }
}

@Composable
private fun StatsContent(stats: FriendStats, currency: PreferredCurrency) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        StatsSection(title = stringResource(R.string.stats_section_inventory_value), icon = Icons.Default.Star) {
            InventoryValueGrid(stats = stats, currency = currency)
        }
        if (stats.favouriteColor != null || stats.mostValuableColor != null) {
            StatsSection(title = stringResource(R.string.friend_stats_section_colour_affinity), icon = Icons.Default.Palette) {
                ColourAffinityRow(stats = stats)
            }
        }
        StatsSection(title = stringResource(R.string.friend_stats_section_snapshot), icon = Icons.Default.Update) {
            SnapshotCard(updatedAt = stats.updatedAt)
        }
    }
}

@Composable
private fun InventoryValueGrid(stats: FriendStats, currency: PreferredCurrency) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val value = if (currency == PreferredCurrency.EUR) stats.totalValueEur else stats.totalValueUsd
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            StatTile(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.friend_stats_total_cards),
                value = stats.totalCards.toString(),
                valueColor = mc.textPrimary,
            )
            StatTile(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.friend_stats_unique_cards),
                value = stats.uniqueCards.toString(),
                valueColor = mc.textPrimary,
            )
        }
        StatTile(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.friend_stats_collection_value),
            value = PriceFormatter.format(value, currency),
            valueColor = mc.goldMtg,
        )
    }
}

@Composable
private fun ColourAffinityRow(stats: FriendStats) {
    val spacing = MaterialTheme.spacing
    val favColor = stats.favouriteColor
    val mvColor = stats.mostValuableColor
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (favColor != null) {
            ColourTile(Modifier.weight(1f), stringResource(R.string.friend_stats_favourite_color), favColor)
        }
        if (mvColor != null) {
            ColourTile(Modifier.weight(1f), stringResource(R.string.friend_stats_most_valuable_color), mvColor)
        }
        // A single tile keeps half the row instead of stretching full-width.
        if (favColor == null || mvColor == null) Spacer(modifier = Modifier.weight(1f))
    }
}

/** [colorCodes] is one WUBRG/C code per character, e.g. "W" or "UB". */
@Composable
private fun ColourTile(modifier: Modifier, label: String, colorCodes: String) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val codes = colorCodes.map { it.toString() }.filter { it in COLOR_NAME_RES }.take(MAX_COLOR_SYMBOLS)
    val names = codes.map { stringResource(COLOR_NAME_RES.getValue(it)) }
    StatsCard(modifier = modifier) {
        Text(text = label, style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            codes.forEach { ManaSymbolImage(token = it, size = spacing.xl) }
            Text(
                text = names.joinToString(" / ").ifBlank { stringResource(R.string.friend_stats_color_unknown) },
                style = MaterialTheme.magicTypography.bodyMedium,
                color = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SnapshotCard(updatedAt: Long) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    StatsCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Icon(Icons.Default.Update, contentDescription = null, tint = mc.textSecondary, modifier = Modifier.size(spacing.lg))
            Column {
                Text(
                    text = stringResource(R.string.friend_stats_last_updated),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                )
                Text(
                    text = TimeAgoFormatter.format(updatedAt),
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun StatsSection(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Icon(imageVector = icon, contentDescription = null, tint = mc.primaryAccent, modifier = Modifier.size(spacing.lg))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.magicTypography.labelLarge,
                color = mc.textSecondary,
                modifier = Modifier.semantics { heading() },
            )
        }
        content()
    }
}

@Composable
private fun StatsCard(modifier: Modifier, content: @Composable () -> Unit) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = modifier,
        shape = CardShape,
        color = mc.backgroundSecondary,
        // textDisabled keeps the outline visible on HallowedPrint, where surfaceVariant nearly vanishes.
        border = BorderStroke(spacing.xxs / 2, mc.textDisabled.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            content()
        }
    }
}

@Composable
private fun StatTile(modifier: Modifier, label: String, value: String, valueColor: Color) {
    StatsCard(modifier = modifier) {
        Text(text = label, style = MaterialTheme.magicTypography.labelSmall, color = MaterialTheme.magicColors.textSecondary)
        Text(
            text = value,
            style = MaterialTheme.magicTypography.titleLarge,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val COLOR_NAME_RES = mapOf(
    "W" to R.string.stats_color_white,
    "U" to R.string.stats_color_blue,
    "B" to R.string.stats_color_black,
    "R" to R.string.stats_color_red,
    "G" to R.string.stats_color_green,
    "C" to R.string.stats_color_colorless,
)
