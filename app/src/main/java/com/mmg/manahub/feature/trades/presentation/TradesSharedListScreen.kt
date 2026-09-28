package com.mmg.manahub.feature.trades.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.model.SharedListType
import com.mmg.manahub.core.ui.components.CardListItem
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.androidx.compose.koinViewModel

/**
 * Landing screen for shared trade-list deep links
 * (`https://miguelmglez.github.io/list/{shareId}`, reached via [Screen.TradesSharedList]).
 */
@Composable
fun TradesSharedListScreen(
    onBack: () -> Unit,
    onCardClick: (scryfallId: String) -> Unit,
    viewModel: TradesSharedListViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing

    Scaffold(
        containerColor = mc.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Surface(color = mc.backgroundSecondary, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = spacing.xs, vertical = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = mc.textPrimary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.trades_shared_list_title),
                        style = MaterialTheme.magicTypography.titleLarge,
                        color = mc.textPrimary,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = uiState) {
                SharedListUiState.Loading -> MagicLoadingSpinner()

                SharedListUiState.Private -> EmptyState(
                    title = stringResource(R.string.trades_shared_list_private),
                    icon = Icons.Default.Lock,
                    actionLabel = stringResource(R.string.action_back),
                    onAction = onBack,
                )

                SharedListUiState.NotFound -> EmptyState(
                    title = stringResource(R.string.trades_shared_list_not_found),
                    icon = Icons.Default.SearchOff,
                    actionLabel = stringResource(R.string.action_back),
                    onAction = onBack,
                )

                SharedListUiState.Error -> FullErrorState(
                    message = stringResource(R.string.trades_shared_list_error),
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = viewModel::retry,
                )

                is SharedListUiState.Success -> SharedListContent(
                    state = state,
                    onCardClick = onCardClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun SharedListContent(
    state: SharedListUiState.Success,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    Column(modifier = modifier) {
        Text(
            text = if (state.ownerNickname.isNotBlank()) {
                stringResource(R.string.trades_shared_list_by, state.ownerNickname)
            } else {
                stringResource(R.string.trades_shared_list_title)
            },
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
        )
        Text(
            text = stringResource(
                when (state.listType) {
                    SharedListType.WISHLIST -> R.string.trades_shared_list_type_wishlist
                    SharedListType.OPEN_FOR_TRADE -> R.string.trades_shared_list_type_open_for_trade
                },
            ),
            style = MaterialTheme.magicTypography.labelMedium,
            color = mc.textSecondary,
            modifier = Modifier.padding(horizontal = spacing.lg),
        )

        if (state.rows.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.trades_shared_list_empty),
                icon = Icons.Default.Style,
            )
        } else {
            SharedListRows(state, onCardClick)
        }
    }
}

@Composable
private fun SharedListRows(
    state: SharedListUiState.Success,
    onCardClick: (String) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val unknownCard = stringResource(R.string.trades_unknown_card)
    val anyVariant = stringResource(R.string.trades_shared_list_any_variant)
    LazyColumn(
        contentPadding = PaddingValues(vertical = spacing.sm),
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        items(state.rows, key = { it.key }) { row ->
            val card = row.card
            CardListItem(
                name = card?.name ?: unknownCard,
                imageUrl = card?.imageNormal,
                priceUsd = if (row.item.isFoil == true) card?.priceUsdFoil ?: card?.priceUsd else card?.priceUsd,
                priceEur = if (row.item.isFoil == true) card?.priceEurFoil ?: card?.priceEur else card?.priceEur,
                onClick = { onCardClick(row.item.cardId) },
                quantityText = stringResource(R.string.trades_quantity_multiplier, row.item.quantity),
                hasFoil = row.item.isFoil == true,
                condition = if (row.item.matchAnyVariant) anyVariant else row.item.condition,
                language = row.item.language,
                isStale = card?.isStale ?: false,
                setCode = card?.setCode,
                setName = card?.setName,
                rarity = card?.rarity,
                typeLine = card?.typeLine,
                manaCost = card?.manaCost,
                scryfallId = row.item.cardId,
            )
        }
    }
}
