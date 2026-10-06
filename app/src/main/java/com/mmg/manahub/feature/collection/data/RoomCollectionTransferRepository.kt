package com.mmg.manahub.feature.collection.data

import androidx.room.withTransaction
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.entity.*
import com.mmg.manahub.core.domain.collection.transfer.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

/** Owner-serialized persistence facade exposes bounded review data and deliberate commands only. */
class RoomCollectionTransferRepository(
    private val database: MtgDatabase,
    private val sessions: TransferSessionGate,
    private val matchesObservedOwner: (TransferOwner)->Boolean,
    private val nowMillis: ()->Long=System::currentTimeMillis,
    private val scheduler: TransferWorkScheduler?=null,
): CollectionTransferRepository {
    private val dao get()=database.collectionTransferDao()
    private fun available(owner: TransferOwner)=matchesObservedOwner(owner) && (sessions.currentSession as? TransferSession.Available)?.owner==owner
    private suspend fun <T> read(owner: TransferOwner, operation: suspend ()->T): T {
        if(!available(owner))throw TransferReadException(TransferError.OWNER_UNAVAILABLE)
        return sessions.withOwner(owner) { _,guard ->
            database.withTransaction {
                guard(); if(!matchesObservedOwner(owner))throw TransferReadException(TransferError.OWNER_CHANGED)
                val result=operation()
                guard(); if(!matchesObservedOwner(owner))throw TransferReadException(TransferError.OWNER_CHANGED)
                result
            }
        } ?: throw TransferReadException(TransferError.OWNER_CHANGED)
    }
    private suspend fun mutate(owner: TransferOwner, operation: suspend ()->Boolean): TransferMutationResult {
        if(!available(owner))return TransferMutationResult.Rejected(TransferError.OWNER_UNAVAILABLE)
        return try {
            if(read(owner,operation))TransferMutationResult.Accepted else TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: TransferSessionChangedException) { TransferMutationResult.Rejected(TransferError.OWNER_CHANGED) }
        catch(error: TransferReadException) { TransferMutationResult.Rejected(error.error) }
        catch(_: Exception) { TransferMutationResult.Rejected(TransferError.STORAGE_FAILURE) }
    }
    private fun phase(value: String)=TransferPhase.entries.firstOrNull { it.name==value } ?: TransferPhase.FAILED_RETRYABLE
    private fun error(value: String?)=value?.let { name -> TransferError.entries.firstOrNull { it.name==name } ?: TransferError.STRUCTURAL_FILE }
    private fun file(value: CollectionTransferFileEntity)=TransferFileSummary(TransferFileId(value.id),value.fileOrder,CollectionFileFormat.entries.firstOrNull { it.name==value.format },phase(value.phase),value.selected,value.duplicateOf!=null,value.priorParticipation,value.bytes,value.dataRecords,value.preambleRecords,value.validRecords,value.invalidRecords,value.resolvedRecords,value.unresolvedRecords,error(value.error),value.retired,value.copies,runCatching { kotlinx.serialization.json.Json.decodeFromString<List<String>>(value.unrepresentedColumns).take(256).map { it.take(300) } }.getOrDefault(emptyList()))
    private fun entry(value: CollectionImportEntryEntity)=TransferReviewEntry(value.id,value.scryfallId,value.isFoil,value.condition,value.language,value.quantity,value.appliedQuantity,value.excluded,error(value.error),TransferDestination.valueOf(value.destination),value.entryVersion,value.activeActionId?.let(::TransferActionId),value.state)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSummary(id: TransferJobId, owner: TransferOwner): Flow<TransferSummary?> = sessions.sessions.flatMapLatest { captured ->
        flow {
            emit(null)
            if((captured as? TransferSession.Available)?.owner!=owner || !matchesObservedOwner(owner))return@flow
            combine(dao.observeJob(id.value,owner.storageKey()),dao.observeCurrentFiles(id.value,owner.storageKey())) { _,_ -> Unit }.collect {
                val summary=read(owner) {
                    val job=dao.getJob(id.value,owner.storageKey()) ?: return@read SummaryResult(null)
                    val totals=dao.repositoryTotals(id.value,owner.storageKey())
                    val wishlist=dao.repositoryWishlistTotals(id.value,owner.storageKey())
                    val retained=dao.repositoryRetainedWishlistTotals(id.value,owner.storageKey())
                    SummaryResult(TransferSummary(id,owner,phase(job.phase),job.generation,job.payloadVersion,dao.currentFiles(id.value,owner.storageKey()).map(::file),job.acceptedCopies,job.appliedCopies,totals.pendingCopies,job.appliedEntries,totals.pendingEntries,error(job.error),totals.excludedEntries,job.intentRevision,wishlist.completedEntries,wishlist.completedCopies,job.filesFrozen,totals.invalidPendingEntries,retained.entries,retained.copies))
                }.summary
                if(sessions.currentSession==captured && matchesObservedOwner(owner))emit(summary) else emit(null)
            }
        }.catch { failure -> if(failure is CancellationException)throw failure; emit(null) }
    }
    private data class SummaryResult(val summary: TransferSummary?)
    private suspend fun scheduled(id: TransferJobId,result: TransferMutationResult,cancel: Boolean=false): TransferMutationResult {
        if(result==TransferMutationResult.Accepted)try { if(cancel)scheduler?.cancel(id) else scheduler?.enqueue(id) }
        catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { }
        return result
    }
    override suspend fun bindReceipt(id: TransferJobId, owner: TransferOwner, authGeneration: Long, origin: TransferOrigin, destinationChosen: Boolean): TransferMutationResult {
        if(!available(owner))return TransferMutationResult.Rejected(TransferError.OWNER_UNAVAILABLE)
        return try {
            val result=read(owner) {
                if((sessions.currentSession as? TransferSession.Available)?.generation!=authGeneration)throw TransferReadException(TransferError.OWNER_CHANGED)
                if(dao.getJob(id.value,owner.storageKey())==null) {
                    val receipt=dao.getReceipt(id.value) ?: throw TransferReadException(TransferError.NOT_FOUND)
                    if(receipt.capturedOwner!=null && receipt.capturedOwner!=owner.storageKey())throw TransferReadException(TransferError.OWNER_CHANGED)
                    if(receipt.authGeneration!=authGeneration && !destinationChosen)throw TransferReadException(TransferError.OWNER_CHANGED)
                    if(receipt.phase=="RECEIVING")throw TransferReadException(TransferError.INVALID_SOURCE)
                }
                dao.bindReceiptOrExisting(id.value,owner.storageKey(),authGeneration,origin.name,nowMillis(),destinationChosen)
            } ?: return TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
            if(result!=id.value)TransferMutationResult.AlreadyReceived(TransferJobId(result)) else scheduled(id,TransferMutationResult.Accepted)
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: TransferSessionChangedException) { TransferMutationResult.Rejected(TransferError.OWNER_CHANGED) }
        catch(error: TransferReadException) { TransferMutationResult.Rejected(error.error) }
        catch(_: Exception) { TransferMutationResult.Rejected(TransferError.STORAGE_FAILURE) }
    }
    override suspend fun readPage(id: TransferJobId, owner: TransferOwner, cursor: TransferPageCursor?): TransferPage = read(owner) {
        val job=dao.getJob(id.value,owner.storageKey()) ?: throw TransferReadException(TransferError.NOT_FOUND)
        if(cursor!=null && cursor.generation!=job.generation)throw TransferReadException(TransferError.REVIEW_CHANGED)
        val rows=dao.reviewPage(id.value,owner.storageKey(),job.generation,cursor?.afterId ?: "")
        TransferPage(rows.map(::entry),rows.lastOrNull()?.takeIf { rows.size==50 }?.let { TransferPageCursor(job.generation,it.id) })
    }
    override suspend fun readReviewPage(id: TransferJobId, owner: TransferOwner, cursor: TransferReviewCursor?, scope: TransferReviewScope, direction: TransferPageDirection): TransferReviewPage = read(owner) {
        val job=dao.getJob(id.value,owner.storageKey()) ?: throw TransferReadException(TransferError.NOT_FOUND)
        if(cursor!=null && (cursor.jobId!=id || cursor.generation!=job.generation || cursor.scope!=scope))throw TransferReadException(TransferError.REVIEW_CHANGED)
        if(scope==TransferReviewScope.COLLECTION || scope==TransferReviewScope.WISHLIST) {
            val snapshots=when(direction) {
                TransferPageDirection.FORWARD -> dao.completedReviewAfter(id.value,owner.storageKey(),scope.name,cursor?.anchorId ?: "")
                TransferPageDirection.BACKWARD -> dao.completedReviewBefore(id.value,owner.storageKey(),scope.name,cursor?.anchorId).asReversed()
            }
            fun snapshotId(value: CollectionTransferActionEntryEntity)="${value.actionId}:${value.entryId}"
            val previous=snapshots.firstOrNull()?.takeIf { dao.completedReviewHasPrevious(id.value,owner.storageKey(),scope.name,snapshotId(it)) }?.let { TransferReviewCursor(id,job.generation,scope,snapshotId(it)) }
            val next=snapshots.lastOrNull()?.takeIf { dao.completedReviewHasNext(id.value,owner.storageKey(),scope.name,snapshotId(it)) }?.let { TransferReviewCursor(id,job.generation,scope,snapshotId(it)) }
            return@read TransferReviewPage(snapshots.map { value -> TransferReviewEntry(snapshotId(value),value.scryfallId,value.isFoil,value.condition,value.language,value.completedQuantity,if(scope==TransferReviewScope.COLLECTION)value.completedQuantity else 0L,false,null,TransferDestination.valueOf(scope.name),value.entryVersion,TransferActionId(value.actionId),"COMPLETED",value.entryId) },previous,next)
        }
        val rows=when(direction) {
            TransferPageDirection.FORWARD -> dao.scopedReviewAfter(id.value,owner.storageKey(),job.generation,scope.name,cursor?.anchorId ?: "")
            TransferPageDirection.BACKWARD -> dao.scopedReviewBefore(id.value,owner.storageKey(),job.generation,scope.name,cursor?.anchorId).asReversed()
        }
        val previous=rows.firstOrNull()?.takeIf { dao.scopedReviewHasPrevious(id.value,owner.storageKey(),job.generation,scope.name,it.id) }?.let { TransferReviewCursor(id,job.generation,scope,it.id) }
        val next=rows.lastOrNull()?.takeIf { dao.scopedReviewHasNext(id.value,owner.storageKey(),job.generation,scope.name,it.id) }?.let { TransferReviewCursor(id,job.generation,scope,it.id) }
        TransferReviewPage(rows.map(::entry),previous,next)
    }
    override suspend fun selectFile(id: TransferJobId, owner: TransferOwner, file: TransferFileId, generation: Long, selected: Boolean)=scheduled(id,mutate(owner) { dao.selectFile(id.value,owner.storageKey(),file.value,generation,selected) })
    override suspend fun includeRepeatedFile(id: TransferJobId, owner: TransferOwner, file: TransferFileId, generation: Long)=scheduled(id,mutate(owner) { dao.selectFile(id.value,owner.storageKey(),file.value,generation,true,true) })
    override suspend fun editPendingEntry(id: TransferJobId, owner: TransferOwner, generation: Long, entry: TransferReviewEntry)=mutate(owner) {
        val job=dao.getJob(id.value,owner.storageKey())
        if(job?.generation==generation && dao.collidingReviewEntry(id.value,owner.storageKey(),entry.id,entry.scryfallId,entry.isFoil,entry.condition,entry.language)!=null)throw TransferReadException(TransferError.VARIANT_COLLISION)
        job?.generation==generation && dao.editPendingEntry(id.value,owner.storageKey(),entry.id,entry.version,entry.scryfallId,entry.isFoil,entry.condition,entry.language,entry.quantity,entry.destination,entry.excluded)
    }
    override suspend fun acknowledgeReview(id: TransferJobId, owner: TransferOwner, confirmation: TransferConfirmation)=mutate(owner) { dao.acknowledgeReview(id.value,owner.storageKey(),confirmation) }
    override suspend fun duplicatePendingEntry(id: TransferJobId, owner: TransferOwner, generation: Long, payloadVersion: Long, entryId: String, entryVersion: Long, newEntryId: String)=mutate(owner) { dao.duplicatePendingEntry(id.value,owner.storageKey(),generation,payloadVersion,entryId,entryVersion,newEntryId,nowMillis()) }
    override suspend fun pause(id: TransferJobId, owner: TransferOwner)=scheduled(id,mutate(owner) { dao.pauseTransfer(id.value,owner.storageKey(),nowMillis()) },true)
    override suspend fun resume(id: TransferJobId, owner: TransferOwner)=scheduled(id,mutate(owner) { dao.resumeTransfer(id.value,owner.storageKey(),nowMillis()) })
    override suspend fun discardPending(id: TransferJobId, owner: TransferOwner, generation: Long, payloadVersion: Long)=scheduled(id,mutate(owner) { dao.discardTransferPending(id.value,owner.storageKey(),generation,payloadVersion,nowMillis()) },true)
    override suspend fun chooseDestination(id: TransferJobId, owner: TransferOwner, generation: Long, intentRevision: Long, destination: TransferDestination)=mutate(owner) { dao.chooseAllDestination(id.value,owner.storageKey(),generation,intentRevision,destination)!=null }
    override suspend fun confirmAction(owner: TransferOwner, request: TransferActionRequest)=scheduled(request.jobId,mutate(owner) { dao.confirmAction(owner.storageKey(),request,nowMillis()) })
    override suspend fun readAction(owner: TransferOwner, action: TransferActionId): TransferActionSummary = read(owner) {
        val value=dao.action(action.value,owner.storageKey()) ?: throw TransferReadException(TransferError.NOT_FOUND)
        val totals=dao.repositoryActionTotals(action.value,owner.storageKey())
        TransferActionSummary(action,TransferJobId(value.jobId),TransferDestination.valueOf(value.destination),TransferActionPhase.valueOf(value.phase),totals.entries,totals.copies,totals.completedEntries,totals.completedCopies)
    }
    override suspend fun reopenWishlistEntry(id: TransferJobId, owner: TransferOwner, entryId: String, version: Long, payloadVersion: Long)=mutate(owner) { dao.reopenWishlistEntry(id.value,owner.storageKey(),entryId,version,payloadVersion) }
    override suspend fun reopenWishlistSelection(id: TransferJobId, owner: TransferOwner, generation: Long, payloadVersion: Long)=mutate(owner) { dao.reopenWishlistSelection(id.value,owner.storageKey(),generation,payloadVersion) }
    override suspend fun readProvenance(id: TransferJobId, owner: TransferOwner, entryId: String): List<TransferEntryProvenance> = read(owner) { dao.provenance(id.value,owner.storageKey(),entryId).map { TransferEntryProvenance(TransferFileId(it.fileId),it.sourceRecords,it.sourceCopies,it.participated,it.wishlistParticipated) } }
    override suspend fun readErrorPreview(id: TransferJobId, owner: TransferOwner): TransferErrorPreview = read(owner) { TransferErrorPreview(dao.errorPreviewCount(id.value,owner.storageKey()),dao.errorPreviewPage(id.value,owner.storageKey(),"",0L).map { TransferErrorExample(TransferFileId(it.fileId),it.sourceOrdinal,it.error ?: "INVALID_SOURCE",it.preview.take(300)) }) }
    override suspend fun readFileInventory(id: TransferJobId, owner: TransferOwner, afterId: String): TransferFileInventoryPage = read(owner) {
        val files=dao.fileInventoryPage(id.value,owner.storageKey(),afterId)
        TransferFileInventoryPage(files.map(::file),files.lastOrNull()?.takeIf { files.size==50 }?.id)
    }
    override suspend fun readDecisions(id: TransferJobId, owner: TransferOwner, afterId: String): List<TransferPendingReviewDecision> = read(owner) { dao.reviewDecisionPage(id.value,owner.storageKey(),afterId).map { value -> TransferPendingReviewDecision(value.entryId,value.generation,value.reason,TransferReviewEntry(value.entryId,value.scryfallId,value.isFoil,value.condition,value.language,value.quantity,0L,value.excluded,TransferError.SOURCE_MEMBERSHIP_CHANGED,TransferDestination.valueOf(value.destination),value.entryVersion)) } }
    override suspend fun decideReviewEdit(id: TransferJobId, owner: TransferOwner, entryId: String, generation: Long, choice: TransferReviewDecision)=mutate(owner) { dao.decideReviewEdit(id.value,owner.storageKey(),entryId,generation,choice) }
}

