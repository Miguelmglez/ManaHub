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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * Full-screen overlay for the London Mulligan bottom-N selection step.
 *
 * Shows the current hand as a 3-column grid. The player taps cards to select them
 * for bottoming. When exactly [mulligansUsed] cards are selected, the Confirm
 * button becomes enabled.
 *
 * @param hand The current hand to select from.
 * @param mulligansUsed Number of cards that must be put on the bottom.
 * @param selectedIndices Set of currently selected hand indices.
 * @param onToggle Called when the player taps a card (pass its index).
 * @param onConfirm Called when the player confirms the selection.
 */
@Composable
fun BottomNSelector(
    hand: List<Card>,
    mulligansUsed: Int,
    selectedIndices: Set<Int>,
    onToggle: (Int) -> Unit,
    onConfirm: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val isConfirmEnabled = selectedIndices.size == mulligansUsed

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
                text     = stringResource(R.string.playtest_bottom_n_title, mulligansUsed),
                style    = ty.titleMedium,
                color    = mc.textPrimary,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
            Text(
                text  = stringResource(
                    R.string.playtest_bottom_n_progress,
                    selectedIndices.size,
                    mulligansUsed,
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
                            .clickable { onToggle(index) },
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

            Button(
                onClick  = onConfirm,
                enabled  = isConfirmEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = mc.primaryAccent,
                    disabledContainerColor = mc.primaryAccent.copy(alpha = 0.35f),
                ),
            ) {
                Text(
                    text  = stringResource(R.string.playtest_bottom_n_confirm),
                    style = ty.labelLarge,
                    color = mc.background,
                )
            }
        }
    }
}
