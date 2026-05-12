package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.UserCard
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * Bottom sheet for managing trade offers on a single card's copies.
 *
 * Shows two sections:
 * 1. "Offered for Trade" — copies currently marked for trade, with steppers to reduce
 * 2. "Available Copies" — copies not yet offered, with steppers to increase
 *
 * All changes are local until the user taps "Save".
 *
 * @param userCards         All collection entries (copies) of this card
 * @param currentTradeQty   Map of userCardId → currently offered quantity (from local_open_for_trade)
 * @param onConfirm         Called with the final map of userCardId → desired trade quantity (0 = remove)
 * @param onDismiss         Called when the sheet is dismissed without saving
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeSelectionSheet(
    userCards: List<UserCard>,
    currentTradeQty: Map<String, Int>,
    onConfirm: (Map<String, Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local mutable state: userCardId → trade quantity being edited
    val editQty = remember(userCards, currentTradeQty) {
        mutableStateMapOf<String, Int>().apply {
            userCards.forEach { uc ->
                this[uc.id] = currentTradeQty[uc.id] ?: 0
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            // Title
            Text(
                text = stringResource(R.string.carddetail_trade_sheet_title),
                style = ty.titleMedium,
                color = mc.textPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.carddetail_trade_sheet_subtitle),
                style = ty.bodySmall,
                color = mc.textSecondary,
            )
            Spacer(Modifier.height(16.dp))

            if (userCards.isEmpty()) {
                Text(
                    text = stringResource(R.string.carddetail_no_copies_for_trade),
                    style = ty.bodySmall,
                    color = mc.textDisabled,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    // ── Section 1: Available in Collection ──────────────────
                    val availableCards = userCards.filter { (editQty[it.id] ?: 0) < it.quantity }

                    item(key = "available_header") {
                        val availableCount =
                            availableCards.sumOf { it.quantity - (editQty[it.id] ?: 0) }
                        SectionHeader(
                            title = stringResource(R.string.carddetail_trade_available_header),
                            count = availableCount,
                        )
                    }
                    items(availableCards, key = { "available_${it.id}" }) { uc ->
                        val tradeQty = editQty[uc.id] ?: 0
                        val availableQty = uc.quantity - tradeQty
                        CopyRow(
                            userCard = uc,
                            displayQty = availableQty,
                            offeredQty = tradeQty,
                            actionIcon = Icons.Default.Add,
                            actionColor = mc.primaryAccent,
                            onAction = { editQty[uc.id] = (tradeQty + 1).coerceAtMost(uc.quantity) }
                        )
                    }
                    

                    // ── Section 2: Offered for Trade ────────────────────────
                    val offeredCards = userCards.filter { (editQty[it.id] ?: 0) > 0 }

                    item(key = "offered_header") {
                        SectionHeader(
                            title = stringResource(R.string.carddetail_trade_offered_header),
                            count = offeredCards.sumOf { editQty[it.id] ?: 0 },
                        )
                    }
                    items(offeredCards, key = { "offered_${it.id}" }) { uc ->
                        val tradeQty = editQty[uc.id] ?: 0
                        CopyRow(
                            userCard = uc,
                            displayQty = tradeQty,
                            offeredQty = tradeQty,
                            actionIcon = Icons.Default.Remove,
                            actionColor = mc.lifeNegative,
                            onAction = { editQty[uc.id] = (tradeQty - 1).coerceAtLeast(0) }
                        )
                    }
                    item(key = "divider") {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = mc.textDisabled.copy(alpha = 0.2f),
                        )
                    }

                }

                Spacer(Modifier.height(12.dp))

                // Save button
                Button(
                    onClick = { onConfirm(editQty.toMap()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mc.primaryAccent,
                        contentColor = mc.background,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.action_save),
                        style = ty.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = ty.labelMedium,
            color = mc.primaryAccent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = mc.primaryAccent.copy(alpha = 0.15f),
        ) {
            Text(
                text = count.toString(),
                style = ty.labelSmall,
                color = mc.primaryAccent,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun CopyRow(
    userCard: UserCard,
    displayQty: Int,
    offeredQty: Int,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    actionColor: androidx.compose.ui.graphics.Color,
    onAction: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(10.dp),
        color = mc.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Attribute badges
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Quantity badge
                    AttributeBadge(
                        "×$displayQty",
                        mc.textPrimary,
                        mc.textPrimary.copy(alpha = 0.1f)
                    )

                    // Language
                    AttributeBadge(
                        userCard.language.uppercase(),
                        mc.textSecondary,
                        mc.textSecondary.copy(alpha = 0.1f),
                    )

                    // Condition
                    AttributeBadge(
                        userCard.condition,
                        mc.textSecondary,
                        mc.textSecondary.copy(alpha = 0.1f),
                    )

                    // Foil
                    if (userCard.isFoil) {
                        AttributeBadge(
                            stringResource(R.string.addcard_confirm_foil),
                            mc.goldMtg,
                            mc.goldMtg.copy(alpha = 0.15f),
                        )
                    }

                    // Alt art
                    if (userCard.isAlternativeArt) {
                        AttributeBadge(
                            stringResource(R.string.carddetail_alternative_art_short),
                            mc.secondaryAccent,
                            mc.secondaryAccent.copy(alpha = 0.15f),
                        )
                    }
                }

                // Trade status line
                if (offeredQty > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$offeredQty of ${userCard.quantity} offered",
                        style = ty.labelSmall,
                        color = mc.goldMtg,
                    )
                }
            }

            // Action button
            IconButton(
                onClick = onAction,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = actionColor.copy(alpha = 0.12f),
                    contentColor = actionColor,
                ),
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AttributeBadge(
    text: String,
    textColor: androidx.compose.ui.graphics.Color,
    backgroundColor: androidx.compose.ui.graphics.Color,
) {
    val ty = MaterialTheme.magicTypography
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor,
    ) {
        Text(
            text = text,
            style = ty.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}
