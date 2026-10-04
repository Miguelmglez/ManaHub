package com.mmg.manahub.feature.collection.presentation.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.ui.components.*
import com.mmg.manahub.core.ui.theme.*
import com.mmg.manahub.feature.collection.data.AndroidTransferDelivery
import com.mmg.manahub.feature.decks.presentation.components.DeckImportSheet
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** The import destination owns bounded review pages independently from CollectionViewModel. */
@Composable
fun DurableTransferScreen(
    jobId: String?,
    intake: TransferIntakeViewModel,
    onBack: () -> Unit,
    onRedirect: (String) -> Unit,
) {
    val receiving by intake.receiving.collectAsStateWithLifecycle()
    val pending by intake.unfinished.collectAsStateWithLifecycle()
    val received by intake.pending.collectAsStateWithLifecycle()
    val recovery by intake.recoveryNotice.collectAsStateWithLifecycle()
    val spacing = MaterialTheme.spacing
    var paste by remember { mutableStateOf(false) }
    var deferredDelivery by rememberSaveable(jobId) { mutableStateOf<String?>(null) }
    var recover by remember { mutableStateOf(false) }
    val recoveryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> uri?.let(intake::saveRecovery) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) intake.receiveUris(uris)
    }

    if (jobId == null) {
        TransferIntakeScreen(
            receiving = receiving,
            pending = pending,
            recovery = recovery,
            onOpenFiles = { picker.launch(AndroidTransferDelivery.mimeTypes) },
            onPaste = { paste = true },
            onBack = onBack,
            onResume = { onRedirect(it) },
            onRecover = { recover = true },
        )
    } else {
        val viewModel: DurableTransferViewModel = koinViewModel(key = "transfer:$jobId") {
            parametersOf(TransferJobId(jobId))
        }
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(state.redirectId) { state.redirectId?.let(onRedirect) }
        DurableTransferReview(viewModel, onBack)
        received.firstOrNull { it != jobId && it != deferredDelivery }?.let { next ->
            MagicAlertDialog(
                onDismissRequest = { deferredDelivery = next },
                title = "Another transfer is waiting",
                text = "This delivery is stored separately. Your current review is retained.",
                buttons = {
                    MagicCtaButton(
                        onClick = { intake.routed(next); onRedirect(next) },
                        text = "Review next transfer",
                        modifier = Modifier.fillMaxWidth().heightIn(min = spacing.xxl + spacing.lg),
                    )
                    MagicCtaButton(
                        onClick = { deferredDelivery = next },
                        text = "Keep reviewing",
                        style = MagicCtaStyle.Ghost,
                        modifier = Modifier.fillMaxWidth().heightIn(min = spacing.xxl + spacing.lg),
                    )
                },
            )
        }
    }

    if (paste) {
        DeckImportSheet(
            isLoading = receiving,
            error = null,
            onImport = { intake.receiveText(it); paste = false },
            onDismiss = { paste = false },
            title = "Import cards",
            hint = "Paste a TXT card list. Choose where each card belongs after review.",
            placeholder = "1 Lightning Bolt",
        )
    }
    if (recover) {
        MagicAlertDialog(
            onDismissRequest = { recover = false },
            title = "Save legacy recovery?",
            text = "This saves an opaque recovery archive outside the app. Its owner and previous application cannot be verified. Saving it does not add any cards.",
            buttons = {
                MagicCtaButton(
                    onClick = { recover = false; recoveryPicker.launch("legacy-import-recovery.mhrecovery") },
                    text = "Save recovery",
                    modifier = Modifier.fillMaxWidth().heightIn(min = spacing.xxl + spacing.lg),
                )
                MagicCtaButton(
                    onClick = { recover = false },
                    text = "Cancel",
                    style = MagicCtaStyle.Ghost,
                    modifier = Modifier.fillMaxWidth().heightIn(min = spacing.xxl + spacing.lg),
                )
            },
        )
    }
}

