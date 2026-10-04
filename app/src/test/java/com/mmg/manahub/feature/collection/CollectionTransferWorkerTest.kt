package com.mmg.manahub.feature.collection

import android.content.Context
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.util.concurrent.Futures
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.feature.collection.data.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CollectionTransferWorkerTest {
    private val id=TransferJobId("00000000-0000-4000-8000-000000000001")
    private val context=mockk<Context>(relaxed=true)
    private fun worker(data: Data,runner: ()->RunTransferWork?)=TestListenableWorkerBuilder<CollectionTransferWorker>(context)
        .setInputData(data).setWorkerFactory(object: WorkerFactory() {
            override fun createWorker(appContext: Context,workerClassName: String,workerParameters: WorkerParameters)=CollectionTransferWorker(appContext,workerParameters,runner)
        }).build()
    @Test fun uuidOnlyKeepAndLinearBackoffUseExistingManager()=runBlocking<Unit> {
        val manager=mockk<WorkManager>(); val operation=mockk<Operation>()
        every { operation.result } returns Futures.immediateFuture(Operation.SUCCESS)
        val captured=slot<OneTimeWorkRequest>()
        every { manager.enqueueUniqueWork(AndroidTransferWorkScheduler.name(id),ExistingWorkPolicy.KEEP,capture(captured)) } returns operation
        AndroidTransferWorkScheduler(manager).enqueue(id)
        val spec=captured.captured.workSpec
        assertEquals(mapOf(AndroidTransferWorkScheduler.JOB_ID to id.value),spec.input.keyValueMap)
        assertEquals(BackoffPolicy.LINEAR,spec.backoffPolicy); assertEquals(10_000L,spec.backoffDelayDuration)
        assertEquals(NetworkType.NOT_REQUIRED,spec.constraints.requiredNetworkType)
    }
    @Test fun unavailableKoinRetriesWithoutCallingAnyCoordinator()=runBlocking<Unit> {
        assertEquals(ListenableWorker.Result.retry(),worker(workDataOf(AndroidTransferWorkScheduler.JOB_ID to id.value)) { null }.doWork())
    }
    @Test fun reviewPauseSucceedsAndMalformedUuidDoesNotExecute()=runBlocking<Unit> {
        val coordinator=mockk<CollectionTransferCoordinator>()
        coEvery { coordinator.runApplicationSlice(id) } returns TransferSliceResult.WAITING
        val runner=RunTransferWork(coordinator)
        assertEquals(ListenableWorker.Result.success(),worker(workDataOf(AndroidTransferWorkScheduler.JOB_ID to id.value)) { runner }.doWork())
        assertEquals(ListenableWorker.Result.failure(),worker(workDataOf(AndroidTransferWorkScheduler.JOB_ID to "provider/path")) { error("Must not resolve") }.doWork())
        coVerify(exactly=0) { coordinator.runPreparationSlice(id) }
    }
}
