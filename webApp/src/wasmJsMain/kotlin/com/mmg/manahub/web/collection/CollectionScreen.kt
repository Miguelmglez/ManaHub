package com.mmg.manahub.web.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.mmg.manahub.core.model.UserCardWithCard
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
 * no quantity edit, no foil/condition/language edit, no card detail navigation -- those are a
 * separate, much bigger future slice (master plan §5/§6 W4+).
 */
@Composable
fun CollectionScreen(windowSizeClass: ManaWindowSizeClass) {
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
        Text(
            text = "Your Collection",
            style = typography.titleLarge,
            color = colors.textPrimary,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primaryAccent)
                }

                uiState.cards.isEmpty() -> EmptyState(
                    title = "No cards yet",
                    subtitle = "Search for a card and add it to your collection to see it here.",
                )

                else -> AdaptiveCardGrid(
                    windowSizeClass = windowSizeClass,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = spacing.lg),
                ) {
                    items(uiState.cards, key = { it.userCard.id }) { entry ->
                        CollectionCardTile(entry = entry, modifier = Modifier.padding(spacing.xs))
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
private fun CollectionCardTile(entry: UserCardWithCard, modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        MagicCard(
            card = entry.card,
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
