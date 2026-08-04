package com.mmg.manahub.web.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.draw.clip
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
 * Tapping a result's card image adds it to the signed-in session's collection (web roadmap W3d —
 * see [CardSearchViewModel.addToCollection]), surfacing a transient inline confirmation/error
 * banner above the grid -- this gesture is UNCHANGED from W3d. Web roadmap W4b adds a small
 * overlay "view details" icon button on each tile (a distinct tap target from the card image, so
 * the existing add-to-collection tap is preserved exactly) that navigates to
 * [com.mmg.manahub.web.carddetail.CardDetailScreen] via [onCardClick].
 */
@Composable
fun CardSearchScreen(windowSizeClass: ManaWindowSizeClass, onCardClick: (String) -> Unit) {
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

        uiState.addToCollectionMessage?.let { message ->
            Text(
                text = message,
                style = typography.bodySmall,
                color = colors.textSecondary,
            )
        }

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
                            onClick = { viewModel.addToCollection(card) },
                            onDetailClick = { onCardClick(card.scryfallId) },
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
 *
 * Web roadmap W4b: the card image itself keeps its ORIGINAL [onClick] gesture (add to collection,
 * unchanged from W3d) -- a small overlay icon button (top-end corner, its own >= 48dp touch target
 * per CLAUDE.md's accessibility rule) is the distinct tap target for [onDetailClick], so the two
 * actions never compete for the same gesture.
 */
@Composable
private fun CardSearchResultTile(
    card: Card,
    onClick: () -> Unit,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            MagicCard(
                card = card,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            )
            IconButton(
                onClick = onDetailClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(spacing.xxs)
                    .clip(CircleShape)
                    .background(colors.background.copy(alpha = 0.75f)),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "View ${card.name} details",
                    tint = colors.textPrimary,
                )
            }
        }
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
