package com.mmg.manahub.feature.friends.presentation.detail

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.CardListItem
import com.mmg.manahub.core.ui.components.MagicLoadingFooter
import com.mmg.manahub.core.ui.components.MagicProgressBar
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.friends.domain.model.FriendCard

/**
 * Folder tab composable for the friend detail screen.
 *
 * Displays a sub-tab row (Collection / Wishlist / Offer for trade),
 * and a lazy list of [FriendCard] entries for the selected sub-tab.
 *
 * @param uiState        Current screen state from [FriendDetailViewModel].
 * @param viewModel      ViewModel used to dispatch user actions.
 * @param friendNickname Friend's nickname, used in empty-state messages.
 * @param onCardClick    Callback invoked when a card is clicked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendFolderTab(
    uiState: FriendDetailViewModel.UiState,
    viewModel: FriendDetailViewModel,
    friendNickname: String,
    onCardClick: (String) -> Unit = {},
) {
    val mc = MaterialTheme.magicColors
    val subTabs = FolderSubTab.entries

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // ── Sub-navigation chips ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            subTabs.forEach { subTab ->
                FilterChip(
                    modifier = Modifier.weight(1f),
                    selected = uiState.folderSubTab == subTab,
                    onClick = { viewModel.selectFolderSubTab(subTab) },
                    label = {
                        Text(
                            text = subTabLabel(subTab),
                            style = MaterialTheme.magicTypography.labelMedium,
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
                        selected = uiState.folderSubTab == subTab,
                        borderColor = mc.surfaceVariant,
                        selectedBorderColor = mc.primaryAccent
                    )
                )
            }
        }

        // ── Loading indicator (overlay, not replacing content) ────────────────
        if (uiState.isLoadingCards) {
            MagicProgressBar(modifier = Modifier.fillMaxWidth())
        }

        // ── Content area ──────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.cardError -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.friend_folder_error),
                            style = MaterialTheme.magicTypography.bodySmall,
                            color = mc.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                }

                uiState.cards.isEmpty() && !uiState.isLoadingCards -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = emptyStateText(uiState.folderSubTab, friendNickname),
                            style = MaterialTheme.magicTypography.bodySmall,
                            color = mc.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                }

                else -> {
                    val listState = rememberLazyListState()

                    // Fire loadMoreCards() when the user scrolls within 3 items of the end.
                    val shouldLoadMore = remember {
                        derivedStateOf {
                            val info = listState.layoutInfo
                            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                            uiState.hasMoreCards && lastVisible >= info.totalItemsCount - 3
                        }
                    }
                    LaunchedEffect(shouldLoadMore.value) {
                        if (shouldLoadMore.value) viewModel.loadMoreCards()
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(
                            items = uiState.cards,
                            key = { card ->
                                "${card.scryfallId}_${card.isFoil}_${card.condition}_${card.language}"
                            },
                        ) { card ->
                            CardListItem(
                                name = card.name,
                                imageUrl = card.imageNormal,
                                priceUsd = if (card.isFoil) card.priceUsdFoil ?: card.priceUsd else card.priceUsd,
                                priceEur = if (card.isFoil) card.priceEurFoil ?: card.priceEur else card.priceEur,
                                onClick = { onCardClick(card.scryfallId) },
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
                        if (uiState.isLoadingMore) {
                            item(key = "loading_footer") {
                                MagicLoadingFooter()
                            }
                        }
                    }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Private helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun subTabLabel(subTab: FolderSubTab): String = when (subTab) {
    FolderSubTab.COLLECTION -> stringResource(R.string.friend_folder_tab_all)
    FolderSubTab.WISHLIST -> stringResource(R.string.friend_folder_tab_wishlist)
    FolderSubTab.TRADE -> stringResource(R.string.friend_folder_tab_trade)
}

@Composable
private fun emptyStateText(subTab: FolderSubTab, friendNickname: String): String = when (subTab) {
    FolderSubTab.COLLECTION -> stringResource(R.string.friend_folder_empty_collection, friendNickname)
    FolderSubTab.WISHLIST -> stringResource(R.string.friend_folder_empty_wishlist, friendNickname)
    FolderSubTab.TRADE -> stringResource(R.string.friend_folder_empty_trade, friendNickname)
}
