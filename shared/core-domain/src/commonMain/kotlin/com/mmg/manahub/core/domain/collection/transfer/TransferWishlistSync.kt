package com.mmg.manahub.core.domain.collection.transfer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Absolute wanted payload is replayable; revision is local acknowledgement evidence. */
data class TransferWishlistDelivery(
    val id: String,
    val revision: Long,
    val printing: String,
    val quantity: Int,
    val foil: Boolean?,
    val condition: String?,
    val language: String?,
    val matchAnyVariant: Boolean,
    val createdAt: Long,
    val deleted: Boolean = false,
)

/** Bounded owner reads and conditional acknowledgements preserve newer local edits. */
interface TransferWishlistDeliveryStore {
    suspend fun pending(owner: TransferOwner.Account): List<TransferWishlistDelivery>
    suspend fun acknowledge(owner: TransferOwner.Account, rows: List<TransferWishlistDelivery>): Int
    suspend fun hasPending(owner: TransferOwner.Account): Boolean = pending(owner).isNotEmpty()
    suspend fun defer(owner: TransferOwner.Account, rows: List<TransferWishlistDelivery>) = Unit
}

/** Gateway sends absolute upserts with the captured account, never quantity increments. */
fun interface TransferWishlistDeliveryGateway {
    suspend fun deliver(owner: TransferOwner.Account, rows: List<TransferWishlistDelivery>)
}

/** Network failures retain the dirty ledger; local wishlist completion never claims remote success. */
class TransferWishlistSync(
    private val sessions: TransferSessionGate,
    private val store: TransferWishlistDeliveryStore,
    private val gateway: TransferWishlistDeliveryGateway,
    private val matchesObservedOwner: (TransferOwner) -> Boolean,
    private val reporter: com.mmg.manahub.core.common.CrashReporter? = null,
) {
    private fun telemetry(phase: String,attempted: Int=0,acknowledged: Int=0,category: TransferFailureCategory=TransferFailureCategory.UNKNOWN,failure: Boolean=false) {
        reporter?.apply {
            setCustomKey("collection_wishlist_phase",phase)
            setCustomKey("collection_wishlist_attempted_bucket",transferCountBucket(attempted))
            setCustomKey("collection_wishlist_acknowledged_bucket",transferCountBucket(acknowledged))
            setCustomKey("collection_wishlist_failure_category",category.value)
            log("collection_wishlist_$phase")
            if(failure)recordException(IllegalStateException("collection_wishlist_delivery_failed"))
        }
    }
    private val dispatch=Mutex()
    suspend fun runSlice(owner: TransferOwner.Account): TransferSliceResult = dispatch.withLock { execute(owner) }
    private suspend fun execute(owner: TransferOwner.Account): TransferSliceResult {
        return try {
        val captured=(sessions.currentSession as? TransferSession.Available)?.takeIf { it.owner==owner && matchesObservedOwner(owner) }
            ?: return TransferSliceResult.WAITING
        fun ensure() { if(sessions.currentSession!=captured || !matchesObservedOwner(owner))throw TransferSessionChangedException() }
        val rows=sessions.withOwner(owner) { _,guard -> guard(); ensure(); store.pending(owner) }
            ?: return TransferSliceResult.WAITING
        require(rows.size<=200)
        if(rows.isEmpty())return if(store.hasPending(owner))TransferSliceResult.WAITING else TransferSliceResult.FINISHED
        ensure()
        try { gateway.deliver(owner,rows) } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: Exception) {
            ensure(); sessions.withOwner(owner) { _,guard -> guard(); ensure(); store.defer(owner,rows); ensure() }
            if(!isExpectedTransferInterruption(error))telemetry("deferred",rows.size,category=transferFailureCategory(error,TransferFailureCategory.NETWORK),failure=true)
            return TransferSliceResult.WAITING
        }
        ensure()
        sessions.withOwner(owner) { _,guard ->
            guard(); ensure(); val acknowledged=store.acknowledge(owner,rows); guard(); ensure()
            telemetry(if(acknowledged<rows.size)"ack_stale" else "acknowledged",rows.size,acknowledged)
            if(!store.hasPending(owner))TransferSliceResult.FINISHED else if(store.pending(owner).isEmpty())TransferSliceResult.WAITING else TransferSliceResult.CONTINUE
        } ?: TransferSliceResult.WAITING
    } catch(cancelled: CancellationException) { throw cancelled }
      catch(error: Exception) { if(!isExpectedTransferInterruption(error))telemetry("delivery_failed",category=transferFailureCategory(error,TransferFailureCategory.STORAGE),failure=true);TransferSliceResult.WAITING }
    }
}
