package com.mmg.manahub.core.domain.collection.transfer

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TransferWorkTest {
    private val id=TransferJobId("00000000-0000-4000-8000-000000000001")
    private class Coordinator: CollectionTransferCoordinator {
        var application: suspend ()->TransferSliceResult={ TransferSliceResult.FINISHED }
        var preparation: suspend ()->TransferSliceResult={ TransferSliceResult.WAITING }
        var prepared=0
        var immediate=false
        override suspend fun hasImmediateWork(id: TransferJobId)=immediate
        override suspend fun runApplicationSlice(id: TransferJobId)=application()
        override suspend fun runPreparationSlice(id: TransferJobId): TransferSliceResult { prepared++; return preparation() }
        override suspend fun reconcile(owner: TransferOwner)=Unit
    }
    @Test fun localContinuationNeverWaitsForPreparationNetwork()=runTest {
        val coordinator=Coordinator().also { it.application={ TransferSliceResult.CONTINUE }; it.preparation={ error("Network must not run") } }
        assertEquals(TransferWorkResult.RETRY,RunTransferWork(coordinator).run(id)); assertEquals(0,coordinator.prepared)
    }
    @Test fun reviewAndManualPauseCompleteWorkWithoutFailureBudget()=runTest {
        val coordinator=Coordinator().also { it.application={ TransferSliceResult.WAITING } }
        assertEquals(TransferWorkResult.SUCCESS,RunTransferWork(coordinator).run(id)); assertEquals(0,coordinator.prepared)
        coordinator.application={ TransferSliceResult.FINISHED }; coordinator.preparation={ TransferSliceResult.CONTINUE }
        assertEquals(TransferWorkResult.RETRY,RunTransferWork(coordinator).run(id))
    }
    @Test fun deadlineIncludesPreparationAndReleasesCancellationAtEightMinutes()=runTest {
        var closed=false
        val coordinator=Coordinator().also { it.preparation={ try { awaitCancellation() } finally { closed=true } } }
        val work=async { RunTransferWork(coordinator).run(id) }; runCurrent(); advanceTimeBy(480_000L); runCurrent()
        assertEquals(TransferWorkResult.RETRY,work.await()); assertTrue(closed); assertEquals(480_000L,currentTime)
    }
    @Test fun externalCancellationPropagatesInsteadOfBecomingRetry()=runTest {
        var closed=false
        val coordinator=Coordinator().also { it.application={ try { awaitCancellation() } finally { closed=true } } }
        val work=async { RunTransferWork(coordinator).run(id) }; runCurrent(); work.cancelAndJoin()
        assertTrue(work.isCancelled); assertTrue(closed)
    }
    @Test fun immediateBatchesDrainInOneSliceWhileCooldownStopsWithoutSpinning()=runTest {
        var batches=0
        val coordinator=Coordinator().also { it.immediate=true; it.preparation={ batches++; if(batches<200)TransferSliceResult.CONTINUE else TransferSliceResult.FINISHED } }
        assertEquals(TransferWorkResult.SUCCESS,RunTransferWork(coordinator).run(id)); assertEquals(200,batches)
        coordinator.immediate=false; coordinator.preparation={ batches++; TransferSliceResult.CONTINUE }
        assertEquals(TransferWorkResult.RETRY,RunTransferWork(coordinator).run(id)); assertEquals(201,batches)
    }
}
