package com.mmg.manahub.feature.collection.data

import androidx.room.withTransaction
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.TradeListOwner
import com.mmg.manahub.core.data.local.entity.LocalWishlistEntity
import com.mmg.manahub.core.domain.collection.transfer.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID

/** Wishlist checkpoints exercise real Room rollback independently of collection markers. */
enum class TransferWishlistCheckpoint { AFTER_WRITE, AFTER_MARKER, AFTER_PROVENANCE, AFTER_COMMIT }

/** Exact local wishlist commands retain pending remote delivery without repeating increments. */
class RoomTransferWishlistExecutor(
    private val database: MtgDatabase,
    private val sessions: TransferSessionGate,
    private val nowMillis: () -> Long,
    private val matchesObservedOwner: (TransferOwner) -> Boolean,
    private val checkpoint: suspend (TransferWishlistCheckpoint,Int) -> Unit = { _,_ -> },
) : TransferWishlistExecutor {
    private class Overflow(val entryId: String): Exception("Transfer wishlist overflow")
    override suspend fun runSlice(action: TransferActionId, owner: TransferOwner): TransferWishlistApplyResult {
        val initial=(sessions.currentSession as? TransferSession.Available)?.takeIf { it.owner==owner && matchesObservedOwner(owner) }
            ?: return TransferWishlistApplyResult.WAITING
        val key=owner.storageKey()
        val localOwner=when(owner) { is TransferOwner.Account -> owner.id; is TransferOwner.VerifiedGuest -> TradeListOwner.GUEST }
        val dao=database.collectionTransferDao()
        var jobId: String?=null
        val result=try { sessions.withOwner(owner) { captured,sessionEnsure ->
            val ensure={ sessionEnsure(); if(!matchesObservedOwner(owner))throw TransferSessionChangedException() }
            if(captured!=initial)return@withOwner TransferWishlistApplyResult.WAITING
            try {
                database.withTransaction {
                    ensure(); currentCoroutineContext().ensureActive()
                    val command=dao.action(action.value,key)
                    if(command?.destination=="WISHLIST" && command.phase=="CONFIRMED")dao.freezeAction(command.id,key)
                    ensure()
                }
                var committed=0
                val outcome=database.withTransaction {
                    ensure(); currentCoroutineContext().ensureActive()
                    val command=dao.action(action.value,key) ?: return@withTransaction TransferWishlistApplyResult.CONFIRMATION_REQUIRED
                    if(command.destination!="WISHLIST")return@withTransaction TransferWishlistApplyResult.WRONG_DESTINATION
                    jobId=command.jobId
                    if(command.phase=="COMPLETED")return@withTransaction TransferWishlistApplyResult.LOCAL_FINISHED
                    dao.resumeCollectionOwner(command.jobId,key)
                    val job=dao.getJob(command.jobId,key) ?: return@withTransaction TransferWishlistApplyResult.WAITING
                    if(command.phase!="FROZEN" || command.generation!=job.generation || !job.filesFrozen || job.phase !in setOf("REVIEW_READY","REVIEW_REQUIRED"))return@withTransaction TransferWishlistApplyResult.CONFIRMATION_REQUIRED
                    val rows=dao.pendingWishlistActionPage(command.id,key)
                    for((index,row) in rows.withIndex()) {
                        ensure(); currentCoroutineContext().ensureActive()
                        val entry=dao.reviewEntry(job.id,key,row.entryId) ?: error("Missing frozen entry")
                        require(entry.activeActionId==command.id && entry.entryVersion==row.entryVersion && entry.destination=="WISHLIST" && entry.state=="PENDING" && entry.appliedQuantity==0L && !entry.excluded && entry.error==null && entry.quantity==row.quantity && entry.scryfallId==row.scryfallId && entry.isFoil==row.isFoil && entry.condition==row.condition && entry.language==row.language)
                        val wishlist=database.localWishlistDao()
                        val existing=if(owner is TransferOwner.Account)wishlist.getByAttributes(row.scryfallId,false,row.isFoil,row.condition,row.language,localOwner)
                            else wishlist.provenLocalByAttributes(key,row.scryfallId,false,row.isFoil,row.condition,row.language)
                        val copies=try { transferCollectionQuantity(existing?.quantity,false,row.quantity) } catch(_: TransferQuantityOverflowException) { throw Overflow(row.entryId) }
                        val wanted=existing?.copy(quantity=copies,synced=false) ?: LocalWishlistEntity(UUID.randomUUID().toString(),row.scryfallId,copies,false,row.isFoil,row.condition,row.language,false,nowMillis(),localOwner)
                        if(existing==null && owner is TransferOwner.VerifiedGuest)wishlist.insertProvenLocal(wanted,key)
                        else if(existing==null)wishlist.insert(wanted) else wishlist.update(wanted)
                        dao.stageWishlistDirty(wanted.id,key)
                        if(owner is TransferOwner.Account)dao.advancePendingWishlistCleanup(owner.id,wanted.id,copies)
                        checkpoint(TransferWishlistCheckpoint.AFTER_WRITE,index)
                        require(dao.markWishlistEntryApplied(job.id,key,command.id,row.entryId)==1)
                        require(dao.markWishlistActionEntryApplied(command.id,key,row.entryId)==1)
                        checkpoint(TransferWishlistCheckpoint.AFTER_MARKER,index)
                        dao.recordWishlistParticipation(job.id,key,row.entryId,nowMillis())
                        checkpoint(TransferWishlistCheckpoint.AFTER_PROVENANCE,index)
                    }
                    dao.completeWishlistBatch(job.id,key,command.id,nowMillis())
                    ensure(); currentCoroutineContext().ensureActive(); committed=rows.size
                    if(dao.action(command.id,key)!!.phase=="COMPLETED")TransferWishlistApplyResult.LOCAL_FINISHED else TransferWishlistApplyResult.CONTINUE
                }
                if(committed>0)checkpoint(TransferWishlistCheckpoint.AFTER_COMMIT,committed)
                outcome
            } catch(error: Overflow) {
                database.withTransaction { ensure(); require(dao.requireWishlistReview(jobId!!,key,action.value,error.entryId)); ensure() }
                TransferWishlistApplyResult.REVIEW_REQUIRED
            }
        } ?: TransferWishlistApplyResult.WAITING } catch(cancelled: CancellationException) { throw cancelled }
          catch(_: Exception) { TransferWishlistApplyResult.FAILED_RETRYABLE }
        return if(sessions.currentSession==initial && matchesObservedOwner(owner))result else TransferWishlistApplyResult.WAITING
    }
}
