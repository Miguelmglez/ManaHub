package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckSlotEntry
import com.mmg.manahub.core.tagging.label
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.CardTagGroup
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.jetbrains.compose.resources.painterResource

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
 * @param deckCard the slot backing every callback (identity/quantity/onAdd/onRemove/onDelete/
 *   onChooseAsCommander ALWAYS operate on `deckCard.card`'s original scryfallId — never the
 *   English-preferred [displayCard] below).
 * @param displayCard the English-preferred printing to render (image/name/type-line/oracle-text/
 *   tags only) when `deckCard.card` was saved in a non-English language — see
 *   [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel.loadCardDetails]. Null while
 *   [isLoadingDetail] is true, or when no redirect was needed (falls back to `deckCard.card`).
 * @param isLoadingDetail true while [displayCard] is being resolved — a loading placeholder (the
 *   card-back art, no text) is shown instead of `deckCard.card`'s own image/name/text, so the
 *   saved non-English printing is never painted for a frame (mirrors the shared-element-flash fix
 *   in [com.mmg.manahub.feature.carddetail.presentation.CardDetailViewModel]).
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
    displayCard: Card?,
    isLoadingDetail: Boolean,
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
    // The ORIGINAL printing — every callback below (onAdd/onRemove/onDelete/onChooseAsCommander)
    // operates on this, never on [displayCard]. Only the visual block further down swaps.
    val originalCard = deckCard.card
    // The card actually RENDERED (image/name/type-line/oracle-text/tags): null while loading (shows
    // the placeholder below), else the English-preferred [displayCard] or the original as fallback.
    val visualCard = if (isLoadingDetail) null else (displayCard ?: originalCard)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true) {
        it != SheetValue.Hidden
    }

    var showBackFace by remember { mutableStateOf(value = false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.background,
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = null,
        tonalElevation = 0.dp,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = spacing.lg),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.sm, vertical = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = mc.surfaceVariant.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxSize(),
                            border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_close),
                                tint = mc.textSecondary,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }

            if (visualCard != null) {
                item {
                    val hasBackFace = !visualCard.imageBackNormal.isNullOrBlank()
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

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .aspectRatio(0.716f)
                                .clip(CardShape),
                            shape = CardShape,
                            shadowElevation = 8.dp,
                            tonalElevation = 4.dp,
                            border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        rotationY = rotation
                                        cameraDistance = 12f * density
                                    }
                                    .then(
                                        if (hasBackFace) Modifier.clickable {
                                            showBackFace = !showBackFace
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(visualCard.imageNormal ?: visualCard.imageArtCrop)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = visualCard.name,
                                    placeholder = painterResource(Res.drawable.mtg_card_back),
                                    error = painterResource(Res.drawable.mtg_card_back),
                                    fallback = painterResource(Res.drawable.mtg_card_back),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = if (rotation >= -90f) 1f else 0f },
                                )
                                if (hasBackFace) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(visualCard.imageBackNormal)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = visualCard.name,
                                        placeholder = painterResource(Res.drawable.mtg_card_back),
                                        error = painterResource(Res.drawable.mtg_card_back),
                                        fallback = painterResource(Res.drawable.mtg_card_back),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                rotationY = 180f
                                                alpha = if (rotation < -90f) 1f else 0f
                                            },
                                    )
                                }
                            }
                        }

                        if (hasBackFace) {
                            Surface(
                                shape = ChipShape,
                                color = mc.primaryAccent.copy(alpha = 0.1f),
                                modifier = Modifier.padding(top = spacing.xs)
                            ) {
                                Text(
                                    text = stringResource(if (showBackFace) R.string.carddetail_flip_see_front else R.string.carddetail_flip_see_back),
                                    style = ty.labelSmall,
                                    color = mc.primaryAccent,
                                    modifier = Modifier.padding(
                                        horizontal = spacing.md,
                                        vertical = spacing.xxs
                                    )
                                )
                            }
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {


                        // Tag chips — color-coded by TagCategory (read-only, no onClick).
                        if (tags.isNotEmpty()) {
                            CardTagGroup(
                                tags = tags,
                                tagLabel = { it.label() }
                            )
                        }
                    }
                }
            } else if (isLoadingDetail) {
                // Loading placeholder: the sheet was opened for a non-English-saved printing and
                // is resolving its English-preferred [displayCard] — show just the card-back art
                // (no name/type-line/oracle-text/tags) so the saved non-English printing is never
                // painted for a frame, mirroring CardDetailViewModel's entry-only redirect.
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .aspectRatio(0.716f)
                                .clip(CardShape),
                            shape = CardShape,
                            shadowElevation = 8.dp,
                            tonalElevation = 4.dp,
                            border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.mtg_card_back),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            // ── Action area ───────────────────────────────────────────────────
            item {
                when {
                    // Case 1: "Choose Commander" search flow.
                    isCommanderSelectionContext -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(spacing.md),
                        ) {
                            if (isCommander) {
                                CommanderStatusBadge()
                                MagicCtaButton(
                                    onClick = onRemoveCommander,
                                    text = stringResource(R.string.deckbuilder_remove_commander),
                                    style = MagicCtaStyle.Outlined,
                                    color = MagicCtaColor.Error,
                                    icon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else if (originalCard != null) {
                                MagicCtaButton(
                                    onClick = { onChooseAsCommander(originalCard) },
                                    text = stringResource(R.string.deckbuilder_choose_as_commander),
                                    color = MagicCtaColor.Gold,
                                    icon = {
                                        Icon(
                                            Icons.Default.Star,
                                            null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                        .height(CommanderCtaHeight),
                                )
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
                            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.deckdetail_in_deck_label),
                                    style = ty.labelMedium,
                                    color = mc.textSecondary,
                                )
                                Text(
                                    text = "${deckCard.quantity} ${if (deckCard.quantity == 1) "copy" else "copies"}",
                                    style = ty.titleMedium,
                                    color = mc.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                            ) {
                                MagicCtaButton(
                                    onClick = onRemove,
                                    enabled = deckCard.quantity > 0,
                                    style = MagicCtaStyle.Outlined,
                                    color = MagicCtaColor.Primary,
                                    icon = {
                                        Icon(
                                            Icons.Default.Remove,
                                            contentDescription = stringResource(R.string.action_remove),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    contentPadding = PaddingValues(spacing.sm),
                                    modifier = Modifier.size(48.dp)
                                )

                                Surface(
                                    shape = ChipShape,
                                    color = mc.surfaceVariant.copy(alpha = 0.3f),
                                    border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.height(48.dp).width(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = deckCard.quantity.toString(),
                                            style = ty.titleLarge,
                                            color = mc.textPrimary,
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                    }
                                }

                                MagicCtaButton(
                                    onClick = onAdd,
                                    style = MagicCtaStyle.Filled,
                                    color = MagicCtaColor.Primary,
                                    icon = {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = stringResource(R.string.action_add),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    contentPadding = PaddingValues(spacing.sm),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Delete / remove-all — hidden in pure commander-selection context.
            if (!isCommanderSelectionContext) {
                item {
                    MagicCtaButton(
                        onClick = onDelete,
                        text = stringResource(if (isCommander) R.string.deckbuilder_remove_commander else R.string.action_remove_all),
                        style = MagicCtaStyle.Outlined,
                        color = MagicCtaColor.Error,
                        icon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.lg, vertical = spacing.sm)
                    )
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
        color = mc.goldMtg.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, mc.goldMtg.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.Star,
                null,
                tint = mc.goldMtg,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(spacing.sm))
            Text(
                text = stringResource(R.string.deckbuilder_commander_label).uppercase(),
                style = ty.titleMedium,
                color = mc.goldMtg,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
            )
        }
    }
}

/** Height of the golden "Choose as commander" CTA button. */
private val CommanderCtaHeight = 52.dp
