package com.mmg.manahub.web.trades

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
import com.mmg.manahub.core.model.OpenForTradeEntry
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStatus
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
 * wave). Four tabs: Active / History (proposal negotiation lists) plus minimal read-only Wishlist /
 * Open-for-Trade lists. Tapping an Active/History row navigates into [TradeThreadScreen] via
 * [onProposalClick] (the rootProposalId). **Creating a brand-new proposal from scratch (picking a
 * friend + their open-for-trade items) -- and Countering an existing one, which needs the same
 * item-picker -- are both explicit, flagged FOLLOW-UPs** -- this slice ships the
 * respond-to-an-existing-proposal flow (accept/decline/cancel/revoke, in [TradeThreadScreen])
 * solidly first, per the same "ship what's solid, defer what's half-built"
 * discipline every prior web slice has followed. Wishlist/Open-for-Trade are read-only lists here
 * (no add/edit UI yet -- that hangs off a Card Detail "add to wishlist" affordance that doesn't
 * exist on web yet either).
 *
 * Reachable via its OWN bottom-nav-rail tab (see `WebNavGraph.kt`'s `TradesRoute`) rather than
 * hanging off the Account surface like Settings/Profile/Friends -- Trades is a primary feature, not
 * an account-adjacent one, per the task brief's explicit call.
 *
 * Trades completion slice (2026-08-05): the Active tab gained a "New proposal" button
 * ([onNewProposal]) navigating to [CreateProposalScreen] -- the single heaviest piece deferred
 * from the original Trades slice, see that screen's KDoc.
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
                subtitle = "Sign in (guest is fine) from the Account tab to see your trades, wishlist, and open-for-trade list.",
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

        if (uiState.selectedTab == TradesTab.ACTIVE || uiState.selectedTab == TradesTab.HISTORY) {
            MagicCtaButton(
                onClick = onNewProposal,
                text = "New proposal",
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
                uiState.selectedTab == TradesTab.WISHLIST -> WishlistList(uiState.wishlist)
                else -> OpenForTradeList(uiState.openForTrade)
            }
        }
    }
}

private fun TradesTab.label(): String = when (this) {
    TradesTab.ACTIVE -> "Active"
    TradesTab.HISTORY -> "History"
    TradesTab.WISHLIST -> "Wishlist"
    TradesTab.OPEN_FOR_TRADE -> "For Trade"
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
private fun WishlistList(entries: List<WishlistEntry>) {
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
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xs)) {
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
                )
            }
        }
    }
}

@Composable
private fun OpenForTradeList(entries: List<OpenForTradeEntry>) {
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
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xs)) {
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
                )
            }
        }
    }
}
