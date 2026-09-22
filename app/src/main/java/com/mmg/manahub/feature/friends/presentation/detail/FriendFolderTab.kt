package com.mmg.manahub.feature.friends.presentation.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.search.FriendCardSearchMapper
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.FriendCard
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.ui.components.CardListItem
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicFilterChip
import com.mmg.manahub.core.ui.components.MagicLoadingFooter
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicProgressBar
import com.mmg.manahub.core.ui.components.search.AdvancedSearchSheet
import com.mmg.manahub.core.ui.components.search.displayLabel
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import kotlinx.coroutines.flow.filter

/** User actions raised by [FriendFolderTab]. */
data class FriendFolderActions(
    val onSubTabSelected: (FolderSubTab) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onSearchSubmit: () -> Unit,
    val onClearText: () -> Unit,
    val onApplyAdvancedSearch: (AdvancedSearchQuery) -> Unit,
    val onRemoveCriterion: (SearchCriterion) -> Unit,
    val onClearNameExact: () -> Unit,
    val onClearSearch: () -> Unit,
    val onLoadMore: () -> Unit,
    val onRetryLoadMore: () -> Unit,
    val onRetry: () -> Unit,
    val onCardClick: (String) -> Unit,
)

/** Folder tab: list chips, name search + Advanced Search, active criteria and the card list. */
@Composable
fun FriendFolderTab(
    uiState: FriendDetailViewModel.UiState,
    friendNickname: String,
    actions: FriendFolderActions,
) {
    val spacing = MaterialTheme.spacing
    val focusManager = LocalFocusManager.current
    var showAdvancedSearch by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            FolderSubTab.entries.forEach { subTab ->
                MagicFilterChip(
                    modifier = Modifier.weight(1f),
                    selected = uiState.folderSubTab == subTab,
                    onClick = { actions.onSubTabSelected(subTab) },
                    label = subTabLabel(subTab),
                )
            }
        }

        FolderSearchRow(
            text = uiState.searchText,
            isSearching = uiState.isLoadingCards,
            criteriaCount = uiState.activeCriteriaCount,
            onQueryChange = actions.onQueryChange,
            onSubmit = actions.onSearchSubmit,
            onClear = actions.onClearText,
            onOpenAdvanced = {
                focusManager.clearFocus()
                showAdvancedSearch = true
            },
        )

        AnimatedVisibility(visible = uiState.showMinLengthHint) {
            Text(
                text = stringResource(R.string.friend_folder_min_length_hint, FriendCardSearchMapper.MIN_NAME_LENGTH),
                style = MaterialTheme.magicTypography.bodySmall,
                color = MaterialTheme.magicColors.textSecondary,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
            )
        }

        AnimatedVisibility(visible = uiState.activeCriteriaCount > 0) {
            ActiveCriteriaRow(
                criteria = uiState.advancedQuery.criteria,
                showNameExact = uiState.nameExact && !uiState.showMinLengthHint && uiState.searchText.isNotBlank(),
                onRemove = actions.onRemoveCriterion,
                onClearNameExact = actions.onClearNameExact,
                onClearAll = actions.onClearSearch,
            )
        }

        if (uiState.isLoadingCards && uiState.cards.isNotEmpty()) {
            MagicProgressBar(modifier = Modifier.fillMaxWidth())
        }

        Box(modifier = Modifier.fillMaxSize()) {
            FolderContent(uiState, friendNickname, actions)
        }
    }

    if (showAdvancedSearch) {
        AdvancedSearchSheet(
            friendListMode = true,
            onDismiss = { showAdvancedSearch = false },
            onSearch = { query, _ ->
                actions.onApplyAdvancedSearch(query)
                showAdvancedSearch = false
            },
            appliedQuery = uiState.sheetQuery,
        )
    }
}

