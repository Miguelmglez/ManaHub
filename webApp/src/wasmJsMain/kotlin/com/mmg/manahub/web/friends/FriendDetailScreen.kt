package com.mmg.manahub.web.friends

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.model.FriendCard
import com.mmg.manahub.core.model.FriendMatchHistory
import com.mmg.manahub.core.model.FriendStats
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.ui.components.CardListItem
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.PriceFormatter
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Friend Detail -- web scope expansion, Friends completion slice (approved 2026-08-05). The friend-
 * detail view deferred from the original Friends slice (`project_w_friends_slice.md`): a destination
 * you navigate INTO from [FriendsScreen]'s friend rows, never a top-level nav item, so it carries
 * no `AdaptiveNavItem` entry in `WebNavGraph`.
 *
 * Surfaces [FriendRepository.getFriendCollection][com.mmg.manahub.core.domain.repository.FriendRepository.getFriendCollection]
 * (Collection tab), `getFriendStats` (Stats tab), and `getFriendMatchHistory` (History tab) -- the
 * three [WebFriendRepository][com.mmg.manahub.core.data.repository.WebFriendRepository] methods that
 * had zero UI consumers before this slice. See [FriendDetailViewModel]'s KDoc for the deliberate MVP
 * scope cuts vs. Android's own `FriendDetailScreen`/`FriendDetailViewModel` (single collection list,
 * no sub-tabs/filters, manual "Load more" instead of scroll pagination).
 */
@Composable
fun FriendDetailScreen(friendUserId: String, onBack: () -> Unit, onCardClick: (String) -> Unit = {}) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    // Keyed on friendUserId per the CardDetailScreen/DeckEditorScreen precedent -- this call site
    // is a real nav destination (fresh NavBackStackEntry per navigate()).
    val viewModel = koinViewModel<FriendDetailViewModel>(key = friendUserId) { parametersOf(friendUserId) }
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                )
            }
            val friend = uiState.friend
            if (friend != null) {
                FriendAvatar(avatarUrl = friend.avatarUrl, size = 40.dp)
                Column {
                    Text(text = friend.nickname, style = typography.titleMedium, color = colors.textPrimary)
                    if (friend.gameTag.isNotBlank()) {
                        Text(text = friend.gameTag, style = typography.bodySmall, color = colors.textSecondary)
                    }
                }
            } else {
                Text(text = "Friend", style = typography.titleMedium, color = colors.textPrimary)
            }
        }

        when {
            uiState.isLoadingFriend -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primaryAccent)
            }

            uiState.friend == null -> EmptyState(
                title = "Friend not found",
                subtitle = "This friend could not be found -- they may no longer be in your friends list.",
            )

            else -> {
                val tabs = FriendDetailTab.entries
                TabRow(
                    selectedTabIndex = uiState.selectedTab.ordinal,
                    containerColor = colors.backgroundSecondary.copy(alpha = 0.9f),
                    contentColor = colors.primaryAccent,
                ) {
                    tabs.forEach { tab ->
                        Tab(
                            selected = uiState.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            text = {
                                // maxLines/Ellipsis, not wrap -- same fix as TradesScreen's 3-tab
                                // TabRow (found live: "Collection" wraps to "Collectio/n" at 375px
                                // without this).
                                Text(
                                    text = tabLabel(tab),
                                    style = typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }

                when (uiState.selectedTab) {
                    FriendDetailTab.COLLECTION -> FriendDetailCollectionTab(
                        uiState = uiState,
                        viewModel = viewModel,
                        onCardClick = onCardClick,
                    )
                    FriendDetailTab.STATS -> FriendDetailStatsTab(
                        uiState = uiState,
                        onRetry = viewModel::retryStats,
                    )
                    FriendDetailTab.HISTORY -> FriendDetailHistoryTab(
                        uiState = uiState,
                        onRetry = viewModel::retryHistory,
                    )
                }
            }
        }
    }
}

private fun tabLabel(tab: FriendDetailTab): String = when (tab) {
    FriendDetailTab.COLLECTION -> "Collection"
    FriendDetailTab.STATS -> "Stats"
    FriendDetailTab.HISTORY -> "History"
}

@Composable
private fun FriendDetailCollectionTab(
    uiState: FriendDetailUiState,
    viewModel: FriendDetailViewModel,
    onCardClick: (String) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            // Caption-above-plain-field pattern -- OutlinedTextField's `label` slot renders as a
            // vertical single-letter stack on this CMP/wasmJs version (see FriendsScreen's
            // AddFriendSection note); never use `label` here.
            Text(text = "Search this collection", style = typography.bodySmall, color = colors.textSecondary)
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = colors.primaryAccent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoadingCards && uiState.cards.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.primaryAccent)
                }

                uiState.cardsError != null -> InlineErrorState(
                    message = uiState.cardsError,
                    retryLabel = "Retry",
                    onRetry = viewModel::retryCollection,
                    modifier = Modifier.padding(spacing.md),
                )

                uiState.cards.isEmpty() -> EmptyState(
                    title = "No cards found",
                    subtitle = if (uiState.searchQuery.isBlank()) {
                        "This friend's collection is empty, or isn't shared with you."
                    } else {
                        "No cards match \"${uiState.searchQuery}\"."
                    },
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = spacing.sm),
                ) {
                    items(
                        items = uiState.cards,
                        key = { card -> "${card.scryfallId}_${card.isFoil}_${card.condition}_${card.language}" },
                    ) { card -> FriendCardRow(card = card, onClick = { onCardClick(card.scryfallId) }) }

                    if (uiState.hasMoreCards) {
                        item(key = "load-more") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(spacing.md),
                                contentAlignment = Alignment.Center,
                            ) {
                                MagicCtaButton(
                                    onClick = viewModel::loadMoreCards,
                                    text = "Load more",
                                    isLoading = uiState.isLoadingMoreCards,
                                    enabled = !uiState.isLoadingMoreCards,
                                    style = MagicCtaStyle.Outlined,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendCardRow(card: FriendCard, onClick: () -> Unit) {
    CardListItem(
        name = card.name,
        imageUrl = card.imageNormal,
        priceUsd = if (card.isFoil) card.priceUsdFoil ?: card.priceUsd else card.priceUsd,
        priceEur = if (card.isFoil) card.priceEurFoil ?: card.priceEur else card.priceEur,
        onClick = onClick,
        quantityText = "×${card.quantity}",
        hasFoil = card.isFoil,
        condition = card.condition?.takeIf { it.isNotBlank() },
        language = card.language?.takeIf { it.isNotBlank() },
        typeLine = card.typeLine,
        isStale = card.isStale,
        setCode = card.setCode,
        setName = card.setName,
        rarity = card.rarity,
        containerColor = Color.Transparent,
    )
}

@Composable
private fun FriendDetailStatsTab(uiState: FriendDetailUiState, onRetry: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors

    Box(modifier = Modifier.fillMaxSize().padding(spacing.md)) {
        when {
            uiState.isLoadingStats -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primaryAccent)
            }

            uiState.statsError != null -> InlineErrorState(
                message = uiState.statsError,
                retryLabel = "Retry",
                onRetry = onRetry,
            )

            uiState.stats == null -> EmptyState(
                title = "No stats yet",
                subtitle = "${uiState.friend?.nickname ?: "This friend"} hasn't synced their collection stats.",
            )

            else -> FriendStatsGrid(stats = uiState.stats)
        }
    }
}

@Composable
private fun FriendStatsGrid(stats: FriendStats) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        StatRow(label = "Total cards", value = stats.totalCards.toString())
        StatRow(label = "Unique cards", value = stats.uniqueCards.toString())
        StatRow(
            label = "Value (EUR)",
            value = PriceFormatter.format(stats.totalValueEur, PreferredCurrency.EUR),
            valueColor = colors.goldMtg,
        )
        StatRow(
            label = "Value (USD)",
            value = PriceFormatter.format(stats.totalValueUsd, PreferredCurrency.USD),
            valueColor = colors.goldMtg,
        )
        stats.favouriteColor?.let { StatRow(label = "Favourite colour", value = colorDisplayName(it)) }
        stats.mostValuableColor?.let { StatRow(label = "Most valuable colour", value = colorDisplayName(it)) }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color = MaterialTheme.magicColors.textPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.magicTypography.bodyMedium, color = MaterialTheme.magicColors.textSecondary)
        Text(text = value, style = MaterialTheme.magicTypography.bodyMedium, color = valueColor)
    }
}

