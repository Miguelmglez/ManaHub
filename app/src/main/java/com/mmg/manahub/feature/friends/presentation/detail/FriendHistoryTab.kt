package com.mmg.manahub.feature.friends.presentation.detail

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.FriendMatchHistory
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * History tab on the friend detail screen.
 *
 * Shows two sub-tabs:
 * - **Trades**: a list of past [TradeProposal]s shared with this friend.
 * - **Games**: aggregate match history from online sessions against this friend.
 *
 * @param friend              The friend whose history is being displayed.
 * @param tradeHistory        Trade proposals filtered to only include proposals between the
 *                            current user and [friend].
 * @param gameHistory         Aggregate win/loss record; null while loading or if never played.
 * @param isLoadingGameHistory True while the game history request is in flight.
 * @param gameHistoryError    True when the last game history request failed.
 * @param onRetryGameHistory  Called when the user taps Retry after a game history error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendHistoryTab(
    friend: Friend,
    tradeHistory: List<TradeProposal>,
    onTradeClick: (proposalId: String, rootProposalId: String) -> Unit,
    gameHistory: FriendMatchHistory? = null,
    isLoadingGameHistory: Boolean = false,
    gameHistoryError: Boolean = false,
    onRetryGameHistory: () -> Unit = {},
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val mc = MaterialTheme.magicColors

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Trades Tab
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = selectedIndex == 0,
                onClick = { selectedIndex = 0 },
                label = {
                    Text(
                        stringResource(R.string.friend_history_tab_trades),
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
                    selected = selectedIndex == 0,
                    borderColor = mc.surfaceVariant,
                    selectedBorderColor = mc.primaryAccent
                )
            )

            // Games Tab
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = selectedIndex == 1,
                onClick = { selectedIndex = 1 },
                label = {
                    Text(
                        stringResource(R.string.friend_history_tab_games),
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
                    selected = selectedIndex == 1,
                    borderColor = mc.surfaceVariant,
                    selectedBorderColor = mc.primaryAccent
                )
            )
        }

        when (selectedIndex) {
            0 -> TradesHistoryContent(
                friend = friend,
                tradeHistory = tradeHistory,
                onTradeClick = onTradeClick,
            )
            else -> GamesHistoryContent(
                friend = friend,
                gameHistory = gameHistory,
                isLoading = isLoadingGameHistory,
                hasError = gameHistoryError,
                onRetry = onRetryGameHistory,
            )
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
    onTradeClick: (proposalId: String, rootProposalId: String) -> Unit,
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            items(tradeHistory, key = { it.id }) { proposal ->
                TradeHistoryRow(
                    proposal = proposal,
                    onTradeClick = onTradeClick,
                )
            }
        }
    }
}

/**
 * Games sub-tab showing aggregate win/loss record against this friend.
 *
 * States:
 * - Loading: spinner while the RPC call is in flight.
 * - Error: message + Retry button when the call failed.
 * - No games: empty-state text when totalGames == 0.
 * - Data: a summary card showing wins, losses, and last played date.
 */
@Composable
private fun GamesHistoryContent(
    friend: Friend,
    gameHistory: FriendMatchHistory?,
    isLoading: Boolean,
    hasError: Boolean,
    onRetry: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = mc.primaryAccent, modifier = Modifier.size(32.dp))
                }
            }

            hasError -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.friend_history_games_error),
                        style = mt.bodySmall,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onRetry) {
                        Text(
                            text = stringResource(R.string.retry),
                            color = mc.primaryAccent,
                            style = mt.labelMedium,
                        )
                    }
                }
            }

            gameHistory == null || gameHistory.totalGames == 0 -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.friend_history_games_empty, friend.nickname),
                        style = mt.bodySmall,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }

            else -> {
                MatchHistoryCard(friend = friend, history = gameHistory)
            }
        }
    }
}

@Composable
private fun MatchHistoryCard(
    friend: Friend,
    history: FriendMatchHistory,
) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography

    val lastPlayedFormatted = remember(history.lastPlayedAt) {
        if (history.lastPlayedAt == 0L) null
        else {
            val local = Instant.fromEpochMilliseconds(history.lastPlayedAt)
                .toLocalDateTime(TimeZone.currentSystemDefault())
            val month = local.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val day = local.dayOfMonth.toString().padStart(2, '0')
            "$day $month ${local.year}"
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = mc.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.friend_history_games_title, history.totalGames),
                style = mt.titleMedium,
                color = mc.textPrimary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                WinLossStat(
                    label = stringResource(R.string.friend_history_games_wins),
                    value = history.myWins,
                    color = mc.lifePositive,
                )
                WinLossStat(
                    label = stringResource(R.string.friend_history_games_losses),
                    value = history.opponentWins,
                    color = mc.lifeNegative,
                )
                WinLossStat(
                    label = stringResource(R.string.friend_history_games_winrate),
                    value = if (history.totalGames > 0)
                        (history.myWins * 100 / history.totalGames)
                    else 0,
                    suffix = "%",
                    color = mc.primaryAccent,
                )
            }

            if (lastPlayedFormatted != null) {
                Text(
                    text = stringResource(R.string.friend_history_games_last_played, lastPlayedFormatted),
                    style = mt.bodySmall,
                    color = mc.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun WinLossStat(
    label: String,
    value: Int,
    color: Color,
    suffix: String = "",
) {
    val mt = MaterialTheme.magicTypography
    val mc = MaterialTheme.magicColors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value$suffix",
            style = mt.titleLarge,
            color = color,
        )
        Text(
            text = label,
            style = mt.labelSmall,
            color = mc.textSecondary,
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
private fun TradeHistoryRow(
    proposal: TradeProposal,
    onTradeClick: (proposalId: String, rootProposalId: String) -> Unit,
) {
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
        shape = RoundedCornerShape(8.dp),
        color = mc.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTradeClick(proposal.id, proposal.rootProposalId) },
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
