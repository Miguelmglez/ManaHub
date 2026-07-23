package com.mmg.manahub.feature.playtest.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * Full-screen overlay for the bottom-N selection step (London Mulligan bottom-N, and/or the
 * "Cards to start the game" gap — see `computeRequiredBottomCount`).
 *
 * Shows the current hand as a 3-column grid. The player taps cards to select them for bottoming.
 * The Confirm button becomes enabled once the selection count reaches [requiredCount], clamped to
 * however many cards are actually selectable (`hand.size - disabledIndices.size`) — a "Custom your
 * hand" forced card can never be bottomed, so if forced cards leave fewer selectable cards than
 * [requiredCount] asks for, Confirm enables at that lower, actually-reachable count instead.
 *
 * @param hand The current hand to select from.
 * @param requiredCount Number of cards that must be put on the bottom.
 * @param selectedIndices Set of currently selected hand indices.
 * @param disabledIndices Indices that can never be selected — "Custom your hand" forced cards.
 *   Rendered non-clickable at reduced alpha so the user can see why they can't be picked.
 * @param onToggle Called when the player taps a selectable card (pass its index).
 * @param onConfirm Called when the player confirms the selection.
 */
@Composable
fun BottomNSelector(
    hand: List<Card>,
    requiredCount: Int,
    selectedIndices: Set<Int>,
    disabledIndices: Set<Int> = emptySet(),
    onToggle: (Int) -> Unit,
    onConfirm: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val effectiveRequired = requiredCount.coerceAtMost((hand.size - disabledIndices.size).coerceAtLeast(0))
    val isConfirmEnabled = selectedIndices.size == effectiveRequired

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(mc.background.copy(alpha = 0.92f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text     = stringResource(R.string.playtest_bottom_n_title, requiredCount),
                style    = ty.titleMedium,
                color    = mc.textPrimary,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
            Text(
                text  = stringResource(
                    R.string.playtest_bottom_n_progress,
                    selectedIndices.size,
                    requiredCount,
                ),
                style = ty.bodyMedium,
                color = mc.textSecondary,
            )

            LazyVerticalGrid(
                columns         = GridCells.Fixed(3),
                contentPadding  = PaddingValues(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
                modifier        = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                itemsIndexed(hand) { index, card ->
                    val isSelected = index in selectedIndices
                    val isDisabled = index in disabledIndices
                    // Find selection order (1-based badge number).
                    val selectionOrder = if (isSelected) {
                        selectedIndices.sorted().indexOf(index) + 1
                    } else null

                    Box(
                        modifier = Modifier
                            .aspectRatio(63f / 88f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 2.dp else 0.5.dp,
                                color = if (isSelected) mc.lifeNegative else mc.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .alpha(if (isDisabled) 0.35f else 1f)
                            .let { base -> if (isDisabled) base else base.clickable { onToggle(index) } },
                    ) {
                        AsyncImage(
                            model             = card.imageNormal,
                            contentDescription = card.name,
                            contentScale      = ContentScale.Crop,
                            modifier          = Modifier.fillMaxSize(),
                        )

                        // Semi-transparent overlay + badge when selected.
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(mc.lifeNegative.copy(alpha = 0.35f)),
                            )
                            if (selectionOrder != null) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier         = Modifier
                                        .size(28.dp)
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .background(mc.lifeNegative, CircleShape),
                                ) {
                                    Text(
                                        text  = selectionOrder.toString(),
                                        style = ty.labelSmall,
                                        color = mc.background,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            MagicCtaButton(
                text = stringResource(R.string.playtest_bottom_n_confirm),
                onClick = onConfirm,
                enabled = isConfirmEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
        }
    }
}
