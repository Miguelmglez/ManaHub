package com.mmg.manahub.feature.communitydecks.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import org.jetbrains.compose.resources.painterResource
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CommunityDeckSummary
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.ui.components.DeckItem
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaHubSelector
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.communitydecks.presentation.components.CommunityAdvancedSearchSheet
import kotlinx.datetime.Instant

/**
 * Community Decks Hub screen (Discover / Search).
 *
 * Shared by both the landing route and the "decks containing card" deep-link
 * (the ViewModel pre-fills + auto-runs the search for the latter). Discover surfaces a
 * multi-section browse feed (trending commanders/cards, popular/recent/updated/primer decks, a
 * weekly-rotating featured format); Search is a deck-NAME search bar plus an Archidekt-style
 * advanced-search sheet (commander/card/username/bracket/colors/size/primers).
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
    val toastState = rememberMagicToastState()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    var showAdvancedSearch by remember { mutableStateOf(false) }

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
            if (!uiState.discoverEnabled) {
                // Phase 5 hard regression bar (D8): flag off = EXACTLY the pre-Phase-5 screen, no
                // tab row, straight to Search.
                CommunityDecksSearchBody(
                    state = uiState,
                    contentPadding = padding,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = viewModel::search,
                    onSortSelected = viewModel::onSortSelected,
                    onLoadMore = viewModel::loadMore,
                    onDeckClick = viewModel::onDeckClick,
                    onShowAdvancedSearch = { showAdvancedSearch = true },
                )
            } else {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    CommunityHubTabRow(selected = uiState.hubTab, onSelect = viewModel::onSelectHubTab)
                    when (uiState.hubTab) {
                        CommunityHubTab.DISCOVER -> CommunityDiscoverBody(
                            state = uiState,
                            onRetry = { viewModel.onSelectHubTab(CommunityHubTab.DISCOVER) },
                            onTrendingCommanderClick = viewModel::onTrendingCommanderClick,
                            onTrendingCardClick = viewModel::onTrendingCardClick,
                            onDeckClick = viewModel::onDeckClick,
                            modifier = Modifier.weight(1f),
                        )
                        CommunityHubTab.SEARCH -> CommunityDecksSearchBody(
                            state = uiState,
                            contentPadding = PaddingValues(0.dp),
                            onQueryChange = viewModel::onQueryChange,
                            onSearch = viewModel::search,
                            onSortSelected = viewModel::onSortSelected,
                            onLoadMore = viewModel::loadMore,
                            onDeckClick = viewModel::onDeckClick,
                            onShowAdvancedSearch = { showAdvancedSearch = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        MagicToastHost(toastState)
    }

    if (showAdvancedSearch) {
        CommunityAdvancedSearchSheet(
            state = uiState,
            onDismiss = { showAdvancedSearch = false },
            onFormatSelected = viewModel::onFormatFilterSelected,
            onColorToggled = viewModel::onColorToggled,
            onBracketSelected = viewModel::onBracketSelected,
            onCommanderQueryChange = viewModel::onCommanderQueryChange,
            onCommanderSelected = viewModel::onCommanderSelected,
            onCommanderCleared = viewModel::onCommanderCleared,
            onCardQueryChange = viewModel::onCardQueryChange,
            onCardSelected = viewModel::onCardFilterSelected,
            onCardCleared = viewModel::onCardFilterCleared,
            onUsernameChanged = viewModel::onUsernameChanged,
            onDeckSizeChanged = viewModel::onDeckSizeChanged,
            onPrimersOnlyToggled = viewModel::onPrimersOnlyToggled,
            onClearAll = viewModel::onClearAdvancedFilters,
            onSearch = viewModel::onApplyAdvancedFilters,
        )
    }
}

/** Stateless body: deck-name search bar + advanced-search entry point + the result grid. */
@Composable
private fun CommunityDecksSearchBody(
    state: CommunityDecksSearchUiState,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSortSelected: (CommunityDeckSort) -> Unit,
    onLoadMore: () -> Unit,
    onDeckClick: (Int) -> Unit,
    onShowAdvancedSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val mc = MaterialTheme.magicColors
    val filterCount = state.advancedFilters.activeCount

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CommunityDeckSearchBar(
                query = state.query,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                modifier = Modifier.weight(1f),
            )
            BadgedBox(
                badge = {
                    if (filterCount > 0) {
                        Badge(containerColor = mc.primaryAccent, contentColor = mc.onAccent) {
                            Text(filterCount.toString())
                        }
                    }
                },
            ) {
                IconButton(
                    onClick = onShowAdvancedSearch,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (filterCount > 0) mc.primaryAccent.copy(alpha = 0.15f)
                            else mc.primaryAccent.copy(alpha = 0.1f),
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.community_advsearch_button),
                        tint = mc.primaryAccent,
                    )
                }
            }
        }

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

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
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
        modifier = modifier,
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
                title = if (state.advancedFilters.activeCount == 0) {
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
        ) {
        if (state.totalCount > 0) {
            item(key = "result_count", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.community_deck_result_count, state.totalCount),
                    style = ty.labelMedium,
                    color = mc.textSecondary,
                    modifier = Modifier.padding(top = spacing.xs, start = spacing.md),
                )
            }
        }

        items(
            items = state.results,
            key = { "deck_${it.archidektId}" },
        ) { deck ->
            DeckItem(
                deck = deck.toDeckSummary(),
                onClick = { onDeckClick(deck.archidektId) },
                ownerName = deck.owner.username,
                cardBackPainter = painterResource(Res.drawable.mtg_card_back),
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
            MagicCtaButton(
                onClick = onLoadMore,
                color = MagicCtaColor.Surface,
                text = stringResource(R.string.community_deck_load_more),
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Community Hub — Discover (overhauled 2026-07-15 into a multi-section browse feed)
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
        containerColor = mc.backgroundSecondary.copy(alpha = 0.9f),
        contentColor = mc.primaryAccent,
        divider = {}
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
                        ).uppercase(),
                        style = ty.labelLarge,
                    )
                },
            )
        }
    }
}

