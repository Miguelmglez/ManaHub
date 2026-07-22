package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.model.AlmostCombo
import com.mmg.manahub.feature.decks.domain.model.Combo

/**
 * Deck Engine Unification plan D7 (Phase 4.3) — a fully-owned combo card for the synergy
 * browser's Combos tab. Card names are tappable chips (not full card-art thumbnails, unlike
 * [DiscoveryRowV2] -- a combo's cards may not be in the user's own image cache and Spellbook
 * doesn't return artwork, only names) that open card detail via [onCardClick] when the tapped
 * name resolves to a known scryfallId is out of scope here -- [onCardClick] receives the raw
 * NAME; the caller is responsible for name-based navigation if it wants one (mirrors this tab's
 * "names only" data shape from the API).
 */
@Composable
internal fun ComboRow(
    combo: Combo,
    onCardClick: (String) -> Unit,
    onUseAsSeed: () -> Unit,
    modifier: Modifier = Modifier,
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
            Text(
                text = combo.description,
                style = ty.bodySmall,
                color = mc.textSecondary,
                maxLines = 3,
            )

            Spacer(Modifier.height(spacing.sm))
            NameChipRow(names = combo.cardNames, onCardClick = onCardClick)

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

/**
 * "You're 1 card away" -- the [AlmostCombo] sibling of [ComboRow]. [AlmostCombo.missingCardName]
 * is visually distinguished (an accent-tinted chip) from the owned cards.
 */
@Composable
internal fun AlmostComboRow(
    almostCombo: AlmostCombo,
    onCardClick: (String) -> Unit,
    onUseAsSeed: () -> Unit,
    modifier: Modifier = Modifier,
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
            Text(
                text = almostCombo.description,
                style = ty.bodySmall,
                color = mc.textSecondary,
                maxLines = 3,
            )

            Spacer(Modifier.height(spacing.sm))
            NameChipRow(
                names = almostCombo.ownedCardNames,
                onCardClick = onCardClick,
                highlightedName = almostCombo.missingCardName,
            )

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

/** A wrapping row of tappable name chips -- [highlightedName] (the missing card, if any) renders
 * in the accent color and is appended last so it always stands out as "the one you don't have." */
@Composable
private fun NameChipRow(
    names: List<String>,
    onCardClick: (String) -> Unit,
    highlightedName: String? = null,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    val allNames = if (highlightedName != null) names + highlightedName else names
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        allNames.forEach { name ->
            val isHighlighted = name == highlightedName
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
