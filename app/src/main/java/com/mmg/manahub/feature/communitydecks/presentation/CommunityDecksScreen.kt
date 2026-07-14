package com.mmg.manahub.feature.communitydecks.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.ManaHubSelector
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.MagicColors
import com.mmg.manahub.core.ui.theme.SmallCardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.model.CommunityDeckSummary
import com.mmg.manahub.core.model.NamedCount

/**
 * Community Decks search / browse screen.
 *
 * Shared by both the landing route and the "decks containing card" deep-link
 * (the ViewModel pre-fills + auto-runs the search for the latter). Lets the user
 * search Archidekt by card name, filter by format, sort, and page through results.
 *
 * @param onBack pops the back stack.
 * @param onNavigateToDeck opens a community deck's detail by its Archidekt id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDecksScreen(
    onBack: () -> Unit,
    onNavigateToDeck: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CommunityDecksSearchViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isEnabled by viewModel.isFeatureEnabled.collectAsStateWithLifecycle()
    val toastState = rememberMagicToastState()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CommunityDecksSearchEvent.NavigateToDeck -> onNavigateToDeck(event.archidektId)
                is CommunityDecksSearchEvent.ShowError ->
                    toastState.show(event.message, MagicToastType.ERROR)
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        Scaffold(
            containerColor = mc.background,
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.community_deck_search_title),
                            style = ty.titleLarge,
                            color = mc.textPrimary,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = mc.textSecondary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
                )
            },
        ) { padding ->
            if (!isEnabled) {
                EmptyState(
                    title = stringResource(R.string.community_deck_feature_disabled),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            } else if (!uiState.discoverEnabled) {
                // Phase 5 hard regression bar (D8): flag off = EXACTLY the pre-Phase-5 screen, no
                // tab row, straight to Search.
                CommunityDecksSearchBody(
                    state = uiState,
                    contentPadding = padding,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = viewModel::search,
                    onFormatSelected = viewModel::onFormatSelected,
                    onSortSelected = viewModel::onSortSelected,
                    onLoadMore = viewModel::loadMore,
                    onDeckClick = viewModel::onDeckClick,
                )
            } else {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    CommunityHubTabRow(selected = uiState.hubTab, onSelect = viewModel::onSelectHubTab)
                    when (uiState.hubTab) {
                        CommunityHubTab.DISCOVER -> CommunityDiscoverBody(
                            state = uiState,
                            onRetry = { viewModel.onSelectHubTab(CommunityHubTab.DISCOVER) },
                            onTermClick = viewModel::onDiscoverTermClick,
                            onDeckClick = viewModel::onDeckClick,
                            modifier = Modifier.weight(1f),
                        )
                        CommunityHubTab.SEARCH -> CommunityDecksSearchBody(
                            state = uiState,
                            contentPadding = PaddingValues(0.dp),
                            onQueryChange = viewModel::onQueryChange,
                            onSearch = viewModel::search,
                            onFormatSelected = viewModel::onFormatSelected,
                            onSortSelected = viewModel::onSortSelected,
                            onLoadMore = viewModel::loadMore,
                            onDeckClick = viewModel::onDeckClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        MagicToastHost(toastState)
    }
}

/** Stateless body: search bar, filters, and the result grid (with all states). */
@Composable
private fun CommunityDecksSearchBody(
    state: CommunityDecksSearchUiState,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onFormatSelected: (CommunityDeckFormatFilter) -> Unit,
    onSortSelected: (CommunityDeckSort) -> Unit,
    onLoadMore: () -> Unit,
    onDeckClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        CommunityDeckSearchBar(
            query = state.query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            modifier = Modifier.padding(
                horizontal = spacing.lg,
                vertical = spacing.sm,
            ),
        )

        CommunityDeckFormatSelector(
            selected = state.selectedFormat,
            onSelect = onFormatSelected,
            modifier = Modifier.padding(horizontal = spacing.lg),
        )

        Spacer(Modifier.height(spacing.xs))

        CommunityDeckSortSelector(
            selected = state.selectedSort,
            onSelect = onSortSelected,
            modifier = Modifier.padding(horizontal = spacing.lg),
        )

        Spacer(Modifier.height(spacing.sm))

        CommunityDeckResultsContent(
            state = state,
            onRetry = onSearch,
            onLoadMore = onLoadMore,
            onDeckClick = onDeckClick,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Search field + a Search button; submits via IME action or the button. */
@Composable
private fun CommunityDeckSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = ty.bodyMedium,
            placeholder = {
                Text(
                    text = stringResource(R.string.community_deck_search_hint),
                    style = ty.bodyMedium,
                    color = mc.textDisabled,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = mc.textSecondary,
                )
            },
            shape = ChipShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = mc.textPrimary,
                unfocusedTextColor = mc.textPrimary,
                cursorColor = mc.primaryAccent,
                focusedBorderColor = mc.primaryAccent,
                unfocusedBorderColor = mc.surfaceVariant,
                focusedContainerColor = mc.surface,
                unfocusedContainerColor = mc.surface,
            ),
        )

        Button(
            onClick = onSearch,
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = mc.primaryAccent,
                contentColor = mc.onAccent,
            ),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(
                text = stringResource(R.string.community_deck_search_button),
                style = ty.labelLarge,
            )
        }
    }
}

