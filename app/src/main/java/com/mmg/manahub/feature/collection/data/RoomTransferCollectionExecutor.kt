package com.mmg.manahub.feature.collection.data

import androidx.room.withTransaction
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.writeCollectionRow
import com.mmg.manahub.core.data.local.entity.CollectionTransferGuestRowEntity
import com.mmg.manahub.core.domain.collection.transfer.*
import kotlinx.coroutines.*

/** These checkpoints allow real Room rollback tests without replacing its transaction implementation. */
enum class TransferApplyCheckpoint { AFTER_INCREMENT, AFTER_MARKER, AFTER_PROVENANCE, BEFORE_COMMIT, AFTER_COMMIT, BEFORE_EVENT_ACK }

/** A command outcome carries no source/account details and never implies wishlist completion. */
enum class TransferCollectionApplyResult { CONTINUE, FINISHED, WAITING, REVIEW_REQUIRED, CONFIRMATION_REQUIRED, WRONG_DESTINATION, FAILED_RETRYABLE }

/** Explicit frozen collection subsets commit increments, markers and participation in one Room transaction. */
class RoomTransferCollectionExecutor(
    private val database: MtgDatabase,
    private val sessions: TransferSessionGate,
    private val nowMillis: () -> Long,
    private val emitCollectionChanged: suspend () -> Unit,
    private val checkpoint: suspend (TransferApplyCheckpoint,Int) -> Unit = { _,_ -> },
    private val matchesObservedOwner: (TransferOwner) -> Boolean = { (sessions.currentSession as? TransferSession.Available)?.owner==it },
) {
    private class Overflow(val entryId: String) : Exception("Transfer quantity overflow")
    private val dao get()=database.collectionTransferDao()

    suspend fun runSlice(actionId: TransferActionId, owner: TransferOwner): TransferCollectionApplyResult {
        val initial=(sessions.currentSession as? TransferSession.Available)?.takeIf { it.owner==owner && matchesObservedOwner(owner) }
            ?: return TransferCollectionApplyResult.WAITING
        val key=owner.storageKey()
        var jobId: String?=null
        var committed=0
        val result=try {
            sessions.withOwner(owner) { captured,sessionEnsure ->
                val ensure={ sessionEnsure(); if(!matchesObservedOwner(owner))throw TransferSessionChangedException() }
                if(captured!=initial) return@withOwner TransferCollectionApplyResult.WAITING
                try {
                    database.withTransaction {
                        ensure(); currentCoroutineContext().ensureActive()
                        val action=dao.action(actionId.value,key)
                        if(action?.destination=="COLLECTION" && action.phase=="CONFIRMED")dao.freezeAction(action.id,key)
                        ensure()
                    }
                    val outcome=database.withTransaction {
                        ensure(); currentCoroutineContext().ensureActive()
                        val action=dao.action(actionId.value,key) ?: return@withTransaction TransferCollectionApplyResult.CONFIRMATION_REQUIRED
                        if(action.destination!="COLLECTION") return@withTransaction TransferCollectionApplyResult.WRONG_DESTINATION
                        jobId=action.jobId
                        if(action.phase=="COMPLETED") return@withTransaction TransferCollectionApplyResult.FINISHED
                        dao.resumeCollectionOwner(action.jobId,key)
                        val frozen=dao.action(action.id,key)!!
                        val job=dao.getJob(action.jobId,key) ?: return@withTransaction TransferCollectionApplyResult.WAITING
                        if(frozen.phase!="FROZEN" || frozen.generation!=job.generation || !job.filesFrozen || job.phase !in setOf("REVIEW_READY","REVIEW_REQUIRED")) return@withTransaction TransferCollectionApplyResult.CONFIRMATION_REQUIRED
                        val rows=dao.pendingCollectionActionPage(action.id,key)
                        for((index,row) in rows.withIndex()) {
                            ensure(); currentCoroutineContext().ensureActive()
                            val entry=dao.reviewEntry(job.id,key,row.entryId) ?: error("Missing frozen entry")
                            require(entry.activeActionId==action.id && entry.entryVersion==row.entryVersion && entry.destination=="COLLECTION" && entry.state=="PENDING" && entry.appliedQuantity==0L && !entry.excluded && entry.error==null && entry.quantity==row.quantity && entry.scryfallId==row.scryfallId && entry.isFoil==row.isFoil && entry.condition==row.condition && entry.language==row.language)
                            if(row.quantity !in 1L..Int.MAX_VALUE.toLong()) throw Overflow(row.entryId)
                            val userId=(owner as? TransferOwner.Account)?.id
                            val collectionDao=database.userCardCollectionDao()
                            val existing=if(owner is TransferOwner.VerifiedGuest)dao.verifiedGuestCollectionRow(key,row.scryfallId,row.isFoil,row.condition,row.language)
                                else collectionDao.getByCompositeKey(userId!!,row.scryfallId,row.isFoil,row.condition,row.language)
                            val written=try { writeCollectionRow(collectionDao,existing,userId,row.scryfallId,row.isFoil,row.condition,row.language,row.quantity.toInt(),false,nowMillis(),strictOverflow=true).second }
                                catch(_: TransferQuantityOverflowException) { throw Overflow(row.entryId) }
                            if(owner is TransferOwner.VerifiedGuest) {
                                dao.recordGuestCollectionRow(CollectionTransferGuestRowEntity(written.id,key))
                                require(dao.verifiedGuestCollectionRow(key,row.scryfallId,row.isFoil,row.condition,row.language)?.id==written.id)
                            }
                            checkpoint(TransferApplyCheckpoint.AFTER_INCREMENT,index)
                            require(dao.markCollectionEntryApplied(job.id,key,action.id,row.entryId,row.quantity)==1)
                            require(dao.markCollectionActionEntryApplied(action.id,key,row.entryId)==1)
                            checkpoint(TransferApplyCheckpoint.AFTER_MARKER,index)
                            dao.recordAppliedParticipation(job.id,key,row.entryId,nowMillis())
                            checkpoint(TransferApplyCheckpoint.AFTER_PROVENANCE,index)
                        }
                        dao.completeCollectionBatch(job.id,key,action.id,nowMillis())
                        ensure(); currentCoroutineContext().ensureActive()
                        checkpoint(TransferApplyCheckpoint.BEFORE_COMMIT,rows.size)
                        ensure()
                        committed=rows.size
                        if(dao.action(action.id,key)!!.phase=="COMPLETED")TransferCollectionApplyResult.FINISHED else TransferCollectionApplyResult.CONTINUE
                    }
                    if(committed>0) checkpoint(TransferApplyCheckpoint.AFTER_COMMIT,committed)
                    outcome
                } catch(error: Overflow) {
                    committed=0
                    database.withTransaction { ensure(); dao.requireCollectionReview(jobId!!,key,actionId.value,error.entryId); ensure() }
                    TransferCollectionApplyResult.REVIEW_REQUIRED
                }
            } ?: TransferCollectionApplyResult.WAITING
        } catch(cancelled: CancellationException) { throw cancelled }
          catch(_: Exception) { TransferCollectionApplyResult.FAILED_RETRYABLE }
        if(sessions.currentSession!=initial || !matchesObservedOwner(owner)) return TransferCollectionApplyResult.WAITING
        if(result==TransferCollectionApplyResult.FINISHED && jobId!=null) reconcileEvent(TransferJobId(jobId!!),owner)
        return if(sessions.currentSession==initial && matchesObservedOwner(owner))result else TransferCollectionApplyResult.WAITING
    }

    suspend fun reconcileEvent(jobId: TransferJobId, owner: TransferOwner): Boolean = try { sessions.withOwner(owner) { _,sessionEnsure ->
        val ensure={ sessionEnsure(); if(!matchesObservedOwner(owner))throw TransferSessionChangedException() }
        val key=owner.storageKey()
        val job=dao.getJob(jobId.value,key) ?: return@withOwner false
        ensure()
        if(!job.collectionEventPending || job.appliedEntries==0L) return@withOwner true
        emitCollectionChanged()
        checkpoint(TransferApplyCheckpoint.BEFORE_EVENT_ACK,0)
        ensure()
        database.withTransaction { ensure(); dao.clearCollectionEvent(job.id,key,job.appliedEntries); ensure() }
        true
    } ?: false } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { false }

    suspend fun reconcilePendingEvents(owner: TransferOwner): Int {
        val captured=(sessions.currentSession as? TransferSession.Available)?.takeIf { it.owner==owner && matchesObservedOwner(owner) } ?: return 0
        var emitted=0
        var afterId=""
        while(true) {
            currentCoroutineContext().ensureActive()
            if(sessions.currentSession!=captured || !matchesObservedOwner(owner)) return 0
            val jobs=dao.pendingCollectionEventPage(owner.storageKey(),afterId)
            if(jobs.isEmpty())break
            for(job in jobs) {
                currentCoroutineContext().ensureActive()
                if(sessions.currentSession!=captured || !matchesObservedOwner(owner)) return 0
                if(reconcileEvent(TransferJobId(job.id),owner))emitted++
                afterId=job.id
            }
        }
        return if(sessions.currentSession==captured && matchesObservedOwner(owner))emitted else 0
    }
}

/** Wishlist work remains pending until its independent owner-scoped local transaction is implemented. */
class PendingTransferWishlistExecutor : TransferWishlistExecutor {
    override suspend fun runSlice(action: TransferActionId, owner: TransferOwner): TransferWishlistApplyResult =
        TransferWishlistApplyResult.UNAVAILABLE
}
