package com.mmg.manahub.feature.collection.presentation.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.selection.toggleable
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
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import com.mmg.manahub.R
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.key
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
    if(jobId.isNullOrBlank()) return
    val viewModel: DurableTransferViewModel = koinViewModel(key = "transfer:$jobId") { parametersOf(TransferJobId(jobId)) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.redirectId) { state.redirectId?.let(onRedirect) }
    DurableTransferReview(viewModel, onBack, intake, onRedirect)
}

private data class PendingTransferConfirmation(
    val destination: TransferDestination,
    val entryId: String?,
    val generation: Long,
    val payloadVersion: Long,
    val entryVersion: Long?,
    val session: TransferSession.Available,
    val followWishlist: Boolean=false,
    val cards: Long,
    val copies: Long,
    val retainedCards: Long,
    val retainedCopies: Long,
    val omittedRecords: Long,
    val excludedCards: Long,
    val repeatedFiles: Int,
)

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
private fun DurableTransferReview(viewModel: DurableTransferViewModel, onBack: () -> Unit, intake: TransferIntakeViewModel, onRedirect: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary = state.summary
    val received by intake.pending.collectAsStateWithLifecycle()
    var details by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val detailsListState = rememberLazyListState()
    fun openDetails() { details=true;viewModel.details() }
    fun closeDetails() { details=false }
    LaunchedEffect(state.publishedInverted) { listState.scrollToItem(0) }
    BackHandler(details) { closeDetails() }
    val activeList=if(details)detailsListState else listState
    LaunchedEffect(activeList,state.scope,details,state.entries.firstOrNull()?.id,state.entries.lastOrNull()?.id) {
        snapshotFlow { activeList.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }.distinctUntilChanged().collect { keys ->
            val first=state.entries.firstOrNull()?.id
            val last=state.entries.lastOrNull()?.id
            if(last!=null && last in keys)viewModel.nextPage()
            if(first!=null && first in keys)viewModel.previousPage()
        }
    }
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val actionHeight = spacing.xxl + spacing.lg
    val toast = key(state.presentationSession) { rememberMagicToastState() }
    var unavailableError by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<PendingTransferConfirmation?>(null) }
    var discard by remember { mutableStateOf<TransferDiscardConsent?>(null) }
    var correcting by remember { mutableStateOf<TransferReviewEntry?>(null) }
    var quantityConsent by remember { mutableStateOf<TransferSession.Available?>(null) }
    var replacementFile by remember { mutableStateOf<TransferFileSummary?>(null) }
    var replacementConsent by remember { mutableStateOf<TransferSession.Available?>(null) }
    var correctedQuantity by remember { mutableStateOf("") }
    var acceptLosses by remember(summary?.generation, summary?.payloadVersion) { mutableStateOf(false) }
    var acceptRepeats by remember(summary?.generation, summary?.payloadVersion) { mutableStateOf(false) }
    var replacementId by rememberSaveable { mutableStateOf<String?>(null) }
    var replacementGeneration by rememberSaveable { mutableLongStateOf(-1L) }
    val replacementPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val file = replacementFile
        if (uri != null && file != null) replacementConsent?.let { viewModel.replace(file, replacementGeneration, uri, it) }
        replacementConsent=null
        replacementFile=null
        replacementId = null
    }

    fun request(destination: TransferDestination, entryId: String?, followWishlist: Boolean=false) {
        val current = summary ?: return
        acceptLosses=false
        acceptRepeats=false
        val action = PendingTransferConfirmation(
            destination = destination,
            entryId = entryId,
            generation = current.generation,
            payloadVersion = current.payloadVersion,
            entryVersion = state.entries.firstOrNull { it.id == entryId }?.version,
            session = viewModel.captureSession() ?: return,
            followWishlist = followWishlist,
            cards = if(entryId==null)current.pendingEntries else 1L,
            copies = if(entryId==null)current.pendingCopies else state.entries.firstOrNull {it.id==entryId}?.quantity ?: return,
            retainedCards = current.retainedWishlistEntries,
            retainedCopies = current.retainedWishlistCopies,
            omittedRecords = current.files.filter {it.selected}.sumOf {it.invalidRecords+it.unresolvedRecords},
            excludedCards = current.excludedEntries,
            repeatedFiles = current.files.count {it.selected && (it.duplicate || it.previouslyParticipated)},
        )
        if((entryId==null && (action.omittedRecords>0L || action.excludedCards>0L)) || action.repeatedFiles>0) {
            confirm=action
        } else {
            viewModel.action(action.destination,action.entryId,action.generation,action.payloadVersion,action.entryVersion,false,false,action.session,action.followWishlist)
        }
    }

    LaunchedEffect(confirm) { if(confirm!=null) { details=false;listState.animateScrollToItem(0) } }
    LaunchedEffect(state.ownerConsent) { confirm=null;discard=null;correcting=null }
    LaunchedEffect(summary?.owner) { confirm=null;discard=null;correcting=null }
    val completionMessage=state.completionNotice?.let { notice -> stringResource(when { notice.partial && notice.destination==TransferDestination.COLLECTION->R.string.import_partial_collection_message;notice.partial->R.string.import_partial_wishlist_message;notice.destination==TransferDestination.COLLECTION->R.string.import_added_collection_message;else->R.string.import_added_wishlist_message },notice.entries,notice.copies) }
    LaunchedEffect(state.completionNotice?.id,state.presentationSession) {
        completionMessage?.let { toast.show(it,MagicToastType.SUCCESS);viewModel.clearCompletionNotice() }
    }
    val errorMessage=state.error?.let { transferFailureLabel(it) }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { error ->
            toast.show(error, MagicToastType.ERROR)
            if(summary==null && !state.loading && !state.needsOwnerChoice)unavailableError=error
            viewModel.clearError()
        }
    }
    Box(Modifier.fillMaxSize()) {
        ThemeBackground(Modifier.fillMaxSize())
        Scaffold(
            containerColor=Color.Transparent,
            contentWindowInsets=WindowInsets.safeDrawing,
            topBar={
                Column {
                TopAppBar(
                    title={Text(stringResource(if(details)R.string.import_card_issues else R.string.import_review),style=ty.titleLarge,color=mc.textPrimary)},
                    navigationIcon={IconButton(onClick={if(details)closeDetails()else onBack()}) {Icon(Icons.AutoMirrored.Filled.ArrowBack,stringResource(R.string.action_back),tint=mc.textPrimary)}},
                    actions={if(!details)IconButton(onClick={openDetails()}) {Icon(Icons.Default.Info,stringResource(R.string.import_card_issues),tint=mc.textPrimary)}},
                    colors=TopAppBarDefaults.topAppBarColors(containerColor=mc.backgroundSecondary),
                )
                if(!details && summary!=null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=spacing.lg),verticalAlignment=Alignment.CenterVertically) {
                        Text(stringResource(R.string.import_remaining_counts,summary.pendingEntries,summary.pendingCopies),style=ty.bodyMedium,color=mc.textSecondary,modifier=Modifier.weight(1f))
                        IconButton(onClick={discard=viewModel.captureDiscard()},enabled=summary.phase!=TransferPhase.DISCARDED && (summary.pendingEntries>0L || summary.retainedWishlistEntries>0L || state.cards.isNotEmpty())) {Icon(Icons.Rounded.Delete,stringResource(R.string.card_queue_clear_cd),tint=mc.lifeNegative)}
                    }
                    Box(Modifier.fillMaxWidth().padding(horizontal=spacing.lg,vertical=spacing.xs)) {
                        QueueToggleRow(title=stringResource(R.string.scanner_queue_auto_delete_title),subtitle=stringResource(R.string.scanner_queue_auto_delete_desc),checked=state.deleteOnAdd,onCheckedChange=viewModel::setDeleteOnAdd,isListInverted=state.inverted,updateSorting=viewModel::toggleInversion)
                    }
                    if(state.cards.isNotEmpty() || state.previous!=null || state.next!=null)Row(Modifier.fillMaxWidth().padding(horizontal=spacing.lg),verticalAlignment=Alignment.CenterVertically) {
                        Text(stringResource(R.string.import_loaded_count,state.entries.size),style=ty.bodySmall,color=mc.textSecondary,modifier=Modifier.weight(1f))
                        IconButton(onClick=viewModel::previousPage,enabled=state.previous!=null && !state.paging && !state.loading) {Icon(Icons.Default.NavigateBefore,stringResource(R.string.import_previous_cards),tint=if(state.previous!=null)mc.textPrimary else mc.textDisabled)}
                        IconButton(onClick=viewModel::nextPage,enabled=state.next!=null && !state.paging && !state.loading) {Icon(Icons.Default.NavigateNext,stringResource(R.string.import_next_cards),tint=if(state.next!=null)mc.textPrimary else mc.textDisabled)}
                    }
                    if(state.paging)MagicProgressBar()
                }
                }
            },
            bottomBar={
                if(!details && summary!=null)Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(spacing.lg),verticalArrangement=Arrangement.spacedBy(spacing.sm)) {
                    if(summary.pendingEntries>0L) {
                        val enabled=summary.phase in setOf(TransferPhase.REVIEW_READY,TransferPhase.REVIEW_REQUIRED) && state.decisions.isEmpty() && summary.invalidPendingEntries==0L
                        MagicCtaButton(onClick={request(TransferDestination.COLLECTION,null)},text=stringResource(R.string.import_add_all_collection),enabled=enabled,modifier=Modifier.fillMaxWidth())
                        MagicCtaButton(onClick={request(TransferDestination.WISHLIST,null)},text=stringResource(R.string.import_add_all_wishlist),enabled=enabled,style=MagicCtaStyle.Outlined,modifier=Modifier.fillMaxWidth())
                    } else if(summary.phase in setOf(TransferPhase.COMPLETED,TransferPhase.COMPLETED_WITH_EXCLUSIONS,TransferPhase.DISCARDED))MagicCtaButton(onClick=onBack,text=stringResource(R.string.import_done),modifier=Modifier.fillMaxWidth())
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state=activeList,
                modifier=Modifier.fillMaxSize(),
                contentPadding=PaddingValues(vertical=spacing.md),
                verticalArrangement=Arrangement.Top,
            ) {
                confirm?.let { action -> item {
                    TransferPanel {
                        Text(stringResource(R.string.import_review_issues),style=ty.titleMedium,color=mc.textPrimary)
                        Text(if(action.entryId==null)stringResource(R.string.import_bulk_confirmation,action.cards,action.copies,action.destination.name.lowercase())else stringResource(R.string.import_single_confirmation,action.copies,action.destination.name.lowercase()),style=ty.bodyMedium,color=mc.textSecondary)
                        if(action.entryId==null && (action.excludedCards>0L || action.omittedRecords>0L))Row(Modifier.toggleable(value=acceptLosses,role=androidx.compose.ui.semantics.Role.Checkbox,onValueChange={acceptLosses=it}).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(checked=acceptLosses,onCheckedChange=null)
                            Text(stringResource(R.string.import_accept_omissions,action.omittedRecords,action.excludedCards),color=mc.textPrimary,modifier=Modifier.weight(1f))
                        }
                        if(action.repeatedFiles>0)Row(Modifier.toggleable(value=acceptRepeats,role=androidx.compose.ui.semantics.Role.Checkbox,onValueChange={acceptRepeats=it}).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(checked=acceptRepeats,onCheckedChange=null)
                            Text(stringResource(R.string.import_accept_repeated,action.repeatedFiles),color=mc.textPrimary,modifier=Modifier.weight(1f))
                        }
                        MagicCtaButton(text=stringResource(R.string.import_copy_23),enabled=(action.entryId!=null || action.excludedCards+action.omittedRecords==0L || acceptLosses) && (action.repeatedFiles==0 || acceptRepeats),onClick={viewModel.action(action.destination,action.entryId,action.generation,action.payloadVersion,action.entryVersion,acceptLosses,acceptRepeats,action.session,action.followWishlist);confirm=null},modifier=Modifier.fillMaxWidth())
                        MagicCtaButton(text=stringResource(R.string.action_cancel),style=MagicCtaStyle.Ghost,onClick={confirm=null},modifier=Modifier.fillMaxWidth())
                    }
                } }
                if(!details && received.any { it!=viewModel.id.value })item {
                    MagicCtaButton(text=stringResource(R.string.import_next_receipt),style=MagicCtaStyle.Outlined,onClick={received.firstOrNull {it!=viewModel.id.value}?.let {intake.routed(it);onRedirect(it)}},modifier=Modifier.fillMaxWidth())
                }
                if(!details && summary!=null)item {
                    val preparing=summary.phase in setOf(TransferPhase.RECEIVING,TransferPhase.PARSING,TransferPhase.RESOLVING,TransferPhase.APPLYING)
                    if(preparing){MagicProgressBar();Text(transferPhaseLabel(summary.phase),color=mc.textSecondary,modifier=Modifier.padding(horizontal=spacing.lg))}
                    else if(summary.pendingEntries==0L && state.cards.isEmpty())EmptyState(title=stringResource(if(summary.phase in setOf(TransferPhase.COMPLETED,TransferPhase.COMPLETED_WITH_EXCLUSIONS))R.string.import_completed else R.string.import_no_cards),actionLabel=stringResource(R.string.import_details),onAction={openDetails()},compact=true,modifier=Modifier.fillMaxWidth())
                    if(state.decisions.isNotEmpty() || summary.invalidPendingEntries>0L)MagicCtaButton(text=stringResource(R.string.import_review_issues),style=MagicCtaStyle.Outlined,onClick={openDetails()},modifier=Modifier.padding(horizontal=spacing.lg))
                }
                if(state.loading)item {MagicProgressBar();Text(stringResource(R.string.import_preparing),color=mc.textSecondary,modifier=Modifier.padding(spacing.lg))}
                if(state.needsOwnerChoice)item {
                    TransferPanel {
                        TransferSectionTitle(stringResource(R.string.import_owner_title),stringResource(if(state.ownerChoice==TransferOwnerChoice.OTHER_ACCOUNT)R.string.import_other_owner else R.string.import_owner_confirm))
                        if(state.ownerChoice!=TransferOwnerChoice.OTHER_ACCOUNT)MagicCtaButton(onClick={state.ownerConsent?.let(viewModel::bindExplicitly)},text=stringResource(R.string.import_review),modifier=Modifier.fillMaxWidth())
                    }
                }
                if(summary==null && !state.loading && !state.needsOwnerChoice)item {
                    FullErrorState(message=unavailableError ?: stringResource(R.string.import_unavailable),retryLabel=stringResource(R.string.action_back),onRetry=onBack,modifier=Modifier.fillMaxWidth())
                }
            if (summary != null) {
                if(details) {
                item {
                    TransferPanel {
                        TransferSectionTitle(stringResource(R.string.import_card_issues), transferPhaseLabel(summary.phase))
                        Text(stringResource(R.string.import_pages_help),style=ty.bodyMedium,color=mc.textSecondary)
                        if(state.decisions.isEmpty() && summary.invalidPendingEntries==0L && summary.files.none { it.invalidRecords>0L || it.unresolvedRecords>0L || it.phase in setOf(TransferPhase.REJECTED,TransferPhase.FAILED_RETRYABLE) })Text(stringResource(R.string.import_no_issues),style=ty.bodyMedium,color=mc.lifePositive)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            if(summary.phase in setOf(TransferPhase.PARSING,TransferPhase.RESOLVING,TransferPhase.REVIEW_READY,TransferPhase.REVIEW_REQUIRED)) MagicCtaButton(
                                onClick = viewModel::pause,
                                text = stringResource(R.string.import_copy_7),
                                style = MagicCtaStyle.Outlined,
                                modifier = Modifier.heightIn(min = actionHeight),
                            )
                            if(summary.phase in setOf(TransferPhase.PAUSED_BY_USER,TransferPhase.FAILED_RETRYABLE)) MagicCtaButton(
                                onClick = viewModel::resume,
                                text = stringResource(R.string.import_copy_8),
                                style = MagicCtaStyle.Outlined,
                                modifier = Modifier.heightIn(min = actionHeight),
                            )
                        }
                    }
                }

                val displayedFiles=summary.files.filter { !it.retired && (it.phase in setOf(TransferPhase.REJECTED,TransferPhase.FAILED_RETRYABLE) || (!it.selected && (it.duplicate || it.previouslyParticipated))) }
                if(displayedFiles.isNotEmpty()) {
                items(displayedFiles, key = { it.id.value }) { file ->
                    TransferPanel {
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            Text(
                                stringResource(R.string.import_file_number,file.order+1),
                                style = ty.titleMedium,
                                color = mc.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            TransferStatusPill(transferPhaseLabel(file.phase))
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            if (file.duplicate || file.previouslyParticipated) {
                                MagicCtaButton(
                                    onClick = { viewModel.includeRepeated(file) },
                                    text = stringResource(R.string.import_copy_11),
                                    enabled = !file.retired && !summary.filesFrozen,
                                    style = MagicCtaStyle.Outlined,
                                    modifier = Modifier.heightIn(min = actionHeight),
                                )
                            }
                        }
                        if (file.phase in setOf(TransferPhase.REJECTED, TransferPhase.FAILED_RETRYABLE)) {
                            Text(stringResource(R.string.import_recovery_help),style=ty.bodyMedium,color=mc.textSecondary)
                            MagicCtaButton(text=stringResource(R.string.import_skip_unreadable),style=MagicCtaStyle.Outlined,onClick={viewModel.selectFile(file)},enabled=file.selected && !summary.filesFrozen,modifier=Modifier.fillMaxWidth())
                            MagicCtaButton(
                                onClick = {
                                    replacementFile=file
                                    replacementConsent=viewModel.captureSession()
                                    replacementId = file.id.value
                                    replacementGeneration = summary.generation
                                    replacementPicker.launch(AndroidTransferDelivery.mimeTypes)
                                },
                                text = stringResource(R.string.import_copy_12),
                                enabled = !file.retired && !summary.filesFrozen,
                                style = MagicCtaStyle.Outlined,
                                modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                            )
                        }
                    }
                }

                }
                items(state.decisions, key = { "decision:${it.entryId}" }) { decision ->
                    TransferPanel {
                        Text(state.decisionNames[decision.entryId] ?: stringResource(R.string.import_card_decision),color=mc.textPrimary,style=ty.titleMedium)
                        Text(stringResource(R.string.import_decision_attributes,decision.entry.quantity,decision.entry.language,decision.entry.condition),color=mc.textSecondary,style=ty.bodySmall)
                        TransferSectionTitle(
                            title = stringResource(R.string.import_copy_13),
                            subtitle = stringResource(R.string.import_copy_14),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            MagicCtaButton(
                                onClick = { viewModel.decision(decision, TransferReviewDecision.KEEP_EDIT) },
                                text = stringResource(R.string.import_copy_15),
                                style = MagicCtaStyle.Outlined,
                                modifier = Modifier.heightIn(min = actionHeight),
                            )
                            MagicCtaButton(
                                onClick = { viewModel.decision(decision, TransferReviewDecision.USE_SOURCE) },
                                text = stringResource(R.string.import_copy_16),
                                style = MagicCtaStyle.Outlined,
                                modifier = Modifier.heightIn(min = actionHeight),
                            )
                            MagicCtaButton(
                                onClick = { viewModel.decision(decision, TransferReviewDecision.DISMISS_REMOVED) },
                                text = stringResource(R.string.import_copy_17),
                                style = MagicCtaStyle.Outlined,
                                modifier = Modifier.heightIn(min = actionHeight),
                            )
                        }
                    }
                }

                }
                if(!details) {
                items(state.cards, key = { it.id }) { card ->
                    val entry = state.entries.first { it.id == card.id }
                    Column(Modifier.animateItem()) {
                        QueueCardItem(
                            card,
                            LocalPreferredCurrency.current,
                            card.card.scryfallId in state.ownedPrintings,
                            entry.state != "PENDING" || entry.activeActionId != null || entry.appliedQuantity > 0L || summary.phase !in setOf(TransferPhase.REVIEW_READY,TransferPhase.REVIEW_REQUIRED),
                            onEdit = {
                                if (entry.quantity > Int.MAX_VALUE.toLong()) {
                                    correcting = entry
                                    quantityConsent=viewModel.captureSession()
                                    correctedQuantity = ""
                                } else {
                                    viewModel.edit(card)
                                }
                            },
                            onDelete = { viewModel.exclude(card) },
                            onAddToCollection = { request(TransferDestination.COLLECTION, card.id) },
                            onAddToWishlist = { request(TransferDestination.WISHLIST, card.id) },
                            onClick = { viewModel.expandImage(card.card.imageNormal ?: card.card.imageArtCrop.orEmpty()) },
                            onDuplicate = { viewModel.duplicate(card) },
                            onIncrement = { viewModel.adjust(card, 1) },
                            onDecrement = { viewModel.adjust(card, -1) },
                            displayQuantity = entry.quantity,
                            showPrice = true,
                            showDuplicate = true,
                            duplicateActionEnabled=entry.state=="PENDING" && entry.activeActionId==null && entry.appliedQuantity==0L && entry.quantity<=Int.MAX_VALUE.toLong() && summary.phase in setOf(TransferPhase.REVIEW_READY,TransferPhase.REVIEW_REQUIRED),
                            collectionActionEnabled=entry.quantity<=Int.MAX_VALUE.toLong() && ((entry.state=="WISHLIST_APPLIED" && summary.phase!=TransferPhase.DISCARDED) || (entry.state=="PENDING" && entry.activeActionId==null && entry.appliedQuantity==0L && summary.phase in setOf(TransferPhase.REVIEW_READY,TransferPhase.REVIEW_REQUIRED))),
                            wishlistActionEnabled=entry.quantity<=Int.MAX_VALUE.toLong() && entry.state=="PENDING" && entry.activeActionId==null && entry.appliedQuantity==0L && summary.phase in setOf(TransferPhase.REVIEW_READY,TransferPhase.REVIEW_REQUIRED),
                            quantityEditable = entry.quantity <= Int.MAX_VALUE.toLong(),
                            onQuantityClick = null,
                        )
                        if(entry.state in setOf("APPLIED","WISHLIST_APPLIED"))Text(stringResource(if(entry.state=="APPLIED")R.string.import_added_collection else R.string.import_added_wishlist),color=mc.lifePositive,style=ty.labelMedium,modifier=Modifier.padding(horizontal=spacing.lg))
                        if(entry.quantity>Int.MAX_VALUE.toLong()) Text(stringResource(R.string.import_quantity_unsupported),color=mc.lifeNegative,style=ty.bodySmall,modifier=Modifier.padding(horizontal=spacing.lg))
                    }
                }
                }
                if(details) {
                items(state.cards.filter { card -> state.entries.any { it.id==card.id && it.quantity>Int.MAX_VALUE.toLong() && it.state=="PENDING" } },key={"issue:${it.id}"}) { card ->
                    val entry=state.entries.first { it.id==card.id }
                    TransferPanel {
                        Text(card.card.name,style=ty.titleMedium,color=mc.textPrimary)
                        Text(stringResource(R.string.import_original_quantity,entry.quantity),style=ty.bodyMedium,color=mc.textSecondary)
                        MagicCtaButton(text=stringResource(R.string.import_copy_29),onClick={correcting=entry;quantityConsent=viewModel.captureSession();correctedQuantity=""},modifier=Modifier.fillMaxWidth())
                        MagicCtaButton(text=stringResource(R.string.action_remove),style=MagicCtaStyle.Outlined,onClick={viewModel.exclude(card)},modifier=Modifier.fillMaxWidth())
                    }
                }
                state.errors?.takeIf { it.total>0L }?.let { errors ->
                    item {
                        TransferSectionTitle(
                            title = stringResource(R.string.import_copy_22),
                            subtitle = stringResource(R.string.import_error_count,errors.total),
                        )
                    }
                    items(errors.examples, key = { "error:${it.file.value}:${it.ordinal}" }) { error ->
                        TransferPanel {
                            Text(stringResource(R.string.import_record_number,error.ordinal), style = ty.labelLarge, color = mc.textSecondary)
                            Text(error.preview, style = ty.bodySmall, color = mc.textPrimary)
                        }
                    }
                }
            }
        }
        }
        if(state.editing==null)key(state.presentationSession) { MagicToastHost(toast) }
        }
        }
    }

    discard?.let { consent ->
        MagicAlertDialog(
            onDismissRequest = { discard = null },
            title = stringResource(R.string.import_clear_title),
            text = stringResource(R.string.import_clear_description),
            confirmColor = MagicCtaColor.Error,
            buttons = {
                MagicCtaButton(
                    onClick = { viewModel.discard(consent); discard = null },
                    text = stringResource(R.string.import_copy_27),
                    color = MagicCtaColor.Error,
                    modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                )
                MagicCtaButton(
                    onClick = { discard = null },
                    text = stringResource(R.string.import_copy_28),
                    style = MagicCtaStyle.Outlined,
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
            extraContent = { key(state.presentationSession) { MagicToastHost(toast) } },
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
            title = stringResource(R.string.import_copy_29),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Text(
                        stringResource(R.string.import_original_quantity,entry.quantity),
                        color = mc.textSecondary,
                        style = ty.bodySmall,
                    )
                    OutlinedTextField(
                        value = correctedQuantity,
                        onValueChange = { correctedQuantity = it.filter(Char::isDigit).take(19) },
                        label = { Text(stringResource(R.string.import_copy_30)) },
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
                        chosen?.let { viewModel.correctQuantity(entry.id, entry.version, it, quantityConsent); correcting = null }
                    },
                    text = stringResource(R.string.import_copy_31),
                    enabled = chosen != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight),
                )
                MagicCtaButton(
                    onClick = { correcting = null },
                    text = stringResource(R.string.import_copy_32),
                    style = MagicCtaStyle.Outlined,
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



@Composable
private fun transferPhaseLabel(phase: TransferPhase): String = stringResource(when(phase) {
    TransferPhase.RECEIVING, TransferPhase.PARSING -> R.string.import_receiving
    TransferPhase.RESOLVING -> R.string.import_finding
    TransferPhase.APPLYING -> R.string.import_applying
    TransferPhase.WAITING_NETWORK -> R.string.import_wait_network
    TransferPhase.PAUSED_BY_USER, TransferPhase.PAUSED_OWNER -> R.string.import_saved
    TransferPhase.COMPLETED, TransferPhase.COMPLETED_WITH_EXCLUSIONS -> R.string.import_completed
    TransferPhase.REVIEW_READY -> R.string.import_ready
    TransferPhase.REVIEW_REQUIRED, TransferPhase.WAITING_FILE_DECISION, TransferPhase.REJECTED -> R.string.import_review_issues
    TransferPhase.FAILED_RETRYABLE -> R.string.import_retry
    TransferPhase.DISCARDED -> R.string.import_discarded
})

@Composable
private fun transferFailureLabel(failure: TransferPresentationFailure): String = stringResource(when(failure) {
    TransferPresentationFailure.STORAGE -> R.string.import_failure_storage
    TransferPresentationFailure.RECEIVE -> R.string.import_failure_receive
    TransferPresentationFailure.REVIEW_CHANGED -> R.string.import_failure_review
    TransferPresentationFailure.PAGE -> R.string.import_failure_page
    TransferPresentationFailure.QUANTITY_OVERFLOW -> R.string.import_failure_quantity
    TransferPresentationFailure.VARIANT_COLLISION -> R.string.import_failure_variant_collision
    TransferPresentationFailure.OWNER -> R.string.import_failure_owner
    TransferPresentationFailure.REPORT -> R.string.import_failure_report
    TransferPresentationFailure.VARIANTS -> R.string.import_failure_variants
    TransferPresentationFailure.UNAVAILABLE -> R.string.import_unavailable
})
