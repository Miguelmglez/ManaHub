package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * A standard card representing a deck summary.
 * Used in deck-list screens and home widgets.
 *
 * @param deck            The deck summary data.
 * @param onClick         Callback when the whole card is tapped.
 * @param modifier        Optional [Modifier].
 * @param cardBackPainter Optional painter for the MTG card-back placeholder shown when
 *                        [DeckSummary.coverImageUrl] is null. Pass `painterResource(R.drawable.mtg_card_back)`
 *                        from the Android call site. When null, a solid [magicColors.surfaceVariant]
 *                        box is shown instead.
 * @param onDelete        Optional callback for deletion (shows a confirmation dialog).
 * @param onPlaytest      Optional callback to start a playtest session.
 * @param reduced         If true, renders a more compact version suitable for widgets/grids.
 * @param ownerName       Optional owner name for community decks.
 */
@Composable
fun DeckItem(
    deck: DeckSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardBackPainter: Painter? = null,
    onDelete: (() -> Unit)? = null,
    onPlaytest: (() -> Unit)? = null,
    reduced: Boolean = false,
    ownerName: String? = null,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (reduced) 0.dp else MaterialTheme.spacing.lg,
                vertical = if (reduced) 0.dp else MaterialTheme.spacing.sm
            ),
        colors = CardDefaults.cardColors(containerColor = mc.surface),
        shape = CardShape,
        border = BorderStroke(0.5.dp, mc.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // ── Art crop / placeholder ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                // Background image (cover art, card back, or solid fill)
                if (deck.coverImageUrl != null) {
                    AsyncImage(
                        model = deck.coverImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (cardBackPainter != null) {
                    Image(
                        painter = cardBackPainter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(mc.surfaceVariant)
                    )
                }

                // Bottom gradient — always shown to blend art into the card surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.6f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, mc.surface.copy(alpha = 0.9f)),
                            ),
                        ),
                )

                // ── Play button overlay ──────────────────────────────────────────
                if (onPlaytest != null && !reduced) {
                    Surface(
                        onClick = onPlaytest,
                        color = mc.primaryAccent,
                        shape = CircleShape,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(MaterialTheme.spacing.sm)
                            .size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Start Playtest",
                                tint = mc.background,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                // ── Format badge — top-right overlay ────────────────────────────
                val formatLower = deck.format.lowercase()
                val formatColor = when (formatLower) {
                    "commander" -> mc.goldMtg.copy(alpha = 0.9f)
                    "casual"    -> mc.primaryAccent.copy(alpha = 0.9f)
                    "draft"     -> mc.secondaryAccent.copy(alpha = 0.9f)
                    "standard"  -> mc.lifePositive.copy(alpha = 0.9f)
                    "modern"    -> mc.lifeNegative.copy(alpha = 0.9f)
                    "pioneer"   -> mc.manaU.copy(alpha = 0.9f)
                    else        -> mc.surfaceVariant.copy(alpha = 0.9f)
                }

                Surface(
                    color = formatColor,
                    shape = RoundedCornerShape(bottomStart = MaterialTheme.spacing.sm),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(
                        text = formatLower.replaceFirstChar { it.uppercase() },
                        style = if (reduced) ty.labelSmall else ty.labelLarge,
                        color = when (formatLower) {
                            "casual", "draft" -> mc.onAccent
                            "commander", "standard", "modern", "pioneer" -> mc.background
                            else -> mc.textPrimary
                        },
                        modifier = Modifier.padding(
                            horizontal = if (reduced) MaterialTheme.spacing.sm else MaterialTheme.spacing.md,
                            vertical = if (reduced) MaterialTheme.spacing.xxs else MaterialTheme.spacing.xs
                        ),
                    )
                }
            }

            // ── Info row ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (reduced) MaterialTheme.spacing.sm else MaterialTheme.spacing.md,
                        end = MaterialTheme.spacing.xs,
                        top = MaterialTheme.spacing.sm,
                        bottom = MaterialTheme.spacing.sm,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
                ) {
                    // Deck name
                    Text(
                        text = deck.name,
                        style = if (reduced) ty.titleMedium else ty.titleLarge,
                        color = mc.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (ownerName != null) {
                        Text(
                            text = if (reduced) ownerName else "by $ownerName",
                            style = ty.labelSmall,
                            color = mc.textDisabled,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (!reduced) {
                        Spacer(Modifier.height(MaterialTheme.spacing.xs))

                        // Card count + last-updated date
                        // Card count + last-updated date
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CardCountBadge(count = deck.cardCount)
                            Text(
                                text = formatEpochMillis(deck.updatedAt),
                                style = ty.bodyMedium,
                                color = mc.textDisabled,
                            )
                        }

                        // Mana identity symbols
                        Spacer(Modifier.height(MaterialTheme.spacing.xs))
                        if (deck.colorIdentity.isNotEmpty()) {
                            ColorIdentityRow(colorIdentity = deck.colorIdentity, size = 18.dp)
                        } else {
                            Spacer(Modifier.height(18.dp))
                        }
                    } else {
                        Spacer(Modifier.height(MaterialTheme.spacing.xs))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Card count with icon
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Style,
                                    contentDescription = null,
                                    tint = mc.textSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = deck.cardCount.toString(),
                                    style = ty.labelSmall,
                                    color = mc.textSecondary,
                                    maxLines = 1
                                )
                            }

                            // Mana identity symbols
                            if (deck.colorIdentity.isNotEmpty()) {
                                ColorIdentityRow(colorIdentity = deck.colorIdentity, size = 14.dp)
                            } else {
                                Spacer(Modifier.height(14.dp))
                            }
                        }
                    }
                }

                if (!reduced) {
                    if (onDelete != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = mc.textDisabled,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Delete deck",
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                )
            },
            text = {
                Text(
                    text = "Delete \"${deck.name}\"? This cannot be undone.",
                    style = ty.bodyMedium,
                    color = mc.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text(
                        text = "Delete",
                        style = ty.labelLarge,
                        color = mc.lifeNegative,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(
                        text = "Cancel",
                        style = ty.labelLarge,
                        color = mc.primaryAccent,
                    )
                }
            },
            containerColor = mc.backgroundSecondary,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Internal helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Formats an epoch-millis timestamp as a short date string, e.g. "Jun 29, 2026".
 * Uses [kotlinx.datetime] so it compiles on all KMP targets without JVM-only APIs.
 */
private fun formatEpochMillis(epochMillis: Long): String {
    val local = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val monthAbbr = local.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$monthAbbr ${local.dayOfMonth}, ${local.year}"
}

/** Chip showing the number of cards in a deck. */
@Composable
private fun CardCountBadge(count: Int) {
    val mc = MaterialTheme.magicColors
    Surface(
        color = mc.secondaryAccent.copy(alpha = 0.12f),
        shape = ChipShape,
    ) {
        Text(
            text = "$count cards",
            style = MaterialTheme.magicTypography.labelSmall,
            color = mc.secondaryAccent,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.sm,
                vertical = MaterialTheme.spacing.xxs
            ),
        )
    }
}

/** Renders the color identity of a deck as a row of mana symbols. */
@Composable
private fun ColorIdentityRow(colorIdentity: Set<String>, size: Dp = 18.dp) {
    val wubrgOrder = listOf("W", "U", "B", "R", "G")
    val sorted = colorIdentity.sortedBy { code ->
        val idx = wubrgOrder.indexOf(code.uppercase())
        if (idx >= 0) idx else 99
    }

    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        sorted.forEach { code ->
            ManaSymbolImage(token = code, size = size)
        }
        if (sorted.isEmpty()) {
            ManaSymbolImage(token = "C", size = size)
        }
    }
}