@Composable
private fun FolderContent(
    uiState: FriendDetailViewModel.UiState,
    friendNickname: String,
    actions: FriendFolderActions,
) {
    val spacing = MaterialTheme.spacing
    when {
        uiState.cardsError == FolderCardsError.ACCESS_DENIED -> EmptyState(
            title = stringResource(R.string.friend_folder_access_denied_title),
            subtitle = stringResource(R.string.friend_folder_access_denied_body, friendNickname),
            icon = Icons.Default.Lock,
        )

        // Retrying cannot succeed until the user acts, so neither terminal state offers Retry.
        uiState.cardsError == FolderCardsError.SESSION_EXPIRED -> EmptyState(
            title = stringResource(R.string.friend_folder_session_expired_title),
            subtitle = stringResource(R.string.friend_folder_session_expired_body),
            icon = Icons.Default.Lock,
        )

        uiState.cardsError == FolderCardsError.PROFILE_INCOMPLETE -> EmptyState(
            title = stringResource(R.string.friend_folder_profile_incomplete_title),
            subtitle = stringResource(R.string.friend_folder_profile_incomplete_body),
            icon = Icons.Default.Person,
        )

        uiState.cardsError != null -> Box(
            modifier = Modifier.fillMaxSize().padding(spacing.lg),
            contentAlignment = Alignment.TopCenter,
        ) {
            InlineErrorState(
                message = stringResource(
                    if (uiState.cardsError == FolderCardsError.NETWORK) R.string.friend_folder_error
                    else R.string.friend_folder_error_generic
                ),
                retryLabel = stringResource(R.string.action_retry),
                onRetry = actions.onRetry,
            )
        }

        // Never flash an empty state while the first page is still loading.
        uiState.cards.isEmpty() && uiState.isLoadingCards -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            MagicLoadingSpinner()
        }

        uiState.cards.isEmpty() && uiState.resultsFiltered && uiState.unindexedCount > 0 -> EmptyState(
            title = stringResource(R.string.friend_folder_no_results_title),
            subtitle = pluralStringResource(
                R.plurals.friend_folder_unindexed_empty,
                uiState.unindexedCount,
                uiState.unindexedCount,
            ),
            icon = Icons.Default.HourglassEmpty,
            actionLabel = stringResource(R.string.action_retry),
            onAction = actions.onRetry,
        )

        uiState.cards.isEmpty() && uiState.resultsFiltered -> EmptyState(
            title = stringResource(R.string.friend_folder_no_results_title),
            subtitle = stringResource(R.string.friend_folder_no_results_body),
            icon = Icons.Default.SearchOff,
            actionLabel = stringResource(R.string.friend_folder_clear_search),
            onAction = actions.onClearSearch,
        )

        uiState.cards.isEmpty() -> EmptyState(
            title = emptyStateText(uiState.folderSubTab, friendNickname),
        )

        else -> FolderCardList(uiState, actions)
    }
}

@Composable
private fun FolderCardList(
    uiState: FriendDetailViewModel.UiState,
    actions: FriendFolderActions,
) {
    val listState = rememberLazyListState()
    val currentState by rememberUpdatedState(uiState)
    val unknownCard = stringResource(R.string.friend_folder_unknown_card)
    val focusManager = LocalFocusManager.current
    // Load-more stays disarmed until the scroll reset for the current results has run, so the
    // previous results' deep scroll position cannot trigger a page fetch for the new ones.
    var scrolledVersion by remember { mutableIntStateOf(uiState.resultsVersion) }

    LaunchedEffect(uiState.resultsVersion) {
        listState.scrollToItem(0)
        scrolledVersion = uiState.resultsVersion
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { it }
            .collect { focusManager.clearFocus() }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            scrolledVersion == currentState.resultsVersion &&
                currentState.hasMoreCards && !currentState.loadMoreFailed &&
                lastVisible >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) actions.onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (uiState.isLoadingCards) LOADING_ALPHA else 1f),
        contentPadding = PaddingValues(vertical = MaterialTheme.spacing.sm),
    ) {
        if (uiState.unindexedCount > 0) {
            item(key = "unindexed_note") {
                Text(
                    text = pluralStringResource(
                        R.plurals.friend_folder_unindexed_note,
                        uiState.unindexedCount,
                        uiState.unindexedCount,
                    ),
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = MaterialTheme.magicColors.textSecondary,
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.xs),
                )
            }
        }
        itemsIndexed(
            items = uiState.cards,
            key = { index, card -> card.rowId ?: "${card.scryfallId}_${card.isFoil}_${card.condition}_${card.language}_$index" },
        ) { _, card ->
            FriendCardRow(card, unknownCard, actions.onCardClick)
        }
        if (uiState.isLoadingMore) {
            item(key = "loading_footer") { MagicLoadingFooter() }
        } else if (uiState.loadMoreFailed) {
            item(key = "load_more_error") {
                InlineErrorState(
                    message = stringResource(R.string.friend_folder_load_more_error),
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = actions.onRetryLoadMore,
                    modifier = Modifier.padding(MaterialTheme.spacing.lg),
                )
            }
        }
    }
}

