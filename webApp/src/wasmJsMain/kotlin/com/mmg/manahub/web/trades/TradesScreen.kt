package com.mmg.manahub.web.trades

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.OpenForTradeEntry
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStatus
import com.mmg.manahub.core.model.TradeSuggestion
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * Trades hub -- the web scope expansion's Trades slice (approved 2026-08-04, Friends + Trades
 * wave). Four tabs: Active / History (proposal negotiation lists) and Wishlist / Open-for-Trade.
 * Tapping an Active/History row navigates into [TradeThreadScreen] via [onProposalClick] (the
 * rootProposalId).
 *
 * Reachable via its OWN bottom-nav-rail tab (see `WebNavGraph.kt`'s `TradesRoute`) rather than
 * hanging off the Account surface like Settings/Profile/Friends -- Trades is a primary feature, not
 * an account-adjacent one, per the task brief's explicit call.
 *
 * Trades completion slice (2026-08-05): every tab now has a real add-affordance sharing ONE
 * top-of-screen button whose label/action swaps per [TradesTab] -- "New proposal" (Active/History,
 * navigates to [CreateProposalScreen] via [onNewProposal]), "Add to wishlist" (a Scryfall search
 * dialog, any card), and "Add card" (Open-for-Trade -- a picker over the caller's OWN collection
 * via [com.mmg.manahub.core.domain.repository.UserCardRepository.observeCollection], since you can
 * only offer what you own -- reuses [CollectionPickerDialog]/[MyCollectionRow] from
 * [CreateProposalScreen]). Both lists also gained a Remove button per row. A fifth
 * [TradesTab.SUGGESTIONS] tab was also added -- a read-only, informational list (no add button;
 * see [TradesViewModel.loadSuggestions]'s KDoc for why).
 */
@Composable
fun TradesScreen(onProposalClick: (String) -> Unit, onNewProposal: () -> Unit = {}) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val viewModel = koinViewModel<TradesViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(vertical = spacing.lg)) {
        Text(
            text = "Trades",
            style = typography.titleLarge,
            color = colors.textPrimary,
            modifier = Modifier.padding(bottom = spacing.md),
        )

        if (!uiState.isSignedIn) {
            EmptyState(
                title = "Sign in to trade",
                subtitle = "Sign in from the Account tab to see your trades, wishlist, and open-for-trade list.",
            )
            return@Column
        }

        val tabs = TradesTab.entries.toList()
        TabRow(
            selectedTabIndex = tabs.indexOf(uiState.selectedTab),
            containerColor = colors.background,
            contentColor = colors.primaryAccent,
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = uiState.selectedTab == tab,
                    onClick = { viewModel.onTabSelected(tab) },
                    text = {
                        Text(
                            text = tab.label(),
                            style = typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }

        if (uiState.error != null) {
            Text(
                text = uiState.error.orEmpty(),
                style = typography.bodyMedium,
                color = colors.lifeNegative,
                modifier = Modifier.padding(top = spacing.sm),
            )
        }

        // SUGGESTIONS is read-only (no add affordance) -- see TradesViewModel.loadSuggestions' KDoc.
        val topButton = when (uiState.selectedTab) {
            TradesTab.ACTIVE, TradesTab.HISTORY -> "New proposal" to onNewProposal
            TradesTab.WISHLIST -> "Add to wishlist" to viewModel::openWishlistSheet
            TradesTab.OPEN_FOR_TRADE -> "Add card" to viewModel::openOpenForTradeSheet
            TradesTab.SUGGESTIONS -> null
        }
        if (topButton != null) {
            val (topButtonLabel, topButtonAction) = topButton
            MagicCtaButton(
                onClick = topButtonAction,
                text = topButtonLabel,
                style = MagicCtaStyle.Filled,
                color = MagicCtaColor.Primary,
                modifier = Modifier.fillMaxWidth().padding(top = spacing.md),
            )
        }

        Box(modifier = Modifier.fillMaxSize().padding(top = spacing.md)) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primaryAccent)
                }
                uiState.selectedTab == TradesTab.ACTIVE -> ProposalList(
                    proposals = uiState.activeProposals,
                    currentUserId = uiState.currentUserId,
                    participantNames = uiState.participantNames,
                    emptyTitle = "No active trades",
                    emptySubtitle = "Proposals you send or receive from friends will show up here.",
                    onProposalClick = onProposalClick,
                )
                uiState.selectedTab == TradesTab.HISTORY -> ProposalList(
                    proposals = uiState.historyProposals,
                    currentUserId = uiState.currentUserId,
                    participantNames = uiState.participantNames,
                    emptyTitle = "No trade history yet",
                    emptySubtitle = "Completed, declined, or cancelled trades will show up here.",
                    onProposalClick = onProposalClick,
                )
                uiState.selectedTab == TradesTab.WISHLIST -> WishlistList(
                    entries = uiState.wishlist,
                    onRemove = viewModel::removeWishlistEntry,
                )
                uiState.selectedTab == TradesTab.OPEN_FOR_TRADE -> OpenForTradeList(
                    entries = uiState.openForTrade,
                    onRemove = viewModel::removeOpenForTradeEntry,
                )
                else -> SuggestionsList(
                    isLoading = uiState.isLoadingSuggestions,
                    suggestions = uiState.suggestions,
                    currentUserId = uiState.currentUserId,
                    participantNames = uiState.participantNames,
                    cards = uiState.suggestionCards,
                )
            }
        }
    }

    if (uiState.isWishlistSheetOpen) {
        CollectionPickerDialog(
            title = "Add to wishlist",
            query = uiState.wishlistSearchQuery,
            onQueryChange = viewModel::onWishlistSearchQueryChanged,
            isLoading = uiState.isSearchingWishlist,
            cards = uiState.wishlistSearchResults,
            rowContent = { card -> ScryfallSearchRow(card = card, onAdd = { viewModel.addToWishlist(card) }) },
            key = { it.scryfallId },
            onDismiss = viewModel::closeWishlistSheet,
        )
    }

    if (uiState.isOpenForTradeSheetOpen) {
        CollectionPickerDialog(
            title = "Mark a card open for trade",
            query = uiState.openForTradeQuery,
            onQueryChange = viewModel::onOpenForTradeQueryChanged,
            isLoading = false,
            cards = uiState.myCollection.filter { uiState.openForTradeQuery.isBlank() || it.card.name.contains(uiState.openForTradeQuery, ignoreCase = true) },
            rowContent = { userCard -> MyCollectionRow(userCard = userCard, onAdd = { viewModel.addToOpenForTrade(userCard) }) },
            key = { it.userCard.id },
            onDismiss = viewModel::closeOpenForTradeSheet,
        )
    }
}

