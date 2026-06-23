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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.AddCardRow
import com.mmg.manahub.core.ui.components.AddCardSheet
import com.mmg.manahub.core.ui.components.CardListItem
import com.mmg.manahub.core.ui.components.CardSearchSheet
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
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

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        toastState.show(msg, MagicToastType.SUCCESS)
        viewModel.onSnackbarDismissed()
    }

    LaunchedEffect(uiState.errorMessage) {
        val err = uiState.errorMessage ?: return@LaunchedEffect
        val message = when (err) {
            "NO_RECEIVER"               -> "Please select a friend to trade with"
            "INITIAL_ASYMMETRY"         -> "Both sides must have at least one item"
            "PROPOSAL_VERSION_MISMATCH" -> "Proposal was modified; please refresh"
            "SELF_TRADE"                -> "You cannot trade with yourself"
            else                        -> err.ifBlank { "An unexpected error occurred" }
        }
        toastState.show(message, MagicToastType.ERROR)
        viewModel.onErrorDismissed()
    }

    LaunchedEffect(uiState.navigateToThread) {
        val nav = uiState.navigateToThread ?: return@LaunchedEffect
        onNavigateToThread(nav.first, nav.second)
        viewModel.onNavigationConsumed()
    }

    LaunchedEffect(uiState.navigateBack) {
        if (uiState.navigateBack) {
            onBack()
            viewModel.onNavigationConsumed()
        }
    }

    var showAddItemSheet by remember { mutableStateOf<TradeSide?>(null) }
    var editingItem by remember { mutableStateOf<TradeItemDraft?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showLoginSheet by remember { mutableStateOf(false) }
    val noFriendMsg = stringResource(R.string.trades_friend_required)

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
                                .padding(horizontal = 4.dp, vertical = 4.dp)
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
                                modifier = Modifier.weight(1f).padding(start = 8.dp)
                            )

                            /*TradeBalanceIndicator(
                                proposerValue = if (preferredCurrency == PreferredCurrency.EUR) uiState.totalProposerValueEur else uiState.totalProposerValueUsd,
                                receiverValue = if (preferredCurrency == PreferredCurrency.EUR) uiState.totalReceiverValueEur else uiState.totalReceiverValueUsd,
                                currency = preferredCurrency
                            )*/
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick  = viewModel::onSendProposal,
                        enabled  = !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = mc.background, strokeWidth = 2.dp)
                        } else {
                            Text(
                                stringResource(
                                    if (uiState.editingProposalId != null) R.string.trades_update_proposal
                                    else R.string.trades_send_proposal
                                ),
                                color = mc.background,
                                style = MaterialTheme.magicTypography.titleMedium,
                            )
                        }
                    }
                    if (!uiState.isCounterMode && uiState.editingProposalId == null) {
                        OutlinedButton(
                            onClick  = viewModel::onSaveDraft,
                            enabled  = !uiState.isSaving,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(12.dp),
                            border   = BorderStroke(1.dp, mc.textDisabled.copy(alpha = 0.2f)),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = mc.textSecondary)
                        ) {
                            Text(
                                stringResource(R.string.trades_save_draft),
                                style = MaterialTheme.magicTypography.bodyLarge,
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            val isLocked = uiState.editingProposalId != null || uiState.isCounterMode

            LazyColumn(
                modifier       = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
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
                                val card = row.card
                                viewModel.addSuggestionToProposer(
                                    TradeItemDraft(
                                        cardId       = card.scryfallId,
                                        cardName     = card.name,
                                        imageUrl     = card.imageArtCrop ?: card.imageNormal,
                                        typeLine     = card.typeLine,
                                        setCode      = card.setCode,
                                        setName      = card.setName,
                                        rarity       = card.rarity,
                                        priceUsd     = card.priceUsd,
                                        priceUsdFoil = card.priceUsdFoil,
                                        priceEur     = card.priceEur,
                                        priceEurFoil = card.priceEurFoil,
                                        quantity     = 1,
                                        isFoil       = row.wishlistEntry?.isFoil ?: row.offerEntry?.isFoil ?: false,
                                        condition    = row.wishlistEntry?.condition ?: row.offerEntry?.condition ?: "NM",
                                        language     = row.wishlistEntry?.language ?: row.offerEntry?.language ?: "en",
                                        userCardIdRef = row.offerEntry?.userCardId,
                                        isInCollection = row.isOwned || row.offerEntry != null,
                                    )
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
                        EmptySidePlaceholder(
                            text = stringResource(R.string.trades_proposer_empty_cta),
                            enabled = uiState.selectedFriend != null,
                            onClick = {
                                if (uiState.selectedFriend == null) {
                                    toastState.show(noFriendMsg, MagicToastType.ERROR)
                                } else {
                                    viewModel.onOpenSearch(TradeSide.PROPOSER)
                                    showAddItemSheet = TradeSide.PROPOSER
                                }
                            }
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
                            onClick = {
                                if (uiState.selectedFriend == null) {
                                    toastState.show(noFriendMsg, MagicToastType.ERROR)
                                } else {
                                    viewModel.onOpenSearch(TradeSide.PROPOSER)
                                    showAddItemSheet = TradeSide.PROPOSER
                                }
                            }
                        )
                    }
                }

                item(key = "divider") {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
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
                                val card = row.card
                                viewModel.addSuggestionToReceiver(
                                    TradeItemDraft(
                                        cardId       = card.scryfallId,
                                        cardName     = card.name,
                                        imageUrl     = card.imageArtCrop ?: card.imageNormal,
                                        typeLine     = card.typeLine,
                                        setCode      = card.setCode,
                                        setName      = card.setName,
                                        rarity       = card.rarity,
                                        priceUsd     = card.priceUsd,
                                        priceUsdFoil = card.priceUsdFoil,
                                        priceEur     = card.priceEur,
                                        priceEurFoil = card.priceEurFoil,
                                        quantity     = 1,
                                        isFoil       = row.offerEntry?.isFoil ?: false,
                                        condition    = row.offerEntry?.condition ?: "NM",
                                        language     = row.offerEntry?.language ?: "en",
                                        userCardIdRef = row.offerEntry?.userCardId,
                                        isInCollection = row.offerEntry != null,
                                    )
                                )
                            },
                        )
                    }
                }

                if (uiState.receiverItems.isEmpty()) {
                    item(key = "receiver_empty") {
                        EmptySidePlaceholder(
                            text = stringResource(R.string.trades_receiver_empty_cta),
                            enabled = uiState.selectedFriend != null,
                            onClick = {
                                if (uiState.selectedFriend == null) {
                                    toastState.show(noFriendMsg, MagicToastType.ERROR)
                                } else {
                                    viewModel.onOpenSearch(TradeSide.RECEIVER)
                                    showAddItemSheet = TradeSide.RECEIVER
                                }
                            }
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
                            onClick = {
                                if (uiState.selectedFriend == null) {
                                    toastState.show(noFriendMsg, MagicToastType.ERROR)
                                } else {
                                    viewModel.onOpenSearch(TradeSide.RECEIVER)
                                    showAddItemSheet = TradeSide.RECEIVER
                                }
                            }
                        )
                    }
                }

                item(key = "bottom_spacer") { Spacer(Modifier.height(16.dp)) }
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
                val card = row.card
                val draft = TradeItemDraft(
                    cardId = card.scryfallId,
                    cardName = card.name,
                    imageUrl = card.imageArtCrop ?: card.imageNormal,
                    typeLine = card.typeLine,
                    setCode = card.setCode,
                    setName = card.setName,
                    rarity = card.rarity,
                    priceUsd = card.priceUsd,
                    priceUsdFoil = card.priceUsdFoil,
                    priceEur = card.priceEur,
                    priceEurFoil = card.priceEurFoil,
                    quantity = 1,
                    isFoil = row.wishlistEntry?.isFoil ?: row.offerEntry?.isFoil ?: false,
                    condition = row.wishlistEntry?.condition ?: row.offerEntry?.condition ?: "NM",
                    language = row.wishlistEntry?.language ?: row.offerEntry?.language ?: "en",
                    userCardIdRef = row.offerEntry?.userCardId,
                    isInCollection = row.isOwned || row.offerEntry != null || row.wishlistEntry != null,
                )
                when (side) {
                    TradeSide.PROPOSER -> viewModel.addProposerItem(draft)
                    TradeSide.RECEIVER -> viewModel.addReceiverItem(draft)
                }
            },
            onRemove = { row ->
                focusManager.clearFocus()
                // Derive the same normalized variant fields that onAdd uses when building the
                // TradeItemDraft, so the findLast predicate matches the stored item exactly.
                // WishlistEntry.condition / .language are nullable; if null, onAdd falls back to
                // "NM" / "en" — we must apply the same fallback here or the comparison fails.
                val resolvedIsFoil = row.wishlistEntry?.isFoil ?: row.offerEntry?.isFoil ?: false
                val resolvedCondition = row.wishlistEntry?.condition ?: row.offerEntry?.condition ?: "NM"
                val resolvedLanguage = row.wishlistEntry?.language ?: row.offerEntry?.language ?: "en"
                val resolvedUserCardIdRef = row.offerEntry?.userCardId

                val predicate: (TradeItemDraft) -> Boolean = { item ->
                    item.cardId == row.card.scryfallId &&
                        item.isFoil == resolvedIsFoil &&
                        item.condition == resolvedCondition &&
                        item.language == resolvedLanguage &&
                            item.userCardIdRef == resolvedUserCardIdRef
                }

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
                manaCost = null,
                cardImage = null,
                confirmButtonText = stringResource(R.string.scanner_edit_save)
            )
        }
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
    val sheetState = rememberModalBottomSheetState()

    Surface(
        onClick = { if (!isLocked) showSheet = true },
        enabled = !isLocked,
        shape = RoundedCornerShape(12.dp),
        color = mc.surface.copy(alpha = if (isLocked) 0.3f else 0.5f),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, mc.textDisabled.copy(alpha = if (isLocked) 0.06f else 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(mc.backgroundSecondary),
                contentAlignment = Alignment.Center
            ) {
                if (selectedFriend?.avatarUrl != null) {
                    AsyncImage(
                        model = selectedFriend.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = mc.textDisabled,
                        modifier = Modifier.size(18.dp)
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
                        color = mc.textDisabled
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
        val sheetState = rememberModalBottomSheetState(
            confirmValueChange = { it != SheetValue.Hidden }
        )
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
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showSheet = false },
                        modifier = Modifier.offset(x = (-12).dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = mc.textSecondary
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.trades_friend_selector_sheet_title),
                        style = MaterialTheme.magicTypography.titleMedium,
                        color = mc.textPrimary
                    )

                    when {
                        sessionState is SessionState.Unauthenticated -> {
                            EmptyState(
                                title = "Log in to trade with your friends and sync your collection across devices.",
                                actionLabel = "Log In",
                                onAction = {
                                    showSheet = false
                                    onNavigateToLogin()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        friends.isEmpty() -> {
                            EmptyState(
                                title = "You don't have any friends yet. Add friends to start trading!",
                                actionLabel = "Add Friends",
                                onAction = {
                                    showSheet = false
                                    onNavigateToAddFriends()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        else -> {
                            // "None" option
                            Surface(
                                onClick = {
                                    onFriendSelected(null)
                                    showSheet = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedFriend == null) mc.primaryAccent.copy(alpha = 0.1f) else Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(R.string.trades_friend_none_option),
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.magicTypography.bodyMedium,
                                    color = if (selectedFriend == null) mc.primaryAccent else mc.textPrimary
                                )
                            }

                            friends.forEach { friend ->
                                Surface(
                                    onClick = {
                                        onFriendSelected(friend)
                                        showSheet = false
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selectedFriend?.userId == friend.userId) mc.primaryAccent.copy(alpha = 0.1f) else Color.Transparent,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        AsyncImage(
                                            model = friend.avatarUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(40.dp).clip(CircleShape)
                                        )
                                        Column {
                                            Text(
                                                friend.nickname,
                                                style = MaterialTheme.magicTypography.bodyMedium,
                                                color = if (selectedFriend?.userId == friend.userId) mc.primaryAccent else mc.textPrimary
                                            )
                                            Text(
                                                friend.gameTag,
                                                style = MaterialTheme.magicTypography.labelSmall,
                                                color = mc.textSecondary
                                            )
                                        }
                                    }
                                }
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
        shape    = RoundedCornerShape(12.dp),
        color    = mc.surface.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
        border   = androidx.compose.foundation.BorderStroke(1.dp, mc.textDisabled.copy(alpha = 0.06f)),
    ) {
        Row(
            modifier              = Modifier.padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier         = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(mc.primaryAccent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text  = nickname.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        style = MaterialTheme.magicTypography.labelLarge,
                        color = mc.primaryAccent,
                    )
                }
            }
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

@Composable
private fun TradeItemDraftRow(
    item:     TradeItemDraft,
    onClick:  () -> Unit,
    onRemove: () -> Unit,
) {
    val mc = MaterialTheme.magicColors

    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = mc.surface.copy(alpha = 0.7f),
        border   = androidx.compose.foundation.BorderStroke(1.dp, mc.textDisabled.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val extraContent: @Composable RowScope.() -> Unit = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = mc.goldMtg,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = stringResource(R.string.trades_warning_not_in_collection),
                    style = MaterialTheme.magicTypography.labelSmall.copy(fontSize = 9.sp),
                    color = mc.goldMtg,
                )
            }

            CardListItem(
                name          = item.cardName,
                imageUrl      = item.imageUrl,
                priceUsd      = null,
                priceEur      = null,
                onClick       = onClick,
                modifier      = Modifier.weight(1f),
                quantityText  = "×${item.quantity}",
                hasFoil       = item.isFoil,
                condition     = item.condition.takeIf { it.isNotBlank() },
                language      = item.language.takeIf { it.isNotBlank() },
                typeLine      = item.typeLine,
                setCode       = item.setCode,
                setName       = item.setName,
                rarity        = item.rarity,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                extraSupportingContent = if (!item.isInCollection) extraContent else null,
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector        = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_remove),
                    tint               = mc.textDisabled,
                    modifier           = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptySidePlaceholder(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val mc = MaterialTheme.magicColors
    val contentAlpha = if (enabled) 1f else 0.5f
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = mc.surface.copy(alpha = if (enabled) 0.3f else 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, mc.textDisabled.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = mc.primaryAccent.copy(alpha = 0.5f * contentAlpha),
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.magicTypography.bodySmall,
                color = mc.textSecondary.copy(alpha = contentAlpha),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ReviewCollectionToggle(
    label:    String,
    checked:  Boolean,
    onToggle: () -> Unit,
) {
    val mc = MaterialTheme.magicColors

    val accentColor = mc.primaryAccent
    val borderColor = if (checked) accentColor else mc.textDisabled.copy(alpha = 0.25f)
    val surfaceColor = if (checked) accentColor.copy(alpha = 0.12f) else mc.surface.copy(alpha = 0.2f)
    val shadowElevation = if (checked) 4.dp else 0.dp

    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = surfaceColor,
        border = BorderStroke(width = if (checked) 1.5.dp else 1.dp, color = borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(12.dp),
                ambientColor = accentColor.copy(alpha = 0.2f),
                spotColor = accentColor.copy(alpha = 0.3f),
            ),
        shadowElevation = 0.dp, // handled manually via Modifier.shadow above
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Collection icon badge ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (checked) accentColor.copy(alpha = 0.22f)
                        else mc.backgroundSecondary.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.LibraryBooks,
                    contentDescription = null,
                    tint = if (checked) accentColor else mc.textDisabled,
                    modifier = Modifier.size(20.dp),
                )
            }

            // ── Label + subtitle ───────────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.magicTypography.bodySmall,
                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (checked) accentColor else mc.textPrimary,
                )
                Text(
                    text = stringResource(R.string.trades_review_collection_subtitle),
                    style = MaterialTheme.magicTypography.labelSmall.copy(fontSize = 10.sp),
                    color = if (checked) accentColor.copy(alpha = 0.75f) else mc.textSecondary,
                )
            }

            // ── Toggle indicator ───────────────────────────────────────────────────
            Icon(
                imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (checked) accentColor else mc.textDisabled.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.magicTypography.labelSmall,
            color = mc.goldMtg,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
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
        shape = RoundedCornerShape(12.dp),
        color = mc.surface.copy(alpha = 0.4f),
        border = BorderStroke(if (isExact || isPartial) 2.dp else 1.dp, borderBrush),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        center = Offset.Zero,
                        radius = 400f
                    )
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp)
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
                    modifier = Modifier.size(36.dp)
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

@Composable
private fun AddItemButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val mc = MaterialTheme.magicColors
    val contentAlpha = if (enabled) 1f else 0.5f
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = mc.primaryAccent.copy(alpha = contentAlpha)),
        border = androidx.compose.foundation.BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.3f * contentAlpha))
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.trades_add_item),
            style = MaterialTheme.magicTypography.labelLarge,
        )
    }
}
