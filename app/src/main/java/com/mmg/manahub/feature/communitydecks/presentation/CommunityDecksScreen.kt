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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.MagicColors
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.communitydecks.domain.model.CommunityDeckSummary

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
    viewModel: CommunityDecksSearchViewModel = hiltViewModel(),
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
            } else {
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
) {
    val spacing = MaterialTheme.spacing

    Column(
        modifier = Modifier
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

        CommunityDeckFormatChips(
            selected = state.selectedFormat,
            onSelect = onFormatSelected,
        )

        Spacer(Modifier.height(spacing.xs))

        CommunityDeckSortRow(
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

/** Horizontally-scrollable format filter chips. */
@Composable
private fun CommunityDeckFormatChips(
    selected: CommunityDeckFormatFilter,
    onSelect: (CommunityDeckFormatFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        items(
            items = CommunityDeckFormatFilter.entries,
            key = { it.name },
        ) { format ->
            val isSelected = format == selected
            Surface(
                onClick = { onSelect(format) },
                shape = ChipShape,
                color = if (isSelected) mc.primaryAccent else mc.surface,
                contentColor = if (isSelected) mc.onAccent else mc.textSecondary,
                border = if (isSelected) {
                    null
                } else {
                    BorderStroke(1.dp, mc.surfaceVariant)
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(
                        horizontal = spacing.md,
                        vertical = spacing.sm,
                    ),
                ) {
                    Text(
                        text = format.label,
                        style = ty.labelMedium,
                    )
                }
            }
        }
    }
}

/** Compact sort selector with a dropdown menu. */
@Composable
private fun CommunityDeckSortRow(
    selected: CommunityDeckSort,
    onSelect: (CommunityDeckSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(ChipShape)
                .clickable { expanded = true }
                .heightIn(min = 48.dp)
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = stringResource(
                    R.string.community_deck_sort_label,
                    stringResource(selected.labelRes),
                ),
                style = ty.labelMedium,
                color = mc.textSecondary,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = mc.textSecondary,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            CommunityDeckSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(sort.labelRes),
                            style = ty.bodyMedium,
                            color = if (sort == selected) mc.primaryAccent else mc.textPrimary,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(sort)
                    },
                )
            }
        }
    }
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
        Column(modifier = Modifier.padding(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = deck.name,
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(spacing.sm))
                ColorIdentityDots(colors = deck.colorIdentity)
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

/** Small colored dots for a deck's color identity (W/U/B/R/G). */
@Composable
private fun ColorIdentityDots(
    colors: List<String>,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing

    if (colors.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEach { symbol ->
            val color = symbol.toManaColor(mc)
            if (color != null) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, mc.textDisabled.copy(alpha = 0.4f), CircleShape),
                )
            }
        }
    }
}

/** Maps a WUBRG symbol to its MagicColors mana token. Returns null for unknown symbols. */
private fun String.toManaColor(mc: MagicColors): Color? = when (uppercase()) {
    "W" -> mc.manaW
    "U" -> mc.manaU
    "B" -> mc.manaB
    "R" -> mc.manaR
    "G" -> mc.manaG
    else -> null
}
