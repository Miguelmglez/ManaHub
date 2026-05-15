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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.friends.domain.model.FriendStats

/**
 * Displays the friend's collection statistics inside the Stats tab of [FriendDetailScreen].
 *
 * Three rendering states:
 * 1. Loading — shows a centred [CircularProgressIndicator].
 * 2. Error   — shows an error message and a retry button.
 * 3. Loaded  — shows a [Column] of labeled stat rows inside a [Card].
 *              If the server returned no row ([UiState.friendStats] is null after a
 *              successful fetch) a "no data yet" message is shown instead.
 *
 * @param uiState  Current [FriendDetailViewModel.UiState] snapshot.
 * @param onRetry  Callback for the retry button; routes to [FriendDetailViewModel.retryStats].
 */
@Composable
fun FriendStatsTab(
    uiState: FriendDetailViewModel.UiState,
    onRetry: () -> Unit,
) {
    val mc = MaterialTheme.magicColors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        when {
            uiState.isLoadingStats -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = mc.primaryAccent)
                }
            }

            uiState.statsError -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.friend_stats_error),
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = mc.primaryAccent,
                            contentColor = mc.background,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.action_retry),
                            style = MaterialTheme.magicTypography.labelLarge,
                        )
                    }
                }
            }

            uiState.friendStats == null -> {
                val nickname = uiState.friend?.nickname ?: ""
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.friend_stats_no_data, nickname),
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }

            else -> StatsContent(stats = uiState.friendStats)
        }
    }
}

/**
 * Renders the actual stats rows when data is available.
 *
 * Uses a scrollable [Column] so that the card fits on small screens without
 * truncating any rows.
 */
@Composable
private fun StatsContent(stats: FriendStats) {
    val mc = MaterialTheme.magicColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = mc.backgroundSecondary),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                StatRow(
                    label = stringResource(R.string.friend_stats_total_cards),
                    value = stats.totalCards.toString(),
                )
                StatDivider()
                StatRow(
                    label = stringResource(R.string.friend_stats_unique_cards),
                    value = stats.uniqueCards.toString(),
                )
                StatDivider()
                StatRow(
                    label = stringResource(R.string.friend_stats_value_eur),
                    value = "€ %.2f".format(stats.totalValueEur),
                )
                StatDivider()
                StatRow(
                    label = stringResource(R.string.friend_stats_value_usd),
                    value = "$ %.2f".format(stats.totalValueUsd),
                )
                if (stats.favouriteColor != null) {
                    StatDivider()
                    StatRow(
                        label = stringResource(R.string.friend_stats_favourite_color),
                        value = stats.favouriteColor,
                    )
                }
                if (stats.mostValuableColor != null) {
                    StatDivider()
                    StatRow(
                        label = stringResource(R.string.friend_stats_most_valuable_color),
                        value = stats.mostValuableColor,
                    )
                }
            }
        }
    }
}

/**
 * A single labeled value row used inside [StatsContent].
 */
@Composable
private fun StatRow(label: String, value: String) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.magicTypography.bodyMedium,
            color = mc.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.magicTypography.bodyMedium,
            color = mc.textPrimary,
        )
    }
}

/** Thin visual separator between stat rows. */
@Composable
private fun StatDivider() {
    HorizontalDivider(
        color = MaterialTheme.magicColors.surface.copy(alpha = 0.6f),
        thickness = 0.5.dp,
    )
}
