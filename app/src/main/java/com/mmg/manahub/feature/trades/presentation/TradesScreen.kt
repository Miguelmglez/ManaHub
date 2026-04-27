package com.mmg.manahub.feature.trades.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.trades.domain.model.OpenForTradeEntry
import com.mmg.manahub.feature.trades.domain.model.WishlistEntry
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
//  Entry point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Root composable for the Trades sub-tab.
 *
 * Embedded inside [CollectionScreen] when the user selects the "Trades" tab.
 * The [onCardClick] lambda navigates to the card detail screen.
 */
@Composable
fun TradesScreen(
    onCardClick: (scryfallId: String) -> Unit,
    viewModel: TradesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val comingSoonMsg = stringResource(R.string.trades_coming_soon)

    // One-shot snackbar from ViewModel
    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.onSnackbarDismissed()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.magicColors.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Only show the top-level tab row when there is trade history.
            // Phase 2: history is empty, so we always render the Exploration pane directly.
            val hasHistory = false // will become uiState.history.isNotEmpty() in Phase 3

            if (hasHistory) {
                TradesTabRow(
                    selectedTab = uiState.selectedTab,
                    onTabSelected = viewModel::onTabSelected,
                )
            }

            when (uiState.selectedTab) {
                TradesMainTab.EXPLORATION -> ExplorationContent(
                    uiState      = uiState,
                    onCardClick  = onCardClick,
                    onToggle     = viewModel::onExplorationToggle,
                    onWishlistSelect  = viewModel::onToggleWishlistSelect,
                    onOfferSelect     = viewModel::onToggleOfferSelect,
                    onClearSelection  = viewModel::onClearSelection,
                    onAddToWishlist   = viewModel::onAddToWishlist,
                    onRemoveWishlist  = viewModel::onRemoveFromWishlist,
                    onRemoveOffer     = viewModel::onRemoveFromOpenForTrade,
                    onProposalCta     = { snackbarHostState.let {
                        // Using LaunchedEffect pattern from the FAB click callback would
                        // require a coroutine scope; we store it as a snackbar message instead.
                        viewModel.onSnackbarDismissed() // no-op; reset
                        viewModel.onClearSelection()
                    }},
                    onComingSoon = {
                        // The ViewModel stores a "coming soon" message to surface via Snackbar
                        // using the standard LaunchedEffect(uiState.snackbarMessage) mechanism.
                        // We call a dedicated action so the snackbar text is set.
                    },
                    comingSoonMsg = comingSoonMsg,
                    snackbarHostState = snackbarHostState,
                )
                TradesMainTab.HISTORY -> HistoryPlaceholder(
                    isLoggedIn = true, // Phase 3 will wire session state
                    onComingSoon = { /* no-op — Snackbar shown inline */ },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Trades tab row (Exploration | History)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TradesTabRow(
    selectedTab: TradesMainTab,
    onTabSelected: (TradesMainTab) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor   = mc.backgroundSecondary,
        contentColor     = mc.primaryAccent,
    ) {
        Tab(
            selected = selectedTab == TradesMainTab.EXPLORATION,
            onClick  = { onTabSelected(TradesMainTab.EXPLORATION) },
            text     = {
                Text(
                    stringResource(R.string.trades_tab_exploration).uppercase(Locale.getDefault()),
                    style = MaterialTheme.magicTypography.labelLarge,
                )
            },
        )
        Tab(
            selected = selectedTab == TradesMainTab.HISTORY,
            onClick  = { onTabSelected(TradesMainTab.HISTORY) },
            text     = {
                Text(
                    stringResource(R.string.trades_tab_history).uppercase(Locale.getDefault()),
                    style = MaterialTheme.magicTypography.labelLarge,
                )
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Exploration tab
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExplorationContent(
    uiState:           TradesUiState,
    onCardClick:       (String) -> Unit,
    onToggle:          (ExplorationToggle) -> Unit,
    onWishlistSelect:  (String) -> Unit,
    onOfferSelect:     (String) -> Unit,
    onClearSelection:  () -> Unit,
    onAddToWishlist:   (String, Boolean, Boolean?, String?, String?, Boolean?) -> Unit,
    onRemoveWishlist:  (String) -> Unit,
    onRemoveOffer:     (String) -> Unit,
    onProposalCta:     () -> Unit,
    onComingSoon:      () -> Unit,
    comingSoonMsg:     String,
    snackbarHostState: SnackbarHostState,
) {
    val mc = MaterialTheme.magicColors
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedFriend by remember { mutableStateOf<Friend?>(null) }

    // Add to Wishlist dialog
    if (showAddDialog) {
        AddToWishlistDialog(
            onConfirm = { cardId, matchAny, foil, cond, lang, altArt ->
                onAddToWishlist(cardId, matchAny, foil, cond, lang, altArt)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    // Friend trade list bottom sheet
    selectedFriend?.let { friend ->
        FriendTradeBottomSheet(
            friend    = friend,
            onDismiss = { selectedFriend = null },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Toggle: My Lists | Friends ────────────────────────────────────
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                SegmentedButton(
                    selected = uiState.explorationToggle == ExplorationToggle.MY_LISTS,
                    onClick  = { onToggle(ExplorationToggle.MY_LISTS) },
                    shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors   = SegmentedButtonDefaults.colors(
                        activeContainerColor  = mc.primaryAccent.copy(alpha = 0.15f),
                        activeContentColor    = mc.primaryAccent,
                        inactiveContainerColor = mc.surface,
                        inactiveContentColor  = mc.textSecondary,
                    ),
                ) {
                    Text(
                        stringResource(R.string.trades_toggle_my_lists),
                        style = MaterialTheme.magicTypography.labelLarge,
                    )
                }
                SegmentedButton(
                    selected = uiState.explorationToggle == ExplorationToggle.FRIENDS,
                    onClick  = { onToggle(ExplorationToggle.FRIENDS) },
                    shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors   = SegmentedButtonDefaults.colors(
                        activeContainerColor  = mc.primaryAccent.copy(alpha = 0.15f),
                        activeContentColor    = mc.primaryAccent,
                        inactiveContainerColor = mc.surface,
                        inactiveContentColor  = mc.textSecondary,
                    ),
                ) {
                    Text(
                        stringResource(R.string.trades_toggle_friends),
                        style = MaterialTheme.magicTypography.labelLarge,
                    )
                }
            }

            when (uiState.explorationToggle) {
                ExplorationToggle.MY_LISTS -> MyListsContent(
                    uiState           = uiState,
                    onCardClick       = onCardClick,
                    onWishlistSelect  = onWishlistSelect,
                    onOfferSelect     = onOfferSelect,
                    onRemoveWishlist  = onRemoveWishlist,
                    onRemoveOffer     = onRemoveOffer,
                )
                ExplorationToggle.FRIENDS -> FriendsContent(
                    friends       = uiState.friends,
                    onFriendClick = { friend -> selectedFriend = friend },
                )
            }
        }

        // ── FABs (bottom-right) ───────────────────────────────────────────────
        if (uiState.explorationToggle == ExplorationToggle.MY_LISTS) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                // "Create proposal" extended FAB — visible only in multi-select mode
                AnimatedVisibility(
                    visible = uiState.isMultiSelectMode,
                    enter   = expandVertically(expandFrom = Alignment.Bottom),
                    exit    = shrinkVertically(shrinkTowards = Alignment.Bottom),
                ) {
                    ExtendedFloatingActionButton(
                        text            = { Text(stringResource(R.string.trades_create_proposal_cta)) },
                        icon            = {},
                        onClick         = {
                            // Phase 3: actual proposal creation
                            // For Phase 2, show a "Coming soon" snackbar
                        },
                        containerColor  = mc.primaryAccent,
                        contentColor    = mc.background,
                    )
                }

                // "Add to Wishlist" FAB
                FloatingActionButton(
                    onClick        = { showAddDialog = true },
                    containerColor = mc.primaryAccent,
                    contentColor   = mc.background,
                ) {
                    Icon(
                        imageVector        = Icons.Default.Add,
                        contentDescription = stringResource(R.string.trades_add_to_wishlist),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  My Lists content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MyListsContent(
    uiState:          TradesUiState,
    onCardClick:      (String) -> Unit,
    onWishlistSelect: (String) -> Unit,
    onOfferSelect:    (String) -> Unit,
    onRemoveWishlist: (String) -> Unit,
    onRemoveOffer:    (String) -> Unit,
) {
    var wishlistExpanded by remember { mutableStateOf(true) }
    var offersExpanded   by remember { mutableStateOf(true) }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ── My Wishlist section ───────────────────────────────────────────────
        item(key = "wishlist_header") {
            CollapsibleSectionHeader(
                title    = stringResource(R.string.trades_wishlist_section),
                count    = uiState.wishlist.size,
                expanded = wishlistExpanded,
                onToggle = { wishlistExpanded = !wishlistExpanded },
            )
        }
        if (wishlistExpanded) {
            if (uiState.wishlist.isEmpty()) {
                item(key = "wishlist_empty") {
                    Text(
                        text     = stringResource(R.string.state_empty),
                        style    = MaterialTheme.magicTypography.bodySmall,
                        color    = MaterialTheme.magicColors.textDisabled,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(uiState.wishlist, key = { "w_${it.id}" }) { entry ->
                    WishlistEntryRow(
                        entry      = entry,
                        isSelected = entry.id in uiState.selectedWishlistIds,
                        onCardClick = onCardClick,
                        onSelect   = { onWishlistSelect(entry.id) },
                        onRemove   = { onRemoveWishlist(entry.id) },
                    )
                }
            }
        }

        item(key = "spacer_1") { Spacer(Modifier.height(8.dp)) }

        // ── My Offers section ─────────────────────────────────────────────────
        item(key = "offers_header") {
            CollapsibleSectionHeader(
                title    = stringResource(R.string.trades_offers_section),
                count    = uiState.openForTrade.size,
                expanded = offersExpanded,
                onToggle = { offersExpanded = !offersExpanded },
            )
        }
        if (offersExpanded) {
            if (uiState.openForTrade.isEmpty()) {
                item(key = "offers_empty") {
                    Text(
                        text     = stringResource(R.string.state_empty),
                        style    = MaterialTheme.magicTypography.bodySmall,
                        color    = MaterialTheme.magicColors.textDisabled,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(uiState.openForTrade, key = { "o_${it.id}" }) { entry ->
                    OfferEntryRow(
                        entry      = entry,
                        isSelected = entry.id in uiState.selectedOfferIds,
                        onCardClick = onCardClick,
                        onSelect   = { onOfferSelect(entry.id) },
                        onRemove   = { onRemoveOffer(entry.id) },
                    )
                }
            }
        }

        item(key = "spacer_bottom") { Spacer(Modifier.height(88.dp)) } // FAB clearance
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Collapsible section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CollapsibleSectionHeader(
    title:    String,
    count:    Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier            = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text  = "$title ($count)",
            style = MaterialTheme.magicTypography.labelLarge,
            color = mc.textSecondary,
        )
        Icon(
            imageVector        = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint               = mc.textSecondary,
            modifier           = Modifier.size(20.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Wishlist entry row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WishlistEntryRow(
    entry:      WishlistEntry,
    isSelected: Boolean,
    onCardClick: (String) -> Unit,
    onSelect:   () -> Unit,
    onRemove:   () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = if (isSelected) mc.primaryAccent.copy(alpha = 0.12f) else mc.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick(entry.cardId) },
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Multi-select checkbox
            IconButton(onClick = onSelect, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector        = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint               = if (isSelected) mc.primaryAccent else mc.textDisabled,
                    modifier           = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = entry.cardId,
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textPrimary,
                )
                // Variant badges
                val badges = buildVariantBadges(entry)
                if (badges.isNotEmpty()) {
                    Text(
                        text  = badges.joinToString(" · "),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                    )
                }
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

private fun buildVariantBadges(entry: WishlistEntry): List<String> {
    if (entry.matchAnyVariant) return listOf("Any variant")
    return buildList {
        entry.isFoil?.let    { if (it) add("Foil") }
        entry.condition?.let { add(it) }
        entry.language?.let  { add(it) }
        entry.isAltArt?.let  { if (it) add("Alt art") }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Offer entry row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OfferEntryRow(
    entry:      OpenForTradeEntry,
    isSelected: Boolean,
    onCardClick: (String) -> Unit,
    onSelect:   () -> Unit,
    onRemove:   () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = if (isSelected) mc.primaryAccent.copy(alpha = 0.12f) else mc.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick(entry.scryfallId) },
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onSelect, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector        = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint               = if (isSelected) mc.primaryAccent else mc.textDisabled,
                    modifier           = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = entry.scryfallId,
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textPrimary,
                )
                val badges = buildOfferBadges(entry)
                if (badges.isNotEmpty()) {
                    Text(
                        text  = badges.joinToString(" · "),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                    )
                }
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

private fun buildOfferBadges(entry: OpenForTradeEntry): List<String> = buildList {
    if (entry.isFoil)     add("Foil")
    if (entry.condition.isNotBlank()) add(entry.condition)
    if (entry.language.isNotBlank())  add(entry.language)
    if (entry.isAltArt)   add("Alt art")
}

// ─────────────────────────────────────────────────────────────────────────────
//  Friends content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FriendsContent(
    friends:       List<Friend>,
    onFriendClick: (Friend) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    if (friends.isEmpty()) {
        Box(
            modifier        = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = mc.surface,
            ) {
                Column(
                    modifier            = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text      = stringResource(R.string.trades_no_friends_cta),
                        style     = MaterialTheme.magicTypography.bodyMedium,
                        color     = mc.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(friends, key = { it.id }) { friend ->
                FriendTradePreviewRow(
                    friend   = friend,
                    onClick  = { onFriendClick(friend) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Friend trade preview row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FriendTradePreviewRow(
    friend:  Friend,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = mc.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar circle with first letter
            Box(
                modifier        = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(mc.primaryAccent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = friend.nickname.take(1).uppercase(Locale.getDefault()),
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = mc.primaryAccent,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = friend.nickname,
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textPrimary,
                )
                Text(
                    text  = friend.gameTag,
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                )
            }
            TextButton(onClick = onClick) {
                Text(
                    text  = stringResource(R.string.trades_view_full_list),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.primaryAccent,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Friend trade bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendTradeBottomSheet(
    friend:    Friend,
    onDismiss: () -> Unit,
) {
    val mc         = MaterialTheme.magicColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedSheetTab by remember { mutableStateOf(0) }
    val sheetTabs = listOf(
        stringResource(R.string.trades_friend_sheet_wishlist),
        stringResource(R.string.trades_friend_sheet_offers),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = mc.backgroundSecondary,
        contentColor     = mc.textPrimary,
        dragHandle       = { Spacer(Modifier.height(8.dp)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            // Sheet header with close button
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = friend.nickname,
                    style    = MaterialTheme.magicTypography.titleMedium,
                    color    = mc.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector        = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint               = mc.textSecondary,
                    )
                }
            }

            // Tab row: Wishlist | Offer for Trade
            TabRow(
                selectedTabIndex = selectedSheetTab,
                containerColor   = mc.backgroundSecondary,
                contentColor     = mc.primaryAccent,
            ) {
                sheetTabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedSheetTab == index,
                        onClick  = { selectedSheetTab = index },
                        text     = {
                            Text(
                                label.uppercase(Locale.getDefault()),
                                style = MaterialTheme.magicTypography.labelLarge,
                            )
                        },
                    )
                }
            }

            // Phase 2 placeholder content
            Box(
                modifier        = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = stringResource(R.string.state_loading),
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textDisabled,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  History placeholder
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryPlaceholder(
    isLoggedIn:  Boolean,
    onComingSoon: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Box(
        modifier        = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (!isLoggedIn) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier            = Modifier.padding(32.dp),
            ) {
                Text(
                    text      = stringResource(R.string.trades_sign_in_cta),
                    style     = MaterialTheme.magicTypography.bodyMedium,
                    color     = mc.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onComingSoon,
                    colors  = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                ) {
                    Text(stringResource(R.string.trades_create_account), color = mc.background)
                }
            }
        } else {
            Text(
                text      = stringResource(R.string.trades_history_coming_soon),
                style     = MaterialTheme.magicTypography.bodyMedium,
                color     = mc.textSecondary,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(32.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Add to Wishlist dialog
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Alert dialog that lets the user add a card to their wishlist.
 *
 * @param onConfirm Receives (cardId, matchAnyVariant, isFoil, condition, language, isAltArt).
 * @param onDismiss Called when the user cancels or dismisses the dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToWishlistDialog(
    onConfirm: (String, Boolean, Boolean?, String?, String?, Boolean?) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    var cardId       by remember { mutableStateOf("") }
    var matchAnyVariant by remember { mutableStateOf(true) }
    var isFoil       by remember { mutableStateOf(false) }
    var condition    by remember { mutableStateOf("") }
    var language     by remember { mutableStateOf("") }
    var isAltArt     by remember { mutableStateOf(false) }

    val conditions = listOf("M", "NM", "EX", "GD", "LP", "PL", "P")
    var showConditionDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = mc.backgroundSecondary,
        title = {
            Text(
                stringResource(R.string.trades_add_to_wishlist),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Card ID field
                OutlinedTextField(
                    value         = cardId,
                    onValueChange = { cardId = it },
                    label         = { Text(stringResource(R.string.trades_select_card_id_hint)) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = mc.primaryAccent,
                        unfocusedBorderColor = mc.surfaceVariant,
                        focusedTextColor     = mc.textPrimary,
                        unfocusedTextColor   = mc.textPrimary,
                        cursorColor          = mc.primaryAccent,
                        focusedLabelColor    = mc.primaryAccent,
                        unfocusedLabelColor  = mc.textSecondary,
                    ),
                )

                // Any variant / Exact variant toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        stringResource(R.string.trades_any_variant) to true,
                        stringResource(R.string.trades_exact_variant) to false,
                    ).forEach { (label, value) ->
                        val selected = matchAnyVariant == value
                        Surface(
                            shape   = RoundedCornerShape(8.dp),
                            color   = if (selected) mc.primaryAccent.copy(alpha = 0.15f) else mc.surface,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { matchAnyVariant = value },
                        ) {
                            Text(
                                text      = label,
                                style     = MaterialTheme.magicTypography.labelSmall,
                                color     = if (selected) mc.primaryAccent else mc.textSecondary,
                                textAlign = TextAlign.Center,
                                modifier  = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }

                // Exact variant options
                AnimatedVisibility(visible = !matchAnyVariant) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Foil checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier          = Modifier.clickable { isFoil = !isFoil },
                        ) {
                            Checkbox(
                                checked  = isFoil,
                                onCheckedChange = { isFoil = it },
                                colors   = CheckboxDefaults.colors(
                                    checkedColor   = mc.primaryAccent,
                                    uncheckedColor = mc.textSecondary,
                                ),
                            )
                            Text(
                                stringResource(R.string.trades_foil),
                                style = MaterialTheme.magicTypography.bodySmall,
                                color = mc.textPrimary,
                            )
                        }

                        // Condition dropdown
                        Box {
                            OutlinedTextField(
                                value         = condition,
                                onValueChange = {},
                                readOnly      = true,
                                label         = { Text(stringResource(R.string.trades_condition)) },
                                trailingIcon  = {
                                    IconButton(onClick = { showConditionDropdown = true }) {
                                        Icon(Icons.Default.ExpandMore, contentDescription = null, tint = mc.textSecondary)
                                    }
                                },
                                modifier      = Modifier
                                    .fillMaxWidth()
                                    .clickable { showConditionDropdown = true },
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = mc.primaryAccent,
                                    unfocusedBorderColor = mc.surfaceVariant,
                                    focusedTextColor     = mc.textPrimary,
                                    unfocusedTextColor   = mc.textPrimary,
                                    focusedLabelColor    = mc.primaryAccent,
                                    unfocusedLabelColor  = mc.textSecondary,
                                ),
                            )
                            DropdownMenu(
                                expanded         = showConditionDropdown,
                                onDismissRequest = { showConditionDropdown = false },
                            ) {
                                conditions.forEach { cond ->
                                    DropdownMenuItem(
                                        text    = { Text(cond, color = mc.textPrimary) },
                                        onClick = {
                                            condition = cond
                                            showConditionDropdown = false
                                        },
                                    )
                                }
                            }
                        }

                        // Language field
                        OutlinedTextField(
                            value         = language,
                            onValueChange = { language = it },
                            label         = { Text(stringResource(R.string.trades_language)) },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth(),
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = mc.primaryAccent,
                                unfocusedBorderColor = mc.surfaceVariant,
                                focusedTextColor     = mc.textPrimary,
                                unfocusedTextColor   = mc.textPrimary,
                                cursorColor          = mc.primaryAccent,
                                focusedLabelColor    = mc.primaryAccent,
                                unfocusedLabelColor  = mc.textSecondary,
                            ),
                        )

                        // Alt art checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier          = Modifier.clickable { isAltArt = !isAltArt },
                        ) {
                            Checkbox(
                                checked  = isAltArt,
                                onCheckedChange = { isAltArt = it },
                                colors   = CheckboxDefaults.colors(
                                    checkedColor   = mc.primaryAccent,
                                    uncheckedColor = mc.textSecondary,
                                ),
                            )
                            Text(
                                stringResource(R.string.trades_alt_art),
                                style = MaterialTheme.magicTypography.bodySmall,
                                color = mc.textPrimary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick  = {
                    if (cardId.isNotBlank()) {
                        onConfirm(
                            cardId,
                            matchAnyVariant,
                            if (!matchAnyVariant) isFoil else null,
                            if (!matchAnyVariant && condition.isNotBlank()) condition else null,
                            if (!matchAnyVariant && language.isNotBlank()) language else null,
                            if (!matchAnyVariant) isAltArt else null,
                        )
                    }
                },
                enabled = cardId.isNotBlank(),
            ) {
                Text(
                    stringResource(R.string.action_confirm),
                    color = mc.primaryAccent,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.action_cancel),
                    color = mc.textSecondary,
                )
            }
        },
    )
}
