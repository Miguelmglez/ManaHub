package com.mmg.manahub.feature.multiadd.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.CardQueueSheet
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.components.LanguageSelectorSheet
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicProgressBar
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.components.rememberRateLimitCountdownSeconds
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.SmallCardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.CardConstants
import org.jetbrains.compose.resources.painterResource
import org.koin.androidx.compose.koinViewModel

@Composable
fun MultiAddCardScreen(
    onBack: () -> Unit,
    viewModel: MultiAddCardViewModel = koinViewModel(),
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onNavigateToCardDetail: (Card) -> Unit,
    preselectedCards: List<Card> = emptyList(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val queueListState = rememberLazyListState()
    val preferredCurrency = LocalPreferredCurrency.current

    LaunchedEffect(Unit) {
        if (preselectedCards.isNotEmpty()) {
            viewModel.preLoadItems(cards = preselectedCards)
        }
    }
    Scaffold(topBar = {
        Surface(color = colors.backgroundSecondary, shadowElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = "Back button",
                        tint = colors.textPrimary,
                    )
                }
                Text(
                    text = "Add Multiple cards",
                    color = colors.textPrimary,
                    style = typography.titleLarge,
                    modifier = Modifier.padding(),
                )
            }
        }
    }) {paddingValues->
        HexGridBackground(modifier = Modifier.fillMaxSize(), color = colors.primaryAccent.copy(alpha = 0.05f))

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {
            SearchSurface(
                onQueryChange = viewModel::onQueryChange,
                onLoadNextPage = viewModel::loadNextPage,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onNavigateToCardDetail = {cardId-> onNavigateToCardDetail(cardId)},
                onShowAdvancedSearchSheet = {viewModel.showCardStatusPicker(true)},
                onShowLanguageSheet = {viewModel.showLanguagePicker(true)},
                onOpenSelectedQueueSheet = {viewModel.showSelectedQueueSheet(true)},
                onForceSearch = viewModel::forceSearch,
                onUpdateCardToSelection = {selected-> viewModel.handleCardSelection(selected)},
                onClearFilters = viewModel::onClearFilters,
                onClearAll = viewModel::onClearAll,
                onViewModeToggle = viewModel::toggleLayout,
                uiState = uiState
            )
        }

        if (uiState.showLanguagePicker) {
            LanguageSelectorSheet(
                selectedLanguage = uiState.defaultLanguage,
                onDismiss = {viewModel.showLanguagePicker(false)},
                onSelectLanguage = {
                    viewModel.onLanguageChange(it)
                    viewModel.showLanguagePicker(false)
                },
            )
        }
        if(uiState.showSelectedQueueSheet){
            CardQueueSheet(
                session = uiState.scanSession,
                preferredCurrency = preferredCurrency,
                ownedCardIdentityKeys = uiState.ownedCardIdentityKeys,
                isAutoDeleteOnAddEnabled = uiState.isAutoDeleteOnAddEnabled,
                isCommitting = uiState.isCommittingQueue,
                toastMessage = uiState.toastMessage,
                toastType = uiState.toastType,
                onToastDismissed = viewModel::onToastDismissed,
                listState = queueListState,
                onDismiss = viewModel::onCloseQueue,
                onRemoveEntry = viewModel::onRemoveSessionCard,
                onEditEntry = viewModel::onEditScannedCard,
                onClearSession = viewModel::onClearSession,
                onAddAllToCollection = viewModel::onAddAllToCollection,
                onAddAllToWishlist = viewModel::onAddAllToWishlist,
                onAddEntryToCollection = viewModel::onAddEntryToCollection,
                onAddEntryToWishlist = viewModel::onAddEntryToWishlist,
                onNavigateToCardDetail = { id -> viewModel.onOpenCardDetail(id, true) },
                onDuplicateEntry = viewModel::onDuplicateSessionCard,
                onToggleAutoDeleteOnAdd = viewModel::onToggleAutoDeleteOnAdd,
                onIncrementQuantity = viewModel::onIncrementSessionCardQuantity,
                onDecrementQuantity = viewModel::onDecrementSessionCardQuantity,
            )
        }
    }
}
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SearchSurface(
    onQueryChange: (String) -> Unit,
    onClearFilters: () -> Unit,
    onClearAll: () -> Unit,
    onShowLanguageSheet: () -> Unit,
    onViewModeToggle: () -> Unit,
    onShowAdvancedSearchSheet: () -> Unit,
    uiState: MultiAddCardUiState,
    onLoadNextPage: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onNavigateToCardDetail: (Card) -> Unit,
    onOpenSelectedQueueSheet: () -> Unit,
    onForceSearch: () -> Unit,
    onUpdateCardToSelection: (Card) -> Unit
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
                        onShowAdvancedSearchSheet()
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
        if (uiState.isLoading && uiState.loadedCards.isNotEmpty()) {
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
                uiState.isLoading && uiState.loadedCards.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MagicLoadingSpinner(
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                uiState.error != null && uiState.loadedCards.isEmpty() -> {
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
                }

                uiState.loadedCards.isEmpty() -> {
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
                            if (uiState.totalCards != null) {
                                Text(
                                    text = stringResource(
                                        R.string.advsearch_show_results,
                                        uiState.totalCards
                                    ),
                                    style = MaterialTheme.magicTypography.labelLarge,
                                    color = mc.textSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            IconButton(onClick = onViewModeToggle, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector =
                                        if (uiState.showAsGrid) {
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

                        if (!uiState.showAsGrid) {
                            ListLayout (
                                cards = uiState.loadedCards,
                                hasMore = uiState.hasMore,
                                contentPaddingBottom = navBarBottom,
                                onCardSelected = onNavigateToCardDetail,
                                onLoadNextPage = onLoadNextPage,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        } else {
                            GridLayout(
                                cards = uiState.loadedCards,
                                hasMore = uiState.hasMore,
                                contentPaddingBottom = navBarBottom,
                                onCardSelected = onNavigateToCardDetail,
                                onLoadNextPage = onLoadNextPage,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onUpdateCardToSelection = onUpdateCardToSelection,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridLayout(
    cards: List<Card>,
    contentPaddingBottom: Dp,
    hasMore: Boolean = false,
    onLoadNextPage: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onCardSelected: (Card) -> Unit,
    onUpdateCardToSelection: (Card) -> Unit
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
        modifier = Modifier,
        state = rememberLazyGridState(),
        columns = GridCells.Adaptive(minSize = 148.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp + contentPaddingBottom),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = cards, key = {it.scryfallId}, contentType = {"card"}) {card->
            AddMassOnGridItem(
                card = card,
                isOwned = true,
                isWishlisted = true,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                openCardDetail = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onCardSelected(card)
                },
                isSelected = true,
                onUpdateCardToSelection = onUpdateCardToSelection
            )
        }
        if (hasMore) {
            item(contentType = "pagination_footer") {
                LaunchedEffect(true) {
                    onLoadNextPage()
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MagicLoadingSpinner(
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListLayout(
    cards: List<Card>,
    contentPaddingBottom: Dp,
    hasMore: Boolean = false,
    onLoadNextPage: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onCardSelected: (Card) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        items(cards.size, key = ({"${cards.size}"})) {
            // AddMassOnListItem(card)
        }
    }
}

@Composable
private fun AddMassOnGridItem(
    card: Card,
    isOwned: Boolean,
    isWishlisted: Boolean,
    openCardDetail: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onUpdateCardToSelection: (Card)->Unit,
    isSelected: Boolean = false,
) {
    val colors = MaterialTheme.magicColors
    Surface(
        shadowElevation = 2.dp,
        shape = SmallCardShape,
        border = if (isSelected) BorderStroke(1.dp, color = colors.primaryAccent) else null,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .clip(SmallCardShape)
                .background(colors.background),
        ) {
            Surface(
                modifier = Modifier.combinedClickable(
                    onClick = {onUpdateCardToSelection(card)},
                    onLongClick = {openCardDetail(card.scryfallId)})
            ) {
                AsyncImage(
                    contentDescription = card.name,
                    placeholder = painterResource(Res.drawable.mtg_card_back),
                    error = painterResource(Res.drawable.mtg_card_back),
                    fallback = painterResource(Res.drawable.mtg_card_back),
                    model = card.imageNormal,
                    modifier = Modifier
                        .aspectRatio(0.716f)
                        .clip(SmallCardShape)
                        .then(
                            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "card-image-${card.scryfallId}"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        clipInOverlayDuringTransition = OverlayClip(
                                            RoundedCornerShape(6.dp),
                                        ),
                                        renderInOverlayDuringTransition = true,
                                    )
                                }
                            } else {
                                Modifier
                            },
                        )
                        .background(colors.background),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
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
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.weight(1f))
                if (isOwned) {
                    Icon(
                        Icons.Default.CollectionsBookmark,
                        tint = colors.primaryAccent,
                        modifier = Modifier.size(12.dp),
                        contentDescription = "owned badge",
                    )
                }
                if (isWishlisted) {
                    Icon(
                        painter = painterResource(R.drawable.ic_heart),
                        tint = colors.secondaryAccent,
                        modifier = Modifier.size(12.dp),
                        contentDescription = "Wishlist Badge",
                    )
                }
            }
        }
    }
}

@Composable
private fun AddMassOnListItem(
    card: Card,
    isOwned: Boolean,
) {
    val colors = MaterialTheme.magicColors
}

@Composable
private fun SelectorCard(
    label: String,
    value: String,
    icon: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        modifier = modifier.height(56.dp),
        color = mc.surface,
        shape = CardShape,
        border = BorderStroke(1.dp, mc.surfaceVariant),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, style = ty.labelSmall, color = mc.textSecondary)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                if (icon != null) icon()
                Text(
                    value,
                    style = ty.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = mc.textPrimary,
                )
            }
        }
    }
}