/** Format selector following the ManaHub design system. */
@Composable
private fun CommunityDeckFormatSelector(
    selected: CommunityDeckFormatFilter,
    onSelect: (CommunityDeckFormatFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    ManaHubSelector(
        icon = Icons.Default.FilterList,
        label = stringResource(R.string.community_deck_format_label),
        valueText = selected.label,
        items = CommunityDeckFormatFilter.entries,
        selectedItem = selected,
        onSelect = onSelect,
        itemLabel = { it.label },
        modifier = modifier
    )
}

/** Sort selector following the ManaHub design system. */
@Composable
private fun CommunityDeckSortSelector(
    selected: CommunityDeckSort,
    onSelect: (CommunityDeckSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    ManaHubSelector(
        icon = Icons.AutoMirrored.Filled.Sort,
        label = stringResource(R.string.community_deck_sort_label_base),
        valueText = stringResource(selected.labelRes),
        items = CommunityDeckSort.entries,
        selectedItem = selected,
        onSelect = onSelect,
        itemLabel = { stringResource(it.labelRes) },
        modifier = modifier
    )
}

/** Resolves the right state (initial / loading / error / empty / results). */
@Composable
private fun CommunityDeckResultsContent(
    state: CommunityDecksSearchUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onDeckClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors

    when {
        state.isLoading -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = mc.primaryAccent)
            }
        }

        state.error != null -> {
            FullErrorState(
                message = state.error,
                retryLabel = stringResource(R.string.retry),
                onRetry = onRetry,
                modifier = modifier,
            )
        }

        !state.hasSearched -> {
            EmptyState(
                title = stringResource(R.string.community_deck_search_initial),
                modifier = modifier.fillMaxSize(),
            )
        }

        state.results.isEmpty() -> {
            EmptyState(
                title = if (state.selectedFormat == CommunityDeckFormatFilter.ALL) {
                    stringResource(R.string.community_deck_search_empty)
                } else {
                    stringResource(R.string.community_deck_search_empty_with_filter)
                },
                modifier = modifier.fillMaxSize(),
            )
        }

        else -> {
            CommunityDeckResultsGrid(
                state = state,
                onLoadMore = onLoadMore,
                onDeckClick = onDeckClick,
                modifier = modifier,
            )
        }
    }
}

/** The result list (one card per row) plus a header count and Load More footer. */
@Composable
private fun CommunityDeckResultsGrid(
    state: CommunityDecksSearchUiState,
    onLoadMore: () -> Unit,
    onDeckClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.lg,
            end = spacing.lg,
            bottom = spacing.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        if (state.totalCount > 0) {
            item(key = "result_count", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.community_deck_result_count, state.totalCount),
                    style = ty.labelMedium,
                    color = mc.textSecondary,
                    modifier = Modifier.padding(vertical = spacing.xs),
                )
            }
        }

        items(
            items = state.results,
            key = { "deck_${it.archidektId}" },
        ) { deck ->
            CommunityDeckSummaryCard(
                deck = deck,
                onClick = { onDeckClick(deck.archidektId) },
            )
        }

        if (state.hasMore) {
            item(key = "load_more", span = { GridItemSpan(maxLineSpan) }) {
                LoadMoreFooter(
                    isLoadingMore = state.isLoadingMore,
                    onLoadMore = onLoadMore,
                )
            }
        }
    }
}

/** A "Load More" button that morphs into a small progress indicator while paging. */
@Composable
private fun LoadMoreFooter(
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoadingMore) {
            CircularProgressIndicator(
                color = mc.primaryAccent,
                modifier = Modifier.size(32.dp),
            )
        } else {
            Button(
                onClick = onLoadMore,
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = mc.surface,
                    contentColor = mc.textPrimary,
                ),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(R.string.community_deck_load_more),
                    style = ty.labelLarge,
                )
            }
        }
    }
}

