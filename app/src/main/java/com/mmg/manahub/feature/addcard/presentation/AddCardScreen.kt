package com.mmg.manahub.feature.addcard.presentation

// COMMENTS_REVIEWED: 2026-09-06

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddToPhotos
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.data.network.RateLimitExhaustedException
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.CardRow
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.components.LanguageSelectorSheet
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicProgressBar
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.components.rememberRateLimitCountdownSeconds
import com.mmg.manahub.core.ui.components.search.AdvancedSearchSheet
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.CardConstants
import com.mmg.manahub.core.util.PriceFormatter
import org.jetbrains.compose.resources.painterResource
import org.koin.androidx.compose.koinViewModel

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
    onNavigateToMultiAdd: () -> Unit,
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
                    modifier =
                        Modifier
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
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onNavigateToMultiAdd) {
                        Icon(
                            imageVector = Icons.Default.AddToPhotos,
                            contentDescription = stringResource(R.string.addcard_multi_add_button),
                            tint = mc.textPrimary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    IconButton(onClick = onNavigateToScanner) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = stringResource(R.string.addcard_scanner_button),
                            tint = mc.textPrimary,
                        )
                    }
                }
            }
        },
        containerColor = mc.background,
    ) { padding ->
        HexGridBackground(modifier = Modifier.fillMaxSize(), color = mc.primaryAccent.copy(alpha = 0.05f))

        Box(
            modifier =
                Modifier
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
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onViewModeToggle = viewModel::onViewModeToggle,
            )

            if (showAdvancedSearch) {
                AdvancedSearchSheet(
                    onDismiss = { showAdvancedSearch = false },
                    onSearch = { query, _ ->
                        viewModel.onAdvancedQuerySearch(query)
                        showAdvancedSearch = false
                    },
                    // The sheet's ViewModel outlives any single open, so it must be re-seeded from
                    // the query actually driving the results right now.
                    appliedQuery = uiState.activeQuery,
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
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onViewModeToggle: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier =
            Modifier
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
                                tint = mc.textDisabled,
                            )
                        }
                    } else {
                        val languageDescription =
                            stringResource(
                                R.string.addcard_language_button,
                                CardConstants.getLanguageName(uiState.searchLanguage),
                            )
                        Box(
                            modifier =
                                Modifier
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
                keyboardActions =
                    KeyboardActions(onSearch = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        onForceSearch()
                    }),
                colors =
                    OutlinedTextFieldDefaults.colors(
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
                },
            ) {
                IconButton(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        onAdvancedSearch()
                    },
                    modifier =
                        Modifier
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
        if (uiState.isSearching && uiState.results.isNotEmpty()) {
            MagicProgressBar(modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(8.dp))

        // ── Active filters indicator ─────────────────────────────────────────
        AnimatedVisibility(visible = uiState.activeFilterCount > 0) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.collection_active_filters, uiState.activeFilterCount),
                    style = ty.bodyMedium,
                    color = mc.primaryAccent,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onClearFilters,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        stringResource(R.string.collection_clear_filters),
                        style = ty.labelMedium,
                        color = mc.lifeNegative,
                    )
                }
            }
        }

        // ── Content states ────────────────────────────────────────────────────
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val isIdle = uiState.query.length < 2 && uiState.activeFilterCount == 0

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isSearching && uiState.results.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MagicLoadingSpinner(
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                uiState.error != null && uiState.results.isEmpty() -> {
                    val rateLimitRetryAfterMs = RateLimitExhaustedException.retryAfterMsOrNull(uiState.error)
                    if (uiState.error == "SCRYFALL_404") {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                title = stringResource(R.string.addcard_no_results),
                                subtitle = stringResource(R.string.addcard_no_results_subtitle),
                                actionLabel = stringResource(R.string.collection_clear_filters),
                                onAction = onClearAll,
                            )
                        }
                    } else if (rateLimitRetryAfterMs != null) {
                        // WS2: Scryfall rate-limit exhausted -- disable retry with a live countdown
                        // instead of letting a rapid re-tap re-trigger the storm the queue is already
                        // recovering from (RateLimitedQueue's shared cooldown gates the actual network
                        // call regardless, but disabling the CTA gives clearer, immediate feedback).
                        val remainingSeconds = rememberRateLimitCountdownSeconds(rateLimitRetryAfterMs)

                        EmptyState(
                            title = stringResource(R.string.error_rate_limited_message),
                            subtitle = stringResource(R.string.error_rate_limited_retry_countdown, remainingSeconds),
                            actionLabel = stringResource(R.string.retry),
                            enabled = remainingSeconds <= 0,
                            onAction = onForceSearch,
                        )
                    } else {
                        EmptyState(
                            title = stringResource(R.string.error_unknown),
                            actionLabel = stringResource(R.string.retry),
                            onAction = onForceSearch,
                        )
                    }
                }

                isIdle -> {
                    EmptyState(
                        title = stringResource(R.string.addcard_index_title),
                        subtitle = stringResource(R.string.addcard_index_subtitle),
                    )
                    /*SpotlightGrid(
                        spotlightCards = uiState.spotlightCards,
                        spotlightSet = uiState.spotlightSet,
                        isSpotlightLoading = uiState.isSpotlightLoading,
                        contentPaddingBottom = navBarBottom,
                        onCardSelected = onCardSelected,
                        onLoadNextSpotlight = onLoadNextSpotlight,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )*/
                }

                uiState.results.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = stringResource(R.string.addcard_no_results),
                            subtitle = stringResource(R.string.addcard_no_results_subtitle),
                            actionLabel = if (uiState.activeFilterCount > 0) stringResource(R.string.collection_clear_filters) else null,
                            onAction = if (uiState.activeFilterCount > 0) onClearFilters else null,
                        )
                    }
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.advsearch_show_results, uiState.totalCards),
                                style = MaterialTheme.magicTypography.labelLarge,
                                color = mc.textSecondary,
                                modifier = Modifier.weight(1f),
                            )

                            IconButton(onClick = onViewModeToggle, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector =
                                        if (uiState.viewMode ==
                                            CollectionViewMode.GRID
                                        ) {
                                            Icons.AutoMirrored.Filled.List
                                        } else {
                                            Icons.Default.GridView
                                        },
                                    contentDescription = stringResource(R.string.collection_view_grid),
                                    tint = mc.textSecondary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }

                        if (uiState.viewMode == CollectionViewMode.LIST) {
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
                        } else {
                            ResultsGrid(
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
        modifier = Modifier.fillMaxSize(),
    ) {
        items(results, key = { it.scryfallId }, contentType = { "card" }) { card ->
            CardRow(
                card = card,
                isInCollection = false,
                onClick = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onCardSelected(card)
                },
                onRemove = null,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                preferredCurrency = uiState.preferredCurrency,
            )
        }
        if (hasMore) {
            item(contentType = "pagination_footer") {
                LaunchedEffect(true) {
                    onLoadNextPage()
                }
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    MagicLoadingSpinner(
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ResultsGrid(
    results: List<Card>,
    uiState: AddCardUiState,
    hasMore: Boolean,
    contentPaddingBottom: Dp,
    onCardSelected: (Card) -> Unit,
    onLoadNextPage: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
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

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        state = rememberLazyGridState(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp + contentPaddingBottom),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(results, key = { it.scryfallId }, contentType = { "card" }) { card ->
            SearchResultGridItem(
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
                    MagicLoadingSpinner(
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Search result Grid Item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchResultGridItem(
    card: Card,
    onClick: () -> Unit,
    uiState: AddCardUiState,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = MaterialTheme.magicColors.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .clip(CardShape)
                    .background(MaterialTheme.magicColors.surfaceVariant),
            horizontalAlignment = Alignment.Start,
        ) {
            AsyncImage(
                model = card.imageNormal,
                contentDescription = card.name,
                placeholder = painterResource(Res.drawable.mtg_card_back),
                error = painterResource(Res.drawable.mtg_card_back),
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .aspectRatio(0.716f)
                        .clip(CardShape)
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
                            } else {
                                Modifier
                            },
                        ).background(MaterialTheme.magicColors.surfaceVariant),
            )
            CardName(
                name = card.name,
                showFrontOnly = true,
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.spacing.xs)
                        .padding(top = MaterialTheme.spacing.xs),
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = MaterialTheme.spacing.xs,
                        ).padding(top = MaterialTheme.spacing.xs, bottom = MaterialTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SetSymbol(
                    setCode = card.setCode,
                    rarity = CardRarity.fromString(card.rarity),
                    size = 14.dp,
                )

                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = card.setCode.uppercase(),
                    style = MaterialTheme.magicTypography.labelSmall.copy(fontSize = 10.sp),
                    color = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.weight(1f))

                val formattedPrice =
                    PriceFormatter.formatFromScryfall(
                        priceUsd = card.priceUsd,
                        priceEur = card.priceEur,
                        preferredCurrency = uiState.preferredCurrency,
                    )
                if (formattedPrice != "—") {
                    Text(
                        text = formattedPrice,
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.goldMtg,
                    )
                }
            }
        }
    }
}

