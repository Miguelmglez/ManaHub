package com.mmg.manahub.feature.trades.presentation

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.PullRefreshHeader
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.components.rememberPullRefreshState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
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
                is TradesHistoryEvent.ShowMessage -> event.message?.let { toastState.show(it) }
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
                uiState.isLoading -> item(key = "loading") {
                    Box(
                        modifier        = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MagicLoadingSpinner(
                            modifier   = Modifier.size(32.dp),
                        )
                    }
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
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    LazyRow(
        contentPadding        = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        items(HistoryFilter.entries, key = { it.name }) { filter ->
            FilterChip(
                selected = filter == selected,
                onClick  = { onSelect(filter) },
                label    = {
                    Text(
                        text  = filter.label(),
                        style = MaterialTheme.magicTypography.labelSmall,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = mc.primaryAccent.copy(alpha = 0.15f),
                    selectedLabelColor     = mc.primaryAccent,
                    containerColor         = mc.surface,
                    labelColor             = mc.textSecondary,
                ),
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
    val otherPartyAvatarUrl = otherPartyFriend?.avatarUrl
    val statusTint = proposal.status.tint(mc)
    val dateLabel = remember(proposal.updatedAt) { TimeAgoFormatter.format(proposal.updatedAt) }

    val isAwaitingTheirResponse = isProposer && proposal.status == TradeStatus.PROPOSED
    val isYourTurn = !isProposer && proposal.status == TradeStatus.PROPOSED

    Surface(
        shape    = CardShape,
        color    = mc.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OtherPartyAvatar(
                avatarUrl  = otherPartyAvatarUrl,
                label      = otherPartyLabel,
                statusTint = statusTint,
                mc         = mc,
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
                            color = mc.textDisabled,
                        )
                    }
                    isYourTurn -> {
                        Spacer(Modifier.height(spacing.xs))
                        Surface(
                            shape = ChipShape,
                            color = mc.primaryAccent.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text     = stringResource(R.string.trades_history_your_turn),
                                style    = MaterialTheme.magicTypography.labelSmall,
                                color    = mc.primaryAccent,
                                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(spacing.sm))
            StatusBadge(status = proposal.status)
        }
    }
}

@Composable
private fun OtherPartyAvatar(
    avatarUrl:  String?,
    label:      String,
    statusTint: androidx.compose.ui.graphics.Color,
    mc:         com.mmg.manahub.core.ui.theme.MagicColors,
) {
    val avatarModifier = Modifier
        .size(40.dp)
        .clip(CircleShape)

    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model              = avatarUrl,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = avatarModifier,
        )
    } else {
        Surface(
            shape    = CircleShape,
            color    = mc.backgroundSecondary,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text  = label.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.magicTypography.labelLarge,
                    color = mc.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: TradeStatus) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    Surface(
        shape = ChipShape,
        color = status.tint(mc).copy(alpha = 0.15f),
    ) {
        Text(
            text     = status.label(),
            style    = MaterialTheme.magicTypography.labelSmall,
            color    = status.tint(mc),
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
        )
    }
}

@Composable
private fun TradeStatus.label(): String = when (this) {
    TradeStatus.COMPLETED -> stringResource(R.string.trade_status_completed)
    TradeStatus.CANCELLED -> stringResource(R.string.trade_status_cancelled)
    TradeStatus.DECLINED  -> stringResource(R.string.trade_status_declined)
    TradeStatus.COUNTERED -> stringResource(R.string.trade_status_countered)
    TradeStatus.ACCEPTED  -> stringResource(R.string.trade_status_accepted)
    TradeStatus.PROPOSED  -> stringResource(R.string.trade_status_proposed)
    TradeStatus.REVOKED   -> stringResource(R.string.trade_status_revoked)
    TradeStatus.DRAFT     -> stringResource(R.string.trade_status_draft)
}

@Composable
private fun TradeStatus.tint(mc: com.mmg.manahub.core.ui.theme.MagicColors) = when (this) {
    TradeStatus.COMPLETED -> mc.lifePositive
    TradeStatus.ACCEPTED  -> mc.primaryAccent
    TradeStatus.CANCELLED,
    TradeStatus.REVOKED   -> mc.lifeNegative
    TradeStatus.DECLINED  -> mc.goldMtg
    TradeStatus.COUNTERED -> mc.secondaryAccent
    else                  -> mc.textSecondary
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
    val spacing = MaterialTheme.spacing
    if (!isLoggedIn) {
        EmptyState(
            icon        = Icons.Default.SwapHoriz,
            title       = stringResource(R.string.trades_login_required_title),
            subtitle    = stringResource(R.string.trades_login_required_subtitle),
            actionLabel = stringResource(R.string.trades_login_required_action),
            onAction    = onLoginClick,
            modifier    = Modifier
                .fillMaxWidth()
                .padding(top = spacing.xxl * 2, start = spacing.xxl, end = spacing.xxl),
        )
    } else {
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .padding(top = spacing.xxl * 2, start = spacing.xxl, end = spacing.xxl),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text      = stringResource(R.string.trades_history_empty),
                    style     = MaterialTheme.magicTypography.bodyMedium,
                    color     = MaterialTheme.magicColors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text      = stringResource(R.string.trades_history_empty_hint),
                    style     = MaterialTheme.magicTypography.labelSmall,
                    color     = MaterialTheme.magicColors.textDisabled,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
