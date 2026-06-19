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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.DeckSummary
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A standard card representing a deck summary.
 * Used in [com.mmg.manahub.feature.decks.presentation.DeckListScreen] and 
 * [com.mmg.manahub.feature.home.presentation.HomeWidgets].
 *
 * @param deck       The deck summary data.
 * @param onClick    Callback when the whole card is tapped.
 * @param onDelete   Optional callback for deletion (shows a confirmation dialog).
 * @param onPlaytest Optional callback to start a playtest session.
 * @param reduced    If true, renders a more compact version suitable for widgets/grids.
 */
@Composable
fun DeckItem(
    deck: DeckSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    onPlaytest: (() -> Unit)? = null,
    reduced: Boolean = false,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (reduced) 0.dp else 16.dp, 
                vertical = if (reduced) 0.dp else 6.dp
            ),
        colors = CardDefaults.cardColors(containerColor = mc.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, mc.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // ── Art crop / placeholder ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            ) {
                if (deck.coverImageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(deck.coverImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Bottom gradient to blend into the card surface
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
                } else {
                    // MTG Card Back Placeholder
                    Image(
                        painter = painterResource(id = R.drawable.mtg_card_back),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Bottom gradient to blend into the card surface
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
                }
                
                // Format badge — top-right overlay
                val formatLower = deck.format.lowercase()
                val formatColor = when (formatLower) {
                    "commander" -> mc.goldMtg.copy(alpha = 0.9f)
                    "casual" -> mc.primaryAccent.copy(alpha = 0.9f)
                    "draft" -> mc.secondaryAccent.copy(alpha = 0.9f)
                    "standard" -> mc.lifePositive.copy(alpha = 0.9f)
                    "modern" -> mc.lifeNegative.copy(alpha = 0.9f)
                    else -> mc.surfaceVariant.copy(alpha = 0.9f)
                }

                Surface(
                    color = formatColor,
                    shape = RoundedCornerShape(bottomStart = 8.dp),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(
                        text = formatLower.replaceFirstChar { it.uppercase() },
                        style = if (reduced) ty.labelSmall else ty.labelLarge,
                        color = if (formatLower == "draft" || formatLower == "casual") mc.onAccent else mc.background,
                        modifier = Modifier.padding(
                            horizontal = if (reduced) 6.dp else 10.dp, 
                            vertical = if (reduced) 2.dp else 4.dp
                        ),
                    )
                }
            }

            // ── Info row ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (reduced) 8.dp else 12.dp,
                        end = if (reduced) 4.dp else 4.dp,
                        top = if (reduced) 6.dp else 8.dp,
                        bottom = if (reduced) 6.dp else 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Deck name
                    Text(
                        text = deck.name,
                        style = if (reduced) ty.titleMedium else ty.titleLarge,
                        color = mc.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    
                    if (!reduced) {
                        Spacer(Modifier.height(4.dp))

                        // Card count + updated date
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CardCountBadge(count = deck.cardCount)
                            Text(
                                text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                    .format(Date(deck.updatedAt)),
                                style = ty.bodyMedium,
                                color = mc.textDisabled,
                            )
                        }

                        // Mana identity symbols
                        Spacer(Modifier.height(4.dp))
                        if (deck.colorIdentity.isNotEmpty()) {
                            ColorIdentityRow(
                                colorIdentity = deck.colorIdentity, 
                                size = 18.dp
                            )
                        } else {
                            Spacer(Modifier.height(18.dp))
                        }
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Card count with icon
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                ColorIdentityRow(
                                    colorIdentity = deck.colorIdentity, 
                                    size = 14.dp
                                )
                            } else {
                                Spacer(Modifier.height(14.dp))
                            }
                        }
                    }
                }

                if (!reduced) {
                    // Playtest button
                    if (onPlaytest != null) {
                        IconButton(
                            onClick = onPlaytest,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.playtest_action_start),
                                tint = mc.primaryAccent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // Delete button
                    if (onDelete != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
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
                    text = stringResource(R.string.decklist_delete_title),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.decklist_delete_message, deck.name),
                    style = ty.bodyMedium,
                    color = mc.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        style = ty.labelLarge,
                        color = mc.lifeNegative,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = ty.labelLarge,
                        color = mc.primaryAccent,
                    )
                }
            },
            containerColor = mc.backgroundSecondary,
        )
    }
}

@Composable
private fun CardCountBadge(count: Int) {
    val mc = MaterialTheme.magicColors
    Surface(
        color = mc.secondaryAccent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = stringResource(R.string.decklist_card_count, count),
            style = MaterialTheme.magicTypography.labelSmall,
            color = mc.secondaryAccent,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Renders the color identity of a deck as a row of mana symbols. */
@Composable
private fun ColorIdentityRow(colorIdentity: Set<String>, size: Dp = 18.dp) {
    val wubrgOrder = listOf("W", "U", "B", "R", "G")
    val sorted = colorIdentity
        .sortedBy { code ->
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

// ─────────────────────────────────────────────────────────────────────────────
//  Previews
// ─────────────────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Full Deck Item")
@Composable
private fun DeckItemPreview() {
    val deck = DeckSummary(
        id = "1",
        name = "Sauron's Shadow",
        description = "A powerful Grixis deck.",
        format = "commander",
        coverCardId = null,
        createdAt = 0L,
        updatedAt = System.currentTimeMillis(),
        cardCount = 100,
        colorIdentity = setOf("U", "B", "R"),
        coverImageUrl = null
    )
    MaterialTheme {
        Box(Modifier.padding(16.dp)) {
            DeckItem(
                deck = deck,
                onClick = {},
                onDelete = {},
                onPlaytest = {}
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Reduced Deck Item")
@Composable
private fun DeckItemReducedPreview() {
    val deck = DeckSummary(
        id = "2",
        name = "Mono Red Aggro",
        description = null,
        format = "standard",
        coverCardId = null,
        createdAt = 0L,
        updatedAt = System.currentTimeMillis(),
        cardCount = 60,
        colorIdentity = setOf("R"),
        coverImageUrl = null
    )
    MaterialTheme {
        Box(Modifier.padding(16.dp).width(160.dp)) {
            DeckItem(
                deck = deck,
                onClick = {},
                reduced = true
            )
        }
    }
}
