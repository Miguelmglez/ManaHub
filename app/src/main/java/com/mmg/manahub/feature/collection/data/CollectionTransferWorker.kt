package com.mmg.manahub.feature.collection.data

import android.content.Context
import androidx.work.*
import com.mmg.manahub.core.domain.collection.transfer.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/** Requests contain no owner, provider URI, payload or review metadata. */
class AndroidTransferWorkScheduler(private val manager: WorkManager): TransferWorkScheduler {
    override suspend fun enqueue(id: TransferJobId) { manager.enqueueUniqueWork(name(id),ExistingWorkPolicy.KEEP,request(id)).await() }
    override suspend fun cancel(id: TransferJobId) { manager.cancelUniqueWork(name(id)).await() }
    override suspend fun enqueueWishlist() { manager.enqueueUniqueWork(WISHLIST_NAME,ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<TransferWishlistDeliveryWorker>().setBackoffCriteria(BackoffPolicy.LINEAR,10,TimeUnit.SECONDS).build()).await() }
    override suspend fun cancelWishlist() { manager.cancelUniqueWork(WISHLIST_NAME).await() }
    override fun observeFinished(id: TransferJobId?)=manager.getWorkInfosForUniqueWorkFlow(id?.let(::name) ?: WISHLIST_NAME).map { rows -> rows.isNotEmpty() && rows.all { it.state.isFinished } }.distinctUntilChanged()
    companion object {
        const val JOB_ID="job_uuid"
        const val WISHLIST_NAME="collection-transfer-wishlist-delivery"
        fun name(id: TransferJobId)="collection-transfer-${id.value}"
        fun request(id: TransferJobId)=OneTimeWorkRequestBuilder<CollectionTransferWorker>()
            .setInputData(workDataOf(JOB_ID to id.value))
            .setBackoffCriteria(BackoffPolicy.LINEAR,10,TimeUnit.SECONDS).build()
    }
}

/** Reflection fallback is safe before Koin starts; unavailable DI retries without touching Room. */
class CollectionTransferWorker(
    context: Context,
    parameters: WorkerParameters,
    private val runner: ()->RunTransferWork?,
): CoroutineWorker(context,parameters) {
    constructor(context: Context,parameters: WorkerParameters): this(context,parameters,{ GlobalContext.getOrNull()?.get<RunTransferWork>() })
    override suspend fun doWork(): Result {
        val id=inputData.getString(AndroidTransferWorkScheduler.JOB_ID)?.let { runCatching { TransferJobId(it) }.getOrNull() } ?: return Result.failure()
        return try {
            val work=runner() ?: return Result.retry()
            if(work.run(id)==TransferWorkResult.RETRY)Result.retry() else Result.success()
        } catch(cancelled: CancellationException) { throw cancelled }
          catch(_: Exception) { Result.retry() }
    }
}

/** Wishlist delivery never delays or rolls back a completed local destination command. */
class TransferWishlistDeliveryWorker(
    context: Context,
    parameters: WorkerParameters,
    private val coordinator: ()->RoomCollectionTransferCoordinator?,
): CoroutineWorker(context,parameters) {
    constructor(context: Context,parameters: WorkerParameters): this(context,parameters,{ GlobalContext.getOrNull()?.get<RoomCollectionTransferCoordinator>() })
    override suspend fun doWork(): Result { return try {
        val runtime=coordinator() ?: return Result.retry()
        val result=withTimeoutOrNull(480_000L) { runtime.runWishlistDelivery() } ?: TransferSliceResult.CONTINUE
        if(result==TransferSliceResult.CONTINUE)Result.retry() else Result.success()
    } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { Result.retry() } }
}
