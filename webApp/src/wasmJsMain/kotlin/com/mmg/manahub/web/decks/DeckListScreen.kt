package com.mmg.manahub.web.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * Deck List -- the third REAL `:webApp` MVP screen (web roadmap W3c, extended W4c). Deliberately
 * NOT Deck Studio: no card editing, no format picker inline here -- those now live one level
 * deeper, in [com.mmg.manahub.web.deckeditor.DeckEditorScreen] (web roadmap W4c), reached by
 * tapping a row via [onDeckClick]. The sole purpose of THIS screen is to prove
 * [com.mmg.manahub.core.data.repository.WebDeckRepository] works end-to-end against real Supabase
 * data: "New deck" writes through the repository immediately (no local staging), the list reflects
 * it right away via the repository's own [DeckSummary] flow, and a full page reload re-hydrates
 * from Supabase rather than from anything cached client-side -- proving the write actually landed
 * remotely, not just an optimistic local-only illusion.
 *
 * Handles all four states (CLAUDE.md requirement): loading, error (a failed create, surfaced as a
 * banner above the list rather than replacing it -- the list itself may still be valid), empty
 * (no decks yet), and content. Requires a signed-in session (guest sign-in via the "Account" tab
 * counts) -- creating a deck while signed out surfaces [DeckRepository]'s own
 * `requireUserId()` error message in the error banner rather than silently failing.
 */
@Composable
fun DeckListScreen(onDeckClick: (String) -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val viewModel = koinViewModel<DeckListViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = "Your Decks",
            style = typography.titleLarge,
            color = colors.textPrimary,
        )

        MagicCtaButton(
            onClick = viewModel::createDeck,
            text = "New deck",
            isLoading = uiState.isCreating,
        )

        if (uiState.error != null) {
            Text(
                text = uiState.error.orEmpty(),
                style = typography.bodyMedium,
                color = colors.lifeNegative,
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primaryAccent)
                }

                uiState.decks.isEmpty() -> EmptyState(
                    title = "No decks yet",
                    subtitle = "Create your first deck to get started.",
                    actionLabel = "Create your first deck",
                    onAction = viewModel::createDeck,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    items(uiState.decks, key = { it.id }) { deck ->
                        DeckRow(deck = deck, onClick = { onDeckClick(deck.id) })
                        HorizontalDivider(color = colors.backgroundSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckRow(deck: DeckSummary, onClick: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            text = deck.name,
            style = typography.titleMedium,
            color = colors.textPrimary,
        )
        Text(
            text = "${deck.format.replaceFirstChar { it.uppercase() }} · ${deck.cardCount} cards",
            style = typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}
