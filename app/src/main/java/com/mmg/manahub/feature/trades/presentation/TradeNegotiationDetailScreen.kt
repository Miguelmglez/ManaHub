package com.mmg.manahub.feature.trades.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.CardListItem
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.PullRefreshHeader
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.components.rememberPullRefreshState
import com.mmg.manahub.core.ui.theme.MagicColors
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.TimeAgoFormatter
import com.mmg.manahub.feature.trades.domain.model.TradeItem
import com.mmg.manahub.feature.trades.domain.model.TradeProposal
import com.mmg.manahub.feature.trades.domain.model.TradeStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeNegotiationDetailScreen(
    onBack: () -> Unit,
    onNavigateToEditor: (EditorNavArgs) -> Unit,
    onNavigateToCardDetail: (String) -> Unit,
    viewModel: TradeNegotiationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastState = rememberMagicToastState()
    val mc = MaterialTheme.magicColors

    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(uiState.snackbarMessage) {
        val raw = uiState.snackbarMessage ?: return@LaunchedEffect
        // Translate internal semantic keys emitted by the ViewModel into
        // localised strings. Any other message is shown as-is.
        val msg = when (raw) {
            "collection_updated" -> "Collection updated successfully"
            "collection_update_failed" -> "Failed to update collection"
            else -> raw
        }
        toastState.show(msg)
        viewModel.onSnackbarDismissed()
    }

    LaunchedEffect(uiState.navigateToEditor) {
        val nav = uiState.navigateToEditor ?: return@LaunchedEffect
        onNavigateToEditor(nav)
        viewModel.onNavigationConsumed()
    }

    uiState.errorDialog?.let { error ->
        NegotiationErrorDialog(
            error = error,
            onDismiss = viewModel::onErrorDismissed,
        )
    }

    if (uiState.pendingCancelProposalId != null) {
        CancelConfirmationDialog(
            onConfirm = viewModel::onCancelConfirmed,
            onDismiss = viewModel::onCancelDismissed,
        )
    }

    if (uiState.pendingMarkCompletedProposalId != null) {
        MarkCompleteDialog(
            sentItems = uiState.pendingMarkCompletedSentItems,
            receivedItems = uiState.pendingMarkCompletedReceivedItems,
            onUpdateAndComplete = { viewModel.onConfirmMarkCompleted(addToCollection = true) },
            onJustComplete = { viewModel.onConfirmMarkCompleted(addToCollection = false) },
            onDismiss = viewModel::onDismissMarkCompletedDialog,
        )
    }

    if (uiState.pendingRevokeProposalId != null) {
        RevokeConfirmationDialog(
            hasSynced = uiState.pendingRevokeHasSynced,
            onRevokeAndReverse = { viewModel.onRevokeConfirmed(reverseCollection = true) },
            onJustRevoke = { viewModel.onRevokeConfirmed(reverseCollection = false) },
            onDismiss = viewModel::onRevokeDismissed,
        )
    }

    if (uiState.pendingGiftAcceptProposalId != null) {
        GiftAcceptDialog(
            onConfirm = viewModel::onGiftAcceptConfirmed,
            onDismiss = viewModel::onGiftAcceptDismissed,
        )
    }

    val pullState = rememberPullRefreshState(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = mc.background,
            contentWindowInsets = WindowInsets.statusBars,
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
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = mc.textPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
                )
            },
        ) { innerPadding ->
            when {
                uiState.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = mc.primaryAccent)
                }

                uiState.thread.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.trades_no_proposals_yet),
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.textSecondary,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .nestedScroll(pullState.nestedScrollConnection),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (pullState.headerHeightDp > 0.dp) {
                        item(key = "pull_header") {
                            PullRefreshHeader(
                                height = pullState.headerHeightDp,
                                isRefreshing = uiState.isRefreshing,
                                dragFraction = pullState.dragFraction,
                            )
                        }
                    }
                    items(uiState.thread, key = { it.id }) { proposal ->
                        ProposalCard(
                            proposal = proposal,
                            currentUserId = uiState.currentUserId,
                            participantNames = uiState.participantNames,
                            isProcessing = uiState.isProcessing,
                            syncedCollectionProposalIds = uiState.syncedCollectionProposalIds,
                            isSyncingCollection = uiState.isSyncingCollection,
                            onAccept = { viewModel.onAccept(proposal.id) },
                            onDecline = { viewModel.onDecline(proposal.id) },
                            onCancel = { viewModel.onCancelRequested(proposal.id) },
                            onRevoke = { viewModel.onRevoke(proposal.id) },
                            onMarkCompleted = { viewModel.onMarkCompleted(proposal.id) },
                            onCounter = { viewModel.onCounter(proposal.id) },
                            onEdit = { viewModel.onEdit(proposal.id) },
                            onUpdateCollection = { viewModel.onUpdateCollection(proposal.id) },
                            onCardClick = onNavigateToCardDetail,
                        )
                    }
                    item(key = "bottom_spacer") {
                        Spacer(
                            Modifier
                                .height(16.dp)
                                .navigationBarsPadding()
                        )
                    }
                }
            }
        }
        MagicToastHost(
            state = toastState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Proposal card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProposalCard(
    proposal: TradeProposal,
    currentUserId: String,
    participantNames: Map<String, String>,
    isProcessing: Boolean,
    syncedCollectionProposalIds: Set<String>,
    isSyncingCollection: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onRevoke: () -> Unit,
    onMarkCompleted: () -> Unit,
    onCounter: () -> Unit,
    onEdit: () -> Unit,
    onUpdateCollection: () -> Unit,
    onCardClick: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val isProposer = proposal.proposerId == currentUserId

    // proposerItems = cards FROM the proposer → label shows the proposer as the sender
    val proposerLabel = if (proposal.proposerId == currentUserId) {
        stringResource(R.string.trades_you_offer_section)
    } else {
        val name = participantNames[proposal.proposerId]
        if (name != null) stringResource(R.string.trades_named_sends, name)
        else stringResource(R.string.trades_they_offer_section)
    }
    // receiverItems = cards FROM the receiver → label shows the receiver as the sender
    val receiverLabel = if (proposal.receiverId == currentUserId) {
        stringResource(R.string.trades_you_offer_section)
    } else {
        val name = participantNames[proposal.receiverId]
        if (name != null) stringResource(R.string.trades_named_sends, name)
        else stringResource(R.string.trades_they_offer_section)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = mc.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: status badge + timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ProposalStatusBadge(status = proposal.status, mc = mc)

                val relativeTime = TimeAgoFormatter.format(proposal.updatedAt)
                if (relativeTime.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = mc.textDisabled,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = relativeTime,
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.textDisabled,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            val proposerItems = proposal.items.filter { it.fromUserId == proposal.proposerId }
            val receiverItems = proposal.items.filter { it.fromUserId == proposal.receiverId }


            if (proposerItems.isNotEmpty() || proposal.includesReviewCollectionFromProposer) {
                ItemsSection(
                    label = proposerLabel,
                    items = proposerItems,
                    reviewPlaceholder = proposal.includesReviewCollectionFromProposer,
                    onCardClick = onCardClick,
                )
                Spacer(Modifier.height(8.dp))
            }

            if (receiverItems.isNotEmpty() || proposal.includesReviewCollectionFromReceiver) {
                ItemsSection(
                    label = receiverLabel,
                    items = receiverItems,
                    reviewPlaceholder = proposal.includesReviewCollectionFromReceiver,
                    onCardClick = onCardClick,
                )
            }

            // Active-state action buttons (accept, decline, cancel, etc.)
            if (proposal.status.isActive) {
                Spacer(Modifier.height(16.dp))
                ProposalActions(
                    proposal = proposal,
                    isProposer = isProposer,
                    isProcessing = isProcessing,
                    onAccept = onAccept,
                    onDecline = onDecline,
                    onCancel = onCancel,
                    onRevoke = onRevoke,
                    onMarkCompleted = onMarkCompleted,
                    onCounter = onCounter,
                    onEdit = onEdit,
                )
            }

            // "Update Collection" section — visible only on completed proposals.
            // Shows a confirmation label once the user has already synced.
            if (proposal.status == TradeStatus.COMPLETED) {
                Spacer(Modifier.height(12.dp))
                val isSynced = proposal.id in syncedCollectionProposalIds
                if (isSynced) {
                    Text(
                        text = stringResource(R.string.trades_collection_updated),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.lifePositive,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Button(
                        onClick = onUpdateCollection,
                        enabled = !isSyncingCollection,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isSyncingCollection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = mc.background,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.trades_update_collection),
                                color = mc.background,
                                style = MaterialTheme.magicTypography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProposalStatusBadge(status: TradeStatus, mc: MagicColors) {
    val color: Color
    val icon: ImageVector
    val labelRes: Int

    when (status) {
        TradeStatus.COMPLETED -> {
            color = mc.lifePositive
            icon = Icons.Default.CheckCircle
            labelRes = R.string.trades_status_completed
        }

        TradeStatus.ACCEPTED -> {
            color = mc.primaryAccent
            icon = Icons.Default.Check
            labelRes = R.string.trades_status_accepted
        }

        TradeStatus.CANCELLED -> {
            color = mc.lifeNegative
            icon = Icons.Default.Cancel
            labelRes = R.string.trades_status_cancelled
        }

        TradeStatus.REVOKED -> {
            color = mc.lifeNegative
            icon = Icons.AutoMirrored.Filled.Undo
            labelRes = R.string.trades_status_revoked
        }

        TradeStatus.DECLINED -> {
            color = mc.goldMtg
            icon = Icons.Default.Block
            labelRes = R.string.trades_status_declined
        }

        TradeStatus.COUNTERED -> {
            color = mc.secondaryAccent
            icon = Icons.Default.SwapHoriz
            labelRes = R.string.trades_status_countered
        }

        TradeStatus.PROPOSED -> {
            color = mc.primaryAccent
            icon = Icons.AutoMirrored.Filled.Send
            labelRes = R.string.trades_status_proposed
        }

        TradeStatus.DRAFT -> {
            color = mc.textSecondary
            icon = Icons.Default.Edit
            labelRes = R.string.trades_status_draft
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.magicTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = color,
            )
        }
    }
}

@Composable
private fun ItemsSection(
    label: String,
    items: List<TradeItem>,
    reviewPlaceholder: Boolean,
    onCardClick: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 12.dp)
                .background(mc.primaryAccent.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.magicTypography.labelLarge.copy(
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Black
            ),
            color = mc.textPrimary.copy(alpha = 0.8f),
        )
    }
    if (reviewPlaceholder) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = mc.secondaryAccent.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, mc.secondaryAccent.copy(alpha = 0.2f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = mc.secondaryAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.trades_review_collection_placeholder),
                    style = MaterialTheme.magicTypography.labelMedium,
                    color = mc.secondaryAccent,
                )
            }
        }
    }
    items.filter { !it.isReviewCollectionPlaceholder }.forEach { item ->
        CardListItem(
            name = item.cardName.ifBlank { item.cardId },
            imageUrl = item.imageUrl,
            priceUsd = null,
            priceEur = null,
            onClick = { onCardClick(item.cardId) },
            quantityText = item.quantity?.let { "×$it" },
            hasFoil = item.isFoil == true,
            condition = item.condition?.takeIf { it.isNotBlank() },
            language = item.language?.takeIf { it.isNotBlank() },
            isAltArt = item.isAltArt == true,
            setCode = item.setCode,
            setName = item.setName,
            rarity = item.rarity,
            containerColor = mc.backgroundSecondary.copy(alpha = 0.5f),
            shape = androidx.compose.ui.graphics.RectangleShape,
        )
    }
}