@Composable
private fun TransferIntakeScreen(
    receiving: Boolean,
    pending: List<PendingTransfer>,
    recovery: LegacyImportRecoveryNotice,
    onOpenFiles: () -> Unit,
    onPaste: () -> Unit,
    onBack: () -> Unit,
    onResume: (String) -> Unit,
    onRecover: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val actionHeight = spacing.xxl + spacing.lg
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    MagicCtaButton(
                        onClick = onBack,
                        text = "Back",
                        style = MagicCtaStyle.Ghost,
                        color = MagicCtaColor.Neutral,
                        modifier = Modifier.heightIn(min = actionHeight),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text("Import cards", style = ty.titleLarge, color = mc.textPrimary)
                        Text(
                            "Open CSV or TXT files, then choose collection or wishlist for each card.",
                            style = ty.bodyMedium,
                            color = mc.textSecondary,
                        )
                    }
                }
            }
            item {
                TransferPanel {
                    TransferSectionTitle("Choose a source", "Add a file or paste a list to begin.")
                    MagicCtaButton(
                        onClick = onOpenFiles,
                        text = "Open files",
                        enabled = !receiving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                    )
                    MagicCtaButton(
                        onClick = onPaste,
                        text = "Paste card list",
                        style = MagicCtaStyle.Outlined,
                        enabled = !receiving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                    )
                    if (receiving) {
                        MagicProgressBar()
                        Text("Receiving files…", style = ty.bodySmall, color = mc.textSecondary)
                    }
                }
            }
            if (pending.isNotEmpty()) {
                item {
                    TransferSectionTitle(
                        title = "Unfinished transfers",
                        subtitle = "Continue a saved import where you left off.",
                    )
                }
                itemsIndexed(pending, key = { _, job -> job.id }) { index, job ->
                    TransferPanel {
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            Text(
                                "Transfer ${index + 1}",
                                style = ty.titleMedium,
                                color = mc.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            TransferStatusPill(job.phase.displayName())
                        }
                        MagicCtaButton(
                            onClick = { onResume(job.id) },
                            text = "Resume transfer",
                            style = MagicCtaStyle.Outlined,
                            modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                        )
                    }
                }
            }
            if (recovery.state != LegacyImportRecoveryState.NONE && recovery.state != LegacyImportRecoveryState.PENDING) {
                item {
                    TransferPanel {
                        TransferSectionTitle(
                            title = "Legacy recovery",
                            subtitle = "Its owner and previous application are unknown. It is never imported automatically.",
                        )
                        MagicCtaButton(
                            onClick = onRecover,
                            text = "Save legacy recovery",
                            enabled = recovery.state == LegacyImportRecoveryState.RECOVERY_AVAILABLE,
                            style = MagicCtaStyle.Outlined,
                            modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                        )
                    }
                }
            }
        }
    }
}

