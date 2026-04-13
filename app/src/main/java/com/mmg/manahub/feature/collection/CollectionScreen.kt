package com.mmg.manahub.feature.collection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.feature.addcard.AdvancedSearchSheet
import com.mmg.manahub.feature.addcard.AdvancedSearchViewModel
import com.mmg.manahub.core.ui.components.CardGridItem
import com.mmg.manahub.core.ui.components.CardListItem
import com.mmg.manahub.core.ui.components.StaleWarningBanner
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.decks.DeckListScreen
import java.util.Locale

// ── Sub-tab index constants ───────────────────────────────────────────────────
private const val TAB_CARDS = 0
private const val TAB_DECKS = 1

@Composable
fun CollectionScreen(
    onCardClick:              (scryfallId: String) -> Unit,
    onScannerClick:           () -> Unit,
    onDeckClick:              (deckId: Long) -> Unit,
    onCreateDeckClick:        () -> Unit,
    viewModel:                CollectionViewModel = hiltViewModel(),
    advancedSearchViewModel:  AdvancedSearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdvancedSearch by remember { mutableStateOf(false) }

    CollectionContent(
        uiState               = uiState,
        onCardClick           = onCardClick,
        onScannerClick        = onScannerClick,
        onDeckClick           = { id ->
            viewModel.onTabSelected(CollectionTab.DECKS)
            onDeckClick(id)
        },
        onCreateDeckClick     = {
            viewModel.onTabSelected(CollectionTab.DECKS)
            onCreateDeckClick()
        },
        onViewModeToggle      = viewModel::onViewModeToggle,
        onSortChange          = viewModel::onSortChange,
        onSearchQueryChange   = viewModel::onSearchQueryChange,
        onClearFilters        = {
            viewModel.clearAdvancedFilters()
            advancedSearchViewModel.clearAll()
        },
        onErrorDismissed      = viewModel::onErrorDismissed,
        onShowAdvancedSearch  = { showAdvancedSearch = true },
        onTabSelected         = viewModel::onTabSelected,
    )

    if (showAdvancedSearch) {
        AdvancedSearchSheet(
            isCollectionMode = true,
            onDismiss = { showAdvancedSearch = false },
            onSearch = { advancedQuery, _ ->
                viewModel.applyAdvancedFilters(advancedQuery)
                showAdvancedSearch = false
            },
        )
    }
}

