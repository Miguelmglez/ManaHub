package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.tagging.label
import com.mmg.manahub.core.domain.model.DeckSlotEntry
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.OracleText
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/**
 * The inline card-detail bottom sheet for the Deck Studio editor.
 *
 * Extracted (Group C / C3) from the legacy `DeckMagicDetailScreen`'s private
 * `CardDetailSheet` so the unified Deck Studio screen can open a card detail in place
 * (image flip + tags + +/-/delete + commander actions) instead of navigating away.
 * Stateless: every state field is a parameter and every action is a callback.
 *
 * The action area branches on three cases:
 *  1. [isCommanderSelectionContext] — the "Choose Commander" search flow (golden CTA /
 *     status badge), no +/- counter.
 *  2. [isCommander] (outside the selection flow) — the current commander, status badge only.
 *  3. A regular deck card — the +/- quantity counter plus the remove-all button.
 *
 * @param deckCard the slot whose [DeckSlotEntry.card] is rendered (null Card → image hidden).
 * @param isCommander true when this card IS the current deck commander.
 * @param isCommanderSelectionContext true when opened from the "Choose Commander" flow.
 * @param tags the resolved tag chips for the card (may be empty).
 * @param onAdd add one copy (or set-as-commander in the selection context).
 * @param onRemove remove one copy.
 * @param onDelete remove the whole slot.
 * @param onChooseAsCommander assign the resolved [Card] as commander.
 * @param onRemoveCommander clear the current commander.
 * @param onDismiss close the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CardDetailSheet(
    deckCard: DeckSlotEntry,
    isCommander: Boolean,
    isCommanderSelectionContext: Boolean,
    tags: List<CardTag>,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit,
    onChooseAsCommander: (Card) -> Unit,
    onRemoveCommander: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val card = deckCard.card
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden },
    )

    var showBackFace by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = spacing.xl),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xs, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close), tint = mc.textSecondary)
                }
            }
            if (card != null) {
                val hasBackFace = !card.imageBackNormal.isNullOrBlank()
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    val rotation by animateFloatAsState(
                        targetValue = if (showBackFace) -180f else 0f,
                        animationSpec = tween(durationMillis = 500),
                        label = "CardFlip",
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .aspectRatio(0.716f)
                            .graphicsLayer {
                                rotationY = rotation
                                cameraDistance = 12f * density
                            }
                            .clip(CardShape)
                            .then(
                                if (hasBackFace) Modifier.clickable { showBackFace = !showBackFace } else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(card.imageNormal ?: card.imageArtCrop).crossfade(true).build(),
                            contentDescription = card.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (rotation >= -90f) 1f else 0f },
                        )
                        if (hasBackFace) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(card.imageBackNormal).crossfade(true).build(),
                                contentDescription = card.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f; alpha = if (rotation < -90f) 1f else 0f },
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val displayName = card.printedName?.takeIf { it.isNotBlank() } ?: card.name
                        CardName(
                            name = displayName,
                            showFrontOnly = true,
                            style = ty.titleMedium,
                            color = mc.textPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        card.manaCost?.let { ManaCostImages(manaCost = it, symbolSize = 18.dp) }
                    }
                    val typeLine = card.printedTypeLine?.takeIf { it.isNotBlank() } ?: card.typeLine
                    Text(typeLine, style = ty.labelMedium, color = mc.textSecondary)
                    HorizontalDivider(color = mc.surfaceVariant)

                    val oracleDisplayText = card.oracleText?.takeIf { it.isNotBlank() } ?: card.printedText ?: ""
                    OracleText(text = oracleDisplayText, style = ty.bodySmall)

                    // Tag chips
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.height(spacing.sm))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(spacing.xs),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            tags.forEach { tag ->
                                Surface(
                                    color = mc.surfaceVariant.copy(alpha = 0.5f),
                                    shape = ChipShape,
                                    border = BorderStroke(0.5.dp, mc.primaryAccent.copy(alpha = 0.2f)),
                                ) {
                                    Text(
                                        text = tag.label(),
                                        style = ty.labelSmall,
                                        color = mc.textSecondary,
                                        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Action area ───────────────────────────────────────────────────
            when {
                // Case 1: "Choose Commander" search flow.
                isCommanderSelectionContext -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        if (isCommander) {
                            CommanderStatusBadge()
                            OutlinedButton(
                                onClick = onRemoveCommander,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                border = BorderStroke(1.dp, mc.lifeNegative.copy(alpha = 0.6f)),
                                shape = ChipShape,
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = mc.lifeNegative, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(spacing.xs))
                                Text(
                                    text = stringResource(R.string.deckbuilder_remove_commander),
                                    color = mc.lifeNegative,
                                    style = ty.bodyMedium,
                                )
                            }
                        } else if (card != null) {
                            Button(
                                onClick = { onChooseAsCommander(card) },
                                modifier = Modifier.fillMaxWidth().height(CommanderCtaHeight),
                                colors = ButtonDefaults.buttonColors(containerColor = mc.goldMtg),
                                shape = ChipShape,
                            ) {
                                Icon(Icons.Default.Star, null, tint = mc.onAccent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(spacing.sm))
                                Text(
                                    text = stringResource(R.string.deckbuilder_choose_as_commander),
                                    style = ty.titleMedium,
                                    color = mc.onAccent,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                // Case 2: current commander, viewed from the normal deck list.
                isCommander -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(spacing.lg)) {
                        CommanderStatusBadge()
                    }
                }

                // Case 3: regular deck card — +/- quantity counter.
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        Text(
                            text = stringResource(R.string.deckdetail_in_deck_label),
                            style = ty.labelMedium,
                            color = mc.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onRemove, enabled = deckCard.quantity > 0) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = stringResource(R.string.action_remove),
                                tint = if (deckCard.quantity > 0) mc.primaryAccent else mc.textDisabled,
                            )
                        }
                        Surface(shape = ChipShape, color = mc.primaryAccent.copy(alpha = 0.15f)) {
                            Text(
                                text = "${deckCard.quantity}",
                                style = ty.titleMedium,
                                color = mc.primaryAccent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
                            )
                        }
                        IconButton(onClick = onAdd) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add), tint = mc.primaryAccent)
                        }
                    }
                }
            }

            // Delete / remove-all — hidden in pure commander-selection context.
            if (!isCommanderSelectionContext) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.xs),
                    border = BorderStroke(1.dp, mc.lifeNegative.copy(alpha = 0.6f)),
                    shape = ChipShape,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = mc.lifeNegative, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(spacing.xs))
                    Text(stringResource(R.string.action_remove_all), color = mc.lifeNegative, style = ty.labelLarge)
                }
            }
        }
    }
}

/** The golden "Commander" status badge shown when the card is already the commander. */
@Composable
private fun CommanderStatusBadge() {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(
        shape = ChipShape,
        color = mc.goldMtg.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, mc.goldMtg.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Star, null, tint = mc.goldMtg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(spacing.sm))
            Text(
                text = stringResource(R.string.deckbuilder_commander_label).uppercase(),
                style = ty.labelLarge,
                color = mc.goldMtg,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Height of the golden "Choose as commander" CTA button. */
private val CommanderCtaHeight = 52.dp
