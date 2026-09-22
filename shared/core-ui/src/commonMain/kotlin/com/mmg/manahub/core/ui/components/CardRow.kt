package com.mmg.manahub.core.ui.components

// COMMENTS_REVIEWED: 2026-09-06

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected as selectedSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckSlotEntry
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.card_row_view_card_a11y
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.CardCornerRadius
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.ExtraSmallCardShape
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.coloredShadow
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.PriceFormatter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CardRow(
    entry: DeckSlotEntry,
    isInCollection: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    isCommander: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedTransitionKey: Any? = null,
    preferredCurrency: PreferredCurrency? = null,
    onImageClick: (() -> Unit)? = null,
) {
    val card = entry.card
    if (card != null) {
        CardRow(
            card = card,
            isInCollection = isInCollection,
            onClick = onClick,
            onRemove = onRemove,
            modifier = modifier,
            quantity = entry.quantity,
            isCommander = isCommander,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedTransitionKey = sharedTransitionKey,
            preferredCurrency = preferredCurrency,
            onImageClick = onImageClick,
        )
        return
    }

    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = mc.surface,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, mc.surfaceVariant.copy(alpha = 0.5f), CardShape)
            .semantics(mergeDescendants = true) {}
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            AsyncImage(
                model = null,
                contentDescription = null,
                placeholder = painterResource(Res.drawable.mtg_card_back),
                error = painterResource(Res.drawable.mtg_card_back),
                fallback = painterResource(Res.drawable.mtg_card_back),
                modifier = Modifier
                    .size(width = 44.dp, height = 62.dp)
                    .clip(ExtraSmallCardShape)
                    .border(1.dp, mc.surfaceVariant, ExtraSmallCardShape),
                contentScale = ContentScale.Crop
            )
            Text(
                text = "Unresolved Card",
                style = ty.titleMedium,
                color = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (entry.quantity > 1) {
                Surface(shape = ChipShape, color = mc.primaryAccent.copy(alpha = 0.2f)) {
                    Text("×${entry.quantity}", style = ty.labelMedium, color = mc.primaryAccent, modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs))
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove card", tint = mc.textDisabled, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** How a selected [CardRow] is drawn. */
enum class CardRowSelectionStyle {
    /** Accent glow + 2dp accent border (deck/wizard pickers). */
    Glow,

    /** Thin softened-accent outline only, for multi-select browsing where many rows are selected. */
    Subtle,
}

/** Outline width of a subtly selected card surface (same as the unselected outline: no layout shift). */
val SubtleSelectionBorderWidth: Dp = 1.dp

private const val SubtleSelectionAlpha = 0.6f

/** Softened accent for a subtly selected card surface; legible on every palette, dark and light. */
@Composable
@ReadOnlyComposable
fun subtleSelectionBorderColor(): Color = MaterialTheme.magicColors.primaryAccent.copy(alpha = SubtleSelectionAlpha)

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun CardRow(
    card: Card,
    isInCollection: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    quantity: Int = 1,
    selected: Boolean = false,
    onAdd: (() -> Unit)? = null,
    isCommander: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedTransitionKey: Any? = null,
    extraSupportingContent: @Composable (RowScope.() -> Unit)? = null,
    preferredCurrency: PreferredCurrency? = null,
    onImageClick: (() -> Unit)? = null,
    addEnabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    selectionStyle: CardRowSelectionStyle = CardRowSelectionStyle.Glow,
    exposeSelectedSemantics: Boolean = false,
    displayName: String? = null,
    displayTypeLine: String? = null,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val resolvedPreferredCurrency = preferredCurrency ?: LocalPreferredCurrency.current
    val formattedPrice = PriceFormatter.formatFromScryfall(
        card.priceUsd,
        card.priceEur,
        resolvedPreferredCurrency,
    ).takeUnless { it == "—" }

    val cardColors = remember(card.colors, isCommander) {
        if (isCommander) listOf(mc.goldMtg)
        else card.colors.map { manaColorFor(it, mc) }
    }
    val primaryAccentColor = cardColors.firstOrNull() ?: mc.surfaceVariant

    val usesCombinedClick = onLongClick != null || onClickLabel != null
    val subtleSelectionColor = subtleSelectionBorderColor()
    val rowModifier = modifier
            .fillMaxWidth()
            .then(
                if (isCommander) {
                    Modifier.border(1.dp, mc.goldMtg.copy(alpha = 0.4f), CardShape)
                } else if (selected && selectionStyle == CardRowSelectionStyle.Subtle) {
                    Modifier.border(SubtleSelectionBorderWidth, subtleSelectionColor, CardShape)
                } else if (selected) {
                    Modifier.coloredShadow(
                        color = mc.primaryAccent,
                        borderRadius = CardCornerRadius,
                        blurRadius = 12.dp,
                        spread = 0.8f
                    ).border(2.dp, mc.primaryAccent, CardShape)
                } else {
                    Modifier.border(1.dp, mc.surfaceVariant.copy(alpha = 0.5f), CardShape)
                }
            )
            .then(
                if (usesCombinedClick) {
                    Modifier
                        .clip(CardShape)
                        .combinedClickable(
                            onClickLabel = onClickLabel,
                            onLongClickLabel = onLongClickLabel,
                            onLongClick = onLongClick,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = true) {
                if (exposeSelectedSemantics) selectedSemantics = selected
            }
    val rowContent: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().heightIn(min = 68.dp)) {
            if (card.imageArtCrop != null) {
                AsyncImage(
                    model = card.imageArtCrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.08f,
                    modifier = Modifier.matchParentSize()
                )
            }

            if (cardColors.isNotEmpty()) {
                val gradient = remember(cardColors) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            primaryAccentColor.copy(alpha = 0.12f),
                            mc.surface.copy(alpha = 0f)
                        )
                    )
                }
                Box(Modifier.matchParentSize().background(gradient))
            }

            if (cardColors.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(
                            primaryAccentColor,
                            shape = RoundedCornerShape(
                                topStart = CardCornerRadius,
                                bottomStart = CardCornerRadius
                            )
                        )
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    val layoutModifier = Modifier.size(width = 44.dp, height = 62.dp)

                    val finalImageModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            layoutModifier
                                .sharedBounds(
                                    sharedContentState = rememberSharedContentState(
                                        key = sharedTransitionKey ?: "card-image-${card.scryfallId}"
                                    ),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    clipInOverlayDuringTransition = OverlayClip(ExtraSmallCardShape),
                                    boundsTransform = { _, _ ->
                                        tween(durationMillis = 380, easing = LinearOutSlowInEasing)
                                    },
                                    renderInOverlayDuringTransition = true,
                                )
                                .clip(ExtraSmallCardShape)
                                .border(1.dp, mc.surfaceVariant.copy(alpha = 0.8f), ExtraSmallCardShape)
                        }
                    } else {
                        layoutModifier
                            .clip(ExtraSmallCardShape)
                            .border(1.dp, mc.surfaceVariant.copy(alpha = 0.8f), ExtraSmallCardShape)
                    }

                    val cardThumbnail = @Composable {
                        AsyncImage(
                            model = card.imageNormal,
                            contentDescription = null,
                            placeholder = painterResource(Res.drawable.mtg_card_back),
                            error = painterResource(Res.drawable.mtg_card_back),
                            fallback = painterResource(Res.drawable.mtg_card_back),
                            modifier = finalImageModifier,
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (onImageClick != null) {
                        val viewCardDescription = stringResource(Res.string.card_row_view_card_a11y)
                        Box(
                            // 44x62 visual stays put; minimumInteractiveComponentSize only pads the touch target to 48dp.
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable(onClick = onImageClick)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = viewCardDescription
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            cardThumbnail()
                        }
                    } else {
                        cardThumbnail()
                    }

                    Column(
                        modifier = Modifier.weight(1f).height(62.dp),
                    ) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopStart) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CardName(
                                    name = displayName ?: card.name,
                                    showFrontOnly = true,
                                    style = ty.titleMedium,
                                    color = mc.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isInCollection) {
                                    Spacer(Modifier.width(spacing.xs))
                                    Icon(
                                        Icons.Rounded.CollectionsBookmark,
                                        contentDescription = null,
                                        tint = if (isCommander) mc.goldMtg else mc.primaryAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            Text(
                                displayTypeLine ?: card.typeLine,
                                style = ty.bodySmall,
                                color = if (isCommander) mc.textPrimary else mc.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomStart) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (!card.manaCost.isNullOrBlank()) {
                                        val costs = card.manaCost!!.split(" // ")
                                        costs.forEachIndexed { index, singleCost ->
                                            ManaCostImages(manaCost = singleCost, symbolSize = 14.dp)
                                            if (index < costs.size - 1) {
                                                Text(
                                                    " // ",
                                                    style = MaterialTheme.magicTypography.titleMedium,
                                                    color = MaterialTheme.magicColors.textSecondary,
                                                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xxs)
                                                )
                                            }
                                        }
                                    }
                                    SetSymbol(
                                        setCode = card.setCode,
                                        rarity = CardRarity.fromString(card.rarity),
                                        size = 14.dp,
                                        modifier = if (!card.manaCost.isNullOrBlank()) Modifier.padding(start = MaterialTheme.spacing.md) else Modifier
                                    )
                                    Text(
                                        text = card.setCode.uppercase(),
                                        style = ty.labelSmall,
                                        color = if (isCommander) mc.textPrimary else mc.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(horizontal = MaterialTheme.spacing.xxs),
                                    )
                                }
                                if (formattedPrice != null) {
                                    Spacer(Modifier.width(spacing.sm))
                                    Text(
                                        text = formattedPrice,
                                        style = ty.labelMedium,
                                        color = mc.goldMtg,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        if (onAdd != null) {
                            if (quantity > 0 && onRemove != null) {
                                IconButton(
                                    onClick = onRemove,
                                ) {
                                    Icon(
                                        Icons.Default.Remove,
                                        contentDescription = "Decrease quantity",
                                        tint = mc.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = "$quantity",
                                    style = ty.labelMedium,
                                    color = if (isCommander) mc.goldMtg else mc.primaryAccent
                                )
                            }

                            IconButton(
                                onClick = onAdd,
                                enabled = addEnabled,
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Increase quantity",
                                    tint = when {
                                        !addEnabled -> mc.textDisabled
                                        isCommander -> mc.goldMtg
                                        else -> mc.primaryAccent
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            if (quantity > 1) {
                                Surface(
                                    shape = ChipShape,
                                    color = (if (isCommander) mc.goldMtg else mc.secondaryAccent).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        "×$quantity",
                                        style = ty.labelMedium,
                                        color = if (isCommander) mc.goldMtg else mc.secondaryAccent,
                                        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs)
                                    )
                                }
                            }
                            if (onRemove != null) {
                                IconButton(
                                    onClick = onRemove,
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove card",
                                        tint = if (isCommander) mc.goldMtg else mc.textDisabled,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (extraSupportingContent != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = spacing.md, end = spacing.md, top = spacing.xxs, bottom = spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        extraSupportingContent()
                    }
                }
            }
        }
    }
    val surfaceColor = if (isCommander) mc.goldMtg.copy(alpha = 0.1f) else mc.surface
    if (usesCombinedClick) {
        Surface(shape = CardShape, color = surfaceColor, modifier = rowModifier, content = rowContent)
    } else {
        Surface(onClick = onClick, shape = CardShape, color = surfaceColor, modifier = rowModifier, content = rowContent)
    }
}
