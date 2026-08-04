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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
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
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.ui.components.CardListItem
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicCard
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
 * renders a [LazyColumn] of the general-purpose [CardListItem] overload (the same one
 * [com.mmg.manahub.web.deckeditor.DeckEditorScreen] uses for its board rows) -- reused directly
 * rather than building a new row component, per that screen's own documented "check `CardListItem`
 * for a lower-level overload before building a new tile" lesson.
 *
 * [CollectionGroupingMode][com.mmg.manahub.core.model.CollectionGroupingMode] is DELIBERATELY NOT
 * wired here yet -- see [com.mmg.manahub.web.settings.SettingsViewModel]'s KDoc for why (needs a
 * `CollectionCardGroup`-shaped collapsing step this screen's raw `List<UserCardWithCard>` doesn't
 * have).
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

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primaryAccent)
                }

                uiState.cards.isEmpty() -> EmptyState(
                    title = "No cards yet",
                    subtitle = "Search for a card and add it to your collection to see it here.",
                )

                uiState.viewMode == CollectionViewMode.LIST -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    items(uiState.cards, key = { it.userCard.id }) { entry ->
                        CollectionCardListRow(
                            entry = entry,
                            onClick = { onCardClick(entry.card.scryfallId) },
                        )
                    }
                }

                else -> AdaptiveCardGrid(
                    windowSizeClass = windowSizeClass,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = spacing.lg),
                ) {
                    items(uiState.cards, key = { it.userCard.id }) { entry ->
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

/**
 * Minimal owned-card tile — built from the same [MagicCard] + [CardName] pair
 * [com.mmg.manahub.web.search.CardSearchScreen]'s `CardSearchResultTile` uses (NOT
 * [com.mmg.manahub.core.ui.components.CardGridItem]; see that tile's own KDoc for why), plus a
 * quantity/foil line drawn from [UserCardWithCard.userCard] -- the ownership data a raw search
 * result never has.
 */
@Composable
private fun CollectionCardTile(entry: UserCardWithCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
                append("x${entry.userCard.quantity}")
                if (entry.userCard.isFoil) append(" · Foil")
            },
            style = typography.labelSmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Owned-card row for [CollectionViewMode.LIST] -- the general-purpose [CardListItem] overload
 * (name/imageUrl/priceUsd/priceEur + optional fields), the SAME one
 * [com.mmg.manahub.web.deckeditor.DeckEditorScreen]'s board rows use. Foil-aware price selection
 * (foil price when [UserCard.isFoil][com.mmg.manahub.core.model.UserCard.isFoil]) mirrors
 * [CardListItem]'s own `CollectionCardGroup` overload logic by hand, since a raw
 * [UserCardWithCard] isn't that shape.
 */
@Composable
private fun CollectionCardListRow(entry: UserCardWithCard, onClick: () -> Unit) {
    CardListItem(
        name = entry.card.name,
        imageUrl = entry.card.imageNormal,
        priceUsd = if (entry.userCard.isFoil) entry.card.priceUsdFoil else entry.card.priceUsd,
        priceEur = if (entry.userCard.isFoil) entry.card.priceEurFoil else entry.card.priceEur,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        quantityText = "×${entry.userCard.quantity}",
        hasFoil = entry.userCard.isFoil,
        isStale = entry.card.isStale,
        setCode = entry.card.setCode,
        setName = entry.card.setName,
        rarity = entry.card.rarity,
        typeLine = entry.card.typeLine,
        scryfallId = entry.card.scryfallId,
    )
}
