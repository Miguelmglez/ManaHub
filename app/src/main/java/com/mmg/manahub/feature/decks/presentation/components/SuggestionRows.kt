package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.usecase.AddOrigin
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.CommunityAddSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.SimilarDeckResult
import kotlin.math.roundToInt

/** Visual flavour of a [SuggestionTagChip], mapped to a semantic token. */
enum class SuggestionTagTone { CUT, GAP, COLLECTION }

/**
 * A small pill describing why a card is suggested. Stateless; tone selects an error/positive/accent
 * tint from [com.mmg.manahub.core.ui.theme.magicColors]. The chip is decorative — its meaning is
 * also conveyed by the row's icon button, so it carries no separate content description.
 */
@Composable
fun SuggestionTagChip(
    text: String,
    tone: SuggestionTagTone,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val color = when (tone) {
        SuggestionTagTone.CUT -> mc.lifeNegative
        SuggestionTagTone.GAP -> mc.lifePositive
        SuggestionTagTone.COLLECTION -> mc.secondaryAccent
    }
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = ChipShape,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.magicTypography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.sm,
                vertical = MaterialTheme.spacing.xs,
            ),
        )
    }
}

/** Card art thumbnail used by both suggestion rows. */
@Composable
private fun SuggestionThumb(imageUrl: String?, name: String) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 62.dp)
            .clip(MaterialTheme.shapes.small),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * One CUT candidate: art, name, the primary reason chip, the fit score, and a remove button.
 * Stateless — the caller supplies [onCut].
 */
@Composable
fun CutSuggestionRow(
    fit: CardFit,
    onCut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val reasonLabel = fit.primaryCutReason()?.label()

    Surface(
        color = mc.surface,
        shape = CardShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            SuggestionThumb(imageUrl = fit.card.imageArtCrop ?: fit.card.imageNormal, name = fit.card.name)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
            ) {
                CardName(
                    name = fit.card.name,
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                ) {
                    if (reasonLabel != null) {
                        SuggestionTagChip(text = reasonLabel, tone = SuggestionTagTone.CUT)
                    }
                    Text(
                        text = stringResource(R.string.deck_doctor_fit_score, fit.score),
                        style = ty.labelSmall,
                        color = mc.textSecondary,
                    )
                }
            }

            IconButton(
                onClick = onCut,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = stringResource(R.string.deck_doctor_cut_cd, fit.card.name),
                    tint = mc.lifeNegative,
                )
            }
        }
    }
}

/**
 * One ADD suggestion: art, name, a "Fills: <role>" gap chip (when applicable), an origin badge,
 * a price (free for owned cards), and an add button. Stateless — the caller supplies [onAdd].
 */
@Composable
fun AddSuggestionRow(
    suggestion: AddSuggestion,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val fit = suggestion.fit
    // WS8.2: prefer the archetype-aware gap (more specific — names the resolved plan's own band)
    // over the legacy GENERIC-skeleton gap when both are present; a card can only ever carry one
    // or the other in practice (FillsArchetypeGap only fires when a non-GENERIC/themed skeleton
    // resolved, at which point the base engine's own FillsGap reason becomes the less-specific
    // signal for the same underlying gap).
    val archetypeGap = fit.fillsArchetypeGapReason()
    val gapRole = fit.fillsGapRole()

    Surface(
        color = mc.surface,
        shape = CardShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            SuggestionThumb(imageUrl = fit.card.imageArtCrop ?: fit.card.imageNormal, name = fit.card.name)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
            ) {
                CardName(
                    name = fit.card.name,
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                ) {
                    if (archetypeGap != null) {
                        SuggestionTagChip(
                            text = stringResource(
                                R.string.deck_reason_fills_archetype_gap_chip,
                                archetypeGap.roleKey.archetypeRoleLabel(),
                                archetypeGap.current,
                                archetypeGap.ideal,
                            ),
                            tone = SuggestionTagTone.GAP,
                        )
                    } else if (gapRole != null) {
                        SuggestionTagChip(
                            text = stringResource(R.string.deck_reason_fills_gap, gapRole.label()),
                            tone = SuggestionTagTone.GAP,
                        )
                    }
                    SuggestionTagChip(
                        text = suggestion.origin.label(),
                        tone = suggestion.origin.tone(),
                    )
                    // Owned cards are free; otherwise show the EUR price (or "No price" when unknown —
                    // we never invent a price). Phase 6 surfaces real prices for NEW/WISHLIST cards.
                    Text(
                        text = suggestion.priceLabel(),
                        style = ty.labelSmall,
                        color = mc.textSecondary,
                    )
                }
            }

            IconButton(
                onClick = onAdd,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.deck_doctor_add_cd, fit.card.name),
                    tint = mc.lifePositive,
                )
            }
        }
    }
}

