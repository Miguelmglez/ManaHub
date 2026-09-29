package com.mmg.manahub.feature.friends.presentation.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStatus
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** History tab on the friend detail screen: the trade proposals shared with [friend]. */
@Composable
fun FriendHistoryTab(
    friend: Friend,
    tradeHistory: List<TradeProposal>,
    onTradeClick: (proposalId: String, rootProposalId: String) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    if (tradeHistory.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.friend_history_trades_empty, friend.nickname),
            icon = Icons.Default.SwapHoriz,
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        ) {
            items(tradeHistory, key = { it.id }) { proposal ->
                TradeHistoryRow(proposal = proposal, onTradeClick = onTradeClick)
            }
        }
    }
}

/**
 * A single row in the trade history list.
 *
 * Displays:
 * - A color-coded status badge ([TradeStatus] label).
 * - The date the proposal was last updated, formatted as "dd MMM yyyy".
 * - A card count summary: "X cards offered / Y cards received", where offered/received
 *   is determined by [TradeItem.fromUserId] relative to [TradeProposal.proposerId].
 *
 * @param proposal The trade proposal to render.
 */
@Composable
private fun TradeHistoryRow(
    proposal: TradeProposal,
    onTradeClick: (proposalId: String, rootProposalId: String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    val statusColor: Color = when (proposal.status) {
        TradeStatus.COMPLETED -> mc.lifePositive
        TradeStatus.CANCELLED, TradeStatus.DECLINED, TradeStatus.REVOKED -> mc.lifeNegative
        else -> mc.textSecondary
    }

    val statusLabel = when (proposal.status) {
        TradeStatus.DRAFT -> stringResource(R.string.trade_status_draft)
        TradeStatus.PROPOSED -> stringResource(R.string.trade_status_proposed)
        TradeStatus.ACCEPTED -> stringResource(R.string.trade_status_accepted)
        TradeStatus.COMPLETED -> stringResource(R.string.trade_status_completed)
        TradeStatus.DECLINED -> stringResource(R.string.trade_status_declined)
        TradeStatus.CANCELLED -> stringResource(R.string.trade_status_cancelled)
        TradeStatus.COUNTERED -> stringResource(R.string.trade_status_countered)
        TradeStatus.REVOKED -> stringResource(R.string.trade_status_revoked)
    }

    val dateFormatted = remember(proposal.updatedAt) {
        val local = Instant.fromEpochMilliseconds(proposal.updatedAt)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val month = local.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        val day = local.dayOfMonth.toString().padStart(2, '0')
        "$day $month ${local.year}"
    }

    // Items offered = cards moving FROM the proposer (proposerId is the sender side).
    // Items received = cards moving TO the proposer (i.e., FROM the receiver).
    val offeredCount = proposal.items.count { it.fromUserId == proposal.proposerId }
    val receivedCount = proposal.items.count { it.fromUserId == proposal.receiverId }

    Surface(
        shape = CardShape,
        color = mc.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onTradeClick(proposal.id, proposal.rootProposalId) },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Status badge
                Surface(
                    shape = ChipShape,
                    color = statusColor.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = statusLabel,
                        style = mt.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                    )
                }
                Spacer(modifier = Modifier.width(spacing.sm))
                Text(
                    text = stringResource(R.string.friend_history_trade_row_updated, dateFormatted),
                    style = mt.bodySmall,
                    color = mc.textSecondary,
                )
            }
            Text(
                text = stringResource(
                    R.string.friend_history_trade_row_cards_offered_received,
                    offeredCount,
                    receivedCount,
                ),
                style = mt.bodySmall,
                color = mc.textPrimary,
            )
        }
    }
}
