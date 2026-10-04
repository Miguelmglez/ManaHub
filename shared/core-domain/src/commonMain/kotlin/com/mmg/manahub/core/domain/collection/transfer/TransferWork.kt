package com.mmg.manahub.core.domain.collection.transfer

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.Flow

/** Scheduling carries only a job UUID; owner and commands remain in persistence. */
interface TransferWorkScheduler {
    suspend fun enqueue(id: TransferJobId)
    suspend fun cancel(id: TransferJobId)
    suspend fun enqueueWishlist()
    suspend fun cancelWishlist()
    fun observeFinished(id: TransferJobId?): Flow<Boolean>
}

/** Normal continuations retry without consuming the resolver's durable failure budget. */
enum class TransferWorkResult { SUCCESS, RETRY }

/** One deadline includes local transactions, decoding, limiter waits and remote preparation. */
class RunTransferWork(
    private val coordinator: CollectionTransferCoordinator,
    private val sliceMillis: Long = 480_000L,
) {
    init { require(sliceMillis in 1L..480_000L) }
    suspend fun run(id: TransferJobId): TransferWorkResult = withTimeoutOrNull(sliceMillis) {
        while(true) {
            yield()
            val result=when(coordinator.runApplicationSlice(id)) {
                TransferSliceResult.CONTINUE -> TransferSliceResult.CONTINUE
                TransferSliceResult.WAITING -> return@withTimeoutOrNull TransferWorkResult.SUCCESS
                TransferSliceResult.FINISHED -> coordinator.runPreparationSlice(id)
            }
            if(result!=TransferSliceResult.CONTINUE)return@withTimeoutOrNull TransferWorkResult.SUCCESS
            if(!coordinator.hasImmediateWork(id))return@withTimeoutOrNull TransferWorkResult.RETRY
        }
        @Suppress("UNREACHABLE_CODE") TransferWorkResult.SUCCESS
    } ?: TransferWorkResult.RETRY
}
