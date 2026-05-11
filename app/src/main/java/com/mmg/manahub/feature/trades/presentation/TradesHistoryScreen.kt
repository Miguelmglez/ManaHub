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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
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
    viewModel: TradesHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.onSnackbarDismissed()
    }

    LaunchedEffect(uiState.navigateToThread) {
        val nav = uiState.navigateToThread ?: return@LaunchedEffect
        onOpenThread(nav.first, nav.second)
        viewModel.onNavigationConsumed()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh    = { viewModel.refresh() },
            modifier     = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                FilterRow(
                    selected  = uiState.filter,
                    onSelect  = viewModel::onFilterSelected,
                )

                when {
                    uiState.isLoading -> Box(
                        modifier        = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.magicColors.primaryAccent)
                    }

                    uiState.filtered.isEmpty() -> EmptyHistory()

                    else -> HistoryList(
                        proposals     = uiState.filtered,
                        currentUserId = uiState.currentUserId,
                        friends       = uiState.friends,
                        onItemClick   = viewModel::onProposalClick,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    selected: HistoryFilter,
    onSelect: (HistoryFilter) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    LazyRow(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(HistoryFilter.entries) { filter ->
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

@Composable
private fun HistoryList(
    proposals: List<TradeProposal>,
    currentUserId: String,
    friends: List<Friend>,
    onItemClick: (TradeProposal) -> Unit,
) {
    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(proposals, key = { it.id }) { proposal ->
            HistoryProposalRow(
                proposal      = proposal,
                currentUserId = currentUserId,
                friends       = friends,
                onClick       = { onItemClick(proposal) },
            )
        }
    }
}

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
    val otherPartyLabel = friends.find { it.userId == otherPartyId }?.nickname ?: otherPartyId
    val dateLabel = remember(proposal.updatedAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(proposal.updatedAt))
    }

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
            Icon(
                imageVector        = Icons.Default.SwapHoriz,
                contentDescription = null,
                tint               = proposal.status.tint(mc),
                modifier           = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = otherPartyLabel,
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textPrimary,
                )
                Text(
                    text  = "${proposal.items.size} ${stringResource(R.string.trades_history_item_count)}  ·  $dateLabel",
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusBadge(status = proposal.status)
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

@Composable
private fun EmptyHistory() {
    Box(
        modifier        = Modifier
            .fillMaxSize()
            .padding(32.dp),
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
