package com.mmg.manahub.feature.communitydecks.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.mmg.manahub.R
import com.mmg.manahub.core.model.CommunityDeckCard
import com.mmg.manahub.core.ui.components.CardListItem
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.presentation.components.GroupHeader

/**
 * Ordered primary-type buckets for the Mainboard grouping, mirroring the Deck Studio editor's
 * grouped card list (`groupCards`/`GroupHeader` in
 * `feature/decks/presentation/components/DeckEditorComponents.kt`).
 */
private val MAINBOARD_TYPE_ORDER = listOf(
    "Creatures", "Instants", "Sorceries", "Artifacts", "Enchantments", "Planeswalkers", "Battles", "Lands", "Other",
)

/**
 * Buckets [card] into one of [MAINBOARD_TYPE_ORDER] from its composed type line.
 * First match wins (so e.g. "Artifact Creature" lands in Creatures, matching the deck editor's rule).
 */
private fun typeGroupOf(card: CommunityDeckCard): String {
    val type = card.typeLine
    return when {
        type.contains("Creature") -> "Creatures"
        type.contains("Instant") -> "Instants"
        type.contains("Sorcery") -> "Sorceries"
        type.contains("Artifact") -> "Artifacts"
        type.contains("Enchantment") -> "Enchantments"
        type.contains("Planeswalker") -> "Planeswalkers"
        type.contains("Battle") -> "Battles"
        type.contains("Land") -> "Lands"
        else -> "Other"
    }
}

/**
 * Emits a community deck's cards as Commander / Mainboard / Sideboard sections
 * directly into an enclosing [LazyListScope].
 *
 * Exposed as a [LazyListScope] extension (rather than its own [LazyColumn]) so the
 * caller can compose these rows into a SINGLE screen-level lazy list — nesting two
 * vertically-scrolling lists is forbidden by Compose and the project UI rules.
 *
 * The Mainboard section is further grouped by primary card type (see [MAINBOARD_TYPE_ORDER])
 * with [GroupHeader] subheaders. Commander/Sideboard stay flat — both are small zones where
 * grouping adds no navigational value.
 *
 * Keys are composed from the zone/group prefix + a stable index so duplicate card names
 * (which can legitimately appear across or within zones, or across type groups) never collide.
 *
 * @param cards all card entries of the deck.
 * @param onCardClick invoked with a card's [CommunityDeckCard.scryfallId] when a resolved row
 *   is tapped. Rows whose `scryfallId` is blank (Archidekt didn't resolve a printing) render
 *   without an image and are not clickable.
 */
fun LazyListScope.communityDeckCardItems(
    cards: List<CommunityDeckCard>,
    onCardClick: (String) -> Unit = {},
) {
    val commanders = cards.filter { it.isCommander }
    val mainboard = cards.filter { !it.isSideboard && !it.isCommander }
    val sideboard = cards.filter { it.isSideboard }
    val mainboardCount = mainboard.sumOf { it.quantity }
    val sideboardCount = sideboard.sumOf { it.quantity }

    if (commanders.isNotEmpty()) {
        item(key = "header_commander") {
            SectionHeader(stringResource(R.string.community_deck_cards_commander))
        }
        cardRows(commanders, prefix = "commander", onCardClick = onCardClick)
    }

    if (mainboard.isNotEmpty()) {
        item(key = "header_mainboard") {
            SectionHeader(stringResource(R.string.community_deck_cards_mainboard, mainboardCount))
        }
        val groupedMainboard = MAINBOARD_TYPE_ORDER.mapNotNull { label ->
            val group = mainboard.filter { typeGroupOf(it) == label }
            if (group.isEmpty()) null else label to group
        }
        groupedMainboard.forEach { (label, group) ->
            item(key = "header_mainboard_$label") {
                GroupHeader(
                    label = label,
                    count = group.sumOf { it.quantity },
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg),
                )
            }
            cardRows(group, prefix = "main_$label", onCardClick = onCardClick)
        }
    }

    if (sideboard.isNotEmpty()) {
        item(key = "header_sideboard") {
            SectionHeader(stringResource(R.string.community_deck_cards_sideboard, sideboardCount))
        }
        cardRows(sideboard, prefix = "side", onCardClick = onCardClick)
    }
}

/**
 * Standalone composable wrapper around [communityDeckCardItems] for contexts that
 * need an independent scroll container (e.g. a dedicated card-list screen).
 *
 * Do NOT place this inside another vertically-scrolling parent.
 *
 * @param cards all card entries of the deck.
 * @param onCardClick see [communityDeckCardItems].
 */
@Composable
fun CommunityDeckCardList(
    cards: List<CommunityDeckCard>,
    modifier: Modifier = Modifier,
    onCardClick: (String) -> Unit = {},
) {
    LazyColumn(modifier = modifier) {
        communityDeckCardItems(cards, onCardClick)
    }
}

/**
 * Emits [items] as rich [CardListItem] rows, with a stable, collision-free key derived
 * from the zone/group [prefix] + the item index.
 */
private fun LazyListScope.cardRows(
    items: List<CommunityDeckCard>,
    prefix: String,
    onCardClick: (String) -> Unit,
) {
    items(
        count = items.size,
        key = { index -> "${prefix}_${index}_${items[index].name}" },
    ) { index ->
        val card = items[index]
        CardListItem(
            name = card.name,
            imageUrl = card.imageUrl,
            priceUsd = card.priceUsd,
            priceEur = card.priceEur,
            onClick = { if (card.scryfallId.isNotBlank()) onCardClick(card.scryfallId) },
            quantityText = "×${card.quantity}",
            setCode = card.setCode.takeIf { it.isNotBlank() },
            setName = card.setName.takeIf { it.isNotBlank() },
            rarity = card.rarity.takeIf { it.isNotBlank() },
            typeLine = card.typeLine.takeIf { it.isNotBlank() },
            containerColor = Color.Transparent,
        )
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
