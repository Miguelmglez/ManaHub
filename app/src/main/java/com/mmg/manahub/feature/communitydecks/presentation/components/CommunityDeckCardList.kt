package com.mmg.manahub.feature.communitydecks.presentation.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
 * @param cards all card entries of the deck. Excluded-from-deck categories (Maybeboard, a custom
 *   "Cut" bucket, ...) are already filtered out upstream by `ArchidektDeckDetailDto.toDomain()` —
 *   every entry here is authoritatively in-deck; no further category filtering is needed here.
 * @param ownedCardIdentityKeys identity keys (`oracleId.ifBlank { name }`, the Card Versions &
 *   Languages convention) of cards already in the user's local collection. A row whose card
 *   matches renders an "already owned" badge (see [cardRows]).
 * @param onCardClick invoked with a card's [CommunityDeckCard.scryfallId] when a resolved row
 *   is tapped. Rows whose `scryfallId` is blank (Archidekt didn't resolve a printing) render
 *   without an image and are not clickable.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun LazyListScope.communityDeckCardItems(
    cards: List<CommunityDeckCard>,
    commanderExpanded: Boolean = true,
    mainboardExpanded: Boolean = true,
    sideboardExpanded: Boolean = true,
    onToggleCommander: () -> Unit = {},
    onToggleMainboard: () -> Unit = {},
    onToggleSideboard: () -> Unit = {},
    onCardClick: (String) -> Unit = {},
    ownedCardIdentityKeys: Set<String> = emptySet(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    // Archidekt cards can be in multiple categories. Sideboard is usually explicit.
    val commanders = cards.filter { it.isCommander }
    val sideboard = cards.filter { it.isSideboard && !it.isCommander }
    val mainboard = cards.filter { card -> !card.isCommander && !card.isSideboard }
    val mainboardCount = mainboard.sumOf { it.quantity }
    val sideboardCount = sideboard.sumOf { it.quantity }

    if (commanders.isNotEmpty()) {
        item(key = "header_commander") {
            SectionHeader(
                title = stringResource(R.string.community_deck_cards_commander),
                expanded = commanderExpanded,
                onToggle = onToggleCommander,
            )
        }
        if (commanderExpanded) {
            cardRows(
                items = commanders,
                prefix = "commander",
                onCardClick = onCardClick,
                ownedCardIdentityKeys = ownedCardIdentityKeys,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    }

    if (mainboard.isNotEmpty()) {
        item(key = "header_mainboard") {
            SectionHeader(
                title = stringResource(R.string.community_deck_cards_mainboard, mainboardCount),
                expanded = mainboardExpanded,
                onToggle = onToggleMainboard,
            )
        }
        if (mainboardExpanded) {
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
                cardRows(
                    items = group,
                    prefix = "main_$label",
                    onCardClick = onCardClick,
                    ownedCardIdentityKeys = ownedCardIdentityKeys,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }
    }

    if (sideboard.isNotEmpty()) {
        item(key = "header_sideboard") {
            SectionHeader(
                title = stringResource(R.string.community_deck_cards_sideboard, sideboardCount),
                expanded = sideboardExpanded,
                onToggle = onToggleSideboard,
            )
        }
        if (sideboardExpanded) {
            cardRows(
                items = sideboard,
                prefix = "side",
                onCardClick = onCardClick,
                ownedCardIdentityKeys = ownedCardIdentityKeys,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
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
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CommunityDeckCardList(
    cards: List<CommunityDeckCard>,
    modifier: Modifier = Modifier,
    onCardClick: (String) -> Unit = {},
    ownedCardIdentityKeys: Set<String> = emptySet(),
) {
    var commanderExpanded by remember { mutableStateOf(true) }
    var mainboardExpanded by remember { mutableStateOf(true) }
    var sideboardExpanded by remember { mutableStateOf(false) }

    LazyColumn(modifier = modifier) {
        communityDeckCardItems(
            cards = cards,
            commanderExpanded = commanderExpanded,
            mainboardExpanded = mainboardExpanded,
            sideboardExpanded = sideboardExpanded,
            onToggleCommander = { commanderExpanded = !commanderExpanded },
            onToggleMainboard = { mainboardExpanded = !mainboardExpanded },
            onToggleSideboard = { sideboardExpanded = !sideboardExpanded },
            onCardClick = onCardClick,
            ownedCardIdentityKeys = ownedCardIdentityKeys,
        )
    }
}

/**
 * Emits [items] as rich [CardListItem] rows, with a stable, collision-free key derived
 * from the zone/group [prefix] + the item index.
 *
 * A row whose card's identity key (`oracleId.ifBlank { name }`, the Card Versions & Languages
 * convention — mirrors `QueueCardItem` in `ScannerScreen.kt`) is present in [ownedCardIdentityKeys]
 * renders an "already in your collection" icon via [CardListItem]'s `extraSupportingContent` slot.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
private fun LazyListScope.cardRows(
    items: List<CommunityDeckCard>,
    prefix: String,
    onCardClick: (String) -> Unit,
    ownedCardIdentityKeys: Set<String> = emptySet(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    items(
        count = items.size,
        key = { index -> "${prefix}_${index}_${items[index].name}" },
    ) { index ->
        val card = items[index]
        val isOwned = (card.oracleId.ifBlank { card.name }) in ownedCardIdentityKeys
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
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            scryfallId = card.scryfallId,
            extraSupportingContent = if (isOwned) {
                {
                    Icon(
                        imageVector = Icons.Rounded.CollectionsBookmark,
                        contentDescription = stringResource(R.string.scanner_already_in_collection),
                        tint = MaterialTheme.magicColors.primaryAccent,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else null,
        )
    }
}

/** Collapsible section title (e.g. "Mainboard (60)"). */
@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "HeaderRotation"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = ty.titleMedium,
            color = mc.primaryAccent,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            tint = mc.primaryAccent,
            modifier = Modifier.graphicsLayer { rotationZ = rotation }
        )
    }
}
