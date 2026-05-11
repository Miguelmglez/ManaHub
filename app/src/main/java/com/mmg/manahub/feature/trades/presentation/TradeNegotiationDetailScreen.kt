package com.mmg.manahub.feature.trades.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.MagicColors
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.trades.domain.model.TradeItem
import com.mmg.manahub.feature.trades.domain.model.TradeProposal
import com.mmg.manahub.feature.trades.domain.model.TradeStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeNegotiationDetailScreen(
    onBack: () -> Unit,
    onNavigateToEditor: (EditorNavArgs) -> Unit,
    viewModel: TradeNegotiationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val mc = MaterialTheme.magicColors

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.onSnackbarDismissed()
    }

    LaunchedEffect(uiState.navigateToEditor) {
        val nav = uiState.navigateToEditor ?: return@LaunchedEffect
        onNavigateToEditor(nav)
        viewModel.onNavigationConsumed()
    }

    uiState.errorDialog?.let { error ->
        NegotiationErrorDialog(
            error     = error,
            onDismiss = viewModel::onErrorDismissed,
        )
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = mc.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.trades_negotiation_title),
                        style = MaterialTheme.magicTypography.titleMedium,
                        color = mc.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = mc.textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = mc.textPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh), tint = mc.textPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier        = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = mc.primaryAccent)
            }

            uiState.thread.isEmpty() -> Box(
                modifier        = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = stringResource(R.string.trades_no_proposals_yet),
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textSecondary,
                )
            }

            else -> LazyColumn(
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(uiState.thread, key = { it.id }) { proposal ->
                    ProposalCard(
                        proposal      = proposal,
                        currentUserId = uiState.currentUserId,
                        isProcessing  = uiState.isProcessing,
                        onAccept      = { viewModel.onAccept(proposal.id) },
                        onDecline     = { viewModel.onDecline(proposal.id) },
                        onCancel      = { viewModel.onCancel(proposal.id) },
                        onRevoke      = { viewModel.onRevoke(proposal.id) },
                        onMarkCompleted = { viewModel.onMarkCompleted(proposal.id) },
                        onCounter     = { viewModel.onCounter(proposal.id) },
                        onEdit        = { viewModel.onEdit(proposal.id) },
                    )
                }
                item(key = "bottom_spacer") { Spacer(Modifier.height(16.dp).navigationBarsPadding()) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Proposal card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProposalCard(
    proposal:       TradeProposal,
    currentUserId:  String,
    isProcessing:   Boolean,
    onAccept:       () -> Unit,
    onDecline:      () -> Unit,
    onCancel:       () -> Unit,
    onRevoke:       () -> Unit,
    onMarkCompleted: () -> Unit,
    onCounter:      () -> Unit,
    onEdit:         () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val isProposer = proposal.proposerId == currentUserId

    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = mc.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: version + status badge
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = stringResource(R.string.trades_proposal_version, proposal.proposalVersion),
                    style    = MaterialTheme.magicTypography.labelSmall,
                    color    = mc.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                ProposalStatusBadge(status = proposal.status, mc = mc)
            }

            Spacer(Modifier.height(12.dp))

            // Items going to receiver (proposer offers)
            val proposerItems = proposal.items.filter { it.fromUserId == proposal.proposerId }
            val receiverItems = proposal.items.filter { it.fromUserId == proposal.receiverId }

            if (proposerItems.isNotEmpty() || proposal.includesReviewCollectionFromProposer) {
                ItemsSection(
                    label  = stringResource(R.string.trades_proposer_offers),
                    items  = proposerItems,
                    reviewPlaceholder = proposal.includesReviewCollectionFromProposer,
                )
                Spacer(Modifier.height(8.dp))
            }

            if (receiverItems.isNotEmpty() || proposal.includesReviewCollectionFromReceiver) {
                ItemsSection(
                    label  = stringResource(R.string.trades_receiver_offers),
                    items  = receiverItems,
                    reviewPlaceholder = proposal.includesReviewCollectionFromReceiver,
                )
            }

            // Actions
            if (proposal.status.isActive) {
                Spacer(Modifier.height(16.dp))
                ProposalActions(
                    proposal      = proposal,
                    isProposer    = isProposer,
                    isProcessing  = isProcessing,
                    onAccept      = onAccept,
                    onDecline     = onDecline,
                    onCancel      = onCancel,
                    onRevoke      = onRevoke,
                    onMarkCompleted = onMarkCompleted,
                    onCounter     = onCounter,
                    onEdit        = onEdit,
                )
            }
        }
    }
}

