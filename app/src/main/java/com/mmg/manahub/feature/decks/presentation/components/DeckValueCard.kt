package com.mmg.manahub.feature.decks.presentation.components
// COMMENTS_REVIEWED: 2026-09-16

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.feature.decks.domain.usecase.BoardValue
import com.mmg.manahub.feature.decks.domain.usecase.DeckValueSummary

@Composable
fun DeckValueCard(
    summary: DeckValueSummary,
    currency: PreferredCurrency,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = mc.surface),
        border = BorderStroke(1.dp, mc.goldMtg.copy(alpha = 0.2f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = mc.goldMtg.copy(alpha = 0.15f),
                    ) {
                        Box(
                            modifier = Modifier.padding(spacing.xs),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = mc.goldMtg,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.deck_studio_value_title),
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                    )
                }
            }

            val totalKnownCopies = summary.mainboard.knownCopies + summary.sideboard.knownCopies
            val totalAmount = summary.mainboard.knownTotal + summary.sideboard.knownTotal
            val allEmpty = summary.mainboard.isEmpty && summary.sideboard.isEmpty
            val grandTotalValue = when {
                allEmpty -> PriceFormatter.format(0.0, currency)
                totalKnownCopies == 0 -> PriceFormatter.format(null as Double?, currency)
                else -> PriceFormatter.format(totalAmount, currency)
            }
            val totalMissingCopies = summary.mainboard.missingPriceCopies + summary.sideboard.missingPriceCopies

            Surface(
                color = mc.backgroundSecondary,
                shape = CardShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                    ) {
                        Text(
                            text = "Estimated Total",
                            style = ty.labelMedium,
                            color = mc.textSecondary,
                        )
                        if (totalMissingCopies > 0) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.deck_studio_value_missing_copies,
                                    totalMissingCopies,
                                    totalMissingCopies,
                                ),
                                style = ty.labelSmall,
                                color = mc.textDisabled,
                            )
                        }
                    }
                    Text(
                        text = grandTotalValue,
                        style = ty.titleLarge,
                        color = mc.goldMtg,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                DeckValueTile(
                    label = stringResource(R.string.deck_studio_value_mainboard),
                    board = summary.mainboard,
                    currency = currency,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                DeckValueTile(
                    label = stringResource(R.string.deck_studio_value_sideboard),
                    board = summary.sideboard,
                    currency = currency,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun DeckValueTile(
    label: String,
    board: BoardValue,
    currency: PreferredCurrency,
    modifier: Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val value = when {
        board.isEmpty -> PriceFormatter.format(0.0, currency)
        board.knownCopies == 0 -> PriceFormatter.format(null as Double?, currency)
        else -> PriceFormatter.format(board.knownTotal, currency)
    }
    val accessibility = stringResource(R.string.deck_studio_value_accessibility, label, value)

    Surface(
        color = mc.backgroundSecondary,
        shape = CardShape,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = accessibility
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(spacing.md),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = label,
                    style = ty.labelMedium,
                    color = mc.textSecondary,
                )
                Text(
                    text = value,
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                )
            }
            if (board.missingPriceCopies > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.deck_studio_value_missing_copies,
                        board.missingPriceCopies,
                        board.missingPriceCopies,
                    ),
                    style = ty.labelSmall,
                    color = mc.textDisabled,
                )
            }
        }
    }
}