/**
 * The Discover section: trending commander/card full-image rows, plus popular / recent / recently
 * updated / primer / featured-format deck rows. Every sub-section renders independently and is
 * simply omitted when empty (a single upstream failure never hides the others); only when EVERY
 * section is empty does [CommunityDecksSearchUiState.discoverUnavailable] show one inline error.
 */
@Composable
private fun CommunityDiscoverBody(
    state: CommunityDecksSearchUiState,
    onRetry: () -> Unit,
    onTrendingCommanderClick: (Card) -> Unit,
    onTrendingCardClick: (Card) -> Unit,
    onDeckClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing

    val everythingEmpty = state.trendingCommanderCards.isEmpty() && state.trendingCardCards.isEmpty() &&
        state.popularDecks.isEmpty() && state.recentDecks.isEmpty() && state.updatedDecks.isEmpty() &&
        state.primerDecks.isEmpty() && state.featuredFormatDecks.isEmpty()

    if (state.isDiscoverLoading && everythingEmpty) {
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

    // Resolved here (this function IS @Composable) rather than inside `discoverDeckSection`'s
    // arguments — `LazyColumn`'s own trailing content lambda is a plain (non-@Composable)
    // `LazyListScope.() -> Unit`; only the lambdas passed to its `item`/`items` builders are
    // truly composable-scoped, so a `stringResource()` call can never sit directly in a
    // `discoverDeckSection(...)` argument at that call site.
    val popularTitle = stringResource(R.string.community_hub_popular_decks)
    val recentTitle = stringResource(R.string.community_hub_recent_decks)
    val updatedTitle = stringResource(R.string.community_hub_updated_decks)
    val primerTitle = stringResource(R.string.community_hub_primer_decks)
    val featuredTitle = stringResource(R.string.community_hub_featured_format, state.featuredFormat.label)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = spacing.md, horizontal = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        if (state.trendingCommanderCards.isNotEmpty()) {
            item(key = "trending_commanders_header") {
                DiscoverSectionHeader(stringResource(R.string.community_hub_trending_commanders))
            }
            item(key = "trending_commanders_row") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    items(state.trendingCommanderCards, key = { it.scryfallId }) { card ->
                        CommunityTrendingCardTile(card = card, onClick = { onTrendingCommanderClick(card) })
                    }
                }
            }
        }

        if (state.trendingCardCards.isNotEmpty()) {
            item(key = "trending_cards_header") {
                DiscoverSectionHeader(stringResource(R.string.community_hub_trending_cards))
            }
            item(key = "trending_cards_row") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    items(state.trendingCardCards, key = { it.scryfallId }) { card ->
                        CommunityTrendingCardTile(card = card, onClick = { onTrendingCardClick(card) })
                    }
                }
            }
        }

        discoverDeckSection(key = "popular", title = popularTitle, decks = state.popularDecks, onDeckClick = onDeckClick)
        discoverDeckSection(key = "recent", title = recentTitle, decks = state.recentDecks, onDeckClick = onDeckClick)
        discoverDeckSection(key = "updated", title = updatedTitle, decks = state.updatedDecks, onDeckClick = onDeckClick)
        discoverDeckSection(key = "primer", title = primerTitle, decks = state.primerDecks, onDeckClick = onDeckClick)
        discoverDeckSection(key = "featured", title = featuredTitle, decks = state.featuredFormatDecks, onDeckClick = onDeckClick)
    }
}

