package com.mmg.manahub.feature.friends.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.trades.domain.model.TradeProposal
import com.mmg.manahub.feature.trades.domain.model.TradeStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * History tab on the friend detail screen.
 *
 * Shows two sub-tabs:
 * - **Trades**: a list of past [TradeProposal]s shared with this friend, filtered
 *   and provided by [FriendDetailViewModel]. Empty state shown when the list is empty.
 * - **Games**: a "coming soon" placeholder.
 *
 * @param friend       The friend whose history is being displayed.
 * @param tradeHistory Trade proposals already filtered to only include proposals
 *                     between the current user and [friend].
 */
@Composable
fun FriendHistoryTab(
    friend: Friend,
    tradeHistory: List<TradeProposal>,
) {
    var selectedIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedIndex,
            containerColor = MaterialTheme.magicColors.backgroundSecondary,
            contentColor = MaterialTheme.magicColors.primaryAccent,
        ) {
            Tab(
                selected = selectedIndex == 0,
                onClick = { selectedIndex = 0 },
                text = {
                    Text(
                        stringResource(R.string.friend_history_tab_trades),
                        style = MaterialTheme.magicTypography.labelLarge,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                },
            )
            Tab(
                selected = selectedIndex == 1,
                onClick = { selectedIndex = 1 },
                text = {
                    Text(
                        stringResource(R.string.friend_history_tab_games),
                        style = MaterialTheme.magicTypography.labelLarge,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                },
            )
        }

        when (selectedIndex) {
            0 -> TradesHistoryContent(
                friend = friend,
                tradeHistory = tradeHistory,
            )
            else -> GamesComingSoonContent()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Private composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders the trades sub-tab content.
 *
 * Shows a [LazyColumn] of [TradeHistoryRow]s when [tradeHistory] is non-empty,
 * or an empty-state message otherwise.
 */
@Composable
private fun TradesHistoryContent(
    friend: Friend,
    tradeHistory: List<TradeProposal>,
) {
    if (tradeHistory.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.friend_history_trades_empty, friend.nickname),
                style = MaterialTheme.magicTypography.bodySmall,
                color = MaterialTheme.magicColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
        ) {
            items(tradeHistory, key = { it.id }) { proposal ->
                TradeHistoryRow(proposal = proposal)
            }
        }
    }
}

/** Centered placeholder for the Games sub-tab. */
@Composable
private fun GamesComingSoonContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.friend_history_games_coming_soon),
            style = MaterialTheme.magicTypography.bodySmall,
            color = MaterialTheme.magicColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
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
private fun TradeHistoryRow(proposal: TradeProposal) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography

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
        val formatter = DateTimeFormatter
            .ofPattern("dd MMM yyyy", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault())
        formatter.format(Instant.ofEpochMilli(proposal.updatedAt))
    }

    // Items offered = cards moving FROM the proposer (proposerId is the sender side).
    // Items received = cards moving TO the proposer (i.e., FROM the receiver).
    val offeredCount = proposal.items.count { it.fromUserId == proposal.proposerId }
    val receivedCount = proposal.items.count { it.fromUserId == proposal.receiverId }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = mc.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Status badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = statusLabel,
                        style = mt.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.friend_history_trade_row_updated, dateFormatted),
                    style = mt.bodySmall,
                    color = mc.textSecondary,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
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
