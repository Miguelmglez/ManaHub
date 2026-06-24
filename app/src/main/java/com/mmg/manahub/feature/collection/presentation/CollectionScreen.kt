package com.mmg.manahub.feature.collection.presentation

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.model.CollectionCardGroup
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.sync.SyncState
import com.mmg.manahub.core.ui.components.CardGridItem
import com.mmg.manahub.core.ui.components.CardListItem
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.StaleWarningBanner
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.components.search.AdvancedSearchSheet
import com.mmg.manahub.core.ui.components.search.AdvancedSearchViewModel
import com.mmg.manahub.core.ui.theme.magicColors
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

@Composable
fun CollectionScreen(
    onCardClick:              (scryfallId: String) -> Unit,
    onScannerClick:           () -> Unit,
    onDeckClick:              (deckId: String) -> Unit,
    onCreateDeck:             () -> Unit = {},
    onPlaytestClick:          (deckId: String) -> Unit = {},
    onBrowseCommunityDecks:   () -> Unit = {},
    onNavigateToTradeProposal: (receiverId: String) -> Unit = {},
    onNavigateToTradeThread:   (proposalId: String, rootProposalId: String) -> Unit = { _, _ -> },
    initialTab:               CollectionTab = CollectionTab.CARDS,
    viewModel:                CollectionViewModel = koinViewModel(),
    advancedSearchViewModel:  AdvancedSearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdvancedSearch by remember { mutableStateOf(false) }

    // Synchronize initialTab with ViewModel — specifically useful for
    // deep-links or direct navigation to sub-routes (e.g. collection/decks).
    LaunchedEffect(initialTab) {
        viewModel.onTabSelected(initialTab)
    }

    CollectionContent(
        uiState               = uiState,
        onCardClick           = onCardClick,
        onScannerClick        = onScannerClick,
        onDeckClick           = { id ->
            viewModel.onTabSelected(CollectionTab.DECKS)
            onDeckClick(id)
        },
        onCreateDeck          = onCreateDeck,
        onPlaytestClick       = onPlaytestClick,
        onBrowseCommunityDecks = onBrowseCommunityDecks,
        onViewModeToggle      = viewModel::onViewModeToggle,
        onSortChange          = viewModel::onSortChange,
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
    onDeckClick:          (String) -> Unit,
    onCreateDeck:         () -> Unit = {},
    onPlaytestClick:      (String) -> Unit = {},
    onBrowseCommunityDecks: () -> Unit = {},
    onViewModeToggle:     () -> Unit,
    onSortChange:         (SortOrder) -> Unit,
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
                        onScannerClick        = onScannerClick,
                        onSearchQueryChange   = onSearchQueryChange,
                        onClearFilters        = onClearFilters,
                        onShowAdvancedSearch  = onShowAdvancedSearch,
                        onViewModeToggle      = onViewModeToggle,
                        onSortChange          = onSortChange,
                    )
                    CollectionTab.DECKS   -> DeckListScreen(
                        onDeckClick     = onDeckClick,
                        onCreateDeck    = onCreateDeck,
                        onPlaytestClick = onPlaytestClick,
                        onBrowseCommunityDecks = onBrowseCommunityDecks,
                    )
                    CollectionTab.TRADES  -> TradesScreen(
                        onCardClick           = onCardClick,
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

@Composable
private fun CardsTabContent(
    uiState:              CollectionUiState,
    onCardClick:          (String) -> Unit,
    onScannerClick:       () -> Unit,
    onSearchQueryChange:  (String) -> Unit,
    onClearFilters:       () -> Unit,
    onShowAdvancedSearch: () -> Unit,
    onViewModeToggle:     () -> Unit,
    onSortChange:         (SortOrder) -> Unit,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text     = "${uiState.cards.size} ${stringResource(R.string.collection_unique_cards)} · $totalCopies ${stringResource(R.string.collection_total_copies)}",
                style    = MaterialTheme.magicTypography.labelLarge,
                color    = mc.textSecondary,
                modifier = Modifier.weight(1f)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onViewModeToggle, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (uiState.viewMode == CollectionViewMode.GRID) Icons.Default.List else Icons.Default.GridView,
                        contentDescription = stringResource(R.string.collection_view_grid),
                        tint = mc.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                Box {
                    IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector        = Icons.Default.Sort,
                            contentDescription = stringResource(R.string.action_refresh),
                            tint               = mc.textSecondary,
                            modifier           = Modifier.size(18.dp)
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
                                leadingIcon = if (sort == uiState.sortOrder) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null,
                            )
                        }
                    }
                }
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
                onAction    = onScannerClick,
            )
            return@Column
        }

        // Grid or list
        when (uiState.viewMode) {
            CollectionViewMode.GRID -> CardGrid(
                cards       = uiState.cards,
                onCardClick = onCardClick,
            )
            CollectionViewMode.LIST -> CardList(
                cards       = uiState.cards,
                onCardClick = onCardClick,
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

@Composable
private fun CardGrid(
    cards:       List<CollectionCardGroup>,
    onCardClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(minSize = 100.dp),
        contentPadding        = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 80.dp),
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
        contentPadding      = PaddingValues(top = 4.dp, bottom = 80.dp),
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
