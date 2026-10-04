package com.mmg.manahub.feature.collection.data

import androidx.room.withTransaction
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.domain.collection.transfer.*

/** Snapshot acknowledgement compares both delivery revision and the current wanted payload. */
class RoomTransferWishlistDeliveryStore(
    private val database: MtgDatabase,
    private val sessions: TransferSessionGate,
    private val matchesObservedOwner: (TransferOwner) -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
): TransferWishlistDeliveryStore {
    override suspend fun pending(owner: TransferOwner.Account): List<TransferWishlistDelivery> {
        require(matchesObservedOwner(owner) && (sessions.currentSession as? TransferSession.Available)?.owner==owner)
        val result=database.collectionTransferDao().wishlistDeliveryPage(owner.storageKey(),owner.id,nowMillis())
        require(matchesObservedOwner(owner) && (sessions.currentSession as? TransferSession.Available)?.owner==owner)
        return result
    }
    override suspend fun acknowledge(owner: TransferOwner.Account, rows: List<TransferWishlistDelivery>): Int {
        require(rows.size<=200)
        val captured=sessions.currentSession
        fun ensure() { if(!matchesObservedOwner(owner) || sessions.currentSession!=captured || (captured as? TransferSession.Available)?.owner!=owner)throw TransferSessionChangedException() }
        return database.withTransaction {
            ensure(); val dao=database.collectionTransferDao(); var acknowledged=0
            for(row in rows) {
                ensure()
                if(row.deleted) {
                    val count=dao.acknowledgeWishlistDeletion(row.id,owner.storageKey(),row.revision)
                    acknowledged+=count
                    if(count==0)dao.reactivateStaleWishlistDelivery(row.id,owner.storageKey(),row.revision,0L)
                } else if(dao.acknowledgeWishlistRow(row.id,owner.id,owner.storageKey(),row.revision,row.printing,row.quantity,row.foil,row.condition,row.language,row.matchAnyVariant,row.createdAt)==1) {
                    dao.clearWishlistDirty(row.id,owner.storageKey(),row.revision); acknowledged++
                } else dao.reactivateStaleWishlistDelivery(row.id,owner.storageKey(),row.revision,0L)
            }
            ensure(); acknowledged
        }
    }
    override suspend fun hasPending(owner: TransferOwner.Account): Boolean {
        require(matchesObservedOwner(owner) && (sessions.currentSession as? TransferSession.Available)?.owner==owner)
        return database.collectionTransferDao().hasWishlistPending(owner.storageKey())
    }
    override suspend fun defer(owner: TransferOwner.Account, rows: List<TransferWishlistDelivery>) {
        val captured=sessions.currentSession
        database.withTransaction {
            fun ensure() { if(!matchesObservedOwner(owner) || sessions.currentSession!=captured)throw TransferSessionChangedException() }
            ensure()
            val next=nowMillis().let { if(it>Long.MAX_VALUE-30_000L)Long.MAX_VALUE else it+30_000L }
            for(row in rows) {
                ensure()
                database.collectionTransferDao().deferWishlistDelivery(row.id,owner.storageKey(),row.revision,next)
                database.collectionTransferDao().reactivateStaleWishlistDelivery(row.id,owner.storageKey(),row.revision,next)
            }
            ensure()
        }
    }
}
