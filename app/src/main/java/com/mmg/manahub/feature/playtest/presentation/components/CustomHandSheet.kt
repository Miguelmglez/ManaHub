package com.mmg.manahub.feature.playtest.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/**
 * "Custom your hand" bottom sheet — lets the user force specific deck cards into the opening
 * hand. Modeled visually on [com.mmg.manahub.core.ui.components.CardSearchSheet]'s
 * `ModalBottomSheet` shell (search field + `LazyColumn` of rows with a qty stepper), but much
 * simpler: [availableCards] is the deck's own already-loaded mainboard — no network calls, no
 * tabs, purely local, in-memory filtering.
 *
 * Every `+`/`-` tap is already committed to [selection] immediately via [onCountChange] (same
 * pattern as [com.mmg.manahub.feature.playtest.presentation.setup.PlaytestSetupScreen]'s
 * steppers) — there is no separate "discard" path. [onDismiss] (tap outside, system back,
 * drag-to-close) simply closes the sheet; the caller is responsible for the instant re-apply.
 *
 * @param availableCards The deck's mainboard, grouped by card with its in-deck quantity
 *   (commander excluded).
 * @param selection Current forced selection: scryfallId → forced copy count.
 * @param drawCount The configured "Cards to draw" — the hard cap on the selection's total.
 * @param onCountChange Called with (scryfallId, newCount) on every stepper tap. The caller (the
 *   ViewModel) owns all clamping — this composable never second-guesses the resulting count.
 * @param onDismiss Called when the sheet should close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomHandSheet(
    availableCards: List<Pair<Card, Int>>,
    selection: Map<String, Int>,
    drawCount: Int,
    onCountChange: (scryfallId: String, count: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var query by remember { mutableStateOf("") }
    val totalSelected = selection.values.sum()
    val filteredCards = remember(availableCards, query) {
        if (query.isBlank()) {
            availableCards
        } else {
            availableCards.filter { (card, _) -> card.name.contains(query, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = mc.textDisabled) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(bottom = sp.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = sp.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = mc.textPrimary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.playtest_custom_hand_title),
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                    )
                    Text(
                        text = stringResource(R.string.playtest_custom_hand_total, totalSelected, drawCount),
                        style = ty.bodySmall,
                        color = if (totalSelected >= drawCount) mc.primaryAccent else mc.textSecondary,
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = sp.md),
                placeholder = {
                    Text(
                        stringResource(R.string.deckbuilder_add_cards_search_hint),
                        color = mc.textDisabled,
                        style = ty.bodyMedium,
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = mc.textSecondary) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, null, tint = mc.textSecondary)
                        }
                    }
                } else null,
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = mc.primaryAccent,
                    unfocusedBorderColor = mc.surfaceVariant,
                    cursorColor = mc.primaryAccent,
                ),
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = sp.md, vertical = sp.sm),
                verticalArrangement = Arrangement.spacedBy(sp.sm),
            ) {
                items(filteredCards, key = { it.first.scryfallId }) { (card, quantityInDeck) ->
                    val selectedCount = selection[card.scryfallId] ?: 0
                    val othersSum = totalSelected - selectedCount
                    val capReached = othersSum >= drawCount
                    CustomHandRow(
                        card = card,
                        quantityInDeck = quantityInDeck,
                        selectedCount = selectedCount,
                        incrementEnabled = selectedCount < quantityInDeck && !capReached,
                        onIncrement = { onCountChange(card.scryfallId, selectedCount + 1) },
                        onDecrement = { onCountChange(card.scryfallId, selectedCount - 1) },
                    )
                }

                if (filteredCards.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.deckbuilder_no_cards),
                                style = ty.bodyMedium,
                                color = mc.textDisabled,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomHandRow(
    card: Card,
    quantityInDeck: Int,
    selectedCount: Int,
    incrementEnabled: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selectedCount > 0) mc.primaryAccent.copy(alpha = 0.08f) else mc.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(
                model = card.imageArtCrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 52.dp, height = 38.dp).clip(RoundedCornerShape(4.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                CardName(
                    name = card.name,
                    showFrontOnly = true,
                    style = ty.bodyMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.playtest_custom_hand_owned, quantityInDeck),
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onDecrement, enabled = selectedCount > 0, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = null,
                        tint = if (selectedCount > 0) mc.textSecondary else mc.textDisabled,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(text = "$selectedCount", style = ty.labelMedium, color = mc.primaryAccent)
                IconButton(onClick = onIncrement, enabled = incrementEnabled, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = if (incrementEnabled) mc.primaryAccent else mc.textDisabled,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