private fun colorDisplayName(code: String): String = when (code) {
    "W" -> "White"
    "U" -> "Blue"
    "B" -> "Black"
    "R" -> "Red"
    "G" -> "Green"
    "C" -> "Colorless"
    else -> code
}

@Composable
private fun FriendDetailHistoryTab(uiState: FriendDetailUiState, onRetry: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors

    Box(modifier = Modifier.fillMaxSize().padding(spacing.md)) {
        when {
            uiState.isLoadingHistory -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primaryAccent)
            }

            uiState.historyError != null -> InlineErrorState(
                message = uiState.historyError,
                retryLabel = "Retry",
                onRetry = onRetry,
            )

            uiState.matchHistory == null || uiState.matchHistory.totalGames == 0 -> EmptyState(
                title = "No games yet",
                subtitle = "You haven't played any games against ${uiState.friend?.nickname ?: "this friend"} yet.",
            )

            else -> FriendMatchHistoryCard(history = uiState.matchHistory)
        }
    }
}

@Composable
private fun FriendMatchHistoryCard(history: FriendMatchHistory) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(
            text = "${history.totalGames} games played",
            style = typography.titleMedium,
            color = colors.textPrimary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            WinLossStat(label = "Wins", value = history.myWins, color = colors.lifePositive)
            WinLossStat(label = "Losses", value = history.opponentWins, color = colors.lifeNegative)
            val winRate = if (history.totalGames > 0) (history.myWins * 100 / history.totalGames) else 0
            WinLossStat(label = "Win rate", value = winRate, suffix = "%", color = colors.primaryAccent)
        }
    }
}

@Composable
private fun WinLossStat(label: String, value: Int, color: Color, suffix: String = "") {
    val typography = MaterialTheme.magicTypography
    val colors = MaterialTheme.magicColors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "$value$suffix", style = typography.titleLarge, color = color)
        Text(text = label, style = typography.labelSmall, color = colors.textSecondary)
    }
}
