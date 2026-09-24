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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.CardListItem
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.PullRefreshHeader
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.components.rememberPullRefreshState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.TimeAgoFormatter
import com.mmg.manahub.core.model.TradeItem
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeNegotiationDetailScreen(
    onBack: () -> Unit,
    onNavigateToEditor: (EditorNavArgs) -> Unit,
    onNavigateToCardDetail: (String) -> Unit,
    viewModel: TradeNegotiationViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastState = rememberMagicToastState()
    val mc = MaterialTheme.magicColors
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.onScreenEntered() }

    // §5.2 fix: one-shot navigation/toast effects are delivered through a buffered
    // Channel (viewModel.events), never a nullable StateFlow field — a StateFlow
    // equality-collapses two consecutive identical events and can drop emissions made
    // while the screen's lifecycle is paused. All string resolution happens HERE, in
    // ONE place (§5.1 fix) — the ViewModel emits semantic outcomes only, never raw
    // literal sentinel keys like the old "collection_updated" string.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is NegotiationEvent.NavigateToEditor -> onNavigateToEditor(event.args)
                is NegotiationEvent.CollectionSyncResult -> {
                    if (event.success) {
                        toastState.show(
                            context.getString(R.string.trades_collection_update_success),
                            MagicToastType.SUCCESS,
                        )
                    } else {
                        toastState.show(
                            context.getString(R.string.trades_collection_update_failed),
                            MagicToastType.ERROR,
                        )
                    }
                }
                NegotiationEvent.CollectionApplyDeferred ->
                    toastState.show(
                        context.getString(R.string.trades_collection_apply_deferred),
                        MagicToastType.INFO,
                    )
                is NegotiationEvent.ShowError -> {
                    val msg = event.message ?: context.getString(R.string.trades_error_generic_body)
                    toastState.show(msg, MagicToastType.ERROR)
                }
            }
        }
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
            canReverse = uiState.pendingRevokeCanReverse,
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
                uiState.thread.isEmpty() && uiState.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    MagicLoadingSpinner()
                }

                uiState.thread.isEmpty() && uiState.refreshFailed -> FullErrorState(
                    message = stringResource(R.string.trades_thread_load_error),
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = viewModel::refresh,
                    enabled = !uiState.isRefreshing,
                    modifier = Modifier.padding(innerPadding),
                )

                uiState.thread.isEmpty() -> EmptyState(
                    title = stringResource(R.string.trades_no_proposals_yet),
                    icon = Icons.Default.SwapHoriz,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .nestedScroll(pullState.nestedScrollConnection),
                    contentPadding = PaddingValues(MaterialTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
                ) {
                    if (pullState.headerHeightDp > 0.dp) {
                        item(key = "pull_header") {
                            PullRefreshHeader(
                                height              = pullState.headerHeightDp,
                                isRefreshing        = uiState.isRefreshing,
                                dragFraction        = pullState.dragFraction,
                                refreshingText      = stringResource(R.string.trades_history_refreshing),
                                pullIcon            = Icons.Default.KeyboardArrowDown,
                                pullHintDescription = stringResource(R.string.trades_history_pull_to_refresh),
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
                            pendingApplyProposalIds = uiState.pendingApplyProposalIds,
                            isSyncingCollection = uiState.isSyncingCollection,
                            onAccept = { viewModel.onAccept(proposal.id) },
                            onDecline = { viewModel.onDecline(proposal.id) },
                            onCancel = { viewModel.onCancelRequested(proposal.id) },
                            onRevoke = { viewModel.onRevoke(proposal.id) },
                            onMarkCompleted = { viewModel.onMarkCompleted(proposal.id) },
                            onCounter = { viewModel.onCounter(proposal.id) },
                            onEdit = { viewModel.onEdit(proposal.id) },
                            onUpdateCollection = { viewModel.onUpdateCollection(proposal.id) },
                            onUndoCollectionChanges = { viewModel.onUndoCollectionChanges(proposal.id) },
                            onRetryItems = viewModel::refresh,
                            onCardClick = onNavigateToCardDetail,
                        )
                    }
                    item(key = "bottom_spacer") {
                        Spacer(
                            Modifier
                                .height(MaterialTheme.spacing.lg)
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
    pendingApplyProposalIds: Set<String>,
    isSyncingCollection: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onRevoke: () -> Unit,
    onMarkCompleted: () -> Unit,
    onCounter: () -> Unit,
    onEdit: () -> Unit,
    onUpdateCollection: () -> Unit,
    onUndoCollectionChanges: () -> Unit,
    onRetryItems: () -> Unit,
    onCardClick: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val isProposer = proposal.proposerId == currentUserId

    // proposerItems = cards FROM the proposer → label shows the proposer as the sender
    val proposerLabel = if (proposal.proposerId == currentUserId) {
        stringResource(R.string.trades_you_offer_section)
    } else {
        val name = participantNames[proposal.proposerId]
        if (!name.isNullOrBlank()) stringResource(R.string.trades_named_sends, name)
        else stringResource(R.string.trades_they_offer_section)
    }
    // receiverItems = cards FROM the receiver → label shows the receiver as the sender
    val receiverLabel = if (proposal.receiverId == currentUserId) {
        stringResource(R.string.trades_you_offer_section)
    } else {
        val name = participantNames[proposal.receiverId]
        if (!name.isNullOrBlank()) stringResource(R.string.trades_named_sends, name)
        else stringResource(R.string.trades_they_offer_section)
    }

    Surface(
        shape = CardShape,
        color = mc.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.lg)) {
            // Header: status badge + timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TradeStatusBadge(status = proposal.status)

                val relativeTime = TimeAgoFormatter.format(proposal.updatedAt)
                if (relativeTime.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = mc.textSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(MaterialTheme.spacing.xs))
                        Text(
                            text = relativeTime,
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.textSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(MaterialTheme.spacing.lg))

            if (!proposal.itemsLoaded) {
                InlineErrorState(
                    message = stringResource(R.string.trades_items_load_failed),
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = onRetryItems,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(MaterialTheme.spacing.sm))
            }

            val proposerItems = proposal.items.filter { it.fromUserId == proposal.proposerId }
            val receiverItems = proposal.items.filter { it.fromUserId == proposal.receiverId }


            if (proposerItems.isNotEmpty() || proposal.includesReviewCollectionFromProposer) {
                ItemsSection(
                    label = proposerLabel,
                    items = proposerItems,
                    reviewPlaceholder = proposal.includesReviewCollectionFromProposer,
                    onCardClick = onCardClick,
                )
                Spacer(Modifier.height(MaterialTheme.spacing.sm))
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
                Spacer(Modifier.height(MaterialTheme.spacing.lg))
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

            if (proposal.status == TradeStatus.ACCEPTED && proposal.id in pendingApplyProposalIds) {
                Spacer(Modifier.height(MaterialTheme.spacing.md))
                Text(
                    text = stringResource(R.string.trades_collection_apply_deferred),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (proposal.status == TradeStatus.REVOKED && proposal.id in syncedCollectionProposalIds) {
                Spacer(Modifier.height(MaterialTheme.spacing.md))
                MagicCtaButton(
                    onClick = onUndoCollectionChanges,
                    text = stringResource(R.string.trades_undo_collection_changes),
                    enabled = proposal.itemsLoaded,
                    isLoading = isSyncingCollection,
                    color = MagicCtaColor.Error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // "Update Collection" section — visible only on completed proposals.
            // Shows a confirmation label once the user has already synced.
            if (proposal.status == TradeStatus.COMPLETED) {
                Spacer(Modifier.height(MaterialTheme.spacing.md))
                val isSynced = proposal.id in syncedCollectionProposalIds
                if (isSynced) {
                    Text(
                        text = stringResource(R.string.trades_collection_updated),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.lifePositive,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    MagicCtaButton(
                        onClick = onUpdateCollection,
                        text = stringResource(R.string.trades_update_collection),
                        enabled = proposal.itemsLoaded,
                        isLoading = isSyncingCollection,
                        color = MagicCtaColor.Primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
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
        modifier = Modifier.padding(bottom = MaterialTheme.spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 12.dp)
                .background(mc.primaryAccent.copy(alpha = 0.4f), CircleShape)
        )
        Spacer(Modifier.width(MaterialTheme.spacing.sm))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.magicTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = mc.textPrimary.copy(alpha = 0.8f),
        )
    }
    if (reviewPlaceholder) {
        Surface(
            shape = ChipShape,
            color = mc.secondaryAccent.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, mc.secondaryAccent.copy(alpha = 0.2f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.spacing.xs)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
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
                    color = mc.textPrimary,
                )
            }
        }
    }
    val unknownCard = stringResource(R.string.trades_unknown_card)
    items.filter { !it.isReviewCollectionPlaceholder }.forEach { item ->
        CardListItem(
            name = item.cardName.ifBlank { unknownCard },
            imageUrl = item.imageUrl,
            priceUsd = null,
            priceEur = null,
            onClick = { onCardClick(item.cardId) },
            quantityText = item.quantity?.let { stringResource(R.string.trades_quantity_multiplier, it) },
            hasFoil = item.isFoil == true,
            condition = item.condition?.takeIf { it.isNotBlank() },
            language = item.language?.takeIf { it.isNotBlank() },
            setCode = item.setCode,
            setName = item.setName,
            rarity = item.rarity,
            typeLine = item.typeLine,
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
    when (proposal.status) {
        // Drafts can no longer be created; legacy ones get no actions because the server rejects them.
        TradeStatus.DRAFT -> Unit

        TradeStatus.PROPOSED -> {
            if (isProposer) {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    MagicCtaButton(
                        onClick = onEdit,
                        text = stringResource(R.string.trades_action_edit),
                        enabled = !isProcessing && proposal.itemsLoaded,
                        style = MagicCtaStyle.Outlined,
                        color = MagicCtaColor.Neutral,
                        modifier = Modifier.weight(1f),
                    )
                    MagicCtaButton(
                        onClick = onCancel,
                        text = stringResource(R.string.trades_action_cancel),
                        enabled = !isProcessing,
                        color = MagicCtaColor.Error,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                        MagicCtaButton(
                            onClick = onAccept,
                            text = stringResource(R.string.trades_action_accept),
                            enabled = !isProcessing,
                            color = MagicCtaColor.Success,
                            modifier = Modifier.weight(1f),
                        )
                        MagicCtaButton(
                            onClick = onDecline,
                            text = stringResource(R.string.trades_action_decline),
                            enabled = !isProcessing,
                            color = MagicCtaColor.Error,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    MagicCtaButton(
                        onClick = onCounter,
                        text = stringResource(R.string.trades_action_counter),
                        enabled = !isProcessing && proposal.itemsLoaded,
                        style = MagicCtaStyle.Outlined,
                        color = MagicCtaColor.Primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                if (!alreadyMarked) {
                    MagicCtaButton(
                        onClick = onMarkCompleted,
                        text = stringResource(R.string.trades_action_mark_completed),
                        enabled = !isProcessing && proposal.itemsLoaded,
                        color = MagicCtaColor.Primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MagicCtaButton(
                    onClick = onRevoke,
                    text = stringResource(R.string.trades_action_revoke),
                    enabled = !isProcessing,
                    style = MagicCtaStyle.Ghost,
                    color = MagicCtaColor.Error,
                    modifier = Modifier.fillMaxWidth(),
                )
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
    MagicAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.trades_cancel_confirm_title),
        text = stringResource(R.string.trades_cancel_confirm_body),
        confirmLabel = stringResource(R.string.trades_cancel_confirm_yes),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.trades_cancel_keep),
        onDismiss = onDismiss,
        confirmColor = MagicCtaColor.Error
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

    MagicAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.trades_complete_dialog_title),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
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
                        TradeItemSummaryList(sentItems)
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
                        TradeItemSummaryList(receivedItems)
                        Text(
                            stringResource(R.string.trades_complete_dialog_receive_note),
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.lifePositive,
                        )
                    }
                }
            }
        },
        buttons = {
            if (hasItems) {
                MagicCtaButton(
                    onClick = onUpdateAndComplete,
                    text = stringResource(R.string.trades_complete_update_and_complete),
                    color = MagicCtaColor.Primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            MagicCtaButton(
                onClick = onJustComplete,
                text = stringResource(R.string.trades_complete_just_complete),
                style = MagicCtaStyle.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
            MagicCtaButton(
                onClick = onDismiss,
                text = stringResource(R.string.action_cancel),
                style = MagicCtaStyle.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    )
}

private const val SUMMARY_VISIBLE_ITEMS = 5

/** Bulleted summary of up to [SUMMARY_VISIBLE_ITEMS] trade items, with an "…and N more" overflow line. */
@Composable
private fun TradeItemSummaryList(items: List<TradeItem>) {
    val mc = MaterialTheme.magicColors
    val style = MaterialTheme.magicTypography.bodyMedium
    val unknownCard = stringResource(R.string.trades_unknown_card)
    val bullet = stringResource(R.string.trades_list_bullet)
    Column {
        items.take(SUMMARY_VISIBLE_ITEMS).forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                Text(text = bullet, style = style, color = mc.textSecondary)
                CardName(
                    name = item.cardName.ifBlank { unknownCard },
                    style = style,
                    color = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = stringResource(R.string.trades_quantity_multiplier, item.quantity ?: 1),
                    style = style,
                    color = mc.textSecondary,
                )
            }
        }
        val hidden = items.size - SUMMARY_VISIBLE_ITEMS
        if (hidden > 0) {
            Text(
                text = pluralStringResource(R.plurals.trades_items_more, hidden, hidden),
                style = style,
                color = mc.textSecondary,
            )
        }
    }
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
    canReverse: Boolean,
    onRevokeAndReverse: () -> Unit,
    onJustRevoke: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    MagicAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.trades_revoke_confirm_title),
        text = stringResource(
            if (hasSynced) R.string.trades_revoke_confirm_synced_body
            else R.string.trades_revoke_confirm_body
        ),
        buttons = {
            MagicCtaButton(
                onClick = if (hasSynced) onRevokeAndReverse else onJustRevoke,
                enabled = !hasSynced || canReverse,
                text = stringResource(
                    if (hasSynced) R.string.trades_revoke_and_reverse
                    else R.string.trades_revoke_just_revoke
                ),
                color = MagicCtaColor.Error,
                modifier = Modifier.fillMaxWidth(),
            )
            if (hasSynced) {
                MagicCtaButton(
                    onClick = onJustRevoke,
                    text = stringResource(R.string.trades_revoke_just_revoke),
                    style = MagicCtaStyle.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            MagicCtaButton(
                onClick = onDismiss,
                text = stringResource(R.string.trades_revoke_keep),
                style = MagicCtaStyle.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
    MagicAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.trades_gift_accept_title),
        text = stringResource(R.string.trades_gift_accept_body),
        confirmLabel = stringResource(R.string.trades_gift_accept_confirm),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.action_cancel),
        onDismiss = onDismiss,
        confirmColor = MagicCtaColor.Success
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

    MagicAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        text = body,
        confirmLabel = stringResource(R.string.action_confirm),
        onConfirm = onDismiss,
    )
}
