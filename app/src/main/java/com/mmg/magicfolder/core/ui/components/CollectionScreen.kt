package com.mmg.magicfolder.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmg.magicfolder.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.magicfolder.feature.collection.CollectionViewModel
import com.mmg.magicfolder.feature.collection.ColorFilter
import com.mmg.magicfolder.feature.collection.SortOrder
import com.mmg.magicfolder.feature.collection.ViewMode
import com.mmg.magicfolder.core.domain.model.UserCardWithCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    onCardClick:    (scryfallId: String) -> Unit,
    onAddCardClick: () -> Unit,
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CollectionTopBar(
                viewMode        = uiState.viewMode,
                onViewModeToggle = viewModel::onViewModeToggle,
                onSortChange    = viewModel::onSortChange,
                currentSort     = uiState.sortOrder,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCardClick) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.collection_empty_action))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Stale data warning
            AnimatedVisibility(visible = uiState.hasStaleCards) {
                StaleWarningBanner()
            }

            // Search bar
            SearchBar(
                query    = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Color filter chips
            ColorFilterRow(
                activeFilter = uiState.activeFilter,
                onFilterChange = viewModel::onFilterChange,
            )

            // Card count
            Text(
                text     = "${uiState.cards.size} cards",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            // Loading
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            // Empty state
            if (uiState.cards.isEmpty() && !uiState.isLoading) {
                EmptyCollectionState(onAddCardClick = onAddCardClick)
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
                    onDelete    = viewModel::onDeleteCard,
                )
            }
        }

        // Error snackbar
        uiState.error?.let { error ->
            LaunchedEffect(error) {
                // Show snackbar? Or just clear for now.
                viewModel.onErrorDismissed()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionTopBar(
    viewMode:        ViewMode,
    onViewModeToggle: () -> Unit,
    onSortChange:    (SortOrder) -> Unit,
    currentSort:     SortOrder,
) {
    var showSortMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(stringResource(R.string.collection_title)) },
        actions = {
            // View mode toggle
            IconButton(onClick = onViewModeToggle) {
                Icon(
                    imageVector = if (viewMode == ViewMode.GRID)
                        Icons.Default.List else Icons.Default.GridView,
                    contentDescription = stringResource(R.string.collection_view_grid),
                )
            }
            // Sort menu
            IconButton(onClick = { showSortMenu = true }) {
                Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.action_refresh))
            }
            DropdownMenu(
                expanded         = showSortMenu,
                onDismissRequest = { showSortMenu = false },
            ) {
                SortOrder.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort.displayName) },
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
        },
    )
}

@Composable
private fun SearchBar(
    query:        String,
    onQueryChange: (String) -> Unit,
    modifier:     Modifier = Modifier,
) {
    OutlinedTextField(
        value         = query,
        onValueChange = onQueryChange,
        modifier      = modifier.fillMaxWidth(),
        placeholder   = { Text(stringResource(R.string.collection_search_hint)) },
        leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon  = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_close))
                }
            }
        } else null,
        singleLine    = true,
        shape         = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun ColorFilterRow(
    activeFilter:  ColorFilter,
    onFilterChange: (ColorFilter) -> Unit,
) {
    LazyRow(
        contentPadding    = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier          = Modifier.padding(vertical = 4.dp),
    ) {
        items(ColorFilter.entries) { filter ->
            FilterChip(
                selected = filter == activeFilter,
                onClick  = { onFilterChange(filter) },
                label    = { Text(filter.displayName) },
            )
        }
    }
}

@Composable
private fun CardGrid(
    cards:       List<UserCardWithCard>,
    onCardClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns             = GridCells.Adaptive(minSize = 110.dp),
        contentPadding      = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(cards, key = { it.userCard.id }) { item ->
            CardGridItem(item = item, onClick = { onCardClick(item.card.scryfallId) })
        }
    }
}

@Composable
private fun CardList(
    cards:       List<UserCardWithCard>,
    onCardClick: (String) -> Unit,
    onDelete:    (Long) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding      = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(cards, key = { it.userCard.id }) { item ->
            CardListItem(
                item      = item,
                onClick   = { onCardClick(item.card.scryfallId) },
                onDelete  = { onDelete(item.userCard.id) },
            )
            HorizontalDivider(thickness = 0.5.dp)
        }
    }
}

@Composable
private fun EmptyCollectionState(onAddCardClick: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector         = Icons.Default.CollectionsBookmark,
            contentDescription  = null,
            tint                = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier            = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.collection_empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.collection_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddCardClick) { Text(stringResource(R.string.collection_empty_action)) }
    }
}

// Display name extensions
val SortOrder.displayName get() = when (this) {
    SortOrder.DATE_ADDED -> "Date added"
    SortOrder.NAME       -> "Name"
    SortOrder.PRICE_DESC -> "Price: high to low"
    SortOrder.PRICE_ASC  -> "Price: low to high"
    SortOrder.RARITY     -> "Rarity"
}

val ColorFilter.displayName get() = when (this) {
    ColorFilter.ALL        -> "All"
    ColorFilter.W          -> "White"
    ColorFilter.U          -> "Blue"
    ColorFilter.B          -> "Black"
    ColorFilter.R          -> "Red"
    ColorFilter.G          -> "Green"
    ColorFilter.COLORLESS  -> "Colorless"
    ColorFilter.MULTICOLOR -> "Multi"
}