private fun TradesTab.label(): String = when (this) {
    TradesTab.ACTIVE -> "Active"
    TradesTab.HISTORY -> "History"
    TradesTab.WISHLIST -> "Wishlist"
    TradesTab.OPEN_FOR_TRADE -> "For Trade"
    TradesTab.SUGGESTIONS -> "Suggestions"
}

@Composable
private fun ProposalList(
    proposals: List<TradeProposal>,
    currentUserId: String,
    participantNames: Map<String, String>,
    emptyTitle: String,
    emptySubtitle: String,
    onProposalClick: (String) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    if (proposals.isEmpty()) {
        EmptyState(title = emptyTitle, subtitle = emptySubtitle)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        items(proposals, key = { it.id }) { proposal ->
            ProposalRow(
                proposal = proposal,
                currentUserId = currentUserId,
                participantNames = participantNames,
                onClick = { onProposalClick(proposal.rootProposalId) },
            )
        }
    }
}

@Composable
private fun ProposalRow(
    proposal: TradeProposal,
    currentUserId: String,
    participantNames: Map<String, String>,
    onClick: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    val counterpartyId = if (proposal.proposerId == currentUserId) proposal.receiverId else proposal.proposerId
    val counterpartyName = participantNames[counterpartyId] ?: counterpartyId.take(8)
    val youSend = proposal.items.count { it.fromUserId == currentUserId && !it.isReviewCollectionPlaceholder }
    val youReceive = proposal.items.count { it.toUserId == currentUserId && !it.isReviewCollectionPlaceholder }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.backgroundSecondary,
        shape = RoundedCornerShape(spacing.sm),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(
                    text = "Trade with $counterpartyName",
                    style = typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "You send $youSend · You receive $youReceive",
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            StatusBadge(status = proposal.status)
        }
    }
}

@Composable
internal fun StatusBadge(status: TradeStatus) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    val (label, color) = when (status) {
        TradeStatus.DRAFT -> "Draft" to colors.textSecondary
        TradeStatus.PROPOSED -> "Proposed" to colors.primaryAccent
        TradeStatus.COUNTERED -> "Countered" to colors.goldMtg
        TradeStatus.ACCEPTED -> "Accepted" to colors.lifePositive
        TradeStatus.COMPLETED -> "Completed" to colors.lifePositive
        TradeStatus.CANCELLED -> "Cancelled" to colors.textDisabled
        TradeStatus.DECLINED -> "Declined" to colors.lifeNegative
        TradeStatus.REVOKED -> "Revoked" to colors.lifeNegative
    }
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(spacing.xs)) {
        Text(
            text = label,
            style = typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
        )
    }
}

