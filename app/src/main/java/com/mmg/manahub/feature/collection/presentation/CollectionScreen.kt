package com.mmg.manahub.feature.collection.presentation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.model.CollectionCardGroup
import com.mmg.manahub.core.model.CollectionGroupingMode
import com.mmg.manahub.core.model.CollectionSection
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.sync.SyncState
import com.mmg.manahub.core.tagging.label
import com.mmg.manahub.core.ui.components.CardGridItem
import com.mmg.manahub.core.ui.components.CardListItem
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaHubBottomSheetSelector
import com.mmg.manahub.core.ui.components.ManaHubSelector
import com.mmg.manahub.core.ui.components.StaleWarningBanner
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.components.search.AdvancedSearchSheet
import com.mmg.manahub.core.ui.components.search.AdvancedSearchViewModel
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.androidx.compose.koinViewModel
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.feature.decks.presentation.DeckListScreen
import com.mmg.manahub.feature.trades.presentation.TradesScreen
import java.util.Locale

// ── Sub-tab index constants ───────────────────────────────────────────────────
private const val TAB_CARDS  = 0
private const val TAB_DECKS  = 1
private const val TAB_TRADES = 2

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CollectionScreen(
    onCardClick:              (scryfallId: String, sharedTransitionKey: String?) -> Unit,
    onAddCardClick:           () -> Unit,
    onDeckClick:              (deckId: String) -> Unit,
    onCreateDeck:             () -> Unit = {},
    onPlaytestClick:          (deckId: String) -> Unit = {},
    onBrowseCommunityDecks:   () -> Unit = {},
    onNavigateToTradeProposal: (receiverId: String) -> Unit = {},
    onNavigateToTradeThread:   (proposalId: String, rootProposalId: String) -> Unit = { _, _ -> },
    viewModel:                CollectionViewModel = koinViewModel(),
    advancedSearchViewModel:  AdvancedSearchViewModel = koinViewModel(),
    sharedTransitionScope:    SharedTransitionScope? = null,
    animatedVisibilityScope:  AnimatedVisibilityScope? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdvancedSearch by remember { mutableStateOf(false) }

    CollectionContent(
        uiState               = uiState,
        onCardClick           = onCardClick,
        onAddCardClick        = onAddCardClick,
        onDeckClick           = { id ->
            viewModel.onTabSelected(CollectionTab.DECKS)
            onDeckClick(id)
        },
        onCreateDeck          = onCreateDeck,
        onPlaytestClick       = onPlaytestClick,
        onBrowseCommunityDecks = onBrowseCommunityDecks,
        onViewModeToggle      = viewModel::onViewModeToggle,
        onSortChange          = viewModel::onSortChange,
        onGroupingChange      = viewModel::onGroupingChange,
        onSearchQueryChange   = viewModel::onSearchQueryChange,
        onClearFilters        = {
            viewModel.clearAdvancedFilters()
            advancedSearchViewModel.clearAll()
        },
        onErrorDismissed      = viewModel::onErrorDismissed,
        onShowAdvancedSearch  = { showAdvancedSearch = true },
        onSync                = viewModel::onSync,
        onTabSelected         = viewModel::onTabSelected,
        onSyncDismissed            = viewModel::onSyncDismissed,
        onSnackbarDismissed        = viewModel::onSnackbarDismissed,
        onNavigateToTradeProposal  = onNavigateToTradeProposal,
        onNavigateToTradeThread    = onNavigateToTradeThread,
        gridState                  = viewModel.gridState,
        listState                  = viewModel.listState,
        sharedTransitionScope      = sharedTransitionScope,
        animatedVisibilityScope    = animatedVisibilityScope,
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CollectionContent(
    uiState:              CollectionUiState,
    onCardClick:          (String, String?) -> Unit,
    onAddCardClick:       () -> Unit,
    onDeckClick:          (String) -> Unit,
    onCreateDeck:         () -> Unit = {},
    onPlaytestClick:      (String) -> Unit = {},
    onBrowseCommunityDecks: () -> Unit = {},
    onViewModeToggle:     () -> Unit,
    onSortChange:         (SortOrder) -> Unit,
    onGroupingChange:     (CollectionGroupingMode) -> Unit,
    onSearchQueryChange:  (String) -> Unit,
    onClearFilters:       () -> Unit,
    onErrorDismissed:     () -> Unit,
    onShowAdvancedSearch: () -> Unit,
    onSync:               () -> Unit,
    onTabSelected:        (CollectionTab) -> Unit,
    onSyncDismissed:      () -> Unit,
    onSnackbarDismissed:  () -> Unit,
    onNavigateToTradeProposal: (String) -> Unit = {},
    onNavigateToTradeThread:   (String, String) -> Unit = { _, _ -> },
    gridState:            LazyGridState,
    listState:            LazyListState,
    sharedTransitionScope:    SharedTransitionScope? = null,
    animatedVisibilityScope:  AnimatedVisibilityScope? = null,
) {
    val mc = MaterialTheme.magicColors
    val toastState = rememberMagicToastState()
    val syncErrorMsg       = stringResource(R.string.collection_sync_error)
    val migrationMsgFmt    = stringResource(R.string.trades_migration_synced_n_cards)

    LaunchedEffect(uiState.syncState, uiState.syncError) {
        when (uiState.syncState) {
            SyncState.SUCCESS -> onSyncDismissed()
            SyncState.ERROR -> {
                toastState.show(uiState.syncError ?: syncErrorMsg, MagicToastType.ERROR)
                onSyncDismissed()
            }
            else -> Unit
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val countStr = uiState.snackbarMessage ?: return@LaunchedEffect
        val count = countStr.toIntOrNull() ?: return@LaunchedEffect
        toastState.show(migrationMsgFmt.format(count), MagicToastType.INFO)
        onSnackbarDismissed()
    }

    Box(modifier = Modifier.fillMaxSize().background(mc.background)) {
        HexGridBackground(modifier = Modifier.fillMaxSize(), color = mc.primaryAccent.copy(alpha = 0.05f))

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                CollectionTopBar()
            },
            floatingActionButton = {
                if (uiState.selectedTab == CollectionTab.CARDS) {
                    FloatingActionButton(
                        onClick        = onAddCardClick,
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
                // ── Cards / Decks / Trades sub-tabs ──────────────────────────────
                val selectedTabIndex = when (uiState.selectedTab) {
                    CollectionTab.CARDS  -> TAB_CARDS
                    CollectionTab.DECKS  -> TAB_DECKS
                    CollectionTab.TRADES -> TAB_TRADES
                }
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor   = mc.backgroundSecondary.copy(alpha = 0.9f),
                    contentColor     = mc.primaryAccent,
                    divider = {}
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
                    Tab(
                        selected = uiState.selectedTab == CollectionTab.TRADES,
                        onClick  = { onTabSelected(CollectionTab.TRADES) },
                        text     = {
                            Text(
                                text  = stringResource(R.string.collection_tab_trades).uppercase(Locale.getDefault()),
                                style = MaterialTheme.magicTypography.labelLarge,
                            )
                        },
                    )
                }

                // "Sync your collection" banner — below tabs so it doesn't obscure navigation.
                // Stays visible during SYNCING so the spinner is shown inline in the list.
                AnimatedVisibility(
                    visible = uiState.sessionState is SessionState.Authenticated &&
                              (uiState.hasUnsyncedChanges || uiState.syncState == SyncState.SYNCING),
                ) {
                    SyncCollectionBanner(
                        isSyncing = uiState.syncState == SyncState.SYNCING,
                        onSync    = onSync,
                    )
                }

                // ── Tab content ───────────────────────────────────────────────────
                when (uiState.selectedTab) {
                    CollectionTab.CARDS -> CardsTabContent(
                        uiState               = uiState,
                        onCardClick           = onCardClick,
                        onAddCardClick        = onAddCardClick,
                        onSearchQueryChange   = onSearchQueryChange,
                        onClearFilters        = onClearFilters,
                        onShowAdvancedSearch  = onShowAdvancedSearch,
                        onViewModeToggle      = onViewModeToggle,
                        onSortChange          = onSortChange,
                        onGroupingChange      = onGroupingChange,
                        gridState             = gridState,
                        listState             = listState,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                    CollectionTab.DECKS   -> DeckListScreen(
                        onDeckClick     = onDeckClick,
                        onCreateDeck    = onCreateDeck,
                        onPlaytestClick = onPlaytestClick,
                        onBrowseCommunityDecks = onBrowseCommunityDecks,
                    )
                    CollectionTab.TRADES  -> TradesScreen(
                        onCardClick           = { onCardClick(it, null) },
                        onNavigateToProposal  = onNavigateToTradeProposal,
                        onNavigateToThread    = onNavigateToTradeThread,
                    )
                }
            }

            // Error dismissal
            uiState.error?.let {
                LaunchedEffect(it) { onErrorDismissed() }
            }
        }

        MagicToastHost(state = toastState)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Cards tab content
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CardsTabContent(
    uiState:              CollectionUiState,
    onCardClick:          (String, String?) -> Unit,
    onAddCardClick:       () -> Unit,
    onSearchQueryChange:  (String) -> Unit,
    onClearFilters:       () -> Unit,
    onShowAdvancedSearch: () -> Unit,
    onViewModeToggle:     () -> Unit,
    onSortChange:         (SortOrder) -> Unit,
    onGroupingChange:     (CollectionGroupingMode) -> Unit,
    gridState:            LazyGridState,
    listState:            LazyListState,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val mc = MaterialTheme.magicColors
    val filterCount = uiState.activeFilterCount
    var showSortMenu by remember { mutableStateOf(false) }

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

        // Card count + Sort/View controls
        val totalCopies = uiState.cards.sumOf { it.totalQuantity }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${uiState.cards.size} ${stringResource(R.string.collection_unique_cards)} · $totalCopies ${stringResource(R.string.collection_total_copies)}",
                    style = MaterialTheme.magicTypography.labelLarge,
                    color = mc.textSecondary,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onViewModeToggle, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (uiState.viewMode == CollectionViewMode.GRID) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                        contentDescription = stringResource(R.string.collection_view_grid),
                        tint = mc.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            ) {
                ManaHubBottomSheetSelector(
                    icon = Icons.AutoMirrored.Filled.Sort,
                    label = stringResource(R.string.collection_sort_label),
                    valueText = stringResource(uiState.sortOrder.displayResId),
                    items = SortOrder.entries,
                    selectedItem = uiState.sortOrder,
                    onSelect = onSortChange,
                    itemLabel = { stringResource(it.displayResId) },
                    modifier = Modifier.fillMaxWidth(),
                )
                ManaHubBottomSheetSelector(
                    icon = Icons.Default.Layers,
                    label = stringResource(R.string.collection_grouping_label),
                    valueText = stringResource(uiState.groupingMode.displayResId),
                    items = CollectionGroupingMode.entries,
                    selectedItem = uiState.groupingMode,
                    onSelect = onGroupingChange,
                    itemLabel = { stringResource(it.displayResId) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Loading
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = mc.primaryAccent)
            }
            return@Column
        }

        // Empty state
        if (uiState.cards.isEmpty()) {
            EmptyState(
                icon        = Icons.Default.CollectionsBookmark,
                title       = stringResource(R.string.collection_empty_title),
                subtitle    = stringResource(R.string.collection_empty_subtitle),
                actionLabel = stringResource(R.string.collection_empty_action),
                onAction    = onAddCardClick,
            )
            return@Column
        }

        // Grid or list — sectioned rendering kicks in whenever a grouping mode is active
        // (uiState.sections is non-empty); flat rendering (uiState.cards) is otherwise
        // byte-identical to before this feature landed.
        when (uiState.viewMode) {
            CollectionViewMode.GRID -> CardGrid(
                cards        = uiState.cards,
                sections     = uiState.sections,
                groupingMode = uiState.groupingMode,
                onCardClick  = onCardClick,
                state        = gridState,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
            CollectionViewMode.LIST -> CardList(
                cards        = uiState.cards,
                sections     = uiState.sections,
                groupingMode = uiState.groupingMode,
                onCardClick  = onCardClick,
                state        = listState,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    }
}

@Composable
private fun CollectionTopBar() {
    val mc = MaterialTheme.magicColors

    Surface(
        color    = mc.backgroundSecondary.copy(alpha = 0.9f),
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text     = stringResource(R.string.collection_title),
                style    = MaterialTheme.magicTypography.titleLarge,
                color    = mc.textPrimary,
                modifier = Modifier.weight(1f)
            )
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
        placeholder   = { Text(stringResource(R.string.collection_search_hint), color = mc.textDisabled, style = MaterialTheme.magicTypography.bodyLarge) },
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CardGrid(
    cards:        List<CollectionCardGroup>,
    sections:     List<CollectionSection>,
    groupingMode: CollectionGroupingMode,
    onCardClick:  (String, String?) -> Unit,
    state:        LazyGridState,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    // In-memory only (resets on leaving the screen) — same lifetime as the rest of this
    // screen's transient UI state (e.g. showSortMenu).
    val collapsedSections = remember { mutableStateMapOf<String, Boolean>() }

    LazyVerticalGrid(
        columns               = GridCells.Adaptive(minSize = 100.dp),
        state                 = state,
        contentPadding        = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 80.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (groupingMode == CollectionGroupingMode.NONE) {
            itemsIndexed(cards, key = { _, item -> item.groupKey }) { index, item ->
                CardGridCell(
                    item = item,
                    visibilityKey = item.groupKey,
                    index = index,
                    onCardClick = { onCardClick(it, item.groupKey) },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        } else {
            sections.forEach { section ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "header|${section.labelToken}") {
                    val isCollapsed = collapsedSections[section.labelToken] == true
                    CollectionGroupHeader(
                        section = section,
                        mode = groupingMode,
                        expanded = !isCollapsed,
                        onToggle = { collapsedSections[section.labelToken] = !isCollapsed },
                    )
                }
                if (collapsedSections[section.labelToken] != true) {
                    itemsIndexed(
                        section.items,
                        // Composite key: TAG mode lets one CollectionCardGroup appear in
                        // multiple sections, so the bare groupKey alone would collide.
                        key = { _, item -> "${section.labelToken}|${item.groupKey}" },
                    ) { index, item ->
                        val uniqueKey = "${section.labelToken}|${item.groupKey}"
                        CardGridCell(
                            item = item,
                            visibilityKey = uniqueKey,
                            index = index,
                            onCardClick = { onCardClick(it, uniqueKey) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CardGridCell(
    item:        CollectionCardGroup,
    visibilityKey: String,
    index:       Int,
    onCardClick: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    var visible by rememberSaveable(key = visibilityKey) { mutableStateOf(false) }
    LaunchedEffect(visibilityKey) { visible = true }
    val delay = (index % 12) * 30

    // Stable container to prevent LazyVerticalGrid from collapsing when returning from
    // a detail screen (which resets visibility to false for a frame during stagger).
    // aspectRatio 0.75f is a safe approximation for MTG card dimensions in this grid.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300, delayMillis = delay)) +
                    scaleIn(tween(300, delayMillis = delay), initialScale = 0.92f),
        ) {
            CardGridItem(
                item = item,
                onClick = { onCardClick(item.card.scryfallId) },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                sharedTransitionKey = visibilityKey
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CardList(
    cards:        List<CollectionCardGroup>,
    sections:     List<CollectionSection>,
    groupingMode: CollectionGroupingMode,
    onCardClick:  (String, String?) -> Unit,
    state:        LazyListState,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val collapsedSections = remember { mutableStateMapOf<String, Boolean>() }

    androidx.compose.foundation.lazy.LazyColumn(
        state               = state,
        contentPadding      = PaddingValues(top = 4.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (groupingMode == CollectionGroupingMode.NONE) {
            items(items = cards, key = { it.groupKey }) { item ->
                CardListRow(
                    item = item,
                    onCardClick = { onCardClick(it, item.groupKey) },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    sharedTransitionKey = item.groupKey
                )
            }
        } else {
            sections.forEach { section ->
                // Unqualified `item(...)`: singular `item` is declared as an INTERFACE MEMBER on
                // both `LazyListScope` and `LazyGridScope` (unlike `items`/`itemsIndexed`, which
                // are top-level List<T> convenience extensions) — there is no top-level
                // `androidx.compose.foundation.lazy(.grid).item` symbol to import, and importing
                // one fails to resolve. It's picked up automatically via the implicit
                // `LazyListScope` receiver here (CardGrid's `LazyGridScope` receiver resolves its
                // own member the same way).
                item(key = "header|${section.labelToken}") {
                    val isCollapsed = collapsedSections[section.labelToken] == true
                    CollectionGroupHeader(
                        section = section,
                        mode = groupingMode,
                        expanded = !isCollapsed,
                        onToggle = { collapsedSections[section.labelToken] = !isCollapsed },
                    )
                }
                if (collapsedSections[section.labelToken] != true) {
                    items(
                        items = section.items,
                        // Composite key — see CardGrid's identical comment (TAG multi-membership).
                        key = { "${section.labelToken}|${it.groupKey}" },
                    ) { item ->
                        val uniqueKey = "${section.labelToken}|${item.groupKey}"
                        CardListRow(
                            item = item,
                            onCardClick = { onCardClick(it, uniqueKey) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            sharedTransitionKey = uniqueKey
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CardListRow(
    item:        CollectionCardGroup,
    onCardClick: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    sharedTransitionKey: String? = null,
) {
    CardListItem(
        item    = item,
        onClick = { onCardClick(item.card.scryfallId) },
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedTransitionKey = sharedTransitionKey
    )
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.magicColors.surfaceVariant)
}

// ─────────────────────────────────────────────────────────────────────────────
//  Group section header
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-span section header for the grouped Cards tab (grid and list). Tapping anywhere on the
 * row collapses/expands the section — the caller (see the `collapsedSections` map in
 * [CardGrid]/[CardList]) simply omits the section's items from the Lazy scope while collapsed.
 */
@Composable
private fun CollectionGroupHeader(
    section:  CollectionSection,
    mode:     CollectionGroupingMode,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    val displayLabel = collectionGroupLabel(section.labelToken, mode, section.items)
    val items = section.items
    val totalCopies = section.totalCopies
    val totalValueEur = section.totalValueEur

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(ChipShape)
            .background(mc.backgroundSecondary.copy(alpha = 0.6f))
            .clickable(onClick = onToggle)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                when (mode) {
                    CollectionGroupingMode.SET -> {
                        com.mmg.manahub.core.ui.components.SetSymbol(
                            setCode = section.labelToken,
                            rarity = com.mmg.manahub.core.ui.components.CardRarity.COMMON,
                            size = 20.dp
                        )
                    }
                    CollectionGroupingMode.COLOR -> {
                        when (section.labelToken) {
                            "Multicolor" -> Icon(
                                imageVector = com.mmg.manahub.core.ui.components.CounterIcon,
                                contentDescription = null,
                                tint = mc.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            "Land" -> Icon(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_land),
                                contentDescription = null,
                                tint = mc.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            else -> com.mmg.manahub.core.ui.components.ManaSymbolImage(
                                token = section.labelToken,
                                size = 18.dp
                            )
                        }
                    }
                    else -> Unit
                }
                
                Text(
                    text = displayLabel,
                    style = ty.titleMedium,
                    color = mc.goldMtg,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = mc.textSecondary,
                modifier = Modifier.padding(start = spacing.sm)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.collection_group_count, items.size, totalCopies),
                style = ty.bodySmall,
                color = mc.textSecondary,
            )
            if (totalValueEur != null) {
                Text(
                    "€%.2f".format(totalValueEur),
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                )
            }
        }
    }
}

/**
 * Resolves the localized display label for a group's [labelToken]. Fixed tokens (type buckets,
 * WUBRG colors, "Lands"/"7+"/set codes/rarities) map to `stringResource`s; [CollectionGroupingMode.TAG]
 * resolves the raw [com.mmg.manahub.core.model.CardTag.key] token back to a real [CardTag] found on
 * one of the section's own items and calls its Android-only [label] extension — `shared/core-model`'s
 * pure [groupCollection] cannot do this itself (see `CollectionGrouping.kt`'s KDoc).
 */
@Composable
private fun collectionGroupLabel(
    labelToken: String,
    mode: CollectionGroupingMode,
    items: List<CollectionCardGroup>,
): String = when (mode) {
    CollectionGroupingMode.TYPE -> when (labelToken) {
        "Creatures"     -> stringResource(R.string.deckdetail_group_creatures)
        "Instants"      -> stringResource(R.string.deckdetail_group_instants)
        "Sorceries"     -> stringResource(R.string.deckdetail_group_sorceries)
        "Enchantments"  -> stringResource(R.string.deckdetail_group_enchantments)
        "Artifacts"     -> stringResource(R.string.deckdetail_group_artifacts)
        "Planeswalkers" -> stringResource(R.string.deckdetail_group_planeswalkers)
        "Lands"         -> stringResource(R.string.deckbuilder_lands)
        else            -> stringResource(R.string.deckdetail_group_other)
    }
    CollectionGroupingMode.COLOR -> when (labelToken) {
        "W"          -> stringResource(R.string.collection_filter_white)
        "U"          -> stringResource(R.string.collection_filter_blue)
        "B"          -> stringResource(R.string.collection_filter_black)
        "R"          -> stringResource(R.string.collection_filter_red)
        "G"          -> stringResource(R.string.collection_filter_green)
        "Multicolor" -> stringResource(R.string.collection_filter_multicolor)
        "Colorless"  -> stringResource(R.string.stats_color_colorless)
        else         -> stringResource(R.string.deckbuilder_lands) // "Land"
    }
    CollectionGroupingMode.CMC -> when {
        labelToken == "Lands" -> stringResource(R.string.deckbuilder_lands)
        labelToken == "7+"    -> stringResource(R.string.deckbuilder_cost_7_plus)
        else                  -> stringResource(R.string.deckbuilder_cost_value, labelToken.toIntOrNull() ?: 0)
    }
    CollectionGroupingMode.SET -> items.firstOrNull()?.card?.setName?.ifBlank { labelToken.uppercase() } ?: labelToken.uppercase()
    CollectionGroupingMode.RARITY -> labelToken.replaceFirstChar { it.uppercase() }.ifBlank { stringResource(R.string.deckdetail_group_other) }
    CollectionGroupingMode.TAG -> {
        if (labelToken == "untagged") {
            stringResource(R.string.deckbuilder_group_untagged)
        } else {
            val tag = items.firstNotNullOfOrNull { group ->
                (group.card.tags + group.card.userTags).find { it.key == labelToken }
            }
            tag?.label() ?: labelToken.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
    }
    CollectionGroupingMode.NONE -> labelToken
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sync banner
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-width tappable banner for pending sync. Tapping directly triggers the sync.
 * While [isSyncing] is true the icon spins and the row is non-interactive.
 */
@Composable
private fun SyncCollectionBanner(
    isSyncing: Boolean,
    onSync:    () -> Unit,
) {
    val mc = MaterialTheme.magicColors

    val infiniteTransition = rememberInfiniteTransition(label = "sync_spin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "angle",
    )

    Surface(
        onClick  = onSync,
        enabled  = !isSyncing,
        color    = mc.primaryAccent.copy(alpha = 0.12f),
        shape    = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector        = Icons.Default.Sync,
                contentDescription = null,
                tint               = mc.primaryAccent,
                modifier           = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = if (isSyncing) angle else 0f },
            )
            Text(
                text     = stringResource(
                    if (isSyncing) R.string.collection_syncing else R.string.collection_sync_banner
                ),
                style    = MaterialTheme.magicTypography.labelLarge,
                color    = mc.primaryAccent,
                modifier = Modifier.weight(1f),
            )
        }
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

val CollectionGroupingMode.displayResId get() = when (this) {
    CollectionGroupingMode.NONE   -> R.string.collection_grouping_none
    CollectionGroupingMode.TYPE   -> R.string.collection_grouping_type
    CollectionGroupingMode.COLOR  -> R.string.collection_grouping_color
    CollectionGroupingMode.CMC    -> R.string.collection_sort_cmc
    CollectionGroupingMode.SET    -> R.string.collection_grouping_set
    CollectionGroupingMode.RARITY -> R.string.collection_sort_rarity
    CollectionGroupingMode.TAG    -> R.string.collection_grouping_tag
}
