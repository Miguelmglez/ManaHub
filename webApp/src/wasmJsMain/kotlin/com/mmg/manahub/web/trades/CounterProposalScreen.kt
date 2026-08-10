package com.mmg.manahub.web.trades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Counter-offer editor -- reuses the SAME item-picker machinery [CreateProposalScreen] built
 * ([ItemSideSection]/[DraftItemRow]/[CollectionPickerDialog]/etc., made `internal` for this exact
 * reuse), pre-filled from the latest proposal version via [CounterProposalViewModel]. Per
 * `project_trades_hub_negotiation.md`'s deferral note: "Counter (needs the SAME item-picker
 * machinery as [Create] -- no separate smaller version exists)".
 */
@Composable
fun CounterProposalScreen(
    parentProposalId: String,
    rootProposalId: String,
    onBack: () -> Unit,
    onCountered: (proposalId: String, rootProposalId: String) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val viewModel = koinViewModel<CounterProposalViewModel>(key = parentProposalId) {
        parametersOf(parentProposalId, rootProposalId)
    }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CounterProposalEvent.NavigateToThread -> onCountered(event.proposalId, event.rootProposalId)
                CounterProposalEvent.NavigateBack -> onBack()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(vertical = spacing.lg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            Text(text = "Counter-offer", style = typography.titleLarge, color = colors.textPrimary)
        }

        if (uiState.error != null) {
            Text(
                text = uiState.error.orEmpty(),
                style = typography.bodyMedium,
                color = colors.lifeNegative,
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            )
        }

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primaryAccent)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                if (uiState.counterpartyName.isNotBlank()) {
                    Text(
                        text = "Countering ${uiState.counterpartyName}'s proposal",
                        style = typography.titleMedium,
                        color = colors.textPrimary,
                    )
                }

                ItemSideSection(
                    title = "You give",
                    items = uiState.giveItems,
                    onAddClick = viewModel::openGiveSheet,
                    onRemove = viewModel::removeGiveItem,
                )

                ItemSideSection(
                    title = "You receive",
                    items = uiState.receiveItems,
                    onAddClick = viewModel::openReceiveSheet,
                    onRemove = viewModel::removeReceiveItem,
                )

                MagicCtaButton(
                    onClick = viewModel::onSubmit,
                    text = "Send counter-offer",
                    style = MagicCtaStyle.Filled,
                    color = MagicCtaColor.Primary,
                    isLoading = uiState.isSubmitting,
                    enabled = !uiState.isSubmitting && uiState.giveItems.isNotEmpty() && uiState.receiveItems.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (uiState.isGiveSheetOpen) {
        CollectionPickerDialog(
            title = "Add from your collection",
            query = uiState.giveQuery,
            onQueryChange = viewModel::onGiveQueryChanged,
            isLoading = false,
            cards = uiState.myCollection.filter { uiState.giveQuery.isBlank() || it.card.name.contains(uiState.giveQuery, ignoreCase = true) },
            rowContent = { userCard -> MyCollectionRow(userCard = userCard, onAdd = { viewModel.addGiveItem(userCard) }) },
            key = { it.userCard.id },
            onDismiss = viewModel::closeGiveSheet,
        )
    }

    if (uiState.isReceiveSheetOpen) {
        CollectionPickerDialog(
            title = "Add from ${uiState.counterpartyName.ifBlank { "their" }} collection",
            query = uiState.receiveQuery,
            onQueryChange = viewModel::onReceiveQueryChanged,
            isLoading = uiState.isLoadingFriendCollection,
            cards = uiState.friendCollection,
            rowContent = { friendCard -> FriendCollectionRow(friendCard = friendCard, onAdd = { viewModel.addReceiveItem(friendCard) }) },
            key = { "${it.scryfallId}_${it.isFoil}_${it.condition}_${it.language}" },
            onDismiss = viewModel::closeReceiveSheet,
        )
    }
}