private data class PendingTransferConfirmation(
    val destination: TransferDestination,
    val entryId: String?,
    val generation: Long,
    val payloadVersion: Long,
    val entryVersion: Long?,
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DurableTransferReview(viewModel: DurableTransferViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary = state.summary
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val actionHeight = spacing.xxl + spacing.lg
    val toast = rememberMagicToastState()
    var unavailableError by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<PendingTransferConfirmation?>(null) }
    var discard by remember { mutableStateOf<TransferDiscardConsent?>(null) }
    var correcting by remember { mutableStateOf<TransferReviewEntry?>(null) }
    var correctedQuantity by remember { mutableStateOf("") }
    var acceptLosses by remember(summary?.generation, summary?.payloadVersion) { mutableStateOf(false) }
    var acceptRepeats by remember(summary?.generation, summary?.payloadVersion) { mutableStateOf(false) }
    var replacementId by rememberSaveable { mutableStateOf<String?>(null) }
    var replacementGeneration by rememberSaveable { mutableLongStateOf(-1L) }
    val replacementPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val file = state.summary?.files?.firstOrNull { it.id.value == replacementId }
        if (uri != null && file != null) viewModel.replace(file, replacementGeneration, uri)
        replacementId = null
    }
    val reportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::writeReport)
    }

    fun request(destination: TransferDestination, entryId: String?) {
        val current = summary ?: return
        confirm = PendingTransferConfirmation(
            destination = destination,
            entryId = entryId,
            generation = current.generation,
            payloadVersion = current.payloadVersion,
            entryVersion = state.entries.firstOrNull { it.id == entryId }?.version,
        )
    }

    LaunchedEffect(state.error, summary, state.loading, state.needsOwnerChoice) {
        state.error?.let { error ->
            toast.show(error, MagicToastType.ERROR)
            if (summary == null && !state.loading && !state.needsOwnerChoice) {
                unavailableError = error
            }
            viewModel.clearError()
        } ?: run {
            if (summary != null || state.loading || state.needsOwnerChoice) {
                unavailableError = null
            }
        }
    }
    LaunchedEffect(state.notice) { state.notice?.let { toast.show(it, MagicToastType.SUCCESS); viewModel.clearNotice() } }

    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    MagicCtaButton(
                        onClick = onBack,
                        text = "Back",
                        style = MagicCtaStyle.Ghost,
                        color = MagicCtaColor.Neutral,
                        modifier = Modifier.heightIn(min = actionHeight),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.weight(1f)) {
                        Text("Review import", style = ty.titleLarge, color = mc.textPrimary)
                        if (summary != null) {
                            Text(summary.phase.name.displayName(), style = ty.bodySmall, color = mc.textSecondary)
                        }
                    }
                }
            }
            if (state.loading) {
                item {
                    TransferPanel {
                        MagicProgressBar()
                        Text(
                            "Preparing files. Android may delay background work.",
                            style = ty.bodyMedium,
                            color = mc.textSecondary,
                        )
                    }
                }
            }
            if (state.needsOwnerChoice) {
                item {
                    TransferPanel {
                        TransferSectionTitle(
                            title = "Choose where to review",
                            subtitle = "These files are not assigned to an account yet. Confirm this destination before continuing.",
                        )
                        MagicCtaButton(
                            onClick = viewModel::bindExplicitly,
                            text = "Review these files here",
                            modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                        )
                    }
                }
            }
            if (summary == null && !state.loading && !state.needsOwnerChoice) {
                item {
                    TransferPanel {
                        TransferSectionTitle(
                            title = "Transfer unavailable",
                            subtitle = unavailableError
                                ?: "The review could not be loaded. Return to transfers and reopen this import.",
                        )
                        MagicCtaButton(
                            onClick = onBack,
                            text = "Back to transfers",
                            modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                        )
                    }
                }
            }
            if (summary != null) {
                item {
                    TransferPanel {
                        TransferSectionTitle("Transfer overview", "Review counts and choose the next action.")
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            TransferMetric(
                                label = "Pending",
                                value = summary.pendingCopies.toString(),
                                modifier = Modifier.weight(1f),
                            )
                            TransferMetric(
                                label = "Collection",
                                value = summary.appliedCopies.toString(),
                                modifier = Modifier.weight(1f),
                            )
                            TransferMetric(
                                label = "Wishlist",
                                value = summary.wishlistCompletedCopies.toString(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            "${summary.pendingEntries} pending entries. Unchosen cards remain available for review.",
                            style = ty.bodySmall,
                            color = mc.textSecondary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            MagicCtaButton(
                                onClick = viewModel::pause,
                                text = "Pause",
                                style = MagicCtaStyle.Outlined,
                                modifier = Modifier.heightIn(min = actionHeight),
                            )
                            MagicCtaButton(
                                onClick = viewModel::resume,
                                text = "Resume",
                                style = MagicCtaStyle.Outlined,
                                modifier = Modifier.heightIn(min = actionHeight),
                            )
                            MagicCtaButton(
                                onClick = { discard = viewModel.captureDiscard() },
                                text = "Discard pending",
                                color = MagicCtaColor.Error,
                                style = MagicCtaStyle.Ghost,
                                modifier = Modifier.heightIn(min = actionHeight),
                            )
                        }
                    }
                }

                val displayedFiles = state.inventory.ifEmpty { summary.files }
                item {
                    TransferSectionTitle(
                        title = "Source files",
                        subtitle = if (summary.filesFrozen) "The source set is frozen because a transfer action has started." else "Choose which source files participate in this review.",
                    )
                }
                items(displayedFiles, key = { it.id.value }) { file ->
                    TransferPanel {
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            Text(
                                "File ${file.order + 1}",
                                style = ty.titleMedium,
                                color = mc.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            TransferStatusPill(file.phase.name.displayName())
                        }
                        Text(
                            "${file.format?.name ?: "Unrecognized"} source",
                            style = ty.bodySmall,
                            color = mc.textSecondary,
                        )
                        Text(
                            "${file.dataRecords} records · ${file.originalCopies} original copies · ${file.bytes} bytes",
                            style = ty.bodySmall,
                            color = mc.textSecondary,
                        )
                        Text(
                            "${file.invalidRecords} errors · ${file.unresolvedRecords} unresolved",
                            style = ty.bodySmall,
                            color = if (file.invalidRecords > 0 || file.unresolvedRecords > 0) mc.lifeNegative else mc.textSecondary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            MagicCtaButton(
                                onClick = { viewModel.selectFile(file) },
                                text = if (file.retired) "Retired source" else if (file.selected) "Exclude file" else "Include file",
                                enabled = !file.retired && !summary.filesFrozen,
                                style = MagicCtaStyle.Ghost,
                                modifier = Modifier.heightIn(min = actionHeight),
                            )
                            if (file.duplicate || file.previouslyParticipated) {
                                MagicCtaButton(
                                    onClick = { viewModel.includeRepeated(file) },
                                    text = "Include this repeated copy",
                                    enabled = !file.retired && !summary.filesFrozen,
                                    style = MagicCtaStyle.Outlined,
                                    modifier = Modifier.heightIn(min = actionHeight),
                                )
                            }
                        }
                        if (file.unrepresentedColumns.isNotEmpty()) {
                            Text(
                                "Fields not preserved: ${file.unrepresentedColumns.joinToString()}",
                                style = ty.bodySmall,
                                color = mc.textSecondary,
                            )
                        }
                        if (file.phase in setOf(TransferPhase.REJECTED, TransferPhase.FAILED_RETRYABLE)) {
                            MagicCtaButton(
                                onClick = {
                                    replacementId = file.id.value
                                    replacementGeneration = summary.generation
                                    replacementPicker.launch(AndroidTransferDelivery.mimeTypes)
                                },
                                text = "Choose this source again",
                                enabled = !file.retired && !summary.filesFrozen,
                                style = MagicCtaStyle.Outlined,
                                modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                            )
                        }
                    }
                }

                items(state.decisions, key = { "decision:${it.entryId}" }) { decision ->
                    TransferPanel {
                        TransferSectionTitle(
                            title = "Resolve a review change",
                            subtitle = "A source changed or an edited variant conflicts. Choose how to rebuild this entry.",
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            MagicCtaButton(
                                onClick = { viewModel.decision(decision, TransferReviewDecision.KEEP_EDIT) },
                                text = "Keep my edit",
                                style = MagicCtaStyle.Outlined,
                                modifier = Modifier.heightIn(min = actionHeight),
                            )
                            MagicCtaButton(
                                onClick = { viewModel.decision(decision, TransferReviewDecision.USE_SOURCE) },
                                text = "Use source",
                                style = MagicCtaStyle.Outlined,
                                modifier = Modifier.heightIn(min = actionHeight),
                            )
                            MagicCtaButton(
                                onClick = { viewModel.decision(decision, TransferReviewDecision.DISMISS_REMOVED) },
                                text = "Dismiss removed",
                                style = MagicCtaStyle.Ghost,
                                modifier = Modifier.heightIn(min = actionHeight),
                            )
                        }
                    }
                }

                item {
                    TransferSectionTitle(
                        title = "Cards to review",
                        subtitle = "Choose a destination per card, or apply one explicit choice to all eligible entries.",
                    )
                }
                items(state.cards, key = { it.id }) { card ->
                    val entry = state.entries.first { it.id == card.id }
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            TransferStatusPill("${entry.quantity} copies")
                            TransferStatusPill(entry.destination.name.displayName(), mc.secondaryAccent)
                            TransferStatusPill(entry.state.displayName(), mc.textSecondary)
                        }
                        QueueCardItem(
                            card,
                            LocalPreferredCurrency.current,
                            card.card.scryfallId in state.ownedPrintings,
                            (entry.state == "PENDING" && entry.activeActionId != null) || entry.appliedQuantity > 0L,
                            onEdit = {
                                if (entry.quantity > Int.MAX_VALUE.toLong()) {
                                    correcting = entry
                                    correctedQuantity = ""
                                } else {
                                    viewModel.edit(card)
                                }
                            },
                            onDelete = { viewModel.exclude(card) },
                            onAddToCollection = { request(TransferDestination.COLLECTION, card.id) },
                            onAddToWishlist = { request(TransferDestination.WISHLIST, card.id) },
                            onClick = { viewModel.inspect(card.id) },
                            onDuplicate = {
                                if (card.quantity.toLong() * 2L <= Int.MAX_VALUE) {
                                    viewModel.update(card.copy(quantity = card.quantity * 2))
                                }
                            },
                            onIncrement = { viewModel.adjust(card, 1) },
                            onDecrement = { viewModel.adjust(card, -1) },
                            displayQuantity = entry.quantity,
                            largeQuantityTargets = true,
                        )
                        if (entry.appliedQuantity == 0L && entry.activeActionId == null && entry.state == "PENDING") {
                            MagicCtaButton(
                                onClick = { correcting = entry; correctedQuantity = "" },
                                text = "Set quantity",
                                style = MagicCtaStyle.Ghost,
                                modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                            )
                        }
                    }
                }

                item {
                    TransferPanel {
                        TransferSectionTitle("Apply or inspect", "Bulk actions include entries across every page.")
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            MagicCtaButton(
                                onClick = viewModel::firstPage,
                                text = "First page",
                                style = MagicCtaStyle.Ghost,
                                modifier = Modifier.weight(1f).heightIn(min = actionHeight),
                            )
                            MagicCtaButton(
                                onClick = viewModel::nextPage,
                                text = "Next 50",
                                enabled = state.cursor != null,
                                style = MagicCtaStyle.Outlined,
                                modifier = Modifier.weight(1f).heightIn(min = actionHeight),
                            )
                        }
                        MagicCtaButton(
                            onClick = { request(TransferDestination.COLLECTION, null) },
                            text = "Add pending + wishlist to collection",
                            modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                        )
                        MagicCtaButton(
                            onClick = { request(TransferDestination.WISHLIST, null) },
                            text = "Add all pending to wishlist",
                            style = MagicCtaStyle.Outlined,
                            modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                        )
                        MagicCtaButton(
                            onClick = viewModel::errors,
                            text = "Review errors",
                            style = MagicCtaStyle.Ghost,
                            modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                        )
                        MagicCtaButton(
                            onClick = { viewModel.inventory() },
                            text = "Source inventory, including previous attempts",
                            style = MagicCtaStyle.Ghost,
                            modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                        )
                        if (state.inventoryCursor != null) {
                            MagicCtaButton(
                                onClick = { viewModel.inventory(true) },
                                text = "Next source inventory page",
                                style = MagicCtaStyle.Ghost,
                                modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                            )
                        }
                        MagicCtaButton(
                            onClick = { reportPicker.launch("collection-transfer-report.csv") },
                            text = "Save complete report",
                            enabled = !state.reporting,
                            style = MagicCtaStyle.Outlined,
                            modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                        )
                        if (state.reporting) MagicProgressBar()
                    }
                }

                items(state.provenance, key = { "provenance:${it.file.value}" }) { source ->
                    TransferPanel {
                        Text("Source ${source.file.value.take(8)}", style = ty.titleMedium, color = mc.textPrimary)
                        Text(
                            "${source.records} records · ${source.copies} original copies",
                            style = ty.bodySmall,
                            color = mc.textSecondary,
                        )
                    }
                }
                state.errors?.let { errors ->
                    item {
                        TransferSectionTitle(
                            title = "Import errors",
                            subtitle = "${errors.total} errors. Showing up to 200 examples.",
                        )
                    }
                    items(errors.examples, key = { "error:${it.file.value}:${it.ordinal}" }) { error ->
                        TransferPanel {
                            Text("Record ${error.ordinal}", style = ty.labelLarge, color = mc.textSecondary)
                            Text(error.preview, style = ty.bodySmall, color = mc.textPrimary)
                        }
                    }
                }
            }
        }
        MagicToastHost(toast)
    }

    confirm?.let { action ->
        val confirmationText = when {
            action.entryId == null && action.destination == TransferDestination.COLLECTION ->
                "Add every pending entry and retained wishlist entry to collection, including pages not currently visible. Completed wishlist quantities remain unchanged. This explicit action assigns unchosen entries to collection."
            action.entryId == null ->
                "Add every pending entry to wishlist, including pages not currently visible. This explicit action assigns unchosen entries to wishlist."
            else -> "Add only this entry. Other entries keep their decisions."
        }
        MagicAlertDialog(
            onDismissRequest = { confirm = null },
            title = "Confirm ${action.destination.name.lowercase()}",
            text = confirmationText,
            buttons = {
                MagicCtaButton(
                    onClick = {
                        viewModel.action(
                            action.destination,
                            action.entryId,
                            action.generation,
                            action.payloadVersion,
                            action.entryVersion,
                            acceptLosses,
                            acceptRepeats,
                        )
                        confirm = null
                    },
                    text = "Confirm",
                    modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                )
                MagicCtaButton(
                    onClick = { confirm = null },
                    text = "Cancel",
                    style = MagicCtaStyle.Ghost,
                    modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                )
            },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    MagicCtaButton(
                        onClick = { acceptLosses = !acceptLosses },
                        text = if (acceptLosses) "Omissions accepted" else "Accept reported errors and exclusions",
                        style = MagicCtaStyle.Outlined,
                        modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                    )
                    MagicCtaButton(
                        onClick = { acceptRepeats = !acceptRepeats },
                        text = if (acceptRepeats) "Repeated files accepted" else "Accept repeated files",
                        style = MagicCtaStyle.Outlined,
                        modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                    )
                }
            },
        )
    }
    discard?.let { consent ->
        MagicAlertDialog(
            onDismissRequest = { discard = null },
            title = "Discard pending entries?",
            text = "Already applied collection and wishlist copies remain unchanged.",
            confirmColor = MagicCtaColor.Error,
            buttons = {
                MagicCtaButton(
                    onClick = { viewModel.discard(consent); discard = null },
                    text = "Discard pending",
                    color = MagicCtaColor.Error,
                    modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                )
                MagicCtaButton(
                    onClick = { discard = null },
                    text = "Cancel",
                    style = MagicCtaStyle.Ghost,
                    modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                )
            },
        )
    }
    state.editing?.let { editing ->
        EditQueuedCardSheet(
            editing,
            state.variants,
            state.variantLoading,
            viewModel::closeEdit,
            viewModel::update,
            viewModel::showVariants,
            maxQty = Int.MAX_VALUE,
        )
        if (state.showVariants) {
            VariantSelectorSheet(
                editing.card.scryfallId,
                state.variants,
                state.variantLoading,
                viewModel::hideVariants,
                viewModel::selectVariant,
                viewModel::expandImage,
            )
        }
    }
    state.expandedImage?.let { FullScreenImageViewer(it, viewModel::closeImage) }
    correcting?.let { entry ->
        val chosen = correctedQuantity.toLongOrNull()?.takeIf { it in 1L..Int.MAX_VALUE.toLong() }
        MagicAlertDialog(
            onDismissRequest = { correcting = null },
            title = "Choose an explicit quantity",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Text(
                        "Original quantity: ${entry.quantity}. Attributes and source records remain unchanged.",
                        color = mc.textSecondary,
                        style = ty.bodySmall,
                    )
                    OutlinedTextField(
                        value = correctedQuantity,
                        onValueChange = { correctedQuantity = it.filter(Char::isDigit).take(19) },
                        label = { Text("Quantity (1–2147483647)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = mc.primaryAccent,
                            unfocusedBorderColor = mc.surfaceVariant,
                            focusedTextColor = mc.textPrimary,
                            unfocusedTextColor = mc.textPrimary,
                            cursorColor = mc.primaryAccent,
                            focusedLabelColor = mc.primaryAccent,
                            unfocusedLabelColor = mc.textSecondary,
                        ),
                    )
                }
            },
            buttons = {
                MagicCtaButton(
                    onClick = {
                        chosen?.let { viewModel.correctQuantity(entry.id, entry.version, it); correcting = null }
                    },
                    text = "Save chosen quantity",
                    enabled = chosen != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                )
                MagicCtaButton(
                    onClick = { correcting = null },
                    text = "Cancel",
                    style = MagicCtaStyle.Ghost,
                    modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                )
            },
        )
    }
}

@Composable
private fun TransferPanel(content: @Composable ColumnScope.() -> Unit) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = mc.surface,
        border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.65f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            content = content,
        )
    }
}

@Composable
private fun TransferSectionTitle(title: String, subtitle: String? = null) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
        Text(title, style = ty.titleMedium, color = mc.textPrimary)
        subtitle?.let { Text(it, style = ty.bodySmall, color = mc.textSecondary) }
    }
}

@Composable
private fun TransferMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(modifier = modifier, shape = CardShape, color = mc.backgroundSecondary) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(value, style = ty.titleMedium, color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = ty.bodySmall, color = mc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TransferStatusPill(label: String, accent: Color = MaterialTheme.magicColors.primaryAccent) {
    val mc = MaterialTheme.magicColors
    Surface(shape = ChipShape, color = accent.copy(alpha = 0.14f)) {
        Text(
            text = label,
            style = MaterialTheme.magicTypography.labelMedium,
            color = mc.textPrimary,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.sm, vertical = MaterialTheme.spacing.xs),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun String.displayName(): String =
    lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
