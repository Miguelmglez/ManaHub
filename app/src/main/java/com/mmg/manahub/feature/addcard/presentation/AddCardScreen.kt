package com.mmg.manahub.feature.addcard.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.MagicProgressBar
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.components.search.AdvancedSearchSheet
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.CardConstants
import com.mmg.manahub.core.util.PriceFormatter

// ─────────────────────────────────────────────────────────────────────────────
//  AddCardScreen — search-first entry point for adding cards to the collection.
//
//  Idle state:   a discovery grid of random cards from one Scryfall set (paginates
//                to the next set as the user scrolls), mirroring Home's Discover widget.
//  Typed query:  the usual results list.
//  The camera scanner is reached via a FAB (not an inline entry point).
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AddCardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToCardDetail: (String) -> Unit,
    viewModel: AddCardViewModel = koinViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdvancedSearch by rememberSaveable { mutableStateOf(false) }
    var showLanguageSheet by rememberSaveable { mutableStateOf(false) }
    val toastState = rememberMagicToastState()

    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    // Error toast for errors that occur when we already have results
    LaunchedEffect(uiState.error, uiState.results.isNotEmpty()) {
        val error = uiState.error
        if (error != null && uiState.results.isNotEmpty()) {
            toastState.show(error, MagicToastType.ERROR)
            viewModel.onErrorDismissed()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Surface(
                color = mc.backgroundSecondary,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = mc.textPrimary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.addcard_title),
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        floatingActionButton = {
            Box(modifier = Modifier.padding(bottom = 80.dp)) {
                FloatingActionButton(
                    onClick = onNavigateToScanner,
                    containerColor = mc.primaryAccent,
                    contentColor = mc.onAccent,
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = stringResource(R.string.addcard_scanner_button),
                    )
                }
            }
        },
        containerColor = mc.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchSurface(
                uiState = uiState,
                onQueryChange = viewModel::onQueryChange,
                onClearFilters = viewModel::onClearFilters,
                onClearAll = viewModel::onClearAll,
                onShowLanguageSheet = { showLanguageSheet = true },
                onCardSelected = { card -> onNavigateToCardDetail(card.scryfallId) },
                onAdvancedSearch = { showAdvancedSearch = true },
                onForceSearch = viewModel::forceSearch,
                onLoadNextPage = viewModel::loadNextPage,
                onLoadNextSpotlight = viewModel::loadSpotlightFeed,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )

            if (showAdvancedSearch) {
                AdvancedSearchSheet(
                    onDismiss = { showAdvancedSearch = false },
                    onSearch = { query, _ ->
                        viewModel.onAdvancedQuerySearch(query)
                        showAdvancedSearch = false
                    },
                )
            }
            if (showLanguageSheet) {
                LanguageSelectorSheet(
                    selectedLanguage = uiState.searchLanguage,
                    onDismiss = { showLanguageSheet = false },
                    onSelectLanguage = { code ->
                        viewModel.onLanguageChange(code)
                        showLanguageSheet = false
                    },
                )
            }
            MagicToastHost(toastState)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Search surface: field + filters + content state
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SearchSurface(
    uiState: AddCardUiState,
    onQueryChange: (String) -> Unit,
    onClearFilters: () -> Unit,
    onClearAll: () -> Unit,
    onShowLanguageSheet: () -> Unit,
    onCardSelected: (Card) -> Unit,
    onAdvancedSearch: () -> Unit,
    onForceSearch: () -> Unit,
    onLoadNextPage: () -> Unit,
    onLoadNextSpotlight: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        // ── Search field + advanced search button ─────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(stringResource(R.string.addcard_search_hint), color = mc.textDisabled)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = mc.textDisabled)
                },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(R.string.action_close),
                                tint = mc.textDisabled
                            )
                        }
                    } else {
                        val languageDescription = stringResource(
                            R.string.addcard_language_button,
                            CardConstants.getLanguageName(uiState.searchLanguage),
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onShowLanguageSheet)
                                .semantics { contentDescription = languageDescription },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = CardConstants.getFlag(uiState.searchLanguage),
                                style = ty.titleLarge,
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onForceSearch()
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = mc.primaryAccent,
                    unfocusedBorderColor = mc.primaryAccent.copy(alpha = 0.25f),
                    cursorColor = mc.primaryAccent,
                    focusedTextColor = mc.textPrimary,
                    unfocusedTextColor = mc.textPrimary,
                    focusedContainerColor = mc.surface,
                    unfocusedContainerColor = mc.surface,
                ),
                shape = CardShape,
            )
            BadgedBox(
                badge = {
                    if (uiState.activeFilterCount > 0) {
                        Badge(
                            containerColor = mc.primaryAccent,
                            contentColor = mc.onAccent,
                        ) { Text("${uiState.activeFilterCount}") }
                    }
                }
            ) {
                IconButton(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        onAdvancedSearch()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CardShape)
                        .background(mc.primaryAccent.copy(alpha = 0.1f)),
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.advsearch_button),
                        tint = mc.primaryAccent,
                    )
                }
            }
        }

        // ── Active filters indicator ─────────────────────────────────────────
        AnimatedVisibility(visible = uiState.activeFilterCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.collection_active_filters, uiState.activeFilterCount),
                    style = ty.bodySmall,
                    color = mc.primaryAccent,
                )
                TextButton(
                    onClick = onClearFilters,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        stringResource(R.string.collection_clear_filters),
                        style = ty.labelSmall,
                        color = mc.lifeNegative,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (uiState.isSearching && uiState.results.isNotEmpty()) {
             MagicProgressBar(modifier = Modifier.fillMaxWidth())
        }

        // ── Content states ────────────────────────────────────────────────────
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val isIdle = uiState.query.length < 2 && uiState.activeFilterCount == 0

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isSearching && uiState.results.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = mc.primaryAccent,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                uiState.error != null && uiState.results.isEmpty() -> {
                    if (uiState.error == "SCRYFALL_404") {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                title = stringResource(R.string.addcard_no_results),
                                subtitle = stringResource(R.string.addcard_no_results_subtitle),
                                actionLabel = stringResource(R.string.collection_clear_filters),
                                onAction = onClearAll
                            )
                        }
                    } else {
                        InlineErrorState(
                            message = uiState.error,
                            onRetry = onForceSearch,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                isIdle -> {
                    SpotlightGrid(
                        spotlightCards = uiState.spotlightCards,
                        spotlightSet = uiState.spotlightSet,
                        isSpotlightLoading = uiState.isSpotlightLoading,
                        contentPaddingBottom = navBarBottom,
                        onCardSelected = onCardSelected,
                        onLoadNextSpotlight = onLoadNextSpotlight,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
                uiState.results.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = stringResource(R.string.addcard_no_results),
                            subtitle = stringResource(R.string.addcard_no_results_subtitle),
                            actionLabel = if (uiState.activeFilterCount > 0) stringResource(R.string.collection_clear_filters) else null,
                            onAction = if (uiState.activeFilterCount > 0) onClearFilters else null
                        )
                    }
                }
                else -> {
                    ResultsList(
                        results = uiState.results,
                        uiState = uiState,
                        hasMore = uiState.hasMore,
                        contentPaddingBottom = navBarBottom,
                        onCardSelected = onCardSelected,
                        onLoadNextPage = onLoadNextPage,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Idle state — discovery grid of random cards from one Scryfall set
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SpotlightGrid(
    spotlightCards: List<Card>,
    spotlightSet: MagicSet?,
    isSpotlightLoading: Boolean,
    contentPaddingBottom: Dp,
    onCardSelected: (Card) -> Unit,
    onLoadNextSpotlight: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 100.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp + contentPaddingBottom),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (spotlightSet != null) {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "spotlight_header") {
                Text(
                    text = stringResource(R.string.addcard_spotlight_header, spotlightSet.name),
                    style = ty.labelLarge,
                    color = mc.textSecondary,
                    modifier = Modifier.padding(top = spacing.md, bottom = spacing.xs)
                )
            }
        }
        items(spotlightCards, key = { it.scryfallId }, contentType = { "spotlight_card" }) { card ->
            SpotlightCardTile(
                card = card,
                onClick = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onCardSelected(card)
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
        if (isSpotlightLoading) {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "spotlight_loading") {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = mc.primaryAccent,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        } else if (spotlightCards.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "spotlight_footer") {
                LaunchedEffect(true) {
                    onLoadNextSpotlight()
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SpotlightCardTile(
    card: Card,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val mc = MaterialTheme.magicColors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Full MTG card aspect ratio (745:1040) so the whole card is shown.
            .aspectRatio(0.717f)
            .clip(CardShape)
            .then(
                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "card-image-${card.scryfallId}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            clipInOverlayDuringTransition = OverlayClip(CardShape),
                            renderInOverlayDuringTransition = true,
                        )
                    }
                } else Modifier
            )
            .background(mc.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = card.imageNormal,
            contentDescription = card.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Typed-query results list
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ResultsList(
    results: List<Card>,
    uiState: AddCardUiState,
    hasMore: Boolean,
    contentPaddingBottom: Dp,
    onCardSelected: (Card) -> Unit,
    onLoadNextPage: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val mc = MaterialTheme.magicColors
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    // Clear focus when scrolling results
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp + contentPaddingBottom),
        modifier = Modifier.fillMaxSize()
    ) {
        items(results, key = { it.scryfallId }, contentType = { "card" }) { card ->
            SearchResultItem(
                card = card,
                uiState = uiState,
                onClick = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onCardSelected(card)
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
        if (hasMore) {
            item(contentType = "pagination_footer") {
                LaunchedEffect(true) {
                    onLoadNextPage()
                }
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = mc.primaryAccent,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Search result row
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SearchResultItem(
    card: Card,
    uiState: AddCardUiState,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        onClick = onClick,
        shape = CardShape,
        color = mc.surface,
        border = BorderStroke(0.5.dp, mc.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Art thumbnail
            AsyncImage(
                model = card.imageNormal,
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 44.dp, height = 60.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "card-image-${card.scryfallId}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(6.dp)),
                                    renderInOverlayDuringTransition = true,
                                )
                            }
                        } else Modifier
                    )
                    .background(mc.surfaceVariant),
            )

            // Name / type / set
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val frontFace = card.cardFaces?.firstOrNull()
                val printedName = card.printedName
                val printedTypeLine = card.printedTypeLine
                CardName(
                    name = frontFace?.name ?: if (printedName.isNullOrEmpty()) card.name else printedName,
                    showFrontOnly = true,
                    style = ty.bodyMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = frontFace?.typeLine ?: if (printedTypeLine.isNullOrEmpty()) card.typeLine else printedTypeLine,
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SetSymbol(
                        setCode = card.setCode,
                        rarity = CardRarity.fromString(card.rarity),
                        size = 14.dp,
                    )
                    Text(
                        text = card.setName,
                        style = ty.labelSmall,
                        color = mc.secondaryAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Mana cost + price
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                card.manaCost?.let {
                    ManaCostImages(manaCost = it, symbolSize = 14.dp)
                }
                val formattedPrice = PriceFormatter.formatFromScryfall(
                    priceUsd = card.priceUsd,
                    priceEur = card.priceEur,
                    preferredCurrency = uiState.preferredCurrency
                )
                if (formattedPrice != "—") {
                    Text(
                        text = formattedPrice,
                        style = ty.bodySmall,
                        color = mc.goldMtg,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Search language bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelectorSheet(
    selectedLanguage: String,
    onDismiss: () -> Unit,
    onSelectLanguage: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.background,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.addcard_language_sheet_title),
                style = ty.titleMedium,
                color = mc.textPrimary,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(CardConstants.languages, key = { it.first }) { (code, flag) ->
                    val isSelected = code == selectedLanguage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onSelectLanguage(code) }
                            .then(
                                if (isSelected) Modifier.background(mc.primaryAccent.copy(alpha = 0.08f))
                                else Modifier
                            )
                            .padding(horizontal = spacing.lg, vertical = spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        Text(text = flag, style = ty.titleLarge)
                        Text(
                            text = CardConstants.getLanguageName(code),
                            style = ty.bodyMedium,
                            color = if (isSelected) mc.primaryAccent else mc.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = mc.primaryAccent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
