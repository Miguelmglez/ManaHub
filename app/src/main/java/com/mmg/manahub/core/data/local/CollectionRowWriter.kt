package com.mmg.manahub.core.data.local

import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import com.mmg.manahub.core.domain.collection.transfer.transferCollectionQuantity
import com.mmg.manahub.core.domain.repository.AddOutcome
import java.util.UUID

/** Shared mutation core runs on its caller's Room transaction without resolving authentication. */
internal fun writeCollectionRow(
    dao: UserCardCollectionDao,
    existing: UserCardCollectionEntity?,
    userId: String?,
    printing: String,
    foil: Boolean,
    condition: String,
    language: String,
    quantity: Int,
    forTrade: Boolean,
    now: Long,
    strictOverflow: Boolean,
): Pair<AddOutcome,UserCardCollectionEntity> {
    val copies=if(strictOverflow)transferCollectionQuantity(existing?.quantity,existing?.isDeleted==true,quantity.toLong())
        else if(existing==null || existing.isDeleted)quantity else (existing.quantity.toLong()+quantity).coerceIn(0L,Int.MAX_VALUE.toLong()).toInt()
    val row=when {
        existing==null -> UserCardCollectionEntity(UUID.randomUUID().toString(),userId,printing,copies,foil,condition,language,forTrade,false,now,now)
        existing.isDeleted -> existing.copy(quantity=copies,isDeleted=false,isForTrade=forTrade,updatedAt=now,createdAt=now)
        else -> existing.copy(quantity=copies,isForTrade=forTrade || existing.isForTrade,updatedAt=now)
    }
    dao.upsert(row)
    return (if(existing==null || existing.isDeleted)AddOutcome.CREATED_NEW else AddOutcome.INCREMENTED_EXISTING) to row
}
