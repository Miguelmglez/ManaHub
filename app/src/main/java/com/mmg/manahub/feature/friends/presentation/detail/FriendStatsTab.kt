package com.mmg.manahub.feature.friends.presentation.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.TimeAgoFormatter
import com.mmg.manahub.core.model.FriendStats
import java.util.Locale

/**
 * Displays the friend's collection statistics inside the Stats tab of [FriendDetailScreen].
 *
 * Three rendering states:
 * 1. Loading — shows a centred [CircularProgressIndicator].
 * 2. Error   — shows an error message and a retry button.
 * 3. Loaded  — shows a scrollable stats dashboard. If the server returned no row
 *              ([UiState.friendStats] is null after a successful fetch) a "no data yet"
 *              message is shown instead.
 */
@Composable
fun FriendStatsTab(
    uiState: FriendDetailViewModel.UiState,
    onRetry: () -> Unit,
) {
    val mc = MaterialTheme.magicColors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        when {
            uiState.isLoadingStats -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = mc.primaryAccent)
                }
            }

            uiState.statsError -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.friend_stats_error),
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = mc.primaryAccent,
                            contentColor = mc.background,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.action_retry),
                            style = MaterialTheme.magicTypography.labelLarge,
                        )
                    }
                }
            }

            uiState.friendStats == null -> {
                val nickname = uiState.friend?.nickname ?: ""
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.friend_stats_no_data, nickname),
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }

            else -> StatsContent(stats = uiState.friendStats)
        }
    }
}

@Composable
private fun StatsContent(stats: FriendStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        // ── Section A — Inventory & Value ─────────────────────────────────────
        StatsSection(
            title = stringResource(R.string.stats_section_inventory_value),
            icon = Icons.Default.Star,
        ) {
            InventoryValueGrid(stats = stats)
        }

        // ── Section B — Colour Affinity ───────────────────────────────────────
        if (stats.favouriteColor != null || stats.mostValuableColor != null) {
            StatsSection(
                title = stringResource(R.string.friend_stats_section_colour_affinity),
                icon = Icons.Default.Palette,
            ) {
                ColourAffinityRow(stats = stats)
            }
        }

        // ── Section C — Snapshot ──────────────────────────────────────────────
        StatsSection(
            title = stringResource(R.string.friend_stats_section_snapshot),
            icon = Icons.Default.Update,
        ) {
            SnapshotCard(updatedAt = stats.updatedAt)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InventoryValueGrid(stats: FriendStats) {
    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.friend_stats_value_eur),
                value = "€ ${"%.2f".format(stats.totalValueEur)}",
                valueColor = mc.goldMtg,
            )
            StatTile(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.friend_stats_value_usd),
                value = "$ ${"%.2f".format(stats.totalValueUsd)}",
                valueColor = mc.goldMtg,
            )
        }
    }
}

@Composable
private fun ColourAffinityRow(stats: FriendStats) {
    val unknownLabel = stringResource(R.string.friend_stats_color_unknown)
    val favColor = stats.favouriteColor
    val mvColor = stats.mostValuableColor
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (favColor != null) {
            ColourTile(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.friend_stats_favourite_color),
                colorCode = favColor,
                unknownLabel = unknownLabel,
            )
        }
        if (mvColor != null) {
            ColourTile(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.friend_stats_most_valuable_color),
                colorCode = mvColor,
                unknownLabel = unknownLabel,
            )
        }
        // If only one colour field is present, fill the other half with a spacer
        // so the single tile doesn't stretch full-width.
        if (favColor == null || mvColor == null) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ColourTile(
    modifier: Modifier = Modifier,
    label: String,
    colorCode: String,
    unknownLabel: String,
) {
    val mc = MaterialTheme.magicColors
    val displayName = colorCode.toColorDisplayName(Locale.getDefault())
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = mc.backgroundSecondary),
        border = BorderStroke(1.dp, mc.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textSecondary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ManaSymbolImage(token = colorCode, size = 28.dp)
                Text(
                    text = displayName.ifBlank { unknownLabel },
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SnapshotCard(updatedAt: Long) {
    val mc = MaterialTheme.magicColors
    val timeAgo = TimeAgoFormatter.format(updatedAt, Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = mc.backgroundSecondary),
        border = BorderStroke(1.dp, mc.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Update,
                contentDescription = null,
                tint = mc.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Column {
                Text(
                    text = stringResource(R.string.friend_stats_last_updated),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                )
                Text(
                    text = timeAgo,
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textPrimary,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header scaffold
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = title.uppercase(Locale.getDefault()),
                style = MaterialTheme.magicTypography.labelLarge,
                color = mc.textSecondary,
                letterSpacing = 1.5.sp,
            )
        }
        content()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Primitive tile
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueColor: Color,
) {
    val mc = MaterialTheme.magicColors
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = mc.backgroundSecondary),
        border = BorderStroke(1.dp, mc.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textSecondary,
            )
            Text(
                text = value,
                style = MaterialTheme.magicTypography.lifeNumberMd.copy(fontSize = 22.sp),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun String.toColorDisplayName(locale: Locale): String = when (locale.language) {
    "es" -> when (this) {
        "W" -> "Blanco"
        "U" -> "Azul"
        "B" -> "Negro"
        "R" -> "Rojo"
        "G" -> "Verde"
        "C" -> "Incoloro"
        else -> ""
    }
    "de" -> when (this) {
        "W" -> "Weiß"
        "U" -> "Blau"
        "B" -> "Schwarz"
        "R" -> "Rot"
        "G" -> "Grün"
        "C" -> "Farblos"
        else -> ""
    }
    else -> when (this) {
        "W" -> "White"
        "U" -> "Blue"
        "B" -> "Black"
        "R" -> "Red"
        "G" -> "Green"
        "C" -> "Colorless"
        else -> ""
    }
}
