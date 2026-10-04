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
        coVerify(exactly=1) { repository.capture(owner,query) }
        assertEquals(7L,runtime.summary.value?.copies)
        runtime.setActive(false);runCurrent()
        assertNull(runtime.summary.value)
        repeat(10) { events.emit(Unit) }
        advanceTimeBy(1000);runCurrent()
        coVerify(exactly=1) { repository.capture(owner,query) }
        job.cancelAndJoin()
    }

    @Test fun rawOwnerChangeClearsSummaryBeforeSnapshotCleanupFinishes()=runTest {
        val gate=TransferSessionGate().also { it.changeOwner(owner) }
        val raw=MutableStateFlow<SessionState>(SessionState.Unauthenticated)
        val observer=mockk<TransferAuthSessionObserver>()
        every { observer.identities } returns raw
        every { observer.matchesObserved(owner) } answers { raw.value!=SessionState.Loading }
        val repository=mockk<CollectionSelectionRepository>()
        coEvery { repository.capture(owner,query) } returns CollectionSelectionSummary("snapshot",3,7,0,emptyList())
        val cleanup=CompletableDeferred<Unit>()
        coEvery { repository.discard(owner,"snapshot") } coAnswers { cleanup.await() }
        val events=MutableSharedFlow<Unit>(replay=1).also { it.tryEmit(Unit) }
        val runtime=CollectionSelectionRuntime(mockk<MtgDatabase>(),repository,gate,observer) { events }
        val job=runtime.start(backgroundScope,MutableStateFlow(query))
        runtime.setActive(true);runCurrent();advanceTimeBy(301);runCurrent()
        assertNotNull(runtime.summary.value)
        raw.value=SessionState.Loading;runCurrent()
        assertNull(runtime.summary.value)
        assertTrue(runtime.tags.isEmpty())
        cleanup.complete(Unit)
        job.cancelAndJoin()
    }
}
