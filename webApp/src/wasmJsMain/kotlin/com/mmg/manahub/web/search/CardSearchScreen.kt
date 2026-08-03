package com.mmg.manahub.web.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.MagicCard
import com.mmg.manahub.core.ui.layout.AdaptiveCardGrid
import com.mmg.manahub.core.ui.layout.ManaWindowSizeClass
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * Card Search — the first REAL `:webApp` MVP screen (web roadmap W3b), superseding
 * [com.mmg.manahub.web.theme.ThemeShowcaseScreen] as the "Search" nav destination in [com.mmg.manahub.web.App].
 * Backed by [CardSearchViewModel] -> [com.mmg.manahub.core.domain.repository.CardRepository]
 * (`WebCardRepository`) -> live Scryfall data via the SAME rate-limited/cached
 * `ScryfallRemoteDataSource` Android uses.
 *
 * Handles all four states (CLAUDE.md requirement): idle (no query yet), loading, error, and
 * content -- via [CardSearchUiState].
 *
 * There is deliberately no per-card detail navigation yet (out of scope for this slice -- a future
 * web Card Detail screen is a separate task); tapping a result is a no-op placeholder.
 */
@Composable
fun CardSearchScreen(windowSizeClass: ManaWindowSizeClass) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val viewModel = koinViewModel<CardSearchViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = "Card Search",
            style = typography.titleLarge,
            color = colors.textPrimary,
        )

        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search for a card, e.g. \"Lightning Bolt\"") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
            trailingIcon = {
                IconButton(onClick = viewModel::search) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                }
            },
        )

        Box(modifier = Modifier.fillMaxSize().padding(top = spacing.sm)) {
            when {
                uiState.isLoading && uiState.cards.isEmpty() -> LoadingState(colors.primaryAccent)

                uiState.error != null && uiState.cards.isEmpty() -> ErrorState(
                    message = uiState.error.orEmpty(),
                    errorColor = colors.lifeNegative,
                )

                uiState.hasSearched && uiState.cards.isEmpty() -> EmptyResultsState(
                    query = uiState.query,
                    textColor = colors.textSecondary,
                )

                uiState.cards.isEmpty() -> IdleState(textColor = colors.textSecondary)

                else -> AdaptiveCardGrid(
                    windowSizeClass = windowSizeClass,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = spacing.lg),
                ) {
                    items(uiState.cards, key = { it.scryfallId }) { card ->
                        CardSearchResultTile(
                            card = card,
                            onClick = { /* Card detail navigation is a future slice. */ },
                            modifier = Modifier.padding(spacing.xs),
                        )
                    }
                    if (uiState.hasMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(spacing.md),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (uiState.isLoadingMore) {
                                    CircularProgressIndicator(color = colors.primaryAccent)
                                } else {
                                    Button(onClick = viewModel::loadMore) {
                                        Text("Load more")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState(indicatorColor: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = indicatorColor)
    }
}

@Composable
private fun ErrorState(message: String, errorColor: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Search failed: $message",
            style = MaterialTheme.magicTypography.bodyMedium,
            color = errorColor,
        )
    }
}

@Composable
private fun EmptyResultsState(query: String, textColor: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No cards found for \"$query\".",
            style = MaterialTheme.magicTypography.bodyMedium,
            color = textColor,
        )
    }
}

@Composable
private fun IdleState(textColor: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Search for a Magic card by name to get started.",
            style = MaterialTheme.magicTypography.bodyMedium,
            color = textColor,
        )
    }
}

/**
 * Minimal search-result tile — deliberately NOT [com.mmg.manahub.core.ui.components.CardGridItem]:
 * that component takes a `CollectionCardGroup` (quantity/foil/distinct-copies), a collection-only
 * shape that doesn't fit a raw Scryfall search result with no ownership data. Built fresh here from
 * two already-`commonMain`, already-mandated components instead: [MagicCard] (the card image, with
 * the correct 63:88 MTG aspect ratio) and [CardName] (CLAUDE.md's mandatory card-name renderer --
 * handles the "A-" Alchemy prefix / DFC front-face truncation).
 */
@Composable
private fun CardSearchResultTile(
    card: Card,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        MagicCard(
            card = card,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
        CardName(
            name = card.name,
            showFrontOnly = true,
            style = typography.labelSmall,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = card.setCode.uppercase(),
            style = typography.labelSmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