/** English label for an [AddOrigin] badge. */
@Composable
private fun AddOrigin.label(): String = stringResource(
    when (this) {
        AddOrigin.COLLECTION -> R.string.deck_reason_in_collection
        AddOrigin.WISHLIST -> R.string.deck_doctor_origin_wishlist
        AddOrigin.NEW -> R.string.deck_doctor_origin_new
    }
)

/** Chip tone per origin: owned = positive accent, wishlist/new = supporting accent. */
private fun AddOrigin.tone(): SuggestionTagTone = when (this) {
    AddOrigin.COLLECTION -> SuggestionTagTone.GAP
    AddOrigin.WISHLIST, AddOrigin.NEW -> SuggestionTagTone.COLLECTION
}

/**
 * The price label for an add suggestion: "Free" when the card is owned (no buying needed), the EUR
 * price formatted as "X.XX €" when known, or "No price" when Scryfall has no EUR price (never invented).
 */
@Composable
private fun AddSuggestion.priceLabel(): String = when {
    fit.isOwned -> stringResource(R.string.deck_doctor_price_free)
    fit.card.priceEur != null ->
        stringResource(R.string.deck_doctor_price_eur, String.format(java.util.Locale.US, "%.2f", fit.card.priceEur))
    else -> stringResource(R.string.deck_doctor_price_unknown)
}

// ─────────────────────────────────────────────────────────────────────────────
//  Motor B — community suggestions (Deck Doctor Community/Archetype plan, Phase 4)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * "Popular in similar decks" (Motor B) row: an [AddSuggestionRow] SIBLING, not a fork — it reuses
 * [SuggestionThumb]/[SuggestionTagChip] and mirrors the layout exactly, but renders a
 * [CommunityAddSuggestion] (community-aggregate synergy/inclusion, no [CardFit] score components)
 * with an inclusion-percentage caption ("78% of &lt;sourceLabel&gt; decks") instead of a fit score,
 * and a trailing "view decks containing this card" action alongside Add.
 *
 * @param sourceLabel the commander name (Commander format) or a short "similar decks" label
 *        (60-card) shown in the inclusion caption.
 * @param onAdd adds one copy of the card to the live deck.
 * @param onViewDecks navigates to [com.mmg.manahub.app.navigation.Screen.CommunityDecksByCard] for
 *        this card.
 */
@Composable
fun CommunityAddSuggestionRow(
    suggestion: CommunityAddSuggestion,
    sourceLabel: String,
    onAdd: () -> Unit,
    onViewDecks: () -> Unit,
    onCardTap: () -> Unit,
    modifier: Modifier = Modifier,
    // Community Decks (browse/import) feature flag — hides just this row's "view decks" affordance
    // when that feature is disabled, independent of the Motor B community suggestion it sits on.
    showViewDecksAction: Boolean = true,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val card = suggestion.card
    val inclusionPercent = (suggestion.inclusionPct * 100).roundToInt().coerceIn(0, 100)

    Surface(
        onClick = onCardTap,
        color = mc.surface,
        shape = CardShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            SuggestionThumb(imageUrl = card.imageArtCrop ?: card.imageNormal, name = card.name)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
            ) {
                CardName(
                    name = card.name,
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.deck_doctor_community_inclusion, inclusionPercent, sourceLabel),
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                ) {
                    if (suggestion.ownedInCollection) {
                        SuggestionTagChip(
                            text = stringResource(R.string.deck_reason_in_collection),
                            tone = SuggestionTagTone.COLLECTION,
                        )
                    }
                    if (suggestion.fillsGapRoles.isNotEmpty()) {
                        SuggestionTagChip(
                            text = stringResource(R.string.deck_doctor_community_fills_gap),
                            tone = SuggestionTagTone.GAP,
                        )
                    }
                }
            }

            if (showViewDecksAction) {
                IconButton(onClick = onViewDecks, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.deck_doctor_community_view_decks_cd, card.name),
                        tint = mc.textSecondary,
                    )
                }
            }
            IconButton(onClick = onAdd, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.deck_doctor_add_cd, card.name),
                    tint = mc.lifePositive,
                )
            }
        }
    }
}

/**
 * A single "Decks like yours" carousel card: deck name, owner, view count, and a color-similarity
 * badge. Stateless; the caller supplies [onClick] (navigates to the real, importable
 * [com.mmg.manahub.app.navigation.Screen.CommunityDeckDetail]).
 */
@Composable
fun SimilarDeckCard(
    result: SimilarDeckResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        onClick = onClick,
        shape = CardShape,
        color = mc.surface,
        modifier = modifier.width(180.dp),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = result.name,
                style = ty.titleMedium,
                color = mc.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = result.ownerUsername,
                style = ty.bodySmall,
                color = mc.textDisabled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = mc.textDisabled,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = " ${result.viewCount}",
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                )
            }
            SuggestionTagChip(
                text = stringResource(
                    R.string.deck_doctor_community_color_match,
                    (result.colorSimilarity * 100).roundToInt().coerceIn(0, 100),
                ),
                tone = SuggestionTagTone.COLLECTION,
            )
        }
    }
}
