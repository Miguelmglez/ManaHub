package com.mmg.manahub.feature.trades.presentation

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.CardListItem
import com.mmg.manahub.core.ui.components.CopyBadge
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.trades.domain.model.OpenForTradeEntry
import com.mmg.manahub.feature.trades.domain.model.WishlistEntry
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradesScreen(
    onCardClick: (scryfallId: String) -> Unit,
    onNavigateToProposal: (receiverId: String) -> Unit = {},
    onNavigateToThread: (proposalId: String, rootProposalId: String) -> Unit = { _, _ -> },
    viewModel: TradesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // One-shot snackbar from ViewModel
    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.onSnackbarDismissed()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Sub-navigation chips ──────────────────────────────────────────
            TradesSubNavigation(
                selectedTab = uiState.selectedTab,
                onTabSelected = viewModel::onTabSelected
            )

            // ── Content area ──────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                when (uiState.selectedTab) {
                    TradesMainTab.MY_LIST -> MyListContent(
                        uiState = uiState,
                        onCardClick = onCardClick,
                        onWishlistSelect = viewModel::onToggleWishlistSelect,
                        onOfferSelect = viewModel::onToggleOfferSelect,
                    )

                    TradesMainTab.FRIENDS -> FriendsContent(
                        friends = uiState.friends,
                        onFriendClick = { friend ->
                            onNavigateToProposal(friend.userId)
                        }
                    )

                    TradesMainTab.HISTORY -> TradesHistoryScreen(
                        onOpenThread = onNavigateToThread,
                    )
                }
            }
        }

        // ── FAB ───────────────────────────────────────────────────────────────
        val mc = MaterialTheme.magicColors
        FloatingActionButton(
            onClick = { onNavigateToProposal("") },
            containerColor = mc.primaryAccent,
            contentColor = mc.background,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sub-Navigation Chips
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TradesSubNavigation(
    selectedTab: TradesMainTab,
    onTabSelected: (TradesMainTab) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TradesMainTab.entries.forEach { tab ->
            val label = when (tab) {
                TradesMainTab.MY_LIST -> stringResource(R.string.trades_toggle_my_lists)
                TradesMainTab.FRIENDS -> stringResource(R.string.trades_toggle_friends)
                TradesMainTab.HISTORY -> stringResource(R.string.trades_tab_history)
            }
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.magicTypography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = mc.primaryAccent.copy(alpha = 0.15f),
                    selectedLabelColor = mc.primaryAccent,
                    containerColor = mc.surface,
                    labelColor = mc.textSecondary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedTab == tab,
                    borderColor = mc.surfaceVariant,
                    selectedBorderColor = mc.primaryAccent
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  My List Content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MyListContent(
    uiState: TradesUiState,
    onCardClick: (String) -> Unit,
    onWishlistSelect: (String) -> Unit,
    onOfferSelect: (String) -> Unit,
) {
    var wishlistExpanded by remember { mutableStateOf(true) }
    var offersExpanded by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // ── My Wishlist section ───────────────────────────────────────────────
        item(key = "wishlist_header") {
            CollapsibleSectionHeader(
                title = stringResource(R.string.trades_wishlist_section),
                count = uiState.wishlist.size,
                expanded = wishlistExpanded,
                onToggle = { wishlistExpanded = !wishlistExpanded },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (wishlistExpanded) {
            if (uiState.wishlist.isEmpty()) {
                item(key = "wishlist_empty") {
                    Text(
                        text = stringResource(R.string.state_empty),
                        style = MaterialTheme.magicTypography.bodySmall,
                        color = MaterialTheme.magicColors.textDisabled,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(uiState.wishlist, key = { "w_${it.id}" }) { entry ->
                    WishlistEntryRow(
                        entry = entry,
                        isSelected = entry.id in uiState.selectedWishlistIds,
                        onCardClick = onCardClick,
                        onSelect = { onWishlistSelect(entry.id) },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.magicColors.surfaceVariant)
                }
            }
        }

        item(key = "spacer_1") { Spacer(Modifier.height(8.dp)) }

        // ── My Offers section ─────────────────────────────────────────────────
        item(key = "offers_header") {
            CollapsibleSectionHeader(
                title = stringResource(R.string.trades_offers_section),
                count = uiState.openForTrade.size,
                expanded = offersExpanded,
                onToggle = { offersExpanded = !offersExpanded },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (offersExpanded) {
            if (uiState.openForTrade.isEmpty()) {
                item(key = "offers_empty") {
                    Text(
                        text = stringResource(R.string.state_empty),
                        style = MaterialTheme.magicTypography.bodySmall,
                        color = MaterialTheme.magicColors.textDisabled,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(uiState.openForTrade, key = { "o_${it.id}" }) { entry ->
                    OfferEntryRow(
                        entry = entry,
                        isSelected = entry.id in uiState.selectedOfferIds,
                        onCardClick = onCardClick,
                        onSelect = { onOfferSelect(entry.id) },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.magicColors.surfaceVariant)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Friends Content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FriendsContent(
    friends: List<Friend>,
    onFriendClick: (Friend) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    if (friends.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = mc.surface,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.trades_no_friends_cta),
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(friends, key = { it.id }) { friend ->
                FriendDashboardRow(
                    friend = friend,
                    onClick = { onFriendClick(friend) },
                )
            }
        }
    }
}

@Composable
private fun FriendDashboardRow(
    friend: Friend,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = mc.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(mc.primaryAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = friend.nickname.take(1).uppercase(Locale.getDefault()),
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = mc.primaryAccent,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.nickname,
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textPrimary,
                )
                Text(
                    text = friend.gameTag,
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                )
            }
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Collapsible section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CollapsibleSectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.magicTypography.labelLarge,
            color = mc.textSecondary,
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = mc.textSecondary,
            modifier = Modifier.size(20.dp),
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
) {
    val mc = MaterialTheme.magicColors
    val card = entry.card

    CardListItem(
        name = card?.name ?: entry.cardId,
        imageUrl = card?.imageArtCrop,
        priceUsd = if (entry.isFoil == true) card?.priceUsdFoil else card?.priceUsd,
        priceEur = if (entry.isFoil == true) card?.priceEurFoil else card?.priceEur,
        quantityText = if (entry.quantity > 1) "×${entry.quantity}" else null,
        onClick = { onCardClick(entry.cardId) },
        containerColor = if (isSelected) mc.primaryAccent.copy(alpha = 0.12f) else Color.Transparent,
        hasFoil = entry.isFoil == true,
        isStale = card?.isStale ?: false,
        setCode = card?.setCode,
        setName = card?.setName,
        rarity = card?.rarity,
        extraSupportingContent = {
            val badges = buildVariantBadges(entry)
            if (badges.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    badges.forEach { label ->
                        CopyBadge(label = label)
                    }
                }
            }
        }
    )
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
) {
    val mc = MaterialTheme.magicColors
    val card = entry.card

    CardListItem(
        name = card?.name ?: entry.scryfallId,
        imageUrl = card?.imageArtCrop,
        priceUsd = if (entry.isFoil) card?.priceUsdFoil else card?.priceUsd,
        priceEur = if (entry.isFoil) card?.priceEurFoil else card?.priceEur,
        quantityText = if (entry.quantity > 1) "×${entry.quantity}" else null,
        onClick = { onCardClick(entry.scryfallId) },
        containerColor = if (isSelected) mc.primaryAccent.copy(alpha = 0.12f) else Color.Transparent,
        hasFoil = entry.isFoil,
        isStale = card?.isStale ?: false,
        setCode = card?.setCode,
        setName = card?.setName,
        rarity = card?.rarity,
        extraSupportingContent = {
            val badges = buildOfferBadges(entry)
            if (badges.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    badges.forEach { label ->
                        CopyBadge(label = label)
                    }
                }
            }
        }
    )
}

private fun buildOfferBadges(entry: OpenForTradeEntry): List<String> = buildList {
    if (entry.isFoil)     add("Foil")
    if (entry.condition.isNotBlank()) add(entry.condition)
    if (entry.language.isNotBlank())  add(entry.language)
    if (entry.isAltArt)   add("Alt art")
}