@Composable
private fun ProposalStatusBadge(status: TradeStatus, mc: MagicColors) {
    val color = when (status) {
        TradeStatus.COMPLETED -> mc.lifePositive
        TradeStatus.ACCEPTED  -> mc.primaryAccent
        TradeStatus.CANCELLED,
        TradeStatus.REVOKED   -> mc.lifeNegative
        TradeStatus.DECLINED  -> mc.goldMtg
        TradeStatus.COUNTERED -> mc.secondaryAccent
        else                  -> mc.textSecondary
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text     = status.name,
            style    = MaterialTheme.magicTypography.labelSmall,
            color    = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ItemsSection(
    label:             String,
    items:             List<TradeItem>,
    reviewPlaceholder: Boolean,
) {
    val mc = MaterialTheme.magicColors
    Text(
        text  = label,
        style = MaterialTheme.magicTypography.labelSmall,
        color = mc.textSecondary,
    )
    Spacer(Modifier.height(4.dp))
    if (reviewPlaceholder) {
        Text(
            text  = stringResource(R.string.trades_review_collection_placeholder),
            style = MaterialTheme.magicTypography.bodySmall,
            color = mc.secondaryAccent,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    items.forEach { item ->
        TradeItemRow(item = item)
    }
}

@Composable
private fun TradeItemRow(item: TradeItem) {
    val mc = MaterialTheme.magicColors
    val badges = buildList {
        item.quantity?.let { add("x$it") }
        if (item.isFoil == true) add("Foil")
        item.condition?.takeIf { it.isNotBlank() }?.let { add(it) }
        item.language?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (item.isAltArt == true) add("Alt art")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text  = item.cardName.ifBlank { item.cardId },
            style = MaterialTheme.magicTypography.bodySmall,
            color = mc.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (badges.isNotEmpty()) {
            Text(
                text  = badges.joinToString(" · "),
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textSecondary,
            )
        }
    }
}

@Composable
private fun ProposalActions(
    proposal:        TradeProposal,
    isProposer:      Boolean,
    isProcessing:    Boolean,
    onAccept:        () -> Unit,
    onDecline:       () -> Unit,
    onCancel:        () -> Unit,
    onRevoke:        () -> Unit,
    onMarkCompleted: () -> Unit,
    onCounter:       () -> Unit,
    onEdit:          () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    when (proposal.status) {
        TradeStatus.DRAFT -> {
            if (isProposer) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, enabled = !isProcessing, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.trades_action_edit), color = mc.textPrimary)
                    }
                    Button(
                        onClick  = onCancel,
                        enabled  = !isProcessing,
                        colors   = ButtonDefaults.buttonColors(containerColor = mc.lifeNegative),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.trades_action_cancel), color = mc.background)
                    }
                }
            }
        }

        TradeStatus.PROPOSED -> {
            if (isProposer) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, enabled = !isProcessing, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.trades_action_edit), color = mc.textPrimary)
                    }
                    Button(
                        onClick  = onCancel,
                        enabled  = !isProcessing,
                        colors   = ButtonDefaults.buttonColors(containerColor = mc.lifeNegative),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.trades_action_cancel), color = mc.background)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick  = onAccept,
                            enabled  = !isProcessing,
                            colors   = ButtonDefaults.buttonColors(containerColor = mc.lifePositive),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.trades_action_accept), color = mc.background)
                        }
                        Button(
                            onClick  = onDecline,
                            enabled  = !isProcessing,
                            colors   = ButtonDefaults.buttonColors(containerColor = mc.lifeNegative),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.trades_action_decline), color = mc.background)
                        }
                    }
                    OutlinedButton(
                        onClick  = onCounter,
                        enabled  = !isProcessing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.trades_action_counter), color = mc.textPrimary)
                    }
                }
            }
        }

        TradeStatus.ACCEPTED -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick  = onMarkCompleted,
                    enabled  = !isProcessing,
                    colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.trades_action_mark_completed), color = mc.background)
                }
                TextButton(
                    onClick  = onRevoke,
                    enabled  = !isProcessing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.trades_action_revoke), color = mc.lifeNegative)
                }
            }
        }

        else -> { /* terminal state — no actions */ }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Error dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NegotiationErrorDialog(
    error:     NegotiationError,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val (title, body) = when (error) {
        is NegotiationError.CardAlreadyLocked -> Pair(
            stringResource(R.string.trades_error_card_locked_title),
            stringResource(R.string.trades_error_card_locked_body, error.cardIds.size),
        )
        NegotiationError.ProposalVersionMismatch -> Pair(
            stringResource(R.string.trades_error_version_mismatch_title),
            stringResource(R.string.trades_error_version_mismatch_body),
        )
        NegotiationError.InventoryGone -> Pair(
            stringResource(R.string.trades_error_inventory_gone_title),
            stringResource(R.string.trades_error_inventory_gone_body),
        )
        is NegotiationError.Generic -> Pair(
            stringResource(R.string.trades_error_generic_title),
            error.message ?: stringResource(R.string.trades_error_generic_body),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = mc.backgroundSecondary,
        title = {
            Text(title, style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
        },
        text = {
            Text(body, style = MaterialTheme.magicTypography.bodyMedium, color = mc.textSecondary, textAlign = TextAlign.Start)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_confirm), color = mc.primaryAccent)
            }
        },
    )
}