@Composable
private fun ProposalActions(
    proposal: TradeProposal,
    isProposer: Boolean,
    isProcessing: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onRevoke: () -> Unit,
    onMarkCompleted: () -> Unit,
    onCounter: () -> Unit,
    onEdit: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    when (proposal.status) {
        TradeStatus.DRAFT -> {
            if (isProposer) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onEdit,
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.trades_action_edit), color = mc.textPrimary)
                    }
                    Button(
                        onClick = onCancel,
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = mc.lifeNegative),
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
                    OutlinedButton(
                        onClick = onEdit,
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.trades_action_edit), color = mc.textPrimary)
                    }
                    Button(
                        onClick = onCancel,
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = mc.lifeNegative),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.trades_action_cancel), color = mc.background)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onAccept,
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = mc.lifePositive),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                stringResource(R.string.trades_action_accept),
                                color = mc.background
                            )
                        }
                        Button(
                            onClick = onDecline,
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = mc.lifeNegative),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                stringResource(R.string.trades_action_decline),
                                color = mc.background
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onCounter,
                        enabled = !isProcessing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.trades_action_counter), color = mc.textPrimary)
                    }
                }
            }
        }

        TradeStatus.ACCEPTED -> {
            // Determine whether this user has already marked the proposal as completed.
            // If so, hide the "Mark as Completed" button to prevent a double-mark.
            val alreadyMarked = if (isProposer) {
                proposal.proposerMarkedCompletedAt != null
            } else {
                proposal.receiverMarkedCompletedAt != null
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!alreadyMarked) {
                    Button(
                        onClick = onMarkCompleted,
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.trades_action_mark_completed),
                            color = mc.background
                        )
                    }
                }
                TextButton(
                    onClick = onRevoke,
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.trades_action_revoke), color = mc.lifeNegative)
                }
            }
        }

        else -> { /* terminal state — no actions */
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Cancel confirmation dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CancelConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        title = {
            Text(
                stringResource(R.string.trades_cancel_confirm_title),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )
        },
        text = {
            Text(
                stringResource(R.string.trades_cancel_confirm_body),
                style = MaterialTheme.magicTypography.bodyMedium,
                color = mc.textSecondary,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = mc.lifeNegative),
            ) {
                Text(stringResource(R.string.trades_cancel_confirm_yes), color = mc.background)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.trades_cancel_keep), color = mc.primaryAccent)
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Mark Complete dialog
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Shown when the user taps "Mark as Completed" on an accepted trade.
 * Lists sent and received items so the user can choose whether to update
 * the local collection as part of completing the trade.
 */