@Composable
private fun FriendCardRow(card: FriendCard, unknownCard: String, onCardClick: (String) -> Unit) {
    CardListItem(
        name = card.name.ifBlank { unknownCard },
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

@Composable
private fun FolderSearchRow(
    text: String,
    isSearching: Boolean,
    criteriaCount: Int,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val focusManager = LocalFocusManager.current
    val searchingDescription = stringResource(R.string.friend_folder_searching)
    val advancedDescription = if (criteriaCount > 0) {
        stringResource(R.string.friend_folder_advanced_search_active, criteriaCount)
    } else {
        stringResource(R.string.advsearch_button)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    stringResource(R.string.friend_folder_search_hint),
                    color = mc.textSecondary,
                    style = MaterialTheme.magicTypography.bodyLarge,
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = mc.textSecondary) },
            trailingIcon = if (text.isNotEmpty() || isSearching) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(MagicLoadingSize.XSmall.dp)
                                    .semantics { contentDescription = searchingDescription },
                                color = mc.primaryAccent,
                                strokeWidth = spacing.xxs,
                            )
                        }
                        if (text.isNotEmpty()) {
                            IconButton(onClick = onClear) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.friend_folder_clear_text),
                                    tint = mc.textSecondary,
                                )
                            }
                        }
                    }
                }
            } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onSubmit()
                focusManager.clearFocus()
            }),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = mc.primaryAccent,
                // surfaceVariant is ~1.1:1 on HallowedPrint; textDisabled keeps the outline visible on every theme.
                unfocusedBorderColor = mc.textDisabled,
                focusedTextColor = mc.textPrimary,
                unfocusedTextColor = mc.textPrimary,
                cursorColor = mc.primaryAccent,
            ),
        )
        BadgedBox(
            badge = {
                if (criteriaCount > 0) {
                    Badge(containerColor = mc.primaryAccent, contentColor = mc.onAccent) {
                        Text("$criteriaCount")
                    }
                }
            },
        ) {
            IconButton(
                onClick = onOpenAdvanced,
                modifier = Modifier
                    .size(TOUCH_TARGET)
                    .clip(MaterialTheme.shapes.medium)
                    .background(mc.primaryAccent.copy(alpha = if (criteriaCount > 0) 0.15f else 0.1f)),
            ) {
                Icon(Icons.Default.Tune, contentDescription = advancedDescription, tint = mc.primaryAccent)
            }
        }
    }
}

@Composable
private fun ActiveCriteriaRow(
    criteria: List<SearchCriterion>,
    showNameExact: Boolean,
    onRemove: (SearchCriterion) -> Unit,
    onClearNameExact: () -> Unit,
    onClearAll: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showNameExact) {
            item(key = "name_exact") {
                RemovableCriterionChip(stringResource(R.string.advsearch_chip_name_exact), onClearNameExact)
            }
        }
        // Criteria are value objects and may repeat, so the index is part of the key.
        itemsIndexed(criteria, key = { index, criterion -> "$index:${criterion.hashCode()}" }) { _, criterion ->
            RemovableCriterionChip(criterion.displayLabel()) { onRemove(criterion) }
        }
        item(key = "clear_all") {
            MagicCtaButton(
                onClick = onClearAll,
                text = stringResource(R.string.collection_clear_filters),
                style = MagicCtaStyle.Ghost,
                color = MagicCtaColor.Error,
            )
        }
    }
}

@Composable
private fun RemovableCriterionChip(label: String, onRemove: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val removeDescription = stringResource(R.string.friend_folder_remove_criterion, label)
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label, style = MaterialTheme.magicTypography.labelMedium) },
        trailingIcon = {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
        shape = ChipShape,
        colors = InputChipDefaults.inputChipColors(
            selectedContainerColor = mc.primaryAccent.copy(alpha = 0.15f),
            selectedLabelColor = mc.primaryAccent,
            selectedTrailingIconColor = mc.primaryAccent,
        ),
        border = null,
        modifier = Modifier.semantics { contentDescription = removeDescription },
    )
}

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

private const val LOAD_MORE_THRESHOLD = 3
private const val LOADING_ALPHA = 0.5f
private val TOUCH_TARGET = 48.dp
