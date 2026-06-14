package com.mmg.manahub.feature.decks.presentation.improvement.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.usecase.AddOrigin
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion
import com.mmg.manahub.feature.decks.domain.engine.CardFit

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
                Text(
                    text = fit.card.name,
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
                Text(
                    text = fit.card.name,
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                ) {
                    if (gapRole != null) {
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