@Composable
private fun MarkCompleteDialog(
    sentItems: List<TradeItem>,
    receivedItems: List<TradeItem>,
    onUpdateAndComplete: () -> Unit,
    onJustComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val hasItems = sentItems.isNotEmpty() || receivedItems.isNotEmpty()

    /** Formats up to 5 items as bullet points, appending "…and N more" when needed. */
    fun formatItems(items: List<TradeItem>): String {
        val shown = items.take(5).joinToString("\n") { item ->
            "• ${item.cardName.ifBlank { item.cardId }} ×${item.quantity ?: 1}"
        }
        return if (items.size > 5) "$shown\n…and ${items.size - 5} more" else shown
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        title = {
            Text(
                stringResource(R.string.trades_complete_dialog_title),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!hasItems) {
                    Text(
                        stringResource(R.string.trades_complete_dialog_no_cards),
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.textSecondary,
                    )
                } else {
                    if (sentItems.isNotEmpty()) {
                        Text(
                            stringResource(R.string.trades_complete_dialog_you_send),
                            style = MaterialTheme.magicTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = mc.textPrimary,
                        )
                        Text(
                            formatItems(sentItems),
                            style = MaterialTheme.magicTypography.bodyMedium,
                            color = mc.textSecondary,
                        )
                        Text(
                            stringResource(R.string.trades_complete_dialog_send_note),
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.lifeNegative,
                        )
                    }
                    if (receivedItems.isNotEmpty()) {
                        Text(
                            stringResource(R.string.trades_complete_dialog_you_receive),
                            style = MaterialTheme.magicTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = mc.textPrimary,
                        )
                        Text(
                            formatItems(receivedItems),
                            style = MaterialTheme.magicTypography.bodyMedium,
                            color = mc.textSecondary,
                        )
                        Text(
                            stringResource(R.string.trades_complete_dialog_receive_note),
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.lifePositive,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (hasItems) {
                    Button(
                        onClick = onUpdateAndComplete,
                        colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.trades_complete_update_and_complete),
                            color = mc.background,
                        )
                    }
                }
                TextButton(
                    onClick = onJustComplete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.trades_complete_just_complete),
                        color = mc.textSecondary,
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_cancel), color = mc.textDisabled)
                }
            }
        },
        dismissButton = null,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Revoke confirmation dialog
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Shown when the user taps "Revoke" on an accepted trade.
 *
 * When [hasSynced] is `true` the user has already applied collection changes for
 * this trade, so an additional option to reverse those changes is offered.
 */
