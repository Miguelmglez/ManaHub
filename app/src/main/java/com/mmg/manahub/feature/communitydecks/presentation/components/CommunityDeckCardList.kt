package com.mmg.manahub.feature.communitydecks.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.model.CommunityDeckCard

/**
 * Emits a community deck's cards as Commander / Mainboard / Sideboard sections
 * directly into an enclosing [LazyListScope].
 *
 * Exposed as a [LazyListScope] extension (rather than its own [LazyColumn]) so the
 * caller can compose these rows into a SINGLE screen-level lazy list — nesting two
 * vertically-scrolling lists is forbidden by Compose and the project UI rules.
 *
 * Keys are composed from the zone prefix + a stable index so duplicate card names
 * (which can legitimately appear across or within zones) never collide.
 *
 * @param cards all card entries of the deck.
 */
fun LazyListScope.communityDeckCardItems(cards: List<CommunityDeckCard>) {
    val commanders = cards.filter { it.isCommander }
    val mainboard = cards.filter { !it.isSideboard && !it.isCommander }
    val sideboard = cards.filter { it.isSideboard }
    val mainboardCount = mainboard.sumOf { it.quantity }
    val sideboardCount = sideboard.sumOf { it.quantity }

    if (commanders.isNotEmpty()) {
        item(key = "header_commander") {
            SectionHeader(stringResource(R.string.community_deck_cards_commander))
        }
        cardRows(commanders, prefix = "commander")
    }

    if (mainboard.isNotEmpty()) {
        item(key = "header_mainboard") {
            SectionHeader(stringResource(R.string.community_deck_cards_mainboard, mainboardCount))
        }
        cardRows(mainboard, prefix = "main")
    }

    if (sideboard.isNotEmpty()) {
        item(key = "header_sideboard") {
            SectionHeader(stringResource(R.string.community_deck_cards_sideboard, sideboardCount))
        }
        cardRows(sideboard, prefix = "side")
    }
}

/**
 * Standalone composable wrapper around [communityDeckCardItems] for contexts that
 * need an independent scroll container (e.g. a dedicated card-list screen).
 *
 * Do NOT place this inside another vertically-scrolling parent.
 *
 * @param cards all card entries of the deck.
 */
@Composable
fun CommunityDeckCardList(
    cards: List<CommunityDeckCard>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        communityDeckCardItems(cards)
    }
}

/**
 * Emits [items] with a stable, collision-free key derived from the zone [prefix]
 * + the item index.
 */
private fun LazyListScope.cardRows(
    items: List<CommunityDeckCard>,
    prefix: String,
) {
    items(
        count = items.size,
        key = { index -> "${prefix}_${index}_${items[index].name}" },
    ) { index ->
        CardRow(items[index])
    }
}

/** Section title (e.g. "Mainboard (60)"). */
@Composable
private fun SectionHeader(title: String) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Text(
        text = title,
        style = ty.titleMedium,
        color = mc.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
    )
}

/** A single "{qty}× {name}" card row. */
@Composable
private fun CardRow(card: CommunityDeckCard) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(horizontal = spacing.lg, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${card.quantity}×",
            style = ty.bodyMedium,
            color = mc.textSecondary,
        )
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = card.name,
            style = ty.bodyMedium,
            color = mc.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}