/** Single result row: name, format, card count, views, author, and color identity dots. */
@Composable
private fun CommunityDeckSummaryCard(
    deck: CommunityDeckSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        onClick = onClick,
        shape = CardShape,
        color = mc.surface,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            // Art-crop thumbnail of the deck's featured card, when Archidekt supplied one.
            // Decorative only — the card's name already carries the accessible label — and
            // gracefully omitted (layout unchanged) when there is nothing to show.
            if (deck.featuredImageUrl != null) {
                AsyncImage(
                    model = deck.featuredImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 64.dp, height = 48.dp)
                        .clip(SmallCardShape)
                        .background(mc.surfaceVariant),
                )
                Spacer(Modifier.width(spacing.sm))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardName(
                        name = deck.name,
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(spacing.sm))
                    val identityString = deck.colorIdentity.joinToString("") { "{${it.uppercase()}}" }
                    ManaCostImages(manaCost = identityString, symbolSize = 14.dp)
                }

                Spacer(Modifier.height(spacing.xs))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = deck.format,
                        style = ty.labelSmall,
                        color = mc.primaryAccent,
                    )
                    Spacer(Modifier.width(spacing.md))
                    Text(
                        text = stringResource(R.string.community_deck_cards_count, deck.size),
                        style = ty.bodySmall,
                        color = mc.textSecondary,
                    )
                    Spacer(Modifier.width(spacing.md))
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = mc.textDisabled,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(spacing.xxs))
                    Text(
                        text = "${deck.viewCount}",
                        style = ty.bodySmall,
                        color = mc.textSecondary,
                    )
                }

                Spacer(Modifier.height(spacing.xxs))

                Text(
                    text = deck.owner.username,
                    style = ty.bodySmall,
                    color = mc.textDisabled,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Community Hub — Discover (Deck Doctor Community/Archetype plan, Phase 5, D8)
// ─────────────────────────────────────────────────────────────────────────────

/** The Discover/Search tab row — only ever composed when [CommunityDecksSearchUiState.discoverEnabled]
 * is true (see the screen's flag-gate above). */
@Composable
private fun CommunityHubTabRow(
    selected: CommunityHubTab,
    onSelect: (CommunityHubTab) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    TabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = mc.backgroundSecondary,
        contentColor = mc.primaryAccent,
    ) {
        CommunityHubTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        text = stringResource(
                            if (tab == CommunityHubTab.DISCOVER) R.string.community_hub_tab_discover
                            else R.string.community_hub_tab_search
                        ),
                        style = ty.labelLarge,
                    )
                },
            )
        }
    }
}

/** The Discover section: trending commanders/cards + popular decks. Every sub-section degrades
 * independently (empty on failure) except for the ALL-failed case, handled by [state.discoverUnavailable]. */
@Composable
private fun CommunityDiscoverBody(
    state: CommunityDecksSearchUiState,
    onRetry: () -> Unit,
    onTermClick: (String) -> Unit,
    onDeckClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    if ((state.isTrendingLoading && state.isPopularDecksLoading) && state.trending == null && state.popularDecks.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = mc.primaryAccent)
        }
        return
    }

    if (state.discoverUnavailable) {
        InlineErrorState(
            message = stringResource(R.string.community_hub_discover_unavailable),
            retryLabel = stringResource(R.string.retry),
            onRetry = onRetry,
            modifier = modifier.padding(spacing.lg),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = spacing.md, horizontal = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        val trending = state.trending
        if (trending != null && trending.topCommanders.isNotEmpty()) {
            item(key = "trending_commanders_header") {
                Text(
                    text = stringResource(R.string.community_hub_trending_commanders),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                )
            }
            item(key = "trending_commanders_row") {
                TrendingTermRow(terms = trending.topCommanders, onClick = onTermClick)
            }
        }
        if (trending != null && trending.topCards.isNotEmpty()) {
            item(key = "trending_cards_header") {
                Text(
                    text = stringResource(R.string.community_hub_trending_cards),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                )
            }
            item(key = "trending_cards_row") {
                TrendingTermRow(terms = trending.topCards, onClick = onTermClick)
            }
        }
        if (state.popularDecks.isNotEmpty()) {
            item(key = "popular_decks_header") {
                Text(
                    text = stringResource(R.string.community_hub_popular_decks),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                )
            }
            items(state.popularDecks, key = { "popular_${it.archidektId}" }) { deck ->
                CommunityDeckSummaryCard(deck = deck, onClick = { onDeckClick(deck.archidektId) })
            }
        }
    }
}

/** A horizontally-scrollable row of trending term chips ("N week"-style ranking is implicit in order). */
@Composable
private fun TrendingTermRow(terms: List<NamedCount>, onClick: (String) -> Unit) {
    val spacing = MaterialTheme.spacing
    LazyRow(
        contentPadding = PaddingValues(vertical = spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        items(terms.take(10), key = { it.name }) { term ->
            TrendingTermChip(term = term, onClick = { onClick(term.name) })
        }
    }
}

@Composable
private fun TrendingTermChip(term: NamedCount, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(
        onClick = onClick,
        shape = ChipShape,
        color = mc.surface,
        border = BorderStroke(1.dp, mc.surfaceVariant),
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
        ) {
            CardName(name = term.name, style = ty.labelMedium, color = mc.textPrimary)
        }
    }
}
