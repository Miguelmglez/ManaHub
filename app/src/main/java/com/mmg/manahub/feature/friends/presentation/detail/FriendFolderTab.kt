package com.mmg.manahub.feature.friends.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.friends.domain.model.FriendCard

/**
 * Folder tab composable for the friend detail screen.
 *
 * Displays a search bar, a sub-tab row (Collection / Wishlist / Offer for trade),
 * and a lazy list of [FriendCard] entries for the selected sub-tab.
 *
 * @param uiState        Current screen state from [FriendDetailViewModel].
 * @param viewModel      ViewModel used to dispatch user actions.
 * @param friendNickname Friend's nickname, used in empty-state messages.
 */
@Composable
fun FriendFolderTab(
    uiState: FriendDetailViewModel.UiState,
    viewModel: FriendDetailViewModel,
    friendNickname: String,
) {
    val mc = MaterialTheme.magicColors
    val subTabs = FolderSubTab.entries

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // ── Search bar ────────────────────────────────────────────────────────
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = viewModel::onSearchQueryChange,
            placeholder = {
                Text(
                    stringResource(R.string.friend_folder_search_hint),
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textDisabled,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = mc.textPrimary,
                unfocusedTextColor = mc.textPrimary,
                focusedBorderColor = mc.primaryAccent,
                unfocusedBorderColor = mc.surfaceVariant,
                cursorColor = mc.primaryAccent,
                focusedContainerColor = mc.surface,
                unfocusedContainerColor = mc.surface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // ── Sub-tab row ───────────────────────────────────────────────────────
        ScrollableTabRow(
            selectedTabIndex = uiState.folderSubTab.ordinal,
            containerColor = mc.backgroundSecondary,
            contentColor = mc.primaryAccent,
            edgePadding = 16.dp,
        ) {
            subTabs.forEachIndexed { index, subTab ->
                Tab(
                    selected = uiState.folderSubTab.ordinal == index,
                    onClick = { viewModel.selectFolderSubTab(subTabs[index]) },
                    text = {
                        Text(
                            text = subTabLabel(subTab),
                            style = MaterialTheme.magicTypography.labelLarge,
                        )
                    },
                )
            }
        }

        // ── Loading indicator (overlay, not replacing content) ────────────────
        if (uiState.isLoadingCards) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = mc.primaryAccent,
                trackColor = mc.surfaceVariant,
            )
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp,
                        ),
                    ) {
                        items(
                            items = uiState.cards,
                            key = { card ->
                                "${card.scryfallId}_${card.isFoil}_${card.condition}_${card.language}"
                            },
                        ) { card ->
                            FriendCardRow(card = card)
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

/**
 * Row composable for a single [FriendCard] in the lazy list.
 *
 * Layout: thumbnail | card info (name, set, badges) | quantity + price.
 */
@Composable
private fun FriendCardRow(card: FriendCard) {
    val mc = MaterialTheme.magicColors

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = mc.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── Card thumbnail ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 80.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(mc.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (card.imageNormal != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(card.imageNormal)
                            .crossfade(true)
                            .build(),
                        contentDescription = card.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        "MTG",
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textDisabled,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // ── Card info ─────────────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    card.name,
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    card.setName ?: "",
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Rarity badge
                    card.rarity?.let { RarityBadge(it) }
                    // Foil badge
                    if (card.isFoil) {
                        SmallBadge(
                            text = "❆ Foil",
                            containerColor = mc.primaryAccent.copy(alpha = 0.15f),
                            textColor = mc.primaryAccent,
                        )
                    }
                    // Condition badge
                    card.condition?.let { cond ->
                        SmallBadge(
                            text = cond,
                            containerColor = mc.surfaceVariant,
                            textColor = mc.textSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // ── Quantity + price ──────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.End) {
                if (card.quantity > 0) {
                    SmallBadge(
                        text = "×${card.quantity}",
                        containerColor = mc.primaryAccent.copy(alpha = 0.15f),
                        textColor = mc.primaryAccent,
                    )
                }
                card.priceEur?.let { price ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "€%.2f".format(price),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                    )
                }
            }
        }
    }
}

/** Small rounded badge used for rarity, foil, condition, and quantity indicators. */
@Composable
private fun SmallBadge(
    text: String,
    containerColor: Color,
    textColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(containerColor)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.magicTypography.labelSmall,
            color = textColor,
        )
    }
}

/** Badge that colours the rarity letter using standard MTG rarity colours. */
@Composable
private fun RarityBadge(rarity: String) {
    val mc = MaterialTheme.magicColors
    val normalised = rarity.lowercase()

    val (bg, fg) = when {
        normalised.startsWith("c") -> mc.surfaceVariant to mc.textSecondary            // common
        normalised.startsWith("u") -> Color(0xFF5B9BD5).copy(alpha = 0.2f) to Color(0xFF5B9BD5) // uncommon
        normalised.startsWith("r") -> Color(0xFFD4A017).copy(alpha = 0.2f) to Color(0xFFD4A017) // rare
        normalised.startsWith("m") -> Color(0xFFB5460F).copy(alpha = 0.2f) to Color(0xFFB5460F) // mythic
        else -> mc.surfaceVariant to mc.textSecondary
    }

    val label = when {
        normalised.startsWith("c") -> "C"
        normalised.startsWith("u") -> "U"
        normalised.startsWith("r") -> "R"
        normalised.startsWith("m") -> "M"
        else -> rarity.take(1).uppercase()
    }

    SmallBadge(text = label, containerColor = bg, textColor = fg)
}
