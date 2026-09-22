package com.mmg.manahub.feature.collection.presentation.importexport

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.collection.transfer.CollectionFileFormat
import com.mmg.manahub.core.model.QueuedCard
import com.mmg.manahub.core.ui.components.CardQueueSheet
import com.mmg.manahub.core.ui.components.EditQueuedCardSheet
import com.mmg.manahub.core.ui.components.FullScreenImageViewer
import com.mmg.manahub.core.ui.components.MagicToastState
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.VariantSelectorSheet
import com.mmg.manahub.feature.decks.presentation.components.DeckImportSheet

private val IMPORT_MIME_TYPES = arrayOf(
    "text/plain", "text/csv", "text/comma-separated-values", "application/csv", "*/*",
)

/**
 * Every Collection import surface: the paste/file input sheet, the resume prompt, the private
 * review queue with its edit/variant sheets, and the unresolved-lines dialog. Sheets render above
 * the NavHost, so they only mount while the destination is RESUMED ([isResumed]); their open state
 * lives in [CollectionImportViewModel].
 */
@Composable
fun CollectionImportHost(
    state: CollectionImportUiState,
    viewModel: CollectionImportViewModel,
    isResumed: Boolean,
    toastState: MagicToastState,
    onCardClick: (QueuedCard) -> Unit,
) {
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.onImportFile(it.toString()) }
    }
    val clipboard = LocalClipboardManager.current
    val copiedMessage = stringResource(R.string.collection_import_unresolved_copied)

    val queueToastMessage = state.queueToast?.let { importToastText(it) }
    val queueToastType = state.queueToast?.toastType() ?: MagicToastType.SUCCESS
    val isQueueSheetVisible = state.isQueueSheetVisible && isResumed && state.queue.isNotEmpty()
    // While the review sheet is visible it shows the toast in its own host (the screen's is covered).
    LaunchedEffect(queueToastMessage, isQueueSheetVisible) {
        if (queueToastMessage != null && !isQueueSheetVisible) {
            toastState.show(queueToastMessage, queueToastType)
            viewModel.onQueueToastShown()
        }
    }

    if (!isResumed) return

    if (state.isInputSheetVisible) {
        DeckImportSheet(
            isLoading = state.isResolving,
            error = state.inputError?.let { importErrorText(it) },
            onImport = viewModel::onImportText,
            onDismiss = viewModel::onDismissInputSheet,
            title = stringResource(R.string.collection_import_title),
            hint = stringResource(R.string.collection_import_hint),
            placeholder = stringResource(R.string.collection_import_placeholder),
            loadingText = if (state.progressTotal > 0) {
                stringResource(R.string.collection_import_progress, state.progressProcessed, state.progressTotal)
            } else null,
            onImportFromFile = { openDocument.launch(IMPORT_MIME_TYPES) },
        )
    }

    if (state.isResumePromptVisible) {
        CollectionImportResumeDialog(
            queuedCount = state.queue.size,
            unresolvedCount = state.unresolvedLines.size,
            onReview = viewModel::onResumeReview,
            onImportMore = viewModel::onImportMore,
            onShowUnresolved = {
                viewModel.onDismissResumePrompt()
                viewModel.onShowUnresolved()
            },
            onDismiss = viewModel::onDismissResumePrompt,
        )
    }

    if (isQueueSheetVisible) {
        CardQueueSheet(
            cards = state.queue,
            preferredCurrency = state.preferredCurrency,
            ownedCardIdentityKeys = state.ownedCardIdentityKeys,
            isAutoDeleteOnAddEnabled = state.isAutoDeleteOnAddEnabled,
            isCommitting = state.isCommitting,
            isAddingAllToWishlist = state.isAddingAllToWishlist,
            inFlightEntryIds = state.inFlightIds,
            toastMessage = queueToastMessage,
            toastType = queueToastType,
            onToastShown = viewModel::onQueueToastShown,
            onDismiss = viewModel::onCloseQueueSheet,
            onRemoveCard = viewModel::onRemoveEntry,
            onEditCard = viewModel::onEditEntry,
            onClearQueue = viewModel::onClearQueue,
            onAddAllToCollection = viewModel::onAddAllToCollection,
            onAddAllToWishlist = viewModel::onAddAllToWishlist,
            onAddEntryToCollection = viewModel::onAddEntryToCollection,
            onAddEntryToWishlist = viewModel::onAddEntryToWishlist,
            onCardClick = onCardClick,
            onDuplicateCard = viewModel::onDuplicateEntry,
            onToggleAutoDeleteOnAdd = viewModel::onToggleAutoDeleteOnAdd,
            onIncrementQuantity = viewModel::onIncrementQuantity,
            onDecrementQuantity = viewModel::onDecrementQuantity,
        )
    }

    state.editingEntry?.let { entry ->
        EditQueuedCardSheet(
            queuedCard = entry,
            availablePrints = state.availablePrints,
            isLoadingPrints = state.isLoadingPrints,
            onDismiss = viewModel::onCloseEditSheet,
            onConfirm = viewModel::onUpdateEntry,
            onOpenVariantSelector = { viewModel.onOpenVariantSelector(entry) },
        )
    }
    state.variantSelectorEntry?.let { entry ->
        VariantSelectorSheet(
            currentCardId = entry.card.scryfallId,
            variants = state.cardVariants,
            isLoading = state.isLoadingVariants,
            onDismiss = viewModel::onCloseVariantSelector,
            onSelectVariant = viewModel::onSelectVariant,
            onExpandImage = viewModel::onExpandVariantImage,
        )
    }
    state.expandedVariantImageUrl?.let { url ->
        FullScreenImageViewer(imageUrl = url, onDismiss = viewModel::onCloseExpandedImage)
    }

    if (state.isUnresolvedDialogVisible && state.unresolvedLines.isNotEmpty()) {
        UnresolvedLinesDialog(
            lines = state.unresolvedLines,
            onCopy = {
                clipboard.setText(AnnotatedString(state.unresolvedLines.joinToString("\n")))
                toastState.show(copiedMessage, MagicToastType.SUCCESS)
            },
            onDismiss = viewModel::onDismissUnresolved,
        )
    }
}

