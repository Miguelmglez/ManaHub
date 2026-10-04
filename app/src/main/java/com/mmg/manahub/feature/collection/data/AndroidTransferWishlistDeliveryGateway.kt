package com.mmg.manahub.feature.collection.data

import com.mmg.manahub.core.data.remote.dto.WishlistEntryDto
import com.mmg.manahub.core.data.remote.trades.WishlistRemoteDataSource
import com.mmg.manahub.core.domain.collection.transfer.*
import kotlinx.datetime.Instant

/** Explicit user_id payloads never derive the destination from a later auth session. */
class AndroidTransferWishlistDeliveryGateway(
    private val remote: WishlistRemoteDataSource,
    private val matchesObservedOwner: (TransferOwner) -> Boolean,
): TransferWishlistDeliveryGateway {
    override suspend fun deliver(owner: TransferOwner.Account, rows: List<TransferWishlistDelivery>) {
        require(rows.size<=200 && matchesObservedOwner(owner))
        val active=rows.filterNot { it.deleted }
        if(active.isNotEmpty())remote.batchAddWishlistEntries(active.map { row -> WishlistEntryDto(row.id,owner.id,row.printing,row.quantity,row.matchAnyVariant,row.foil,row.condition,row.language,Instant.fromEpochMilliseconds(row.createdAt).toString()) }).getOrThrow()
        val deleted=rows.filter { it.deleted }
        if(deleted.isNotEmpty()) {
            if(!matchesObservedOwner(owner))throw TransferSessionChangedException()
            remote.removeWishlistEntriesForOwner(deleted.map { it.id },owner.id).getOrThrow()
        }
        if(!matchesObservedOwner(owner))throw TransferSessionChangedException()
    }
}