@Composable
private fun RevokeConfirmationDialog(
    hasSynced: Boolean,
    onRevokeAndReverse: () -> Unit,
    onJustRevoke: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        title = {
            Text(
                stringResource(R.string.trades_revoke_confirm_title),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )
        },
        text = {
            Text(
                stringResource(
                    if (hasSynced) R.string.trades_revoke_confirm_synced_body
                    else R.string.trades_revoke_confirm_body
                ),
                style = MaterialTheme.magicTypography.bodyMedium,
                color = mc.textSecondary,
            )
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = if (hasSynced) onRevokeAndReverse else onJustRevoke,
                    colors = ButtonDefaults.buttonColors(containerColor = mc.lifeNegative),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (hasSynced) R.string.trades_revoke_and_reverse
                            else R.string.trades_revoke_just_revoke
                        ),
                        color = mc.background,
                    )
                }
                if (hasSynced) {
                    TextButton(
                        onClick = onJustRevoke,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.trades_revoke_just_revoke), color = mc.textSecondary)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.trades_revoke_keep), color = mc.textDisabled)
                }
            }
        },
        dismissButton = null,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Gift-accept warning dialog
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Shown when the user attempts to accept a trade where the other party has only added
 * "Review my collection" (i.e., a gift trade with no concrete card commitment).
 */
@Composable
private fun GiftAcceptDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        title = {
            Text(
                text = stringResource(R.string.trades_gift_accept_title),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.trades_gift_accept_body),
                style = MaterialTheme.magicTypography.bodyMedium,
                color = mc.textSecondary,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = mc.lifePositive),
            ) {
                Text(stringResource(R.string.trades_gift_accept_confirm), color = mc.background)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = mc.textSecondary)
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Error dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NegotiationErrorDialog(
    error: NegotiationError,
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
        containerColor = mc.backgroundSecondary,
        title = {
            Text(title, style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
        },
        text = {
            Text(
                body,
                style = MaterialTheme.magicTypography.bodyMedium,
                color = mc.textSecondary,
                textAlign = TextAlign.Start
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_confirm), color = mc.primaryAccent)
            }
        },
    )
}
