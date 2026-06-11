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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.PullRefreshHeader
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.components.rememberPullRefreshState
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.trades.domain.model.TradeProposal
import com.mmg.manahub.feature.trades.domain.model.TradeStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradesHistoryScreen(
    onOpenThread: (proposalId: String, rootProposalId: String) -> Unit,
    onLoginClick: () -> Unit = {},
    viewModel: TradesHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastState = rememberMagicToastState()

    LaunchedEffect(Unit) { viewModel.refreshIfStale() }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        toastState.show(msg)
        viewModel.onSnackbarDismissed()
    }

    LaunchedEffect(uiState.navigateToThread) {
        val nav = uiState.navigateToThread ?: return@LaunchedEffect
        onOpenThread(nav.first, nav.second)
        viewModel.onNavigationConsumed()
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
            contentPadding  = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Telegram-style pull-to-refresh header ─────────────────────────
            if (pullState.headerHeightDp > 0.dp) {
                item(key = "pull_header") {
                    PullRefreshHeader(
                        height       = pullState.headerHeightDp,
                        isRefreshing = uiState.isRefreshing,
                        dragFraction = pullState.dragFraction,
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
                        CircularProgressIndicator(
                            modifier   = Modifier.size(32.dp),
                            strokeWidth = 2.5.dp,
                            color      = MaterialTheme.magicColors.primaryAccent,
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
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
    val isProposer = proposal.proposerId == currentUserId
    val otherPartyId = if (isProposer) proposal.receiverId else proposal.proposerId
    val otherPartyFriend = friends.find { it.userId == otherPartyId }
    val otherPartyLabel = otherPartyFriend?.nickname ?: otherPartyId
    val otherPartyAvatarUrl = otherPartyFriend?.avatarUrl
    val statusTint = proposal.status.tint(mc)
    val dateLabel = remember(proposal.updatedAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(proposal.updatedAt))
    }

    val isAwaitingTheirResponse = isProposer && proposal.status == TradeStatus.PROPOSED
    val isYourTurn = !isProposer && proposal.status == TradeStatus.PROPOSED

    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = mc.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OtherPartyAvatar(
                avatarUrl  = otherPartyAvatarUrl,
                label      = otherPartyLabel,
                statusTint = statusTint,
                mc         = mc,
            )
            Spacer(Modifier.width(12.dp))
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
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text  = stringResource(R.string.trades_history_awaiting_response),
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.textDisabled,
                        )
                    }
                    isYourTurn -> {
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = mc.primaryAccent.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text     = stringResource(R.string.trades_history_your_turn),
                                style    = MaterialTheme.magicTypography.labelSmall,
                                color    = mc.primaryAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
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
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = status.tint(mc).copy(alpha = 0.15f),
    ) {
        Text(
            text     = status.label(),
            style    = MaterialTheme.magicTypography.labelSmall,
            color    = status.tint(mc),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
    if (!isLoggedIn) {
        EmptyState(
            icon        = Icons.Default.SwapHoriz,
            title       = stringResource(R.string.trades_login_required_title),
            subtitle    = stringResource(R.string.trades_login_required_subtitle),
            actionLabel = stringResource(R.string.trades_login_required_action),
            onAction    = onLoginClick,
            modifier    = Modifier
                .fillMaxWidth()
                .padding(top = 64.dp, start = 32.dp, end = 32.dp),
        )
    } else {
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .padding(top = 64.dp, start = 32.dp, end = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text      = stringResource(R.string.trades_history_empty),
                    style     = MaterialTheme.magicTypography.bodyMedium,
                    color     = MaterialTheme.magicColors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
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
