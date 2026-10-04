package com.mmg.manahub.core.domain.collection.transfer

import com.mmg.manahub.core.common.CrashReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TransferTelemetryTest {
    private class Reporter: CrashReporter {
        val logs=mutableListOf<String>();val keys=mutableMapOf<String,String>();val failures=mutableListOf<Throwable>()
        override fun log(message: String) { logs+=message }
        override fun setCustomKey(key: String,value: String) { keys[key]=value }
        override fun recordException(throwable: Throwable) { failures+=throwable }
    }
    @Test fun longCountsUseImportBucketsWithoutNarrowing() {
        for((count,bucket) in listOf(-1L to "0",0L to "0",1L to "1-10",10L to "1-10",11L to "11-100",100L to "11-100",101L to "101-1000",1000L to "101-1000",1001L to "1000+",Int.MAX_VALUE.toLong()+1L to "1000+",Long.MAX_VALUE to "1000+"))assertEquals(bucket,transferCountBucket(count))
        val reporter=Reporter();exportTelemetry(reporter,"save_failed",CollectionFileFormat.MANABOX_CSV.name,Long.MAX_VALUE,TransferFailureCategory.STORAGE,true)
        assertEquals(4,reporter.keys.size);assertEquals("1000+",reporter.keys["collection_export_rows_bucket"]);assertNull(reporter.failures.single().cause)
    }
    @Test fun deferredCooldownAndStaleAcknowledgementAreSafeBoundedOutcomes()=runTest {
        val marker="PRIVATE_OWNER_REVISION_URI_SQL";val owner=TransferOwner.Account(marker);val gate=TransferSessionGate().also { it.changeOwner(owner) }
        val row=TransferWishlistDelivery(marker,Long.MAX_VALUE,marker,Int.MAX_VALUE,true,marker,marker,false,Long.MAX_VALUE)
        var visible=true;var fail=true;var stale=true;var live=true
        val store=object: TransferWishlistDeliveryStore {
            override suspend fun pending(owner: TransferOwner.Account)=if(visible && live)listOf(row) else emptyList()
            override suspend fun hasPending(owner: TransferOwner.Account)=live
            override suspend fun defer(owner: TransferOwner.Account,rows: List<TransferWishlistDelivery>) { visible=false }
            override suspend fun acknowledge(owner: TransferOwner.Account,rows: List<TransferWishlistDelivery>): Int { if(stale)return 0;live=false;return rows.size }
        }
        val reporter=Reporter();val sync=TransferWishlistSync(gate,store,{ _,_ -> if(fail)throw IllegalStateException(marker,IllegalArgumentException(marker)) },{ it==owner },reporter)
        assertEquals(TransferSliceResult.WAITING,sync.runSlice(owner));assertEquals(listOf("collection_wishlist_deferred"),reporter.logs);assertEquals(1,reporter.failures.size)
        repeat(3) { assertEquals(TransferSliceResult.WAITING,sync.runSlice(owner)) };assertEquals(1,reporter.logs.size)
        visible=true;fail=false;assertEquals(TransferSliceResult.CONTINUE,sync.runSlice(owner));assertEquals("collection_wishlist_ack_stale",reporter.logs.last());assertEquals(1,reporter.failures.size)
        stale=false;assertEquals(TransferSliceResult.FINISHED,sync.runSlice(owner));assertEquals("collection_wishlist_acknowledged",reporter.logs.last())
        assertFalse((reporter.logs+reporter.keys.entries.map { "${it.key}=${it.value}" }+reporter.failures.map { it.stackTraceToString() }).joinToString().contains(marker));assertTrue(reporter.failures.all { it.cause==null })
    }
    @Test fun ownerAbaAndCancellationNeverBecomeNonfatals()=runTest {
        val owner=TransferOwner.Account("a");val gate=TransferSessionGate().also { it.changeOwner(owner) };val reporter=Reporter()
        val store=object: TransferWishlistDeliveryStore {
            override suspend fun pending(owner: TransferOwner.Account)=listOf(TransferWishlistDelivery("row",1,"printing",1,false,"NM","en",false,1))
            override suspend fun acknowledge(owner: TransferOwner.Account,rows: List<TransferWishlistDelivery>): Int=error("Old response must not acknowledge")
        }
        val sync=TransferWishlistSync(gate,store,{ _,_ -> gate.changeOwner(TransferOwner.Account("b"));gate.changeOwner(owner) },{ it==owner },reporter)
        assertEquals(TransferSliceResult.WAITING,sync.runSlice(owner));assertTrue(reporter.failures.isEmpty());assertTrue(reporter.logs.isEmpty())
        val cancelled=TransferWishlistSync(gate,store,{ _,_ -> throw CancellationException("PRIVATE_CANCEL") },{ it==owner },reporter)
        assertFailsWith<CancellationException> { cancelled.runSlice(owner) };assertTrue(reporter.failures.isEmpty())
    }
}