@Composable
private fun CollectionContent(
    uiState:              CollectionUiState,
    onCardClick:          (String) -> Unit,
    onScannerClick:       () -> Unit,
    onDeckClick:          (Long) -> Unit,
    onCreateDeckClick:    () -> Unit,
    onViewModeToggle:     () -> Unit,
    onSortChange:         (SortOrder) -> Unit,
    onSearchQueryChange:  (String) -> Unit,
    onClearFilters:       () -> Unit,
    onErrorDismissed:     () -> Unit,
    onShowAdvancedSearch: () -> Unit,
    onTabSelected:        (CollectionTab) -> Unit,
) {
    val mc = MaterialTheme.magicColors

    Scaffold(
        topBar = {
            CollectionTopBar(
                viewMode         = uiState.viewMode,
                onViewModeToggle = onViewModeToggle,
                onSortChange     = onSortChange,
                currentSort      = uiState.sortOrder,
            )
        },
        floatingActionButton = {
            if (uiState.selectedTab == CollectionTab.CARDS) {
                FloatingActionButton(
                    onClick        = onScannerClick,
                    containerColor = mc.primaryAccent,
                    contentColor   = mc.background
                ) {
                    Icon(
                        imageVector        = Icons.Default.Add,
                        contentDescription = stringResource(R.string.addcard_title)
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Cards / Decks sub-tabs ────────────────────────────────────────
            TabRow(
                selectedTabIndex = if (uiState.selectedTab == CollectionTab.CARDS) TAB_CARDS else TAB_DECKS,
                containerColor   = mc.backgroundSecondary,
                contentColor     = mc.primaryAccent,
            ) {
                Tab(
                    selected = uiState.selectedTab == CollectionTab.CARDS,
                    onClick  = { onTabSelected(CollectionTab.CARDS) },
                    text     = {
                        Text(
                            text  = stringResource(R.string.collection_tab_cards).uppercase(Locale.getDefault()),
                            style = MaterialTheme.magicTypography.labelLarge,
                        )
                    },
                )
                Tab(
                    selected = uiState.selectedTab == CollectionTab.DECKS,
                    onClick  = { onTabSelected(CollectionTab.DECKS) },
                    text     = {
                        Text(
                            text  = stringResource(R.string.collection_tab_decks).uppercase(Locale.getDefault()),
                            style = MaterialTheme.magicTypography.labelLarge,
                        )
                    },
                )
            }

            // ── Tab content ───────────────────────────────────────────────────
            when (uiState.selectedTab) {
                CollectionTab.CARDS -> CardsTabContent(
                    uiState               = uiState,
                    onCardClick           = onCardClick,
                    onScannerClick        = onScannerClick,
                    onSearchQueryChange   = onSearchQueryChange,
                    onClearFilters        = onClearFilters,
                    onShowAdvancedSearch  = onShowAdvancedSearch,
                )
                CollectionTab.DECKS -> DeckListScreen(
                    onDeckClick       = onDeckClick,
                    onCreateDeckClick = onCreateDeckClick,
                )
            }
        }

        // Error dismissal
        uiState.error?.let {
            LaunchedEffect(it) { onErrorDismissed() }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Cards tab content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CardsTabContent(
    uiState:              CollectionUiState,
    onCardClick:          (String) -> Unit,
    onScannerClick:       () -> Unit,
    onSearchQueryChange:  (String) -> Unit,
    onClearFilters:       () -> Unit,
    onShowAdvancedSearch: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val filterCount = uiState.activeFilterCount
    Column(modifier = Modifier.fillMaxSize()) {
        // Stale data warning
        AnimatedVisibility(visible = uiState.hasStaleCards) {
            StaleWarningBanner()
        }

        // Search bar + advanced search button (with active-filter badge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchBar(
                query         = uiState.searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier      = Modifier.weight(1f),
            )
            BadgedBox(
                badge = {
                    if (filterCount > 0) {
                        Badge(
                            containerColor = mc.primaryAccent,
                            contentColor   = mc.background,
                        ) { Text("$filterCount") }
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
                            else mc.primaryAccent.copy(alpha = 0.1f)
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.advsearch_button),
                        tint = mc.primaryAccent,
                    )
                }
            }
        }

        // Active filters indicator
        AnimatedVisibility(visible = filterCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.collection_active_filters, filterCount),
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.primaryAccent,
                )
                TextButton(
                    onClick = onClearFilters,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        stringResource(R.string.collection_clear_filters),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.lifeNegative,
                    )
                }
            }
        }

        // Card count
        val totalCopies = uiState.cards.sumOf { it.totalQuantity }
        Text(
            text     = "${uiState.cards.size} ${stringResource(R.string.collection_unique_cards)} · $totalCopies ${stringResource(R.string.collection_total_copies)}",
            style    = MaterialTheme.magicTypography.labelSmall,
            color    = mc.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // Loading
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = mc.primaryAccent)
            }
            return@Column
        }

        // Empty state
        if (uiState.cards.isEmpty()) {
            EmptyCollectionState(onAddCardClick = onScannerClick)
            return@Column
        }

        // Grid or list
        when (uiState.viewMode) {
            ViewMode.GRID -> CardGrid(
                cards       = uiState.cards,
                onCardClick = onCardClick,
            )
            ViewMode.LIST -> CardList(
                cards       = uiState.cards,
                onCardClick = onCardClick,
            )
        }
    }
}

