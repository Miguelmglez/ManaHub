package com.mmg.manahub.feature.addcard.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.data.network.RateLimitExhaustedException
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.CardQueueSheet
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.EditQueuedCardSheet
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.FullScreenImageViewer
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.components.LanguageSelectorSheet
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicProgressBar
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.components.VariantSelectorSheet
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

    // Sheets render in their own window above the NavHost: unmount them while navigating away and
    // remount on return (their open state lives in the ViewModel).
    val isResumed = LocalLifecycleOwner.current.lifecycle
        .currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    val isQueueSheetVisible = uiState.showQueueSheet && isResumed
    val queueToastMessage = uiState.queueToast?.let { queueToastText(it) }
    val queueToastType = uiState.queueToast?.toastType() ?: MagicToastType.SUCCESS
    // While the queue sheet is visible it shows the toast in its own host (the screen's is covered).
    LaunchedEffect(queueToastMessage, isQueueSheetVisible) {
        if (queueToastMessage != null && !isQueueSheetVisible) {
            toastState.show(queueToastMessage, queueToastType)
            viewModel.onQueueToastShown()
        }
    }

    val isMultiSelectMode = uiState.isMultiSelectMode
    val onCardClick: (Card) -> Unit = remember(isMultiSelectMode, onNavigateToCardDetail) {
        if (isMultiSelectMode) viewModel::onToggleCardSelection
        else { card -> onNavigateToCardDetail(card.scryfallId) }
    }
    val onCardLongClick: ((Card) -> Unit)? = remember(isMultiSelectMode, onNavigateToCardDetail) {
        if (isMultiSelectMode) { card -> onNavigateToCardDetail(card.scryfallId) } else null
    }
    val queueListState = rememberLazyListState()
    val density = LocalDensity.current
    var ctaHeightPx by remember { mutableIntStateOf(0) }
    val ctaReservedHeight = if (uiState.showProceedCta) {
        with(density) { ctaHeightPx.toDp() }
    } else 0.dp

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
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (isMultiSelectMode) R.string.addcard_multi_select_title else R.string.addcard_title
                            ),
                            style = ty.titleLarge,
                            color = mc.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val deckName = uiState.deckName
                        if (uiState.isDeckMode && !deckName.isNullOrBlank()) {
                            Text(
                                text = deckName,
                                style = ty.bodySmall,
                                color = mc.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconToggleButton(
                        checked = isMultiSelectMode,
                        onCheckedChange = { viewModel.onToggleMultiSelectMode() },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = stringResource(R.string.addcard_multi_select_toggle_cd),
                            tint = if (isMultiSelectMode) mc.primaryAccent else mc.textPrimary,
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
                onCardSelected = onCardClick,
                onCardLongClick = onCardLongClick,
                onAdvancedSearch = { showAdvancedSearch = true },
                onForceSearch = viewModel::forceSearch,
                onLoadNextPage = viewModel::loadNextPage,
                onLoadNextSpotlight = viewModel::loadSpotlightFeed,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onViewModeToggle = viewModel::onViewModeToggle,
                extraBottomPadding = ctaReservedHeight,
                onClearDeckCards = viewModel::onClearDeckCards,
                onSelectAllDeckCards = viewModel::onSelectAllDeckCards,
                onSelectMissingDeckCards = viewModel::onSelectMissingDeckCards,
                onRetryDeckLoad = viewModel::onRetryDeckLoad,
            )

            AnimatedVisibility(
                visible = uiState.showProceedCta,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { ctaHeightPx = it.height },
            ) {
                MagicCtaButton(
                    onClick = viewModel::onOpenQueueSheet,
                    text = pluralStringResource(
                        R.plurals.addcard_multi_select_proceed,
                        uiState.queueCount,
                        uiState.queueCount,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.md),
                )
            }

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
            if (isQueueSheetVisible) {
                CardQueueSheet(
                    cards = uiState.queue,
                    preferredCurrency = uiState.preferredCurrency,
                    ownedCardIdentityKeys = uiState.ownedCardIdentityKeys,
                    isAutoDeleteOnAddEnabled = uiState.isAutoDeleteOnAddEnabled,
                    isCommitting = uiState.isCommittingQueue,
                    toastMessage = queueToastMessage,
                    toastType = queueToastType,
                    onToastShown = viewModel::onQueueToastShown,
                    onDismiss = viewModel::onCloseQueueSheet,
                    onRemoveCard = viewModel::onRemoveQueuedCard,
                    onEditCard = viewModel::onEditQueuedCard,
                    onClearQueue = viewModel::onClearQueue,
                    onAddAllToCollection = viewModel::onAddAllToCollection,
                    onAddAllToWishlist = viewModel::onAddAllToWishlist,
                    onAddEntryToCollection = viewModel::onAddEntryToCollection,
                    onAddEntryToWishlist = viewModel::onAddEntryToWishlist,
                    onCardClick = { entry -> onNavigateToCardDetail(entry.card.scryfallId) },
                    onDuplicateCard = viewModel::onDuplicateQueuedCard,
                    onToggleAutoDeleteOnAdd = viewModel::onToggleAutoDeleteOnAdd,
                    onIncrementQuantity = viewModel::onIncrementQueuedCardQuantity,
                    onDecrementQuantity = viewModel::onDecrementQueuedCardQuantity,
                    listState = queueListState,
                )
            }
            val editingQueuedCard = uiState.editingQueuedCard
            if (editingQueuedCard != null && isResumed) {
                EditQueuedCardSheet(
                    queuedCard = editingQueuedCard,
                    availablePrints = uiState.availablePrints,
                    isLoadingPrints = uiState.isLoadingPrints,
                    onDismiss = viewModel::onCloseEditSheet,
                    onConfirm = viewModel::onUpdateQueuedCard,
                    onOpenVariantSelector = { viewModel.onOpenVariantSelector(editingQueuedCard) },
                )
            }
            val variantSelectorEntry = uiState.variantSelectorEntry
            if (variantSelectorEntry != null && isResumed) {
                VariantSelectorSheet(
                    currentCardId = variantSelectorEntry.card.scryfallId,
                    variants = uiState.cardVariants,
                    isLoading = uiState.isLoadingVariants,
                    onDismiss = viewModel::onCloseVariantSelector,
                    onSelectVariant = viewModel::onSelectVariant,
                    onExpandImage = viewModel::onExpandVariantImage,
                )
            }
            val expandedVariantImageUrl = uiState.expandedVariantImageUrl
            if (expandedVariantImageUrl != null && isResumed) {
                FullScreenImageViewer(
                    imageUrl = expandedVariantImageUrl,
                    onDismiss = viewModel::onCloseExpandedImage,
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
    onCardLongClick: ((Card) -> Unit)?,
    onAdvancedSearch: () -> Unit,
    onForceSearch: () -> Unit,
    onLoadNextPage: () -> Unit,
    onLoadNextSpotlight: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onViewModeToggle: () -> Unit,
    extraBottomPadding: Dp,
    onClearDeckCards: () -> Unit,
    onSelectAllDeckCards: () -> Unit,
    onSelectMissingDeckCards: () -> Unit,
    onRetryDeckLoad: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val focusManager = LocalFocusManager.current
    val isDeckMode = uiState.isDeckMode
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
                    } else if (!isDeckMode) {
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
        if (uiState.isSearching && uiState.results.isNotEmpty()) {
            MagicProgressBar(modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(8.dp))

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
                    style = ty.bodyMedium,
                    color = mc.primaryAccent,
                    modifier = Modifier.weight(1f)
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
        // The Proceed CTA's measured height already includes the navigation bar inset.
        val navBarBottom = if (extraBottomPadding > 0.dp) {
            extraBottomPadding
        } else {
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        }
        val isIdle = !isDeckMode && uiState.query.length < 2 && uiState.activeFilterCount == 0

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isDeckMode && uiState.isDeckLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MagicLoadingSpinner(modifier = Modifier.size(32.dp))
                    }
                }
                isDeckMode && uiState.deckLoadFailed -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        InlineErrorState(
                            message = stringResource(R.string.addcard_deck_load_error),
                            retryLabel = stringResource(R.string.retry),
                            onRetry = onRetryDeckLoad,
                        )
                        TextButton(onClick = onClearDeckCards) {
                            Text(
                                text = stringResource(R.string.addcard_clear_deck_cards),
                                style = ty.labelLarge,
                                color = mc.primaryAccent,
                            )
                        }
                    }
                }
                isDeckMode && uiState.deckCards.isEmpty() -> {
                    EmptyState(
                        title = stringResource(R.string.addcard_deck_empty_title),
                        subtitle = stringResource(R.string.addcard_deck_empty_subtitle),
                        actionLabel = stringResource(R.string.addcard_clear_deck_cards),
                        onAction = onClearDeckCards,
                    )
                }
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
                                onAction = onClearAll
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
                            onAction = onForceSearch
                        )
                    } else {
                        EmptyState(
                            title = stringResource(R.string.error_unknown),
                            actionLabel = stringResource(R.string.retry),
                            onAction = onForceSearch
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
                            onAction = if (uiState.activeFilterCount > 0) onClearFilters else null
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
                            if (isDeckMode) {
                                Box(modifier = Modifier.weight(1f)) {
                                    TextButton(
                                        onClick = onClearDeckCards,
                                        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.sm),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = null,
                                            tint = mc.lifeNegative,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(MaterialTheme.spacing.xs))
                                        Text(
                                            text = stringResource(R.string.addcard_clear_deck_cards),
                                            style = ty.labelLarge,
                                            color = mc.lifeNegative,
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.advsearch_show_results, uiState.totalCards),
                                    style = MaterialTheme.magicTypography.labelLarge,
                                    color = mc.textSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            IconButton(onClick = onViewModeToggle, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = if (uiState.viewMode == CollectionViewMode.GRID) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                                    contentDescription = stringResource(R.string.collection_view_grid),
                                    tint = mc.textSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        if (isDeckMode) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = MaterialTheme.spacing.sm),
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                            ) {
                                MagicCtaButton(
                                    onClick = onSelectAllDeckCards,
                                    text = stringResource(R.string.addcard_select_all),
                                    style = MagicCtaStyle.Outlined,
                                    modifier = Modifier.weight(1f),
                                )
                                MagicCtaButton(
                                    onClick = onSelectMissingDeckCards,
                                    text = stringResource(R.string.addcard_select_missing),
                                    style = MagicCtaStyle.Outlined,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                    if (uiState.viewMode == CollectionViewMode.LIST) {

                        ResultsList(
                            results = uiState.results,
                            uiState = uiState,
                            hasMore = uiState.hasMore,
                            contentPaddingBottom = navBarBottom,
                            isMultiSelectMode = uiState.isMultiSelectMode,
                            selectedScryfallIds = uiState.selectedScryfallIds,
                            onCardSelected = onCardSelected,
                            onCardLongClick = onCardLongClick,
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
                            isMultiSelectMode = uiState.isMultiSelectMode,
                            selectedScryfallIds = uiState.selectedScryfallIds,
                            onCardSelected = onCardSelected,
                            onCardLongClick = onCardLongClick,
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
                    MagicLoadingSpinner(
                        modifier = Modifier.size(24.dp),
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
            placeholder = painterResource(Res.drawable.mtg_card_back),
            error = painterResource(Res.drawable.mtg_card_back),
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
    isMultiSelectMode: Boolean,
    selectedScryfallIds: Set<String>,
    onCardSelected: (Card) -> Unit,
    onCardLongClick: ((Card) -> Unit)?,
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
        modifier = Modifier.fillMaxSize()
    ) {
        items(results, key = { it.scryfallId }, contentType = { "card" }) { card ->
            SearchResultItem(
                card = card,
                uiState = uiState,
                isMultiSelectMode = isMultiSelectMode,
                isSelected = card.scryfallId in selectedScryfallIds,
                onClick = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onCardSelected(card)
                },
                onLongClick = onCardLongClick?.let { longClick ->
                    {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        longClick(card)
                    }
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ResultsGrid(
    results: List<Card>,
    uiState: AddCardUiState,
    hasMore: Boolean,
    contentPaddingBottom: Dp,
    isMultiSelectMode: Boolean,
    selectedScryfallIds: Set<String>,
    onCardSelected: (Card) -> Unit,
    onCardLongClick: ((Card) -> Unit)?,
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

    LazyVerticalGrid(
        columns               = GridCells.Adaptive(minSize = 100.dp),
        state                 = rememberLazyGridState(),
        contentPadding        = PaddingValues(top = 4.dp, bottom = 4.dp + contentPaddingBottom ),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(results, key = { it.scryfallId }, contentType = { "card" }) { card ->
            SearchResultGridItem(
                card = card,
                uiState = uiState,
                isMultiSelectMode = isMultiSelectMode,
                isSelected = card.scryfallId in selectedScryfallIds,
                onClick = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onCardSelected(card)
                },
                onLongClick = onCardLongClick?.let { longClick ->
                    {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        longClick(card)
                    }
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
    onLongClick: (() -> Unit)?,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    uiState: AddCardUiState,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?){
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(
        shape = CardShape,
        color = MaterialTheme.magicColors.surface,
        border = if (isSelected) BorderStroke(SelectedBorderWidth, mc.primaryAccent) else null,
        modifier = Modifier.resultClickable(isMultiSelectMode, isSelected, onClick, onLongClick),
    ) {
      Box {
        Column(modifier = Modifier.clip(CardShape)
            .background(MaterialTheme.magicColors.surfaceVariant),
            horizontalAlignment = Alignment.Start) {
            AsyncImage(
                model = card.imageNormal,
                contentDescription = card.name,
                placeholder = painterResource(Res.drawable.mtg_card_back),
                error = painterResource(Res.drawable.mtg_card_back),
                contentScale = ContentScale.Crop,
                modifier = Modifier
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
                        } else Modifier
                    )
                    .background(MaterialTheme.magicColors.surfaceVariant),
            )
            CardName(
                name = card.name,
                showFrontOnly = true,
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.xs)
                    .padding(top = MaterialTheme.spacing.xs)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.xs).padding(top = MaterialTheme.spacing.xs, bottom = MaterialTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically
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
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.weight(1f))

                val formattedPrice = PriceFormatter.formatFromScryfall(
                    priceUsd = card.priceUsd,
                    priceEur = card.priceEur,
                    preferredCurrency = uiState.preferredCurrency
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
        if (isSelected) {
            SelectedCheckBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(MaterialTheme.spacing.xs),
            )
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
    onLongClick: (() -> Unit)?,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        shape = CardShape,
        color = mc.surface,
        border = if (isSelected) {
            BorderStroke(SelectedBorderWidth, mc.primaryAccent)
        } else {
            BorderStroke(0.5.dp, mc.surfaceVariant)
        },
        modifier = Modifier
            .fillMaxWidth()
            .resultClickable(isMultiSelectMode, isSelected, onClick, onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Art thumbnail
          Box {
            AsyncImage(
                model = card.imageNormal,
                contentDescription = card.name,
                placeholder = painterResource(Res.drawable.mtg_card_back),
                error = painterResource(Res.drawable.mtg_card_back),
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
            if (isSelected) {
                SelectedCheckBadge(modifier = Modifier.align(Alignment.Center))
            }
          }

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
                card.manaCost?.let { cost ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val costs = cost.split(" // ")
                        costs.forEachIndexed { index, singleCost ->
                            ManaCostImages(manaCost = singleCost, symbolSize = 20.dp)
                            if (index < costs.size - 1) {
                                Text(
                                    " // ",
                                    style = MaterialTheme.magicTypography.titleMedium,
                                    color = MaterialTheme.magicColors.textSecondary,
                                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xxs)
                                )
                            }
                        }
                    }
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
//  Multi-select helpers
// ─────────────────────────────────────────────────────────────────────────────

private val SelectedBorderWidth = 2.dp
private val SelectedBadgeSize = 24.dp
private val SelectedBadgeIconSize = 16.dp

/** Tap / long-press handling shared by the grid tile and the list row, with selection semantics. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.resultClickable(
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
): Modifier {
    val toggleLabel = stringResource(R.string.addcard_multi_select_toggle_action)
    val detailsLabel = stringResource(R.string.addcard_multi_select_open_details)
    return this
        .clip(CardShape)
        .then(if (isMultiSelectMode) Modifier.semantics { selected = isSelected } else Modifier)
        .combinedClickable(
            onClickLabel = if (isMultiSelectMode) toggleLabel else null,
            onLongClickLabel = if (onLongClick != null) detailsLabel else null,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/** Check badge marking a card that is in the shared queue; surface ring keeps it legible over art. */
@Composable
private fun SelectedCheckBadge(modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    Box(
        modifier = modifier
            .size(SelectedBadgeSize)
            .background(mc.surface, CircleShape)
            .padding(MaterialTheme.spacing.xxs)
            .background(mc.primaryAccent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = mc.onAccent,
            modifier = Modifier.size(SelectedBadgeIconSize),
        )
    }
}

@Composable
private fun queueToastText(toast: AddCardQueueToast): String = when (toast) {
    is AddCardQueueToast.AddedToCollection ->
        stringResource(R.string.scanner_toast_added_to_collection, toast.cardName)
    is AddCardQueueToast.AddedToWishlist ->
        stringResource(R.string.scanner_toast_added_to_wishlist, toast.cardName)
    is AddCardQueueToast.AddFailed ->
        stringResource(R.string.scanner_toast_add_failed, toast.cardName)
    is AddCardQueueToast.AddedAllToCollection ->
        stringResource(R.string.scanner_toast_added_all_to_collection, toast.count)
    is AddCardQueueToast.AddedAllToWishlist ->
        stringResource(R.string.scanner_toast_added_all_to_wishlist, toast.count)
    is AddCardQueueToast.AddAllPartialFailure ->
        stringResource(R.string.scanner_toast_add_all_partial_failure, toast.failed, toast.total)
    is AddCardQueueToast.DeckCardsSelected ->
        if (toast.count == 0) stringResource(R.string.addcard_deck_nothing_new_selected)
        else pluralStringResource(R.plurals.addcard_deck_cards_selected, toast.count, toast.count)
}

private fun AddCardQueueToast.toastType(): MagicToastType = when (this) {
    is AddCardQueueToast.AddedToCollection,
    is AddCardQueueToast.AddedToWishlist,
    is AddCardQueueToast.AddedAllToCollection,
    is AddCardQueueToast.AddedAllToWishlist -> MagicToastType.SUCCESS
    is AddCardQueueToast.AddFailed -> MagicToastType.ERROR
    is AddCardQueueToast.AddAllPartialFailure -> MagicToastType.WARNING
    is AddCardQueueToast.DeckCardsSelected ->
        if (count == 0) MagicToastType.INFO else MagicToastType.SUCCESS
}