@Composable
private fun WishlistList(entries: List<WishlistEntry>, onRemove: (String) -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    if (entries.isEmpty()) {
        EmptyState(
            title = "Your wishlist is empty",
            subtitle = "Cards you want to acquire via trade will show up here.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        items(entries, key = { it.id }) { entry ->
            // MagicCtaButton's internal fillMaxWidth() hogs the row when placed unweighted next to
            // a weight(1f) sibling (same bug documented on PickerRow above -- caught live during
            // Trades completion verification on THIS exact row: the name/detail text collapsed to
            // nothing). Both children need an explicit weight.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Column(modifier = Modifier.weight(2f)) {
                    Text(
                        text = entry.card?.name ?: entry.cardId,
                        style = typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append("×${entry.quantity}")
                            if (entry.isFoil) append(" · Foil")
                            if (entry.matchAnyVariant) append(" · Any printing")
                        },
                        style = typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MagicCtaButton(
                    onClick = { onRemove(entry.id) },
                    text = "Remove",
                    style = MagicCtaStyle.Outlined,
                    color = MagicCtaColor.Error,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OpenForTradeList(entries: List<OpenForTradeEntry>, onRemove: (OpenForTradeEntry) -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    if (entries.isEmpty()) {
        EmptyState(
            title = "Nothing open for trade",
            subtitle = "Mark cards in your collection as open for trade to list them here.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        items(entries, key = { it.id }) { entry ->
            // Same MagicCtaButton unweighted-sibling fix as WishlistList above.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Column(modifier = Modifier.weight(2f)) {
                    Text(
                        text = entry.card?.name ?: entry.scryfallId,
                        style = typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "×${entry.quantity}${if (entry.isFoil) " · Foil" else ""}",
                        style = typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MagicCtaButton(
                    onClick = { onRemove(entry) },
                    text = "Remove",
                    style = MagicCtaStyle.Outlined,
                    color = MagicCtaColor.Error,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ScryfallSearchRow(card: Card, onAdd: () -> Unit) {
    PickerRow(
        name = card.name,
        imageUrl = card.imageArtCrop ?: card.imageNormal,
        detail = card.setName,
        onAdd = onAdd,
    )
}

/**
 * Trade Suggestions -- a read-only, informational list (no action button; see
 * [TradesViewModel.loadSuggestions]'s KDoc for why an unwired "propose from this" button would
 * violate the no-stub rule). Each row states which direction the match runs relative to the
 * current user: [TradeSuggestion.offeringUserId] == me means I have something a counterparty
 * wants; [TradeSuggestion.wishingUserId] == me means a counterparty has something I want.
 */
@Composable
private fun SuggestionsList(
    isLoading: Boolean,
    suggestions: List<TradeSuggestion>,
    currentUserId: String,
    participantNames: Map<String, String>,
    cards: Map<String, Card>,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.primaryAccent)
        }
        suggestions.isEmpty() -> EmptyState(
            title = "No suggestions yet",
            subtitle = "Matches between your wishlist/offers and your friends' will show up here.",
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            items(
                suggestions,
                key = { "${it.wishingUserId}_${it.offeringUserId}_${it.cardId}_${it.isFoilKey()}" },
            ) { suggestion ->
                SuggestionRow(
                    suggestion = suggestion,
                    currentUserId = currentUserId,
                    participantNames = participantNames,
                    card = cards[suggestion.cardId],
                )
            }
        }
    }
}

private fun TradeSuggestion.isFoilKey() = "${offerFoil}_${offerCondition}_${offerLanguage}"

@Composable
private fun SuggestionRow(
    suggestion: TradeSuggestion,
    currentUserId: String,
    participantNames: Map<String, String>,
    card: Card?,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    val cardName = card?.name ?: suggestion.cardId
    val iAmOffering = suggestion.offeringUserId == currentUserId
    val counterpartyId = if (iAmOffering) suggestion.wishingUserId else suggestion.offeringUserId
    val counterpartyName = participantNames[counterpartyId] ?: counterpartyId.take(8)
    val headline = if (iAmOffering) "You have $cardName -- $counterpartyName wants it" else "$counterpartyName has $cardName -- you want it"

    Column(
        modifier = Modifier.fillMaxWidth().background(colors.backgroundSecondary, RoundedCornerShape(spacing.xs)).padding(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(text = headline, style = typography.bodyMedium, color = colors.textPrimary)
        Text(
            text = "${if (suggestion.offerFoil) "Foil · " else ""}${suggestion.offerCondition} · ${suggestion.offerLanguage}",
            style = typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}
