package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.OracleText
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.model.AlmostCombo
import com.mmg.manahub.feature.decks.domain.model.Combo
import com.mmg.manahub.core.FeatureFlags
import org.jetbrains.compose.resources.painterResource

/** Standard MTG card aspect ratio, shared by every combo card tile. */
private const val COMBO_CARD_ASPECT_RATIO = 63f / 88f

/**
 * Deck Engine Unification plan D7 (Phase 4.3) — a fully-owned combo card for the synergy
 * browser's Combos tab. Each card name is rendered as a real card-art tile when resolved in
 * [cardsByName] (Deck Studio visual-overhaul pass: replaced the original name-only
 * [NameChipRow] chips), falling back to a text chip for any name that failed to resolve to a
 * [Card] -- see [DeckStudioViewModel.resolveComboCards]. Tapping a resolved tile navigates to the
 * real [com.mmg.manahub.feature.carddetail.presentation.CardDetailScreen] (via [onCardClick],
 * now receiving the resolved scryfallId) with a shared-element transition, UNLIKE the Strategies
 * tab's [DiscoveryRowV2] tiles, which open the inline zoom overlay in place.
 *
 * @param cardsByName every combo card name resolved to a full [Card] (missing entries fall back
 *   to a plain text chip).
 * @param sharedTransitionScope / @param animatedVisibilityScope non-null only when the hosting
 *   nav destination participates in a `SharedTransitionLayout` -- null degrades to a plain tap
 *   with no shared-element animation.
 * @param showUseAsSeed Deck Wizard & Engine Rework plan (WS 1.3, D-E): gates the "Use as seed" CTA
 *   behind `DeckFeatureFlags.DISCOVERY_BUILD_HANDOFF_ENABLED` -- the combo itself always renders
 *   read-only, only the hand-off action is hidden when `false`.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ComboRow(
    combo: Combo,
    cardsByName: Map<String, Card>,
    onCardClick: (String) -> Unit,
    onUseAsSeed: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier,
    showUseAsSeed: Boolean = FeatureFlags.Decks.DISCOVERY_BUILD_HANDOFF_ENABLED,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = mc.backgroundSecondary,
        shape = CardShape,
    ) {
        Column(modifier = Modifier.padding(spacing.md)) {
            CardName(
                name = combo.cardNames.joinToString(" + "),
                style = ty.titleMedium,
                color = mc.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (combo.produces.isNotEmpty()) {
                Text(
                    text = combo.produces.joinToString(", "),
                    style = ty.labelMedium,
                    color = mc.goldMtg,
                )
            }
            Spacer(Modifier.height(spacing.xxs))
            // Combo explanations are copied from card oracle text and may contain literal mana
            // tokens (e.g. "{T}: Add {W}.") -- OracleText renders those as inline mana symbols
            // instead of showing the raw "{W}" text (CLAUDE.md: any Magic rules text must use
            // OracleText, never a plain Text). No maxLines here -- OracleText has no truncation
            // param (see its other call sites in CardDetailScreen.kt/CardDetailSheet.kt).
            OracleText(
                text = combo.description,
                style = ty.bodySmall.copy(color = mc.textSecondary),
            )

            Spacer(Modifier.height(spacing.sm))
            ComboCardsRow(
                names = combo.cardNames,
                cardsByName = cardsByName,
                onCardClick = onCardClick,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )

            if (showUseAsSeed) {
                Spacer(Modifier.height(spacing.sm))
                OutlinedButton(
                    onClick = onUseAsSeed,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.5f)),
                    shape = ButtonShape,
                ) {
                    Text(text = stringResource(R.string.deck_wizard_build_this), style = ty.labelLarge, color = mc.primaryAccent)
                }
            }
        }
    }
}

/**
 * "You're 1 card away" -- the [AlmostCombo] sibling of [ComboRow]. [AlmostCombo.missingCardName]
 * is visually distinguished (an accent border on its tile, or an accent-tinted fallback chip)
 * from the owned cards.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AlmostComboRow(
    almostCombo: AlmostCombo,
    cardsByName: Map<String, Card>,
    onCardClick: (String) -> Unit,
    onUseAsSeed: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier,
    // See ComboRow's showUseAsSeed KDoc (WS 1.3, D-E).
    showUseAsSeed: Boolean = FeatureFlags.Decks.DISCOVERY_BUILD_HANDOFF_ENABLED,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = mc.backgroundSecondary,
        shape = CardShape,
    ) {
        Column(modifier = Modifier.padding(spacing.md)) {
            CardName(
                name = (almostCombo.ownedCardNames + almostCombo.missingCardName).joinToString(" + "),
                style = ty.titleMedium,
                color = mc.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.deck_studio_combos_missing_card, almostCombo.missingCardName),
                style = ty.labelMedium,
                color = mc.primaryAccent,
            )
            if (almostCombo.produces.isNotEmpty()) {
                Text(
                    text = almostCombo.produces.joinToString(", "),
                    style = ty.labelMedium,
                    color = mc.goldMtg,
                )
            }
            Spacer(Modifier.height(spacing.xxs))
            // Same rationale as ComboRow above -- may contain literal mana tokens.
            OracleText(
                text = almostCombo.description,
                style = ty.bodySmall.copy(color = mc.textSecondary),
            )

            Spacer(Modifier.height(spacing.sm))
            ComboCardsRow(
                names = almostCombo.ownedCardNames,
                cardsByName = cardsByName,
                onCardClick = onCardClick,
                highlightedName = almostCombo.missingCardName,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )

            if (showUseAsSeed) {
                Spacer(Modifier.height(spacing.sm))
                OutlinedButton(
                    onClick = onUseAsSeed,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.5f)),
                    shape = ButtonShape,
                ) {
                    Text(text = stringResource(R.string.deck_wizard_build_this), style = ty.labelLarge, color = mc.primaryAccent)
                }
            }
        }
    }
}

/**
 * A horizontally-scrollable row of combo card tiles -- [highlightedName] (the missing card, if
 * any) is appended last so it always stands out as "the one you don't have." Each name renders as
 * a real card-image tile when resolved in [cardsByName], else a plain text chip fallback (mirrors
 * the pre-image-tile [ComboRow]/[AlmostComboRow] look for that one card only, never the whole
 * row).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ComboCardsRow(
    names: List<String>,
    cardsByName: Map<String, Card>,
    onCardClick: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    highlightedName: String? = null,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    val allNames = if (highlightedName != null) names + highlightedName else names
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        items(allNames, key = { it }) { name ->
            val isHighlighted = name == highlightedName
            val card = cardsByName[name]
            if (card != null) {
                ComboCardTile(
                    card = card,
                    isHighlighted = isHighlighted,
                    onClick = { onCardClick(card.scryfallId) },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            } else {
                Row(
                    modifier = Modifier
                        .clip(ChipShape)
                        .background(if (isHighlighted) mc.primaryAccent.copy(alpha = 0.15f) else mc.surfaceVariant)
                        .clickable { onCardClick(name) }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = spacing.sm, vertical = spacing.xs),
                ) {
                    Text(
                        text = name,
                        style = ty.labelMedium,
                        color = if (isHighlighted) mc.primaryAccent else mc.textPrimary,
                    )
                }
            }
        }
    }
}

/** One full card-image tile inside [ComboCardsRow], with an optional shared-element transition
 * into the real [com.mmg.manahub.feature.carddetail.presentation.CardDetailScreen] -- the SAME
 * `"card-image-${scryfallId}"` key format used by [com.mmg.manahub.feature.addcard.presentation
 * .AddCardScreen]'s spotlight tiles, so the transition connects across screens. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ComboCardTile(
    card: Card,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val mc = MaterialTheme.magicColors
    Box(
        modifier = Modifier
            .width(72.dp)
            .aspectRatio(COMBO_CARD_ASPECT_RATIO)
            .clip(CardShape)
            .then(
                if (isHighlighted) Modifier.border(2.dp, mc.primaryAccent, CardShape) else Modifier
            )
            .then(
                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "card-image-${card.scryfallId}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            clipInOverlayDuringTransition = OverlayClip(CardShape),
                            renderInOverlayDuringTransition = true,
                        )
                    }
                } else Modifier
            )
            .background(mc.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = card.imageNormal,
            contentDescription = card.name,
            placeholder = painterResource(Res.drawable.mtg_card_back),
            error = painterResource(Res.drawable.mtg_card_back),
            fallback = painterResource(Res.drawable.mtg_card_back),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
