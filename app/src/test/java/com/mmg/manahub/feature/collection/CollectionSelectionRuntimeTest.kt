package com.mmg.manahub.feature.collection

import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.model.CollectionGroupingMode
import com.mmg.manahub.core.model.CollectionSource
import com.mmg.manahub.feature.collection.data.TransferAuthSessionObserver
import com.mmg.manahub.feature.collection.data.RoomCollectionOwnershipRepository
import com.mmg.manahub.feature.collection.presentation.CollectionSelectionRuntime
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CollectionSelectionRuntimeTest {
    private val owner=TransferOwner.Account("runtime-owner")
    private val query=CollectionSelectionQuery(CollectionSource.COLLECTION,"",null,CollectionSelectionSort.NAME,true,CollectionGroupingMode.NONE)

    @Test fun emptyScannerCandidatesDoNotSubscribeToCollectionInvalidations()=runTest {
        val gate=TransferSessionGate().also { it.changeOwner(owner) }
        val raw=MutableStateFlow<SessionState>(SessionState.Unauthenticated)
        val observer=mockk<TransferAuthSessionObserver>()
        every { observer.identities } returns raw
        every { observer.matchesObserved(owner) } returns true
        val database=mockk<MtgDatabase>()
        val repository=RoomCollectionOwnershipRepository(database,gate,observer,StandardTestDispatcher(testScheduler))
        val job=backgroundScope.launch { repository.observe(MutableStateFlow(CollectionOwnershipCandidates(emptyList()))).collect { assertTrue(it.isEmpty()) } }
        runCurrent();advanceTimeBy(1000);runCurrent()
        verify { database wasNot Called }
        job.cancelAndJoin()
    }

    @Test fun hiddenInvalidationsDoNotCaptureAndVisibleBurstsCoalesce()=runTest {
        val gate=TransferSessionGate().also { it.changeOwner(owner) }
        val raw=MutableStateFlow<SessionState>(SessionState.Unauthenticated)
        val observer=mockk<TransferAuthSessionObserver>()
        every { observer.identities } returns raw
        every { observer.matchesObserved(owner) } returns true
        val repository=mockk<CollectionSelectionRepository>()
        coEvery { repository.capture(owner,query) } returns CollectionSelectionSummary("snapshot",3,7,0,emptyList())
        coEvery { repository.discard(any(),any()) } just Runs
        val events=MutableSharedFlow<Unit>(replay=1).also { it.tryEmit(Unit) }
        val runtime=CollectionSelectionRuntime(mockk<MtgDatabase>(),repository,gate,observer) { events }
        val job=runtime.start(backgroundScope,MutableStateFlow(query))
        runCurrent();advanceTimeBy(1000);runCurrent()
        coVerify(exactly=0) { repository.capture(any(),any()) }
        runtime.setActive(true);runCurrent()
        repeat(10) { events.emit(Unit);advanceTimeBy(20) }
        advanceTimeBy(301);runCurrent()
        coVerify(exactly=2) { repository.capture(owner,query) }
        assertEquals(7L,runtime.summary.value?.copies)
        runtime.setActive(false);runCurrent()
        assertNotNull(runtime.summary.value)
        assertEquals(7L,runtime.summary.value?.copies)
        repeat(10) { events.emit(Unit) }
        advanceTimeBy(1000);runCurrent()
        coVerify(exactly=2) { repository.capture(owner,query) }
        job.cancelAndJoin()
    }

    @Test fun rawOwnerChangeClearsSummaryBeforeSnapshotCleanupFinishes()=runTest {
        val gate=TransferSessionGate().also { it.changeOwner(owner) }
        val raw=MutableStateFlow<SessionState>(SessionState.Unauthenticated)
        val observer=mockk<TransferAuthSessionObserver>()
        every { observer.identities } returns raw
        every { observer.matchesObserved(owner) } returns true
        val owner2=TransferOwner.Account("runtime-owner-2")
        every { observer.matchesObserved(owner2) } returns true
        val repository=mockk<CollectionSelectionRepository>()
        coEvery { repository.capture(owner,query) } returns CollectionSelectionSummary("snapshot",3,7,0,emptyList())
        val cleanup=CompletableDeferred<Unit>()
        coEvery { repository.discard(owner,"snapshot") } coAnswers { cleanup.await() }
        val events=MutableSharedFlow<Unit>(replay=1).also { it.tryEmit(Unit) }
        val runtime=CollectionSelectionRuntime(mockk<MtgDatabase>(),repository,gate,observer) { events }
        val job=runtime.start(backgroundScope,MutableStateFlow(query))
        runtime.setActive(true);runCurrent();advanceTimeBy(301);runCurrent()
        assertNotNull(runtime.summary.value)
        gate.changeOwner(owner2);runCurrent()
        assertNull(runtime.summary.value)
        assertTrue(runtime.tags.isEmpty())
        cleanup.complete(Unit)
        job.cancelAndJoin()
    }

    @Test fun newQuerySetsLoadingTrueAndSameQueryReactivatedDoesNotSetLoadingTrue()=runTest {
        val gate=TransferSessionGate().also { it.changeOwner(owner) }
        val raw=MutableStateFlow<SessionState>(SessionState.Unauthenticated)
        val observer=mockk<TransferAuthSessionObserver>()
        every { observer.identities } returns raw
        every { observer.matchesObserved(owner) } returns true
        val repository=mockk<CollectionSelectionRepository>()
        val query2 = query.copy(search = "Black Lotus")
        val captureDeferred = CompletableDeferred<Unit>()
        coEvery { repository.capture(owner, query) } returns CollectionSelectionSummary("snapshot",3,7,0,emptyList())
        coEvery { repository.capture(owner, query2) } coAnswers {
            captureDeferred.await()
            CollectionSelectionSummary("snapshot2",1,1,0,emptyList())
        }
        coEvery { repository.discard(any(),any()) } just Runs
        val events=MutableSharedFlow<Unit>(replay=1).also { it.tryEmit(Unit) }
        val runtime=CollectionSelectionRuntime(mockk<MtgDatabase>(),repository,gate,observer) { events }
        val queryFlow=MutableStateFlow(query)
        val job=runtime.start(backgroundScope, queryFlow)

        runtime.setActive(true); runCurrent(); advanceTimeBy(301); runCurrent()
        assertFalse(runtime.loading.value)
        assertNotNull(runtime.summary.value)

        // Deactivate (e.g. navigate away to detail)
        runtime.setActive(false); runCurrent()

        // Reactivate with same query (e.g. return from detail)
        runtime.setActive(true); runCurrent()
        assertFalse(runtime.loading.value)

        // Change query (e.g. new search)
        queryFlow.value = query2
        runCurrent()
        assertTrue(runtime.loading.value)

        captureDeferred.complete(Unit)
        runCurrent(); advanceTimeBy(301); runCurrent()
        assertFalse(runtime.loading.value)
        assertEquals("snapshot2", runtime.summary.value?.id)

        job.cancelAndJoin()
    }
}
