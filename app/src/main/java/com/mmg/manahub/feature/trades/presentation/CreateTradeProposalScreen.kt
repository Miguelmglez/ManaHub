package com.mmg.manahub.feature.trades.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.model.AddCardRow
import com.mmg.manahub.core.ui.components.AddCardSheet
import com.mmg.manahub.core.ui.components.AvatarImage
import com.mmg.manahub.core.ui.components.CardListItem
import com.mmg.manahub.core.ui.components.CardSearchSheet
import com.mmg.manahub.core.ui.components.rememberRateLimitCountdownSeconds
import com.mmg.manahub.core.ui.components.MagicLoadingFooter
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.data.network.RateLimitExhaustedException
import androidx.compose.foundation.lazy.LazyListScope
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicSelectionItem
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.feature.auth.presentation.AuthViewModel
import com.mmg.manahub.feature.auth.presentation.LoginSheet
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.TradeSide

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTradeProposalScreen(
    onBack: () -> Unit,
    onNavigateToThread: (proposalId: String, rootProposalId: String) -> Unit,
    onNavigateToCardDetail: (scryfallId: String) -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onNavigateToAddFriends: () -> Unit = {},
    viewModel: TradeProposalViewModel = koinViewModel(),
    authViewModel: AuthViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastState = rememberMagicToastState()
    val mc = MaterialTheme.magicColors
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // §5.2 fix: one-shot navigation/toast effects are delivered through a buffered Channel
    // (viewModel.events), never nullable StateFlow fields — a StateFlow equality-collapses two
    // consecutive identical events (e.g. two "select a friend first" errors from a fast
    // double-tap) and can drop emissions made while the screen's lifecycle is paused. All string
    // resolution happens HERE, in ONE place (§5.1 fix) — the ViewModel emits semantic outcomes
    // only, never a raw literal sentinel key like the old `errorMessage = "NO_RECEIVER"`.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProposalEvent.ShowValidationError ->
                    toastState.show(context.getString(event.messageRes), MagicToastType.ERROR)
                is ProposalEvent.ShowRemoteError -> {
                    val msg = event.message ?: context.getString(R.string.trades_error_generic_body)
                    toastState.show(msg, MagicToastType.ERROR)
                }
                is ProposalEvent.NavigateToThread -> onNavigateToThread(event.proposalId, event.rootProposalId)
                ProposalEvent.NavigateBack -> onBack()
            }
        }
    }

    var showAddItemSheet by remember { mutableStateOf<TradeSide?>(null) }
    var editingItem by remember { mutableStateOf<TradeItemDraft?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showLoginSheet by remember { mutableStateOf(false) }
    val noFriendMsg = stringResource(R.string.trades_friend_required)
    val openSearch: (TradeSide) -> Unit = { side ->
        if (uiState.selectedFriend == null) {
            toastState.show(noFriendMsg, MagicToastType.ERROR)
        } else {
            viewModel.onOpenSearch(side)
            showAddItemSheet = side
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(mc.background)) {
        HexGridBackground(modifier = Modifier.fillMaxSize(), color = mc.primaryAccent.copy(alpha = 0.05f))

        Scaffold(
            containerColor      = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                Surface(
                    color = mc.backgroundSecondary.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 4.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = MaterialTheme.spacing.xs, vertical = MaterialTheme.spacing.xs)
                                .heightIn(min = 56.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                    tint = mc.textPrimary
                                )
                            }
                            Text(
                                text = stringResource(
                                    when {
                                        uiState.isCounterMode -> R.string.trades_counter_proposal_title
                                        uiState.editingProposalId != null -> R.string.trades_edit_proposal_title
                                        else -> R.string.trades_create_proposal_title
                                    }
                                ),
                                style = MaterialTheme.magicTypography.titleMedium,
                                color = mc.textPrimary,
                                modifier = Modifier.weight(1f).padding(start = MaterialTheme.spacing.sm)
                            )
                            Spacer(Modifier.width(MaterialTheme.spacing.sm))
                        }
                    }
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                ) {
                    MagicCtaButton(
                        onClick   = viewModel::onSendProposal,
                        text      = stringResource(
                            if (uiState.editingProposalId != null) R.string.trades_update_proposal
                            else R.string.trades_send_proposal
                        ),
                        isLoading = uiState.isSaving,
                        color     = MagicCtaColor.Primary,
                        modifier  = Modifier.fillMaxWidth(),
                    )
                }
            },
        ) { innerPadding ->
            // §2.9 fix: the counter/edit prefill can miss the in-memory proposals cache (e.g.
            // after process death) and must warm it via a network refresh before showing the
            // form — while that's in flight, or if it ultimately fails, the editor form itself
            // is hidden entirely so a save can never wipe the real proposal's items from a
            // blank draft.
            when {
                uiState.isPrefillLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        MagicLoadingSpinner()
                    }
                }

                uiState.prefillFailed -> {
                    FullErrorState(
                        message = stringResource(R.string.trades_error_prefill_failed),
                        retryLabel = stringResource(R.string.action_retry),
                        onRetry = viewModel::retryPrefill,
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                    )
                }

                else -> {
                    val isLocked = uiState.editingProposalId != null || uiState.isCounterMode

                    LazyColumn(
                        modifier       = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(MaterialTheme.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xl),
                    ) {
                // ── Proposer identity card ────────────────────────────────────────
                item(key = "proposer_identity") {
                    ProposerIdentityCard(
                        nickname = uiState.currentUserNickname,
                        avatarUrl = uiState.currentUserAvatarUrl
                    )
                }

                // ── "You send" section header ─────────────────────────────────────
                item(key = "you_send_header") {
                    Text(
                        text = stringResource(R.string.trades_you_offer_section),
                        style = MaterialTheme.magicTypography.labelLarge,
                        color = mc.textSecondary,
                    )
                }

                // ── Proposer-match suggestions (inline, above "They get" items) ──
                if (uiState.selectedFriend != null && uiState.proposerMatches.isNotEmpty()) {
                    item(key = "proposer_suggestions") {
                        InlineSuggestionsRow(
                            label   = stringResource(R.string.trades_suggested_for_them),
                            matches = uiState.proposerMatches,
                            onCardClick = onNavigateToCardDetail,
                            onAdd   = { row ->
                                viewModel.addSuggestionToProposer(
                                    row.toTradeItemDraft(isInCollection = row.isOwned || row.offerEntry != null)
                                )
                            },
                        )
                    }
                }

                // ── Review collection toggle (proposer side only, shown before items) ──
                item(key = "you_send_review") {
                    ReviewCollectionToggle(
                        label   = stringResource(R.string.trades_review_collection_proposer),
                        checked = uiState.includesReviewFromProposer,
                        onToggle = viewModel::toggleReviewCollectionProposer,
                    )
                }

                if (uiState.proposerItems.isEmpty() && !uiState.includesReviewFromProposer) {
                    item(key = "proposer_empty") {
                        SideEmptyState(
                            title = stringResource(R.string.trades_proposer_empty_cta),
                            onAdd = { openSearch(TradeSide.PROPOSER) },
                        )
                    }
                } else {
                    items(uiState.proposerItems, key = { "pi_${it.id}" }) { item ->
                        TradeItemDraftRow(
                            item     = item,
                            onClick  = {
                                editingItem = item
                                showEditSheet = true
                            },
                            onRemove = { viewModel.removeProposerItem(item.id) },
                        )
                    }

                    item(key = "they_get_add") {
                        AddItemButton(
                            enabled = uiState.selectedFriend != null,
                            onClick = { openSearch(TradeSide.PROPOSER) },
                        )
                    }
                }

                item(key = "divider") {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = MaterialTheme.spacing.sm),
                        color = mc.textDisabled.copy(alpha = 0.2f)
                    )
                }

                // ── Receiver / friend selector ────────────────────────────────────
                item(key = "friend_selector") {
                    FriendSelector(
                        friends = uiState.friends,
                        selectedFriend = uiState.selectedFriend,
                        onFriendSelected = viewModel::onFriendSelected,
                        sessionState = uiState.sessionState,
                        onNavigateToLogin = { showLoginSheet = true },
                        onNavigateToAddFriends = onNavigateToAddFriends,
                        isLocked = isLocked,
                    )
                }

                // ── "X sends" section header ──────────────────────────────────────

                item(key = "friend_send_header") {
                    val friendName = uiState.selectedFriend?.nickname
                    Text(
                        text = if (friendName != null)
                            stringResource(R.string.trades_named_sends, friendName)
                        else
                            stringResource(R.string.trades_they_offer_section),
                        style = MaterialTheme.magicTypography.labelLarge,
                        color = mc.textSecondary,
                    )
                }

                // ── Receiver-match suggestions (inline, above "You get" items) ────
                if (uiState.selectedFriend != null && uiState.receiverMatches.isNotEmpty()) {
                    item(key = "receiver_suggestions") {
                        InlineSuggestionsRow(
                            label   = stringResource(R.string.trades_suggested_for_you),
                            matches = uiState.receiverMatches,
                            onCardClick = onNavigateToCardDetail,
                            onAdd   = { row ->
                                viewModel.addSuggestionToReceiver(
                                    row.toTradeItemDraft(isInCollection = row.offerEntry != null)
                                )
                            },
                        )
                    }
                }

                if (uiState.receiverItems.isEmpty()) {
                    item(key = "receiver_empty") {
                        SideEmptyState(
                            title = stringResource(R.string.trades_receiver_empty_cta),
                            onAdd = { openSearch(TradeSide.RECEIVER) },
                        )
                    }
                } else {
                    items(uiState.receiverItems, key = { "ri_${it.id}" }) { item ->
                        TradeItemDraftRow(
                            item     = item,
                            onClick  = {
                                editingItem = item
                                showEditSheet = true
                            },
                            onRemove = { viewModel.removeReceiverItem(item.id) },
                        )
                    }

                    item(key = "you_get_add") {
                        AddItemButton(
                            enabled = uiState.selectedFriend != null,
                            onClick = { openSearch(TradeSide.RECEIVER) },
                        )
                    }
                }

                item(key = "bottom_spacer") { Spacer(Modifier.height(MaterialTheme.spacing.lg)) }
                    }
                }
            }
        }

        MagicToastHost(state = toastState)
    }

    showAddItemSheet?.let { side ->
        // Capture stringResources before the lambda to satisfy Compose composable-call rules
        val friendName = uiState.selectedFriend?.nickname ?: ""
        val wishlistLabel = if (side == TradeSide.PROPOSER) {
            // "They get" side: browsing friend's wishlist
            if (friendName.isNotBlank()) stringResource(R.string.trades_tab_friend_wishlist, friendName)
            else stringResource(R.string.trades_tab_your_wishlist)
        } else {
            // "You get" side: browsing your own wishlist
            stringResource(R.string.trades_tab_your_wishlist)
        }
        val offerLabel = if (side == TradeSide.PROPOSER) {
            // "They get" side: your offers / what you can give
            stringResource(R.string.trades_tab_your_offers)
        } else {
            // "You get" side: friend's offers + collection
            if (friendName.isNotBlank()) stringResource(R.string.trades_tab_friend_offers, friendName)
            else stringResource(R.string.trades_tab_your_offers)
        }
        val allCardsLabel = stringResource(R.string.trades_tab_all_cards)

        CardSearchSheet(
            query = uiState.addCardsQuery,
            offerResults = uiState.offerResults,
            addCardsResults = uiState.addCardsResults,
            wishlistResults = uiState.wishlistResults,
            scryfallResults = uiState.scryfallResults,
            isSearchingCards = uiState.isSearchingCards,
            isSearchingWishlist = uiState.isSearchingWishlist,
            isSearchingScryfall = uiState.isSearchingScryfall,
            onQueryChange = viewModel::onAddCardsQueryChange,
            onScryfallSearch = viewModel::searchScryfallDirect,
            friendName = null, // title is now controlled via explicit title param
            wishlistTabLabel = wishlistLabel,
            offerTabLabel = offerLabel,
            allCardsTabLabel = allCardsLabel,
            showWishlistTab = true,
            title = stringResource(
                if (side == TradeSide.PROPOSER) R.string.trades_search_title_proposer
                else R.string.trades_search_title_receiver_generic
            ),
            onAdd = { row ->
                focusManager.clearFocus()
                val draft = row.toTradeItemDraft(
                    isInCollection = row.isOwned || row.offerEntry != null || row.wishlistEntry != null,
                    fromFriendList = side == TradeSide.PROPOSER && row.offerEntry == null && row.wishlistEntry != null,
                )
                when (side) {
                    TradeSide.PROPOSER -> viewModel.addProposerItem(draft)
                    TradeSide.RECEIVER -> viewModel.addReceiverItem(draft)
                }
            },
            onRemove = { row ->
                focusManager.clearFocus()
                // Same variant resolution as onAdd, so the removed item is the one that was added.
                val predicate: (TradeItemDraft) -> Boolean = { item -> row.matchesDraft(item) }

                when (side) {
                    TradeSide.PROPOSER ->
                        uiState.pendingAddedItems.findLast(predicate)
                            ?.let { viewModel.removeProposerItem(it.id) }
                    TradeSide.RECEIVER ->
                        uiState.pendingAddedItems.findLast(predicate)
                            ?.let { viewModel.removeReceiverItem(it.id) }
                }
            },
            onConfirm = viewModel::onConfirmPendingItems,
            onCancel = viewModel::onCancelPendingItems,
            onCardClick = { scryfallId ->
                focusManager.clearFocus()
                viewModel.setNavigatingToDetail(true)
                onNavigateToCardDetail(scryfallId)
            },
            offerResultsFooter = if (side == TradeSide.RECEIVER) {
                { friendCollectionFooter(uiState, viewModel::onLoadMoreFriendCollection, viewModel::onRetryFriendCollection) }
            } else {
                null
            },
            scryfallErrorContent = uiState.scryfallError?.let { error ->
                @Composable { ScryfallSearchError(error = error, onRetry = viewModel::retryScryfallSearch) }
            },
            onDismiss = {
                focusManager.clearFocus()
                if (!uiState.isNavigatingToDetail) {
                    viewModel.confirmPendingItemsForSide(side)
                    showAddItemSheet = null
                    viewModel.clearAddCardsState()
                }
                viewModel.setNavigatingToDetail(false)
            }
        )
    }

    if (showEditSheet) {
        editingItem?.let { item ->
            AddCardSheet(
                cardName = item.cardName,
                initialFoil = item.isFoil,
                initialCondition = item.condition,
                initialLanguage = item.language,
                initialQty = item.quantity,
                maxQty = item.maxQuantity ?: 99,
                variantLocked = item.isVariantLocked,
                onConfirm = { isFoil, condition, language, qty ->
                    val updated = item.copy(
                        quantity = qty,
                        isFoil = isFoil,
                        condition = condition,
                        language = language,
                    )
                    if (uiState.proposerItems.any { it.id == item.id }) {
                        viewModel.updateProposerItem(updated)
                    } else {
                        viewModel.updateReceiverItem(updated)
                    }
                    showEditSheet = false
                    editingItem = null
                },
                onDismiss = {
                    showEditSheet = false
                    editingItem = null
                },
                cardImage = null,
                confirmButtonText = stringResource(R.string.scanner_edit_save)
            )
        }
    }

    uiState.pendingFriendSwitch?.let { pending ->
        MagicAlertDialog(
            onDismissRequest = viewModel::onDismissFriendSwitch,
            title = stringResource(R.string.trades_friend_switch_title),
            text = stringResource(R.string.trades_friend_switch_body),
            confirmLabel = stringResource(
                if (pending.friend == null) R.string.trades_friend_switch_confirm_none
                else R.string.trades_friend_switch_confirm
            ),
            onConfirm = viewModel::onConfirmFriendSwitch,
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = viewModel::onDismissFriendSwitch,
            confirmColor = MagicCtaColor.Error,
        )
    }

    if (showLoginSheet) {
        LoginSheet(
            authViewModel = authViewModel,
            onDismiss = { showLoginSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendSelector(
    friends: List<Friend>,
    selectedFriend: Friend?,
    onFriendSelected: (Friend?) -> Unit,
    sessionState: SessionState,
    onNavigateToLogin: () -> Unit,
    onNavigateToAddFriends: () -> Unit,
    isLocked: Boolean = false,
) {
    var showSheet by remember { mutableStateOf(false) }
    val mc = MaterialTheme.magicColors

    Surface(
        onClick = { if (!isLocked) showSheet = true },
        enabled = !isLocked,
        shape = CardShape,
        color = mc.surface.copy(alpha = if (isLocked) 0.3f else 0.5f),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, mc.textDisabled.copy(alpha = if (isLocked) 0.06f else 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            if (selectedFriend != null) {
                FriendAvatar(selectedFriend)
            } else {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = mc.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedFriend?.nickname ?: stringResource(R.string.trades_friend_selector_placeholder),
                    style = MaterialTheme.magicTypography.labelMedium,
                    color = if (selectedFriend != null) mc.textPrimary else mc.textSecondary
                )
                if (selectedFriend != null) {
                    Text(
                        text = selectedFriend.gameTag,
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary
                    )
                } else if (!isLocked) {
                    Text(
                        text = stringResource(R.string.trades_friend_selector_hint),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary
                    )
                }
            }

            if (!isLocked) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    if (showSheet && !isLocked) {
        // §5.7 fix: no documented reason to block swipe/scrim dismissal — the sheet now
        // dismisses like every other bottom sheet in the app (default confirmValueChange).
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = mc.backgroundSecondary,
            contentColor = mc.textPrimary,
            dragHandle = null,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.xl, vertical = MaterialTheme.spacing.sm)
                    .navigationBarsPadding(),
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = MaterialTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showSheet = false },
                        modifier = Modifier.offset(x = -MaterialTheme.spacing.md)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = mc.textSecondary
                        )
                    }
                }
                // Backend & Performance Optimization plan, WS5b (2026-07-28): was a
                // Column + verticalScroll rendering every friend eagerly — an unbounded list per
                // CLAUDE.md's Compose rules. Converted to a LazyColumn keyed by `friend.userId`
                // (never by index — the CLAUDE.md rule against a key value that can repeat).
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                ) {
                    item(key = "friend_selector_title") {
                        Text(
                            text = stringResource(R.string.trades_friend_selector_sheet_title),
                            style = MaterialTheme.magicTypography.titleMedium,
                            color = mc.textPrimary
                        )
                    }

                    when {
                        sessionState is SessionState.Unauthenticated -> {
                            item(key = "friend_selector_login_empty_state") {
                                EmptyState(
                                    title = stringResource(R.string.trades_friend_sheet_login_title),
                                    actionLabel = stringResource(R.string.trades_friend_sheet_login_action),
                                    onAction = {
                                        showSheet = false
                                        onNavigateToLogin()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        friends.isEmpty() -> {
                            item(key = "friend_selector_no_friends_empty_state") {
                                EmptyState(
                                    title = stringResource(R.string.trades_friend_sheet_no_friends_title),
                                    actionLabel = stringResource(R.string.trades_friend_sheet_no_friends_action),
                                    onAction = {
                                        showSheet = false
                                        onNavigateToAddFriends()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        else -> {
                            // "None" option
                            item(key = "friend_selector_none_option") {
                                val isSelected = selectedFriend == null
                                MagicSelectionItem(
                                    title = stringResource(R.string.trades_friend_none_option),
                                    isSelected = isSelected,
                                    onClick = {
                                        onFriendSelected(null)
                                        showSheet = false
                                    },
                                    modifier = Modifier.semantics {
                                        role = Role.RadioButton
                                        selected = isSelected
                                    },
                                )
                            }

                            items(friends, key = { it.userId }) { friend ->
                                val isSelected = selectedFriend?.userId == friend.userId
                                MagicSelectionItem(
                                    title = friend.nickname,
                                    description = friend.gameTag,
                                    isSelected = isSelected,
                                    onClick = {
                                        onFriendSelected(friend)
                                        showSheet = false
                                    },
                                    icon = { FriendAvatar(friend) },
                                    modifier = Modifier.semantics {
                                        role = Role.RadioButton
                                        selected = isSelected
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProposerIdentityCard(nickname: String, avatarUrl: String? = null) {
    val mc = MaterialTheme.magicColors
    Surface(
        shape    = CardShape,
        color    = mc.surface.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
        border   = BorderStroke(1.dp, mc.textDisabled.copy(alpha = 0.06f)),
    ) {
        Row(
            modifier              = Modifier.padding(MaterialTheme.spacing.md),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            AvatarImage(
                avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
                initials  = nickname.initial(),
                size      = AVATAR_SIZE,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = nickname.ifBlank { stringResource(R.string.trades_you_offer_section) },
                    style = MaterialTheme.magicTypography.labelMedium,
                    color = mc.textPrimary,
                )
                Text(
                    text  = stringResource(R.string.trades_proposer_identity_subtitle),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                )
            }
        }
    }
}

private const val AVATAR_SIZE = 40

private fun String.initial(): String = firstOrNull()?.uppercaseChar()?.toString() ?: "?"

@Composable
private fun FriendAvatar(friend: Friend) {
    AvatarImage(
        avatarUrl = friend.avatarUrl?.takeIf { it.isNotBlank() },
        initials  = friend.nickname.initial(),
        size      = AVATAR_SIZE,
    )
}

@Composable
private fun TradeItemDraftRow(
    item:     TradeItemDraft,
    onClick:  () -> Unit,
    onRemove: () -> Unit,
) {
    val mc = MaterialTheme.magicColors

    Surface(
        shape    = CardShape,
        color    = mc.surface.copy(alpha = 0.7f),
        border   = BorderStroke(1.dp, mc.textDisabled.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val extraContent: @Composable RowScope.() -> Unit = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = mc.goldMtg,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text(
                    text = stringResource(R.string.trades_warning_not_in_collection),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                )
            }

            CardListItem(
                name          = item.cardName,
                imageUrl      = item.imageUrl,
                priceUsd      = null,
                priceEur      = null,
                onClick       = onClick,
                modifier      = Modifier.weight(1f),
                quantityText  = stringResource(R.string.trades_quantity_multiplier, item.quantity),
                hasFoil       = item.isFoil,
                condition     = item.condition.takeIf { it.isNotBlank() },
                language      = item.language.takeIf { it.isNotBlank() },
                typeLine      = item.typeLine,
                setCode       = item.setCode,
                setName       = item.setName,
                rarity        = item.rarity,
                containerColor = Color.Transparent,
                extraSupportingContent = if (!item.isInCollection) extraContent else null,
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector        = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_remove),
                    tint               = mc.textSecondary,
                    modifier           = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Empty trade side; the CTA stays tappable without a partner so the tap can explain why search is blocked. */
@Composable
private fun SideEmptyState(
    title: String,
    onAdd: () -> Unit,
) {
    EmptyState(
        title       = title,
        icon        = Icons.Default.Add,
        actionLabel = stringResource(R.string.trades_add_item),
        onAction    = onAdd,
        modifier    = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReviewCollectionToggle(
    label:    String,
    checked:  Boolean,
    onToggle: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val tint = if (checked) mc.primaryAccent else mc.textSecondary
    MagicSelectionItem(
        title       = label,
        description = stringResource(R.string.trades_review_collection_subtitle),
        isSelected  = checked,
        onClick     = onToggle,
        icon        = {
            Icon(
                imageVector = Icons.Default.LibraryBooks,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        },
        trailing    = {
            Icon(
                imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        },
        modifier    = Modifier.semantics {
            role = Role.Checkbox
            toggleableState = ToggleableState(checked)
        },
    )
}

/**
 * Horizontal scrollable row of suggestion cards shown inline above a trade side's item list.
 * Each card has a golden border and a "+" button that directly adds it to the side.
 */
@Composable
private fun InlineSuggestionsRow(
    label: String,
    matches: List<AddCardRow>,
    onAdd: (AddCardRow) -> Unit,
    onCardClick: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        Row(
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = mc.goldMtg,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textSecondary,
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.xs, vertical = MaterialTheme.spacing.xxs)
        ) {
            items(matches, key = { "sug_${it.uniqueKey}" }) { row ->
                SuggestionCardItem(
                    row = row,
                    onAdd = { onAdd(row) },
                    onClick = { onCardClick(row.card.scryfallId) },
                    modifier = Modifier.fillParentMaxWidth(0.95f)
                )
            }
        }
    }
}

// Fraction of the card's longer side the corner glow spreads over, so it scales with the card.
private const val SUGGESTION_GLOW_RADIUS_FRACTION = 0.4f

@Composable
private fun SuggestionCardItem(
    row: AddCardRow,
    onAdd: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val isExact = row.isExactMatch
    val isPartial = !isExact && row.wishlistEntry != null && row.offerEntry != null

    val borderBrush = when {
        isExact -> Brush.linearGradient(
            colors = listOf(mc.goldMtg, mc.goldMtg.copy(alpha = 0.3f), mc.goldMtg)
        )
        isPartial -> Brush.linearGradient(
            colors = listOf(mc.primaryAccent, mc.primaryAccent.copy(alpha = 0.3f), mc.primaryAccent)
        )
        else -> SolidColor(mc.primaryAccent.copy(alpha = 0.2f))
    }

    val glowColor = when {
        isExact -> mc.goldMtg.copy(alpha = 0.15f)
        isPartial -> mc.primaryAccent.copy(alpha = 0.1f)
        else -> Color.Transparent
    }

    val addTint = if (isExact) mc.goldMtg else mc.primaryAccent

    Surface(
        shape = CardShape,
        color = mc.surface.copy(alpha = 0.4f),
        border = BorderStroke(if (isExact || isPartial) 2.dp else 1.dp, borderBrush),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val radius = size.maxDimension * SUGGESTION_GLOW_RADIUS_FRACTION
                    if (glowColor.alpha > 0f && radius > 0f) {
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(glowColor, Color.Transparent),
                                center = Offset.Zero,
                                radius = radius,
                            )
                        )
                    }
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = MaterialTheme.spacing.xs)
            ) {
                CardListItem(
                    name = row.card.name,
                    imageUrl = row.card.imageNormal,
                    priceUsd = null,
                    priceEur = null,
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                    hasFoil = row.offerEntry?.isFoil ?: false,
                    condition = row.offerEntry?.condition?.takeIf { it.isNotBlank() },
                    language = row.offerEntry?.language?.takeIf { it.isNotBlank() },
                    setCode = row.card.setCode,
                    setName = row.card.setName,
                    rarity = row.card.rarity,
                    typeLine = row.card.typeLine,
                    containerColor = Color.Transparent,
                )
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.trades_add_item),
                        tint = addTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** Adds a card to a trade side; without a partner it renders neutral but still reports why on tap. */
@Composable
private fun AddItemButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    MagicCtaButton(
        onClick  = onClick,
        text     = stringResource(R.string.trades_add_item),
        style    = MagicCtaStyle.Outlined,
        color    = if (enabled) MagicCtaColor.Primary else MagicCtaColor.Neutral,
        icon     = { Icon(Icons.Default.Add, contentDescription = null) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Paging footer of the friend's collection in the "You get" sheet: loads the next page when it scrolls into view. */
private fun LazyListScope.friendCollectionFooter(
    uiState: ProposalEditorUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        uiState.friendCollectionLoadFailed -> item(key = "friend_collection_error") {
            InlineErrorState(
                message = stringResource(R.string.trades_friend_collection_load_error),
                retryLabel = stringResource(R.string.action_retry),
                onRetry = onRetry,
            )
        }
        uiState.friendCollectionHasMore -> item(key = "friend_collection_more") {
            // Keyed on the row count so a footer still on screen after a page lands asks for the next one.
            LaunchedEffect(uiState.offerResults.size) { onLoadMore() }
            MagicLoadingFooter(label = null)
        }
    }
}

/** Failed Scryfall search in the "All cards" tab; a rate-limited failure disables retry until the cooldown ends. */
@Composable
private fun ScryfallSearchError(error: String, onRetry: () -> Unit) {
    val retryAfterMs = RateLimitExhaustedException.retryAfterMsOrNull(error)
    val remainingSeconds = rememberRateLimitCountdownSeconds(retryAfterMs)
    InlineErrorState(
        message = when {
            retryAfterMs == null -> stringResource(R.string.trades_scryfall_search_error)
            remainingSeconds > 0 -> stringResource(R.string.error_rate_limited_retry_countdown, remainingSeconds)
            else -> stringResource(R.string.error_rate_limited_message)
        },
        retryLabel = stringResource(R.string.action_retry),
        onRetry = onRetry,
        enabled = remainingSeconds <= 0,
    )
}
