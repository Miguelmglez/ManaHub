package com.mmg.manahub.feature.trades.presentation

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.ui.components.AddCardSheet
import com.mmg.manahub.core.ui.components.CardSearchSheet
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.FoilBadge
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.presentation.AuthViewModel
import com.mmg.manahub.feature.auth.presentation.LoginSheet
import com.mmg.manahub.feature.friends.domain.model.Friend

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTradeProposalScreen(
    onBack: () -> Unit,
    onNavigateToThread: (proposalId: String, rootProposalId: String) -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onNavigateToAddFriends: () -> Unit = {},
    viewModel: TradeProposalViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastState = rememberMagicToastState()
    val mc = MaterialTheme.magicColors
    val focusManager = LocalFocusManager.current
    val preferredCurrency = LocalPreferredCurrency.current

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
            else                        -> err
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

    var showAddItemSheet by remember { mutableStateOf<ItemSide?>(null) }
    var editingItem by remember { mutableStateOf<TradeItemDraft?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showLoginSheet by remember { mutableStateOf(false) }

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
                                    if (uiState.isCounterMode) R.string.trades_counter_proposal_title
                                    else R.string.trades_create_proposal_title
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
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = mc.background, strokeWidth = 2.dp)
                        } else {
                            Text(
                                stringResource(R.string.trades_send_proposal),
                                color = mc.background,
                                style = MaterialTheme.magicTypography.bodyLarge,
                            )
                        }
                    }
                    if (!uiState.isCounterMode) {
                        TextButton(
                            onClick  = viewModel::onSaveDraft,
                            enabled  = !uiState.isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.trades_save_draft),
                                color = mc.textSecondary,
                                style = MaterialTheme.magicTypography.bodyLarge,
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            LazyColumn(
                modifier       = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // ── "You offer" section ───────────────────────────────────────────
                item(key = "you_offer_header") {
                    TradeSectionHeader(
                        title = stringResource(R.string.trades_you_offer_section),
                        totalValue = if (preferredCurrency == PreferredCurrency.EUR) uiState.totalProposerValueEur else uiState.totalProposerValueUsd,
                        currency = preferredCurrency
                    )
                }

                if (uiState.proposerItems.isEmpty() && !uiState.includesReviewFromProposer) {
                    item(key = "proposer_empty") {
                        EmptySidePlaceholder(
                            text = "Add cards you want to trade away",
                            onClick = { showAddItemSheet = ItemSide.PROPOSER }
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

                    item(key = "you_offer_review") {
                        ReviewCollectionToggle(
                            label   = stringResource(R.string.trades_review_collection_proposer),
                            checked = uiState.includesReviewFromProposer,
                            onToggle = viewModel::toggleReviewCollectionProposer,
                        )
                    }

                    item(key = "you_offer_add") {
                        AddItemButton(onClick = { showAddItemSheet = ItemSide.PROPOSER })
                    }
                }

                item(key = "divider") {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = mc.textDisabled.copy(alpha = 0.2f)
                    )
                }

                // ── "They offer" section ──────────────────────────────────────────
                item(key = "they_offer_header") {
                    TradeSectionHeader(
                        title = stringResource(R.string.trades_they_offer_section),
                        totalValue = if (preferredCurrency == PreferredCurrency.EUR) uiState.totalReceiverValueEur else uiState.totalReceiverValueUsd,
                        currency = preferredCurrency
                    )
                }

                item(key = "friend_selector") {
                    FriendSelector(
                        friends = uiState.friends,
                        selectedFriend = uiState.selectedFriend,
                        onFriendSelected = viewModel::onFriendSelected,
                        sessionState = uiState.sessionState,
                        onNavigateToLogin = { showLoginSheet = true },
                        onNavigateToAddFriends = onNavigateToAddFriends
                    )
                }

                if (uiState.receiverItems.isEmpty() && !uiState.includesReviewFromReceiver) {
                    item(key = "receiver_empty") {
                        EmptySidePlaceholder(
                            text = "Search for cards you want from them",
                            onClick = { showAddItemSheet = ItemSide.RECEIVER }
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

                    item(key = "they_offer_review") {
                        ReviewCollectionToggle(
                            label   = stringResource(R.string.trades_review_collection_receiver),
                            checked = uiState.includesReviewFromReceiver,
                            onToggle = viewModel::toggleReviewCollectionReceiver,
                        )
                    }

                    item(key = "they_offer_add") {
                        AddItemButton(onClick = { showAddItemSheet = ItemSide.RECEIVER })
                    }
                }

                item(key = "bottom_spacer") { Spacer(Modifier.height(16.dp)) }
            }
        }

        MagicToastHost(state = toastState)
    }

    showAddItemSheet?.let { side ->
        CardSearchSheet(
            query = uiState.addCardsQuery,
            addCardsResults = uiState.addCardsResults,
            wishlistResults = uiState.wishlistResults,
            scryfallResults = uiState.scryfallResults,
            isSearchingCards = uiState.isSearchingCards,
            isSearchingWishlist = uiState.isSearchingWishlist,
            isSearchingScryfall = uiState.isSearchingScryfall,
            onQueryChange = viewModel::onAddCardsQueryChange,
            onScryfallSearch = viewModel::searchScryfallDirect,
            friendName = uiState.selectedFriend?.nickname,
            onAdd = { id ->
                focusManager.clearFocus()
                val card = viewModel.getCardById(id)
                if (card != null) {
                    val draft = TradeItemDraft(
                        cardId = card.scryfallId,
                        cardName = card.name,
                        imageUrl = card.imageArtCrop ?: card.imageNormal,
                        priceUsd = card.priceUsd,
                        priceUsdFoil = card.priceUsdFoil,
                        priceEur = card.priceEur,
                        priceEurFoil = card.priceEurFoil,
                        quantity = 1,
                        condition = "NM",
                        language = "en"
                    )
                    when (side) {
                        ItemSide.PROPOSER -> viewModel.addProposerItem(draft)
                        ItemSide.RECEIVER -> viewModel.addReceiverItem(draft)
                    }
                    showAddItemSheet = null
                    viewModel.clearAddCardsState()
                }
            },
            onRemove = { /* No-op here */ },
            onCardClick = { 
                focusManager.clearFocus()
                /* Maybe show detail later */ 
            },
            onDismiss = {
                focusManager.clearFocus()
                showAddItemSheet = null
                viewModel.clearAddCardsState()
            }
        )
    }

    if (showEditSheet) {
        editingItem?.let { item ->
            AddCardSheet(
                cardName = item.cardName,
                onConfirm = { isFoil, isAltArt, condition, language, qty ->
                    val updated = item.copy(
                        quantity = qty,
                        isFoil = isFoil,
                        condition = condition,
                        language = language,
                        isAltArt = isAltArt
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

private enum class ItemSide { PROPOSER, RECEIVER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendSelector(
    friends: List<Friend>,
    selectedFriend: Friend?,
    onFriendSelected: (Friend?) -> Unit,
    sessionState: SessionState,
    onNavigateToLogin: () -> Unit,
    onNavigateToAddFriends: () -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    val mc = MaterialTheme.magicColors
    val sheetState = rememberModalBottomSheetState()

    Surface(
        onClick = { showSheet = true },
        shape = RoundedCornerShape(12.dp),
        color = mc.surface.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, mc.textDisabled.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
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
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedFriend?.nickname ?: "Select a friend",
                    style = MaterialTheme.magicTypography.labelMedium,
                    color = if (selectedFriend != null) mc.textPrimary else mc.textSecondary
                )
                if (selectedFriend != null) {
                    Text(
                        text = selectedFriend.gameTag,
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = mc.backgroundSecondary,
            contentColor = mc.textPrimary,
            dragHandle = { BottomSheetDefaults.DragHandle(color = mc.textDisabled.copy(alpha = 0.4f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Select Friend",
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
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                        ) {
                            item {
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
                                        "None (Direct Trade)",
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.magicTypography.bodyMedium,
                                        color = if (selectedFriend == null) mc.primaryAccent else mc.textPrimary
                                    )
                                }
                            }
                            items(friends) { friend ->
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
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TradeSectionHeader(
    title: String,
    totalValue: Double,
    currency: PreferredCurrency
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text  = title.uppercase(),
            style = MaterialTheme.magicTypography.labelLarge,
            color = MaterialTheme.magicColors.textSecondary,
            letterSpacing = 1.sp
        )
        if (totalValue > 0) {
            Text(
                text = PriceFormatter.format(totalValue, currency),
                style = MaterialTheme.magicTypography.labelLarge,
                color = MaterialTheme.magicColors.goldMtg,
                fontWeight = FontWeight.Bold
            )
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
    val ty = MaterialTheme.magicTypography
    val preferredCurrency = LocalPreferredCurrency.current

    val currentPrice = if (item.isFoil) {
        item.priceUsdFoil ?: item.priceUsd
    } else {
        item.priceUsd
    }
    
    val currentPriceEur = if (item.isFoil) {
        item.priceEurFoil ?: item.priceEur
    } else {
        item.priceEur
    }

    Card(
        onClick  = onClick,
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = mc.surface.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, mc.textDisabled.copy(alpha = 0.1f))
    ) {
        Row(
            modifier          = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Card Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 54.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(mc.backgroundSecondary)
            ) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.cardName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text     = item.cardName,
                        style    = ty.bodyMedium,
                        color    = mc.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isFoil) {
                        Spacer(Modifier.width(6.dp))
                        FoilBadge()
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text  = "x${item.quantity}",
                        style = ty.labelSmall,
                        color = mc.primaryAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text  = "·",
                        style = ty.labelSmall,
                        color = mc.textDisabled
                    )
                    Text(
                        text  = listOfNotNull(
                            item.condition.takeIf { it.isNotBlank() },
                            item.language.takeIf { it.isNotBlank() },
                            "Alt art".takeIf { item.isAltArt }
                        ).joinToString(" · "),
                        style = ty.labelSmall,
                        color = mc.textSecondary,
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                val priceText = PriceFormatter.format(
                    if (preferredCurrency == PreferredCurrency.EUR) currentPriceEur else currentPrice,
                    preferredCurrency
                )
                if (priceText != "—") {
                    Text(
                        text = priceText,
                        style = ty.labelMedium,
                        color = mc.goldMtg,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
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
}

@Composable
private fun EmptySidePlaceholder(
    text: String,
    onClick: () -> Unit
) {
    val mc = MaterialTheme.magicColors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = mc.surface.copy(alpha = 0.3f),
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
                tint = mc.primaryAccent.copy(alpha = 0.5f),
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.magicTypography.bodySmall,
                color = mc.textSecondary,
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
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(8.dp),
        color = if (checked) mc.primaryAccent.copy(alpha = 0.1f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier          = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked         = checked,
                onCheckedChange = { onToggle() },
                colors          = CheckboxDefaults.colors(
                    checkedColor   = mc.primaryAccent,
                    uncheckedColor = mc.textDisabled,
                ),
            )
            Spacer(Modifier.width(4.dp))
            Column {
                Text(
                    text  = label,
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = if (checked) mc.primaryAccent else mc.textPrimary,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "Let them browse your collection for more cards",
                    style = MaterialTheme.magicTypography.labelSmall.copy(fontSize = 10.sp),
                    color = mc.textSecondary
                )
            }
        }
    }
}

@Composable
private fun AddItemButton(onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = mc.primaryAccent),
        border = androidx.compose.foundation.BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.3f))
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.trades_add_item),
            style = MaterialTheme.magicTypography.labelLarge,
        )
    }
}