/**
 * Export surfaces: the format/action sheet, the "Save to device" document picker, the share chooser
 * (a FileProvider `content://` URI, never EXTRA_TEXT) and the outcome toast.
 */
@Composable
fun CollectionExportHost(
    state: CollectionExportUiState,
    isResumed: Boolean,
    toastState: MagicToastState,
    fileName: () -> String,
    onFormatSelected: (CollectionFileFormat) -> Unit,
    onSaveTo: (location: String) -> Unit,
    onShare: () -> Unit,
    onShareLaunched: () -> Unit,
    onMessageShown: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnSaveTo by rememberUpdatedState(onSaveTo)
    val saveText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        uri?.let { currentOnSaveTo(it.toString()) }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri?.let { currentOnSaveTo(it.toString()) }
    }
    val chooserTitle = stringResource(R.string.collection_export_share_chooser)

    LaunchedEffect(state.pendingShare) {
        val share = state.pendingShare ?: return@LaunchedEffect
        val uri = Uri.parse(share.location)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = share.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        onShareLaunched()
    }

    val message = state.message
    val messageText = message?.let { exportMessageText(it) }
    LaunchedEffect(message) {
        if (message != null && messageText != null) {
            toastState.show(messageText, message.toastType())
            onMessageShown()
        }
    }

    if (state.isSheetVisible && isResumed) {
        CollectionExportSheet(
            state = state,
            onFormatSelected = onFormatSelected,
            onSave = {
                val launcher = if (state.format == CollectionFileFormat.TEXT) saveText else saveCsv
                launcher.launch(fileName())
            },
            onShare = onShare,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun importErrorText(error: CollectionImportError): String = when (error) {
    CollectionImportError.NothingRecognized -> stringResource(R.string.collection_import_error_nothing_recognized)
    CollectionImportError.NothingResolved -> stringResource(R.string.collection_import_error_nothing_resolved)
    is CollectionImportError.RateLimited ->
        stringResource(R.string.collection_import_error_rate_limited, error.retryAfterSeconds.toInt())
    CollectionImportError.LookupFailed -> stringResource(R.string.collection_import_error_lookup_failed)
    CollectionImportError.FileTooLarge -> stringResource(R.string.collection_import_error_file_too_large)
    CollectionImportError.FileUnreadable -> stringResource(R.string.collection_import_error_file_unreadable)
}

@Composable
private fun importToastText(toast: CollectionImportToast): String = when (toast) {
    is CollectionImportToast.Resolved -> if (toast.unresolved == 0) {
        pluralStringResource(R.plurals.collection_import_resolved, toast.entries, toast.entries)
    } else {
        pluralStringResource(R.plurals.collection_import_resolved_with_unresolved, toast.unresolved, toast.entries, toast.unresolved)
    }
    is CollectionImportToast.AddedToCollection -> stringResource(R.string.scanner_toast_added_to_collection, toast.cardName)
    is CollectionImportToast.AddedToWishlist -> stringResource(R.string.scanner_toast_added_to_wishlist, toast.cardName)
    is CollectionImportToast.AddFailed -> stringResource(R.string.scanner_toast_add_failed, toast.cardName)
    is CollectionImportToast.AddedAllToCollection ->
        pluralStringResource(R.plurals.addcard_queue_added_all_to_collection, toast.count, toast.count)
    is CollectionImportToast.AddedAllToWishlist ->
        pluralStringResource(R.plurals.addcard_queue_added_all_to_wishlist, toast.count, toast.count)
    is CollectionImportToast.AddAllPartialFailure ->
        pluralStringResource(R.plurals.addcard_queue_add_all_partial_failure, toast.total, toast.failed, toast.total)
}

private fun CollectionImportToast.toastType(): MagicToastType = when (this) {
    is CollectionImportToast.Resolved -> if (unresolved == 0) MagicToastType.SUCCESS else MagicToastType.WARNING
    is CollectionImportToast.AddedToCollection,
    is CollectionImportToast.AddedToWishlist,
    is CollectionImportToast.AddedAllToCollection,
    is CollectionImportToast.AddedAllToWishlist -> MagicToastType.SUCCESS
    is CollectionImportToast.AddFailed -> MagicToastType.ERROR
    is CollectionImportToast.AddAllPartialFailure -> MagicToastType.WARNING
}

@Composable
private fun exportMessageText(message: CollectionExportMessage): String = when (message) {
    is CollectionExportMessage.Completed -> if (message.skippedRows == 0) {
        pluralStringResource(R.plurals.collection_export_saved, message.rows, message.rows)
    } else {
        pluralStringResource(R.plurals.collection_export_saved_with_skipped, message.skippedRows, message.rows, message.skippedRows)
    }
    is CollectionExportMessage.NothingToExport -> stringResource(R.string.collection_export_nothing)
    CollectionExportMessage.Failed -> stringResource(R.string.collection_export_failed)
}

private fun CollectionExportMessage.toastType(): MagicToastType = when (this) {
    is CollectionExportMessage.Completed -> if (skippedRows == 0) MagicToastType.SUCCESS else MagicToastType.WARNING
    is CollectionExportMessage.NothingToExport -> MagicToastType.WARNING
    CollectionExportMessage.Failed -> MagicToastType.ERROR
}
