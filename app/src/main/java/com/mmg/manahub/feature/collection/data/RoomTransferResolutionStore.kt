package com.mmg.manahub.feature.collection.data

import com.mmg.manahub.core.data.local.dao.CollectionTransferDao
import com.mmg.manahub.core.data.local.dao.TransferStoredResolution
import com.mmg.manahub.core.domain.collection.transfer.*

/** A scoped persistence adapter never returns another session's pending source data. */
class RoomTransferResolutionStore(private val dao: CollectionTransferDao, private val session: () -> TransferSession) : TransferResolutionStore {
    private fun capture(owner: TransferOwner): TransferSession.Available? = (session() as? TransferSession.Available)?.takeIf { it.owner==owner }
    private fun TransferOwner.key(): String = when(this) { is TransferOwner.Account -> "account:$id"; is TransferOwner.VerifiedGuest -> "guest:$installationToken" }

    override suspend fun state(id: TransferJobId, owner: TransferOwner): TransferPreparationState? {
        val captured=capture(owner) ?: return null
        val job=dao.getJob(id.value,owner.key()) ?: return null
        if(session()!=captured || job.filesFrozen) return null
        return TransferPreparationState(TransferPhase.valueOf(job.phase),job.nextAttemptAt,job.networkFailures)
    }

    override suspend fun pending(id: TransferJobId, owner: TransferOwner): List<TransferResolutionRow> {
        val captured=capture(owner) ?: return emptyList()
        for(file in dao.currentFiles(id.value,owner.key()).filter { it.selected && it.phase=="RESOLVING" }) {
            val rows=dao.pendingResolutionPage(id.value,owner.key(),file.id,0L).take(75).map { row -> TransferResolutionRow(TransferFileId(file.id),row.sourceOrdinal,transferIdentifier(row.scryfallId,row.setCode,row.collectorNumber,row.name)) }
            if(session()!=captured) return emptyList()
            if(rows.isNotEmpty()) return rows
        }
        return emptyList()
    }

    override suspend fun commit(id: TransferJobId, owner: TransferOwner, outcomes: List<TransferResolutionOutcome>, now: Long): Boolean {
        val captured=capture(owner) ?: return false
        require(outcomes.size<=75)
        for((file,rows) in outcomes.groupBy { it.row.file }) {
            if(session()!=captured) return false
            if(!dao.commitPreparationResults(id.value,owner.key(),file.value,rows.map { TransferStoredResolution(it.row.ordinal,it.id,it.error?.name) },now)) return false
        }
        return session()==captured
    }

    override suspend fun claimName(id: TransferJobId, owner: TransferOwner, name: String, now: Long): Boolean {
        val captured=capture(owner) ?: return false
        if(!dao.claimFallbackName(id.value,owner.key(),name,now)) return false
        return session()==captured
    }

    override suspend fun wait(id: TransferJobId, owner: TransferOwner, nextAttemptAt: Long, realFailure: Boolean): Boolean {
        val captured=capture(owner) ?: return false
        return dao.waitPreparation(id.value,owner.key(),nextAttemptAt,realFailure) && session()==captured
    }

    override suspend fun finishEmptyFiles(id: TransferJobId, owner: TransferOwner): Boolean {
        val captured=capture(owner) ?: return false
        val files=dao.currentFiles(id.value,owner.key()).filter { it.selected }
        if(files.any { it.phase in setOf("PARSING","RECEIVING") }) return false
        for(file in files.filter { it.phase=="RESOLVING" }) {
            if(session()!=captured) return false
            if(dao.pendingResolutionPage(id.value,owner.key(),file.id,0L).isEmpty()) dao.completeResolution(id.value,owner.key(),file.id)
        }
        return session()==captured
    }
}
