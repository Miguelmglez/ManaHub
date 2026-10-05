package com.mmg.manahub.feature.collection.data

import com.mmg.manahub.core.data.local.dao.CollectionTransferDao
import com.mmg.manahub.core.domain.collection.transfer.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A matching unfinished delivery is surfaced for resumption without merging or applying its contents. */
data class TransferReviewBuildResult(val result: TransferSliceResult, val resumeJob: TransferJobId? = null)

/** Session-bound review generation uses disk groups and preserves neutral destination decisions. */
class RoomTransferReviewBuilder(
    private val dao: CollectionTransferDao,
    private val session: () -> TransferSession,
    private val nowMillis: () -> Long,
) {
    suspend fun runSlice(id: TransferJobId, owner: TransferOwner, maxBatches: Int = 3): TransferReviewBuildResult {
        require(maxBatches in 1..100)
        val captured=(session() as? TransferSession.Available)?.takeIf { it.owner==owner }
            ?: return TransferReviewBuildResult(TransferSliceResult.WAITING)
        val key=when(owner) { is TransferOwner.Account -> "account:${owner.id}"; is TransferOwner.VerifiedGuest -> "guest:${owner.installationToken}" }
        var job=dao.getJob(id.value,key) ?: return TransferReviewBuildResult(TransferSliceResult.WAITING)
        if(session()!=captured) return TransferReviewBuildResult(TransferSliceResult.WAITING)
        if(job.phase in setOf("REVIEW_READY","REVIEW_REQUIRED")) return finished(id,key,job.fingerprint,captured)
        if(job.phase !in setOf("RESOLVING","WAITING_FILE_DECISION","PARSING") || job.filesFrozen) return TransferReviewBuildResult(TransferSliceResult.WAITING)
        if(job.workCursor?.startsWith("review:")!=true) {
            if(!dao.prepareReviewMembership(id.value,key) || session()!=captured) return TransferReviewBuildResult(TransferSliceResult.WAITING)
            job=dao.getJob(id.value,key) ?: return TransferReviewBuildResult(TransferSliceResult.WAITING)
            if(job.phase!="RESOLVING") {
                if(dao.beginReviewRebuild(id.value,key)==null) return TransferReviewBuildResult(TransferSliceResult.WAITING)
                job=dao.getJob(id.value,key)!!
            }
        }
        repeat(maxBatches) {
            currentCoroutineContext().ensureActive()
            if(session()!=captured) return TransferReviewBuildResult(TransferSliceResult.WAITING)
            when(dao.rebuildReviewSlice(id.value,key,job.generation,nowMillis())) {
                null -> return TransferReviewBuildResult(TransferSliceResult.WAITING)
                true -> return finished(id,key,dao.getJob(id.value,key)?.fingerprint,captured)
                false -> Unit
            }
        }
        return TransferReviewBuildResult(if(session()==captured)TransferSliceResult.CONTINUE else TransferSliceResult.WAITING)
    }

    private suspend fun finished(id: TransferJobId, key: String, fingerprint: String?, captured: TransferSession.Available): TransferReviewBuildResult {
        val existing=fingerprint?.let { dao.matchingPendingJob(id.value,key,it)?.let { job -> TransferJobId(job.id) } }
        return if(session()==captured)TransferReviewBuildResult(TransferSliceResult.FINISHED,existing) else TransferReviewBuildResult(TransferSliceResult.WAITING)
    }
}
