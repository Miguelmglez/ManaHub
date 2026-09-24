package com.mmg.manahub.feature.trades.presentation

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.AvatarImage
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicFilterChip
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.PullRefreshHeader
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.components.rememberPullRefreshState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStatus
import com.mmg.manahub.core.util.TimeAgoFormatter

/** Reserves room above the bottom bar for [TradesScreen]'s FloatingActionButton, which overlays
 *  this list from the parent screen. Mirrors `TradesScreen.FabClearance`. */
private val FabClearance = 88.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradesHistoryScreen(
    onOpenThread: (proposalId: String, rootProposalId: String) -> Unit,
    onLoginClick: () -> Unit = {},
    viewModel: TradesHistoryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastState = rememberMagicToastState()

    LaunchedEffect(Unit) { viewModel.refreshIfStale() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TradesHistoryEvent.ShowMessage -> event.message?.let { toastState.show(it, MagicToastType.ERROR) }
                is TradesHistoryEvent.NavigateToThread ->
                    onOpenThread(event.proposalId, event.rootProposalId)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HistoryContent(
            uiState = uiState,
            onRefresh = viewModel::refresh,
            onFilterSelected = viewModel::onFilterSelected,
            onItemClick = viewModel::onProposalClick,
            onLoginClick = onLoginClick,
        )

        MagicToastHost(
            state    = toastState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun HistoryContent(
    uiState: TradesHistoryUiState,
    onRefresh: () -> Unit,
    onFilterSelected: (HistoryFilter) -> Unit,
    onItemClick: (TradeProposal) -> Unit,
    onLoginClick: () -> Unit,
) {
    val pullState = rememberPullRefreshState(
        isRefreshing = uiState.isRefreshing,
        onRefresh     = onRefresh,
    )
    val spacing = MaterialTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullState.nestedScrollConnection),
    ) {
        FilterRow(
            selected = uiState.filter,
            onSelect = onFilterSelected,
        )

        LazyColumn(
            modifier        = Modifier.fillMaxSize(),
            contentPadding  = PaddingValues(start = spacing.lg, top = spacing.sm, end = spacing.lg, bottom = FabClearance),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            // ── Telegram-style pull-to-refresh header ─────────────────────────
            if (pullState.headerHeightDp > 0.dp) {
                item(key = "pull_header") {
                    PullRefreshHeader(
                        height              = pullState.headerHeightDp,
                        isRefreshing        = uiState.isRefreshing,
                        dragFraction        = pullState.dragFraction,
                        refreshingText      = stringResource(R.string.trades_history_refreshing),
                        pullIcon            = Icons.Default.KeyboardArrowDown,
                        pullHintDescription = stringResource(R.string.trades_history_pull_to_refresh),
                    )
                }
            }

            // ── Main content ──────────────────────────────────────────────────
            when {
                uiState.isLoading && uiState.proposals.isEmpty() -> item(key = "loading") {
                    Box(
                        modifier        = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MagicLoadingSpinner(size = MagicLoadingSize.Medium)
                    }
                }

                uiState.refreshFailed && uiState.proposals.isEmpty() -> item(key = "error") {
                    InlineErrorState(
                        message = stringResource(R.string.trades_history_load_error),
                        retryLabel = stringResource(R.string.action_retry),
                        onRetry = onRefresh,
                        enabled = !uiState.isRefreshing,
                    )
                }

                uiState.filtered.isEmpty() -> item(key = "empty") {
                    EmptyHistory(isLoggedIn = uiState.isLoggedIn, onLoginClick = onLoginClick)
                }

                else -> items(uiState.filtered, key = { it.id }) { proposal ->
                    HistoryProposalRow(
                        proposal      = proposal,
                        currentUserId = uiState.currentUserId,
                        friends       = uiState.friends,
                        onClick       = { onItemClick(proposal) },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Filter chips
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    selected: HistoryFilter,
    onSelect: (HistoryFilter) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    LazyRow(
        contentPadding        = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        items(HistoryFilter.entries, key = { it.name }) { filter ->
            MagicFilterChip(
                selected = filter == selected,
                onClick  = { onSelect(filter) },
                label    = filter.label(),
            )
        }
    }
}

@Composable
private fun HistoryFilter.label(): String = when (this) {
    HistoryFilter.ALL       -> stringResource(R.string.trades_history_filter_all)
    HistoryFilter.ACTIVE    -> stringResource(R.string.trades_history_filter_active)
    HistoryFilter.COMPLETED -> stringResource(R.string.trades_history_filter_completed)
    HistoryFilter.DECLINED  -> stringResource(R.string.trades_history_filter_declined)
}

// ─────────────────────────────────────────────────────────────────────────────
//  History list
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryProposalRow(
    proposal: TradeProposal,
    currentUserId: String,
    friends: List<Friend>,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val isProposer = proposal.proposerId == currentUserId
    val otherPartyId = if (isProposer) proposal.receiverId else proposal.proposerId
    val otherPartyFriend = friends.find { it.userId == otherPartyId }
    // Falls back to a localized "Unknown trader" string rather than the raw counterparty UUID
    // (trades audit §3.2, 2026-07-10) — the id is an internal identifier, not user-facing text.
    val unknownTraderLabel = stringResource(R.string.trades_history_unknown_trader)
    val otherPartyLabel = otherPartyFriend?.nickname ?: unknownTraderLabel
    val otherPartyAvatarUrl = otherPartyFriend?.avatarUrl?.takeIf { it.isNotBlank() }
    val dateLabel = remember(proposal.updatedAt) { TimeAgoFormatter.format(proposal.updatedAt) }

    val isAwaitingTheirResponse = isProposer && proposal.status == TradeStatus.PROPOSED
    val isYourTurn = !isProposer && proposal.status == TradeStatus.PROPOSED

    Surface(
        onClick  = onClick,
        shape    = CardShape,
        color    = mc.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarImage(
                avatarUrl = otherPartyAvatarUrl,
                initials  = otherPartyLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                size      = 40,
            )
            Spacer(Modifier.width(spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = otherPartyLabel,
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textPrimary,
                )
                Text(
                    text  = dateLabel,
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                )
                when {
                    isAwaitingTheirResponse -> {
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text  = stringResource(R.string.trades_history_awaiting_response),
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.textSecondary,
                        )
                    }
                    isYourTurn -> {
                        Spacer(Modifier.height(spacing.xs))
                        TradeAccentBadge(
                            label  = stringResource(R.string.trades_history_your_turn),
                            accent = mc.primaryAccent,
                        )
                    }
                }
            }
            Spacer(Modifier.width(spacing.sm))
            TradeStatusBadge(status = proposal.status)
        }
    }
}

/**
 * Renders the empty state for the trade history list.
 *
 * When [isLoggedIn] is false, shows the generic trades promo with a login CTA.
 * When [isLoggedIn] is true, shows the authenticated empty-history message.
 */
@Composable
private fun EmptyHistory(
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
) {
    // EmptyState already pads itself on every side; this only pushes it below the filter row.
    val modifier = Modifier
        .fillMaxWidth()
        .padding(top = MaterialTheme.spacing.xxl)
    if (!isLoggedIn) {
        EmptyState(
            icon        = Icons.Default.SwapHoriz,
            title       = stringResource(R.string.trades_login_required_title),
            subtitle    = stringResource(R.string.trades_login_required_subtitle),
            actionLabel = stringResource(R.string.trades_login_required_action),
            onAction    = onLoginClick,
            modifier    = modifier,
        )
    } else {
        EmptyState(
            icon     = Icons.Default.SwapHoriz,
            title    = stringResource(R.string.trades_history_empty),
            subtitle = stringResource(R.string.trades_history_empty_hint),
            modifier = modifier,
        )
    }
}