@Composable
private fun CollectionTopBar(
    viewMode:         ViewMode,
    onViewModeToggle: () -> Unit,
    onSortChange:     (SortOrder) -> Unit,
    currentSort:      SortOrder,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    val mc = MaterialTheme.magicColors

    Surface(
        color    = mc.backgroundSecondary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text     = stringResource(R.string.collection_title),
                style    = MaterialTheme.magicTypography.titleLarge,
                color    = mc.textPrimary,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onViewModeToggle) {
                Icon(
                    imageVector        = if (viewMode == ViewMode.GRID) Icons.Default.List else Icons.Default.GridView,
                    contentDescription = stringResource(R.string.collection_view_grid),
                    tint               = mc.textSecondary
                )
            }
            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(
                        imageVector        = Icons.Default.Sort,
                        contentDescription = stringResource(R.string.action_refresh),
                        tint               = mc.textSecondary
                    )
                }
                DropdownMenu(
                    expanded         = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    SortOrder.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(stringResource(sort.displayResId)) },
                            onClick = {
                                onSortChange(sort)
                                showSortMenu = false
                            },
                            leadingIcon = if (sort == currentSort) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query:        String,
    onQueryChange: (String) -> Unit,
    modifier:     Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    OutlinedTextField(
        value         = query,
        onValueChange = onQueryChange,
        modifier      = modifier.fillMaxWidth(),
        placeholder   = { Text(stringResource(R.string.collection_search_hint), color = mc.textDisabled) },
        leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null, tint = mc.textSecondary) },
        trailingIcon  = if (query.isNotEmpty()) {{
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_close), tint = mc.textSecondary)
            }
        }} else null,
        singleLine    = true,
        shape         = MaterialTheme.shapes.medium,
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = mc.primaryAccent,
            unfocusedBorderColor = mc.surfaceVariant,
            focusedTextColor     = mc.textPrimary,
            unfocusedTextColor   = mc.textPrimary,
            cursorColor          = mc.primaryAccent,
        ),
    )
}

@Composable
private fun CardGrid(
    cards:       List<CollectionCardGroup>,
    onCardClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(minSize = 110.dp),
        contentPadding        = PaddingValues(12.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(cards, key = { _, item -> item.card.scryfallId }) { index, item ->
            var visible by remember(item.card.scryfallId) { mutableStateOf(false) }
            LaunchedEffect(item.card.scryfallId) { visible = true }
            val delay = (index % 12) * 30
            AnimatedVisibility(
                visible = visible,
                enter   = fadeIn(tween(300, delayMillis = delay)) +
                          scaleIn(tween(300, delayMillis = delay), initialScale = 0.92f),
            ) {
                CardGridItem(item = item, onClick = { onCardClick(item.card.scryfallId) })
            }
        }
    }
}

@Composable
private fun CardList(
    cards:       List<CollectionCardGroup>,
    onCardClick: (String) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding      = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(
            items = cards,
            key   = { it.card.scryfallId },
        ) { item ->
            CardListItem(
                item    = item,
                onClick = { onCardClick(item.card.scryfallId) },
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.magicColors.surfaceVariant)
        }
    }
}

@Composable
private fun EmptyCollectionState(onAddCardClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector        = Icons.Default.CollectionsBookmark,
            contentDescription = null,
            tint               = mc.textDisabled,
            modifier           = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.collection_empty_title),
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.collection_empty_subtitle),
            style = MaterialTheme.magicTypography.bodyMedium,
            color = mc.textSecondary,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAddCardClick,
            colors  = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
        ) { Text(stringResource(R.string.collection_empty_action), color = mc.background) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Display name extensions
// ─────────────────────────────────────────────────────────────────────────────

val SortOrder.displayResId get() = when (this) {
    SortOrder.DATE_ADDED -> R.string.collection_sort_date
    SortOrder.NAME       -> R.string.collection_sort_name
    SortOrder.PRICE_DESC -> R.string.collection_sort_price_desc
    SortOrder.PRICE_ASC  -> R.string.collection_sort_price_asc
    SortOrder.RARITY     -> R.string.collection_sort_rarity
}
