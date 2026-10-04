package com.mmg.manahub.core.domain.collection.transfer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TransferWishlistSyncTest {
    private val owner=TransferOwner.Account("fixture")
    private val row=TransferWishlistDelivery("wish",1L,"printing",3,true,"NM","en",false,1L)
    private class Store(var rows: List<TransferWishlistDelivery>): TransferWishlistDeliveryStore {
        var acknowledgements=0
        override suspend fun pending(owner: TransferOwner.Account)=rows
        override suspend fun acknowledge(owner: TransferOwner.Account, rows: List<TransferWishlistDelivery>): Int {
            val matching=rows.count { it in this.rows }; acknowledgements+=matching; this.rows=this.rows.filterNot { it in rows }; return matching
        }
    }
    @Test fun failureKeepsDirtyAndRetryUsesAbsoluteSnapshot()=runTest {
        val gate=TransferSessionGate().also { it.changeOwner(owner) }; val store=Store(listOf(row)); var fail=true; val sent=mutableListOf<List<TransferWishlistDelivery>>()
        val sync=TransferWishlistSync(gate,store,{ account,rows -> assertEquals(owner,account); sent+=rows; if(fail)error("Remote failure") },{ it==owner })
        assertEquals(TransferSliceResult.WAITING,sync.runSlice(owner)); assertEquals(listOf(row),store.rows); assertEquals(0,store.acknowledgements)
        fail=false; assertEquals(TransferSliceResult.FINISHED,sync.runSlice(owner)); assertEquals(listOf(listOf(row),listOf(row)),sent); assertEquals(1,store.acknowledgements)
    }
    @Test fun obsoleteRemoteResultNeverAcknowledgesUnderNewAccount()=runTest {
        val gate=TransferSessionGate().also { it.changeOwner(owner) }; val store=Store(listOf(row)); var current: TransferOwner=owner
        val sync=TransferWishlistSync(gate,store,{ account,_ -> assertEquals(owner,account); current=TransferOwner.Account("other") },{ it==current })
        assertEquals(TransferSliceResult.WAITING,sync.runSlice(owner)); assertEquals(0,store.acknowledgements); assertEquals(listOf(row),store.rows)
    }
    @Test fun newerPayloadSurvivesOldAcknowledgementAndCancellationPropagates()=runTest {
        val gate=TransferSessionGate().also { it.changeOwner(owner) }; val store=Store(listOf(row))
        val sync=TransferWishlistSync(gate,store,{ _,_ -> store.rows=listOf(row.copy(revision=2L,quantity=5)) },{ it==owner })
        assertEquals(TransferSliceResult.CONTINUE,sync.runSlice(owner)); assertEquals(0,store.acknowledgements); assertEquals(5,store.rows.single().quantity)
        val cancelled=TransferWishlistSync(gate,store,{ _,_ -> throw CancellationException("Fixture cancellation") },{ it==owner })
        assertFailsWith<CancellationException> { cancelled.runSlice(owner) }; assertEquals(5,store.rows.single().quantity)
    }
    @Test fun ownerGateRemainsAvailableDuringRemoteDispatch()=runTest {
        val gate=TransferSessionGate().also { it.changeOwner(owner) }; val store=Store(listOf(row))
        val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>()
        val sync=TransferWishlistSync(gate,store,{ _,_ -> entered.complete(Unit); release.await() },{ it==owner })
        val remote=async { sync.runSlice(owner) }; entered.await()
        gate.withOwner(owner) { _,guard -> guard(); store.rows=listOf(row.copy(revision=2L,deleted=true,quantity=0)) }
        release.complete(Unit); assertEquals(TransferSliceResult.CONTINUE,remote.await()); assertTrue(store.rows.single().deleted)
    }
}
