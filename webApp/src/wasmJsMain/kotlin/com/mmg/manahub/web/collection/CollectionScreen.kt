package com.mmg.manahub.web.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.mmg.manahub.core.model.CollectionCardGroup
import com.mmg.manahub.core.model.CollectionGroupingMode
import com.mmg.manahub.core.model.CollectionSection
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.ui.components.CardListItem
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicCard
import com.mmg.manahub.core.ui.components.ManaHubSelector
import com.mmg.manahub.core.ui.layout.AdaptiveCardGrid
import com.mmg.manahub.core.ui.layout.ManaWindowSizeClass
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * Collection — the fourth REAL `:webApp` MVP screen (web roadmap W3d), backed by
 * [CollectionViewModel] -> [com.mmg.manahub.core.domain.repository.UserCardRepository]
 * (`WebUserCardRepository`) -> real Supabase data.
 *
 * Handles all three states relevant to this read-only slice (CLAUDE.md requirement): loading,
 * empty (no cards owned yet), and content. There is no error state surfaced here yet -- this
 * screen has no mutations of its own; the one write path proving the repository end-to-end is
 * [com.mmg.manahub.web.search.CardSearchScreen]'s "tap a result to add" flow, whose own error
 * handling lives there.
 *
 * Deliberately minimal, mirroring [com.mmg.manahub.web.decks.DeckListScreen]'s scope discipline:
 * no quantity edit, no foil/condition/language edit -- those remain a separate, much bigger future
 * slice (master plan §5/§6 W4+). Web roadmap W4b adds card detail navigation: tapping a tile's
 * image (no competing gesture existed here, unlike the search screen's tap-to-add) opens
 * [com.mmg.manahub.web.carddetail.CardDetailScreen] via [onCardClick].
 *
 * **Settings expansion slice**: [CollectionViewMode] is now fully respected, not just persisted --
 * a header toggle (this screen) and the [com.mmg.manahub.web.settings.SettingsScreen] picker both
 * write through [CollectionViewModel.setViewMode] to the SAME `uiState.viewMode`. `GRID`
 * (default, unchanged) renders the original [AdaptiveCardGrid]/[CollectionCardTile] pair; `LIST`
 * renders a [LazyColumn] of [CardListItem]'s dedicated [CollectionCardGroup] overload.
 *
 * **Grouping-wiring slice**: [CollectionGroupingMode] is now fully respected, not just persisted --
 * both this screen's inline [ManaHubSelector] and the Settings screen's picker write through
 * [CollectionViewModel.setGroupingMode] to the SAME `uiState.groupingMode`. Grouping and view-mode
 * are orthogonal: [CollectionUiState.sections] is rendered in EITHER the grid or the list layout
 * depending on [CollectionUiState.viewMode], with a section header rendered only when
 * [CollectionGroupingMode] != [CollectionGroupingMode.NONE] (the [CollectionGroupingMode.NONE]
 * section always exists per [com.mmg.manahub.core.model.groupCollection]'s contract, it just has a
 * blank `labelToken` and is rendered header-less). Items are keyed
 * `"${section.labelToken}|${item.groupKey}"`, never the bare `groupKey` -- required * sections. Callers must key items by "$labelToken|${item.groupKey}" to avoid collisions.
 */
@Composable
fun CollectionScreen(windowSizeClass: ManaWindowSizeClass, onCardClick: (String) -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val viewModel = koinViewModel<CollectionViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Your Collection",
                style = typography.titleLarge,
                color = colors.textPrimary,
            )
            Row {
                IconButton(onClick = { viewModel.setViewMode(CollectionViewMode.GRID) }) {
                    Icon(
                        imageVector = Icons.Filled.GridView,
                        contentDescription = "Grid view",
                        tint = if (uiState.viewMode == CollectionViewMode.GRID) colors.primaryAccent else colors.textSecondary,
                    )
                }
                IconButton(onClick = { viewModel.setViewMode(CollectionViewMode.LIST) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = "List view",
                        tint = if (uiState.viewMode == CollectionViewMode.LIST) colors.primaryAccent else colors.textSecondary,
                    )
                }
            }
        }

        ManaHubSelector(
            icon = Icons.Filled.Layers,
            label = "Group by:",
            valueText = uiState.groupingMode.toDisplayLabel(),
            items = CollectionGroupingMode.entries,
            selectedItem = uiState.groupingMode,
            onSelect = viewModel::setGroupingMode,
            itemLabel = { it.toDisplayLabel() },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primaryAccent)
                }

                uiState.groups.isEmpty() -> EmptyState(
                    title = "No cards yet",
                    subtitle = "Search for a card and add it to your collection to see it here.",
                )

                uiState.viewMode == CollectionViewMode.LIST -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    uiState.sections.forEach { section ->
                        if (uiState.groupingMode != CollectionGroupingMode.NONE) {
                            item(key = "header|${section.labelToken}") {
                                CollectionSectionHeader(section, uiState.groupingMode)
                            }
                        }
                        items(section.items, key = { "${section.labelToken}|${it.groupKey}" }) { entry ->
                            CardListItem(
                                item = entry,
                                onClick = { onCardClick(entry.card.scryfallId) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                else -> AdaptiveCardGrid(
                    windowSizeClass = windowSizeClass,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = spacing.lg),
                ) {
                    uiState.sections.forEach { section ->
                        if (uiState.groupingMode != CollectionGroupingMode.NONE) {
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "header|${section.labelToken}",
                            ) {
                                CollectionSectionHeader(section, uiState.groupingMode)
                            }
                        }
                        items(section.items, key = { "${section.labelToken}|${it.groupKey}" }) { entry ->
                            CollectionCardTile(
                                entry = entry,
                                onClick = { onCardClick(entry.card.scryfallId) },
                                modifier = Modifier.padding(spacing.xs),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Section header shared by both the grid and the list layout -- section name (resolved from the
 * raw [CollectionSection.labelToken] via [collectionSectionLabel]) plus a compact copy count.
 * Deliberately no collapse affordance (unlike Android's `CollectionScreen`, which has one) -- out
 * of scope for this wiring slice; the grouping/view-mode combination is the thing being proven
 * here, not section collapsing.
 */
@Composable
private fun CollectionSectionHeader(section: CollectionSection, mode: CollectionGroupingMode) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.sm, bottom = spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = collectionSectionLabel(section, mode),
            style = typography.titleMedium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${section.items.size} · x${section.totalCopies}",
            style = typography.labelSmall,
            color = colors.textSecondary,
        )
    }
}

/**
 * Resolves a [CollectionSection.labelToken] to a readable display string, per [mode]. A
 * deliberately minimal resolver (no * (CLAUDE.md) so a full localization layer isn't needed.
 */
private fun collectionSectionLabel(section: CollectionSection, mode: CollectionGroupingMode): String {
    val token = section.labelToken
    return when (mode) {
        CollectionGroupingMode.NONE -> token
        CollectionGroupingMode.TYPE -> token
        CollectionGroupingMode.COLOR -> when (token) {
            "W" -> "White"
            "U" -> "Blue"
            "B" -> "Black"
            "R" -> "Red"
            "G" -> "Green"
            else -> token // Multicolor / Colorless / Land
        }
        CollectionGroupingMode.CMC -> when (token) {
            "Lands" -> "Lands"
            "7+" -> "7+ mana"
            else -> "$token mana"
        }
        CollectionGroupingMode.SET ->
            section.items.firstOrNull()?.card?.setName?.ifBlank { token.uppercase() } ?: token.uppercase()
        CollectionGroupingMode.RARITY ->
            token.replaceFirstChar { it.uppercase() }.ifBlank { "Other" }

    }
}

private fun CollectionGroupingMode.toDisplayLabel(): String = when (this) {
    CollectionGroupingMode.NONE -> "None"
    CollectionGroupingMode.TYPE -> "Type"
    CollectionGroupingMode.COLOR -> "Color"
    CollectionGroupingMode.CMC -> "Mana value"
    CollectionGroupingMode.SET -> "Set"
    CollectionGroupingMode.RARITY -> "Rarity"

}

/**
 * Minimal owned-card tile — built from the same [MagicCard] + [CardName] pair
 * [com.mmg.manahub.web.search.CardSearchScreen]'s `CardSearchResultTile` uses (NOT
 * [com.mmg.manahub.core.ui.components.CardGridItem]; see that tile's own KDoc for why), plus a
 * quantity/foil line drawn from [CollectionCardGroup] -- the ownership data a raw search result
 * never has.
 */
@Composable
private fun CollectionCardTile(entry: CollectionCardGroup, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        MagicCard(
            card = entry.card,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
        CardName(
            name = entry.card.name,
            showFrontOnly = true,
            style = typography.labelSmall,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildString {
                append("x${entry.totalQuantity}")
                if (entry.hasFoil) append(" · Foil")
            },
            style = typography.labelSmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
