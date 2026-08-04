package com.mmg.manahub.web.trades

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mmg.manahub.core.model.TradeItem
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStatus
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Negotiation thread detail -- the core "real trading" surface of the web Trades slice (approved
 * 2026-08-04). Shows every version of a proposal chain (rootProposalId) and the actions available
 * for the LATEST version based on its [TradeStatus] and whether the current user is proposer or
 * receiver: Accept / Decline / Counter (receiver, on a `PROPOSED` proposal), Cancel (proposer, on a
 * `PROPOSED`/`DRAFT` proposal), Revoke acceptance (either party, on `ACCEPTED`). Terminal statuses
 * (`COMPLETED`/`CANCELLED`/`DECLINED`/`REVOKED`) and superseded (non-latest) versions render
 * read-only (no action row).
 *
 * **Deliberately deferred (flagged follow-up, not half-built)**: Mark Completed + automatic
 * collection sync, and the "gift trade" (review-collection-only) warning dialog -- see
 * [TradeThreadViewModel]'s KDoc. Counter navigates to the counter-offer item picker pre-filled with
 * the counterparty + parent proposal id (also a flagged follow-up -- see `TradesScreen`'s KDoc).
 */
@Composable
fun TradeThreadScreen(rootProposalId: String, onCounter: (parentProposalId: String, counterpartyId: String) -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val viewModel = koinViewModel<TradeThreadViewModel>(key = rootProposalId) { parametersOf(rootProposalId) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is NegotiationEvent.NavigateToCounter -> onCounter(event.parentProposalId, event.counterpartyId)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(vertical = spacing.lg)) {
        Text(
            text = "Trade negotiation",
            style = typography.titleLarge,
            color = colors.textPrimary,
            modifier = Modifier.padding(bottom = spacing.md),
        )

        if (uiState.error != null) {
            Text(
                text = uiState.error.orEmpty(),
                style = typography.bodyMedium,
                color = colors.lifeNegative,
                modifier = Modifier.padding(bottom = spacing.sm),
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primaryAccent)
                }
                uiState.thread.isEmpty() -> EmptyState(
                    title = "Trade not found",
                    subtitle = "This proposal may have been removed.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    // Oldest first, so the negotiation reads top-to-bottom like a chat thread.
                    val sorted = uiState.thread.sortedBy { it.proposalVersion }
                    items(sorted, key = { it.id }) { proposal ->
                        val isLatest = proposal.id == sorted.last().id
                        ProposalVersionCard(
                            proposal = proposal,
                            currentUserId = uiState.currentUserId,
                            participantNames = uiState.participantNames,
                            isProcessing = uiState.isProcessing,
                            showActions = isLatest,
                            onAccept = { viewModel.onAccept(proposal.id) },
                            onDecline = { viewModel.onDecline(proposal.id) },
                            onCounter = { viewModel.onCounter(proposal.id) },
                            onCancelRequested = { viewModel.onCancelRequested(proposal.id) },
                            onRevoke = { viewModel.onRevoke(proposal.id) },
                        )
                    }
                }
            }
        }
    }

    if (uiState.pendingCancelProposalId != null) {
        MagicAlertDialog(
            onDismissRequest = viewModel::onCancelDismissed,
            title = "Cancel this proposal?",
            text = "The other party will no longer be able to accept it.",
            confirmLabel = "Cancel proposal",
            onConfirm = viewModel::onCancelConfirmed,
            confirmColor = MagicCtaColor.Error,
            dismissLabel = "Keep it",
            onDismiss = viewModel::onCancelDismissed,
        )
    }
}

@Composable
private fun ProposalVersionCard(
    proposal: TradeProposal,
    currentUserId: String,
    participantNames: Map<String, String>,
    isProcessing: Boolean,
    showActions: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCounter: () -> Unit,
    onCancelRequested: () -> Unit,
    onRevoke: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    val isProposer = proposal.proposerId == currentUserId
    val proposerName = if (isProposer) "You" else participantNames[proposal.proposerId] ?: proposal.proposerId.take(8)
    val receiverName = if (!isProposer) "You" else participantNames[proposal.receiverId] ?: proposal.receiverId.take(8)
    val yourItems = proposal.items.filter { it.fromUserId == currentUserId && !it.isReviewCollectionPlaceholder }
    val theirItems = proposal.items.filter { it.toUserId == currentUserId && !it.isReviewCollectionPlaceholder }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.backgroundSecondary,
        shape = RoundedCornerShape(spacing.sm),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(
                text = "Version ${proposal.proposalVersion} · $proposerName → $receiverName",
                style = typography.titleMedium,
                color = colors.textPrimary,
            )

            ItemGroup(title = "You give", items = yourItems)
            ItemGroup(title = "You receive", items = theirItems)

            if (showActions) {
                val canRespond = !isProposer && proposal.status == TradeStatus.PROPOSED
                val canCancel = isProposer && (proposal.status == TradeStatus.PROPOSED || proposal.status == TradeStatus.DRAFT)
                val canRevoke = proposal.status == TradeStatus.ACCEPTED

                if (canRespond || canCancel || canRevoke) {
                    HorizontalDivider(color = colors.backgroundSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        if (canRespond) {
                            MagicCtaButton(
                                onClick = onAccept,
                                text = "Accept",
                                style = MagicCtaStyle.Filled,
                                color = MagicCtaColor.Success,
                                isLoading = isProcessing,
                                modifier = Modifier.weight(1f),
                            )
                            MagicCtaButton(
                                onClick = onCounter,
                                text = "Counter",
                                style = MagicCtaStyle.Outlined,
                                color = MagicCtaColor.Accent,
                                modifier = Modifier.weight(1f),
                            )
                            MagicCtaButton(
                                onClick = onDecline,
                                text = "Decline",
                                style = MagicCtaStyle.Outlined,
                                color = MagicCtaColor.Error,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (canCancel) {
                            MagicCtaButton(
                                onClick = onCancelRequested,
                                text = "Cancel proposal",
                                style = MagicCtaStyle.Outlined,
                                color = MagicCtaColor.Error,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (canRevoke) {
                            MagicCtaButton(
                                onClick = onRevoke,
                                text = "Revoke acceptance",
                                style = MagicCtaStyle.Outlined,
                                color = MagicCtaColor.Neutral,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemGroup(title: String, items: List<TradeItem>) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Text(text = title, style = typography.labelMedium, color = colors.textSecondary)
        if (items.isEmpty()) {
            Text(text = "Nothing", style = typography.bodySmall, color = colors.textDisabled)
        } else {
            items.forEach { item ->
                Text(
                    text = "×${item.quantity ?: 1} ${item.cardName.ifBlank { item.cardId }}${if (item.isFoil == true) " (Foil)" else ""}",
                    style = typography.bodySmall,
                    color = colors.textPrimary,
                )
            }
        }
    }
}
