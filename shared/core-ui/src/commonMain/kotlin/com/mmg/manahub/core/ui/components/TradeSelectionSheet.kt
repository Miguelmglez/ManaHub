package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.CardConstants

/**
 * Bottom sheet for managing trade offers on a single card's copies.
 *
 * Shows two sections:
 * 1. "Available in Collection" — copies not yet offered, with steppers to increase
 * 2. "Offered for Trade" — copies currently marked for trade, with steppers to reduce
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
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )

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
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            // Drag Handle Indicator Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 36.dp, height = 4.dp),
                    shape = CircleShape,
                    color = mc.textDisabled.copy(alpha = 0.4f)
                ) {}
            }

            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = mc.secondaryAccent.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = mc.secondaryAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Select copies to mark for trade",
                            style = ty.titleMedium,
                            color = mc.textPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Choose which copies you want to mark for trade.",
                            style = ty.bodySmall,
                            color = mc.textSecondary,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = mc.textSecondary
                    )
                }
            }

            // Summary Card
            if (userCards.isNotEmpty()) {
                val totalOwned = userCards.sumOf { it.quantity }
                val totalOffered = editQty.values.sum()
                val totalAvailable = (totalOwned - totalOffered).coerceAtLeast(0)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = mc.surface,
                    border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.6f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SummaryStatItem(
                            label = "In Collection",
                            value = "$totalOwned",
                            color = mc.textPrimary
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(mc.surfaceVariant.copy(alpha = 0.6f))
                        )
                        SummaryStatItem(
                            label = "Offered for Trade",
                            value = "$totalOffered",
                            color = mc.secondaryAccent
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(mc.surfaceVariant.copy(alpha = 0.6f))
                        )
                        SummaryStatItem(
                            label = "Remaining",
                            value = "$totalAvailable",
                            color = if (totalAvailable > 0) mc.primaryAccent else mc.textDisabled
                        )
                    }
                }
            }

            if (userCards.isEmpty()) {
                Text(
                    text = "You have no collection copies to mark for trade.",
                    style = ty.bodySmall,
                    color = mc.textDisabled,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // ── Section 1: Available in Collection ──────────────────
                    val availableCards = userCards.filter { (editQty[it.id] ?: 0) < it.quantity }

                    item(key = "available_header") {
                        val availableCount =
                            availableCards.sumOf { it.quantity - (editQty[it.id] ?: 0) }
                        SectionHeader(
                            title = "Available in Collection",
                            count = availableCount,
                            accentColor = mc.primaryAccent,
                        )
                    }
                    items(availableCards, key = { "available_${it.id}" }) { uc ->
                        val tradeQty = editQty[uc.id] ?: 0
                        CopyRow(
                            userCard = uc,
                            offeredQty = tradeQty,
                            onDecrement = { editQty[uc.id] = (tradeQty - 1).coerceAtLeast(0) },
                            onIncrement = { editQty[uc.id] = (tradeQty + 1).coerceAtMost(uc.quantity) },
                        )
                    }

                    // ── Section 2: Offered for Trade ────────────────────────
                    val offeredCards = userCards.filter { (editQty[it.id] ?: 0) > 0 }

                    item(key = "offered_header") {
                        SectionHeader(
                            title = "Offered for Trade",
                            count = offeredCards.sumOf { editQty[it.id] ?: 0 },
                            accentColor = mc.secondaryAccent,
                        )
                    }
                    items(offeredCards, key = { "offered_${it.id}" }) { uc ->
                        val tradeQty = editQty[uc.id] ?: 0
                        CopyRow(
                            userCard = uc,
                            offeredQty = tradeQty,
                            onDecrement = { editQty[uc.id] = (tradeQty - 1).coerceAtLeast(0) },
                            onIncrement = { editQty[uc.id] = (tradeQty + 1).coerceAtMost(uc.quantity) },
                        )
                    }

                    item(key = "divider") {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = mc.surfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Save button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    MagicCtaButton(
                        onClick = { onConfirm(editQty.toMap()) },
                        text = "Save",
                        color = MagicCtaColor.Primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStatItem(
    label: String,
    value: String,
    color: Color,
) {
    val ty = MaterialTheme.magicTypography
    val mc = MaterialTheme.magicColors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = ty.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
        Text(
            text = label,
            style = ty.labelSmall,
            color = mc.textSecondary,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    accentColor: Color,
) {
    val ty = MaterialTheme.magicTypography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = ty.labelLarge,
            color = accentColor,
            fontWeight = FontWeight.Bold,
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = accentColor.copy(alpha = 0.15f),
        ) {
            Text(
                text = count.toString(),
                style = ty.labelMedium,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun CopyRow(
    userCard: UserCard,
    offeredQty: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = mc.surface,
        border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Attribute badges
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Language Flag
                    Text(
                        text = CardConstants.getFlag(userCard.language),
                        style = ty.labelLarge.copy(fontSize = 14.sp)
                    )

                    // Condition Badge
                    AttributeBadge(
                        text = userCard.condition,
                        textColor = mc.textSecondary,
                        backgroundColor = mc.surfaceVariant.copy(alpha = 0.6f),
                    )

                    // Foil Badge
                    if (userCard.isFoil) FoilBadge()
                }

                // Subtitle showing offered ratio
                Text(
                    text = if (offeredQty > 0) "$offeredQty of ${userCard.quantity} offered for trade"
                    else "Total owned: ${userCard.quantity}",
                    style = ty.labelSmall,
                    color = if (offeredQty > 0) mc.secondaryAccent else mc.textSecondary,
                )
            }

            // Interactive Stepper Controller
            StepperControl(
                qty = offeredQty,
                maxQty = userCard.quantity,
                onDecrement = onDecrement,
                onIncrement = onIncrement,
            )
        }
    }
}

@Composable
private fun StepperControl(
    qty: Int,
    maxQty: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = mc.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, mc.surfaceVariant),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(2.dp),
        ) {
            IconButton(
                onClick = onDecrement,
                enabled = qty > 0,
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (qty > 0) mc.lifeNegative.copy(alpha = 0.15f) else Color.Transparent,
                    contentColor = mc.lifeNegative,
                    disabledContentColor = mc.textDisabled.copy(alpha = 0.3f),
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease trade quantity",
                    modifier = Modifier.size(14.dp)
                )
            }

            Text(
                text = "$qty",
                style = ty.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (qty > 0) mc.secondaryAccent else mc.textPrimary,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            IconButton(
                onClick = onIncrement,
                enabled = qty < maxQty,
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (qty < maxQty) mc.primaryAccent.copy(alpha = 0.15f) else Color.Transparent,
                    contentColor = mc.primaryAccent,
                    disabledContentColor = mc.textDisabled.copy(alpha = 0.3f),
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase trade quantity",
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun AttributeBadge(
    text: String,
    textColor: Color,
    backgroundColor: Color,
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
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