/** One horizontal deck-row Discover section; omitted entirely when [decks] is empty. */
private fun LazyListScope.discoverDeckSection(
    key: String,
    title: String,
    decks: List<CommunityDeckSummary>,
    onDeckClick: (Int) -> Unit,
) {
    if (decks.isEmpty()) return
    item(key = "${key}_header") { DiscoverSectionHeader(title) }
    item(key = "${key}_row") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            items(decks, key = { "${key}_${it.archidektId}" }) { deck ->
                DeckItem(
                    deck = deck.toDeckSummary(),
                    onClick = { onDeckClick(deck.archidektId) },
                    reduced = true,
                    ownerName = deck.owner.username,
                    cardBackPainter = painterResource(Res.drawable.mtg_card_back),
                    modifier = Modifier.width(160.dp),
                )
            }
        }
    }
}

@Composable
private fun DiscoverSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.magicTypography.titleMedium,
        color = MaterialTheme.magicColors.textPrimary,
    )
}

/** One full-card-image trending tile (63:88 aspect) for the Commander/Card Discover rows. */
@Composable
private fun CommunityTrendingCardTile(card: Card, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors

    Box(
        modifier = Modifier
            .width(110.dp)
            .aspectRatio(63f / 88f)
            .clip(CardShape)
            .background(mc.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = card.imageNormal,
            contentDescription = card.name,
            placeholder = painterResource(Res.drawable.mtg_card_back),
            error = painterResource(Res.drawable.mtg_card_back),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Lightweight mapping from the community result model to the standard deck list model. */
private fun CommunityDeckSummary.toDeckSummary(): DeckSummary = DeckSummary(
    id = archidektId.toString(),
    name = name,
    description = null,
    format = format,
    coverCardId = null,
    createdAt = runCatching { Instant.parse(createdAt).toEpochMilliseconds() }.getOrDefault(0L),
    updatedAt = runCatching { Instant.parse(updatedAt).toEpochMilliseconds() }.getOrDefault(0L),
    cardCount = size,
    colorIdentity = colorIdentity.toSet(),
    coverImageUrl = featuredImageUrl
)
