package com.mmg.manahub.feature.collection.data

import androidx.room.withTransaction
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.domain.collection.transfer.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.FileNotFoundException

/** Production orchestration keeps every executable checkpoint in the owner's Room database. */
class RoomCollectionTransferCoordinator(
    private val database: MtgDatabase,
    private val sessions: TransferSessionGate,
    private val matchesObservedOwner: (TransferOwner)->Boolean,
    private val files: AndroidCollectionTransferFileStore,
    private val resolver: DurableTransferResolver,
    private val review: RoomTransferReviewBuilder,
    private val collection: RoomTransferCollectionExecutor,
    private val wishlist: TransferWishlistExecutor,
    private val wishlistSync: TransferWishlistSync,
    private val scheduler: TransferWorkScheduler,
    private val nowMillis: ()->Long=System::currentTimeMillis,
): CollectionTransferCoordinator {
    private val dao get()=database.collectionTransferDao()
    private fun capture()=(sessions.currentSession as? TransferSession.Available)?.takeIf { matchesObservedOwner(it.owner) }
    private fun current(captured: TransferSession.Available)=sessions.currentSession==captured && matchesObservedOwner(captured.owner)
    private suspend fun job(id: TransferJobId,captured: TransferSession.Available)=sessions.withOwner(captured.owner) { _,ensure ->
        ensure(); if(!current(captured))return@withOwner null
        dao.getJob(id.value,captured.owner.storageKey()).takeIf { current(captured) }
    }
    override suspend fun hasImmediateWork(id: TransferJobId): Boolean {
        val captured=capture() ?: return false
        val value=job(id,captured) ?: return false
        if(value.phase in stopped || value.nextAttemptAt>nowMillis())return false
        if(dao.executableAction(id.value,captured.owner.storageKey())!=null)return current(captured)
        return !value.filesFrozen && value.phase in setOf("RECEIVING","PARSING","RESOLVING","WAITING_NETWORK") && current(captured)
    }
    override suspend fun runApplicationSlice(id: TransferJobId): TransferSliceResult {
        val captured=capture() ?: return TransferSliceResult.WAITING
        val job=job(id,captured) ?: return TransferSliceResult.WAITING
        if(job.phase in stopped)return TransferSliceResult.WAITING
        if(job.error=="STORAGE_FAILURE" && job.nextAttemptAt>nowMillis())return TransferSliceResult.CONTINUE
        collection.reconcileEvent(id,captured.owner)
        if(!current(captured))return TransferSliceResult.WAITING
        val command=dao.executableAction(id.value,captured.owner.storageKey()) ?: return TransferSliceResult.FINISHED
        val result=when(command.destination) {
            "COLLECTION" -> when(collection.runSlice(TransferActionId(command.id),captured.owner)) {
                TransferCollectionApplyResult.FINISHED -> TransferSliceResult.FINISHED
                TransferCollectionApplyResult.CONTINUE -> TransferSliceResult.CONTINUE
                TransferCollectionApplyResult.FAILED_RETRYABLE -> { deferApplication(id,captured); TransferSliceResult.CONTINUE }
                else -> TransferSliceResult.WAITING
            }
            "WISHLIST" -> when(wishlist.runSlice(TransferActionId(command.id),captured.owner)) {
                TransferWishlistApplyResult.LOCAL_FINISHED -> TransferSliceResult.FINISHED
                TransferWishlistApplyResult.CONTINUE -> TransferSliceResult.CONTINUE
                TransferWishlistApplyResult.FAILED_RETRYABLE -> { deferApplication(id,captured); TransferSliceResult.CONTINUE }
                else -> TransferSliceResult.WAITING
            }
            else -> TransferSliceResult.WAITING
        }
        if(!current(captured))return TransferSliceResult.WAITING
        return if(result==TransferSliceResult.FINISHED && dao.executableAction(id.value,captured.owner.storageKey())!=null)TransferSliceResult.CONTINUE else result
    }
    private suspend fun deferApplication(id: TransferJobId,captured: TransferSession.Available) {
        sessions.withOwner(captured.owner) { _,ensure -> database.withTransaction {
            ensure(); if(!current(captured))return@withTransaction
            val now=nowMillis(); dao.deferTransferApplication(id.value,captured.owner.storageKey(),if(now>Long.MAX_VALUE-10_000L)Long.MAX_VALUE else now+10_000L)
            ensure(); if(!current(captured))throw TransferSessionChangedException()
        } }
    }
    override suspend fun runPreparationSlice(id: TransferJobId): TransferSliceResult {
        val captured=capture() ?: return TransferSliceResult.WAITING
        var job=job(id,captured) ?: return TransferSliceResult.WAITING
        if(job.phase in stopped || job.phase in setOf("REVIEW_READY","REVIEW_REQUIRED","COMPLETED","COMPLETED_WITH_EXCLUSIONS") || job.filesFrozen)return TransferSliceResult.WAITING
        if(job.nextAttemptAt>nowMillis())return TransferSliceResult.CONTINUE
        try {
            if(job.phase in setOf("RECEIVING","PARSING")) {
                files.recoverReceipt(id)
                if(!current(captured))return TransferSliceResult.WAITING
                val source=dao.currentFiles(id.value,captured.owner.storageKey()).firstOrNull { it.selected && it.phase=="PARSING" }
                if(source!=null)try { files.verifySource(id,TransferFileId(source.id)); files.parseSource(id,TransferFileId(source.id)) }
                catch(error: java.io.IOException) {
                    if(error is TransferStorageException && error.category in setOf(TransferError.OWNER_CHANGED,TransferError.OWNER_UNAVAILABLE))throw error
                    fail(id,captured,"WAITING_FILE_DECISION",TransferError.INVALID_SOURCE,source.id)
                    return TransferSliceResult.WAITING
                }
                if(!current(captured))return TransferSliceResult.WAITING
                if(dao.currentFiles(id.value,captured.owner.storageKey()).any { it.selected && it.phase=="PARSING" })return TransferSliceResult.CONTINUE
            }
            val resolution=resolver.runSlice(id,captured.owner,1)
            if(!current(captured))return TransferSliceResult.WAITING
            job=job(id,captured) ?: return TransferSliceResult.WAITING
            if(job.phase=="FAILED_RETRYABLE")return TransferSliceResult.WAITING
            if(job.phase=="WAITING_NETWORK" || resolution==TransferSliceResult.CONTINUE)return TransferSliceResult.CONTINUE
            return review.runSlice(id,captured.owner,3).result
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: TransferStorageException) {
            if(error.category in setOf(TransferError.OWNER_CHANGED,TransferError.OWNER_UNAVAILABLE))return TransferSliceResult.WAITING
            fail(id,captured,"WAITING_FILE_DECISION",error.category); return TransferSliceResult.WAITING
        } catch(_: Exception) {
            fail(id,captured,"FAILED_RETRYABLE",TransferError.STORAGE_FAILURE); return TransferSliceResult.WAITING
        }
    }
    private suspend fun fail(id: TransferJobId,captured: TransferSession.Available,phase: String,error: TransferError,fileId: String?=null) {
        sessions.withOwner(captured.owner) { _,ensure -> database.withTransaction {
            ensure(); if(!current(captured))return@withTransaction
            dao.failTransferPreparation(id.value,captured.owner.storageKey(),phase,error.name,nowMillis(),fileId)
            ensure(); if(!current(captured))throw TransferSessionChangedException()
        } }
    }
    override suspend fun reconcile(owner: TransferOwner) {
        val captured=capture()?.takeIf { it.owner==owner } ?: return
        collection.reconcilePendingEvents(owner)
        var after=""
        while(current(captured)) {
            currentCoroutineContext().ensureActive()
            val page=dao.reconciliationPage(owner.storageKey(),after)
            for(value in page) {
                if(!current(captured))return
                val id=TransferJobId(value.id)
                if(value.phase=="PAUSED_OWNER")sessions.withOwner(owner) { _,ensure ->
                    ensure(); if(current(captured))dao.resumeCollectionOwner(value.id,owner.storageKey())
                }
                val refreshed=job(id,captured) ?: return
                if(refreshed.phase !in stopped && (refreshed.phase !in setOf("REVIEW_READY","REVIEW_REQUIRED") || dao.executableAction(id.value,owner.storageKey())!=null))scheduler.enqueue(id)
                after=value.id
            }
            if(page.size<50)break
        }
    }
    /** Startup and owner transitions cancel old delivery and reconcile persisted executable work. */
    fun start(scope: CoroutineScope): Job=scope.launch(Dispatchers.IO) {
        sessions.sessions.collectLatest { state ->
            scheduler.cancelWishlist()
            val available=state as? TransferSession.Available ?: return@collectLatest
            if(!matchesObservedOwner(available.owner))return@collectLatest
            reconcile(available.owner)
            coroutineScope {
                launch {
                    dao.observeReconciliationJobs(available.owner.storageKey()).collectLatest { jobs -> coroutineScope {
                        for(value in jobs)launch { scheduler.observeFinished(TransferJobId(value.id)).collectLatest { finished -> if(finished && current(available))reconcile(available.owner) } }
                    } }
                }
                val account=available.owner as? TransferOwner.Account ?: return@coroutineScope
                launch { scheduler.observeFinished(null).collectLatest { finished -> if(finished && current(available) && dao.hasWishlistPending(account.storageKey()))scheduler.enqueueWishlist() } }
                dao.observeWishlistPending(account.storageKey()).distinctUntilChanged().collectLatest { count ->
                    if(current(available) && count>0L)scheduler.enqueueWishlist()
                }
            }
        }
    }
    /** Delivery is independent from local commands and bounded to one ledger page per invocation. */
    suspend fun runWishlistDelivery(): TransferSliceResult {
        val captured=capture() ?: return TransferSliceResult.WAITING
        val owner=captured.owner as? TransferOwner.Account ?: return TransferSliceResult.WAITING
        val result=wishlistSync.runSlice(owner)
        if(!current(captured))return TransferSliceResult.WAITING
        return if(result==TransferSliceResult.WAITING && dao.hasWishlistPending(owner.storageKey()))TransferSliceResult.CONTINUE else result
    }
    private companion object { val stopped=setOf("PAUSED_BY_USER","PAUSED_OWNER","FAILED_RETRYABLE","DISCARDED","REJECTED","WAITING_FILE_DECISION") }
}
