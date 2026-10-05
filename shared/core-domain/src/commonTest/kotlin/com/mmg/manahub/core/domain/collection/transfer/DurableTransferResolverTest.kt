package com.mmg.manahub.core.domain.collection.transfer

import com.mmg.manahub.core.domain.repository.CardLookupIdentifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DurableTransferResolverTest {
    private val id=TransferJobId("01234567-89ab-cdef-0123-456789abcdef")
    private val file=TransferFileId(id.value)
    private val owner=TransferOwner.Account("fixture")
    private fun row(n: Int, identifier: CardLookupIdentifier=CardLookupIdentifier(scryfallId="printing-$n"))=TransferResolutionRow(file,n.toLong(),identifier)
    private fun card(identifier: CardLookupIdentifier)=TransferPrinting(identifier.scryfallId ?: "found",identifier.name ?: "Plains",identifier.setCode ?: "set",identifier.collectorNumber ?: "1")
    private class Store(val rows: List<TransferResolutionRow>) : TransferResolutionStore {
        var status=TransferPreparationState(TransferPhase.RESOLVING,0L,0)
        val done=linkedMapOf<Long,TransferResolutionOutcome>()
        val names=mutableSetOf<String>()
        var available=true
        override suspend fun state(id: TransferJobId,owner: TransferOwner)=status.takeIf { available }
        override suspend fun pending(id: TransferJobId,owner: TransferOwner)=rows.filter { it.ordinal !in done }.take(75)
        override suspend fun commit(id: TransferJobId,owner: TransferOwner,outcomes: List<TransferResolutionOutcome>,now: Long): Boolean {
            outcomes.forEach { done[it.row.ordinal]=it }
            status=TransferPreparationState(TransferPhase.RESOLVING,0L,0)
            return available
        }
        override suspend fun claimName(id: TransferJobId,owner: TransferOwner,name: String,now: Long): Boolean = name in names || (names.size<100 && names.add(name))
        override suspend fun wait(id: TransferJobId,owner: TransferOwner,nextAttemptAt: Long,realFailure: Boolean): Boolean {
            val count=status.failures+if(realFailure)1 else 0
            status=TransferPreparationState(if(count>=5)TransferPhase.FAILED_RETRYABLE else TransferPhase.WAITING_NETWORK,nextAttemptAt,count)
            return true
        }
        override suspend fun finishEmptyFiles(id: TransferJobId,owner: TransferOwner)=available
    }
    private class Gateway : TransferResolutionGateway {
        val sizes=mutableListOf<Int>(); val fallback=mutableListOf<String>()
        var cached: (CardLookupIdentifier)->TransferPrinting?={ null }
        var response: (List<CardLookupIdentifier>)->TransferLookupBatch={ identifiers -> TransferLookupBatch(identifiers.map { TransferPrinting(it.scryfallId!!,"Plains","set","1") },emptyList()) }
        var nameResult: (String)->TransferNameLookup={ TransferNameLookup(notFound=true) }
        override suspend fun cached(identifier: CardLookupIdentifier)=cached.invoke(identifier)
        override suspend fun lookup(identifiers: List<CardLookupIdentifier>): TransferLookupBatch { sizes+=identifiers.size; return response(identifiers) }
        override suspend fun fallbackName(name: String): TransferNameLookup { fallback+=name; return nameResult(name) }
    }

    @Test fun identifiersKeepStrictPriorityAndPrintingConstraints() {
        assertEquals(CardLookupIdentifier(scryfallId="id"),transferIdentifier("id","set","1","Name"))
        assertEquals(CardLookupIdentifier(setCode="set",collectorNumber="1"),transferIdentifier(null,"set","1","Name"))
        assertEquals(CardLookupIdentifier(name="Name",setCode="set"),transferIdentifier(null,"set",null,"Name"))
        assertFalse(CardLookupIdentifier(scryfallId="missing").matches(TransferPrinting("other","Name","set","1")))
        assertFalse(CardLookupIdentifier(name="Name",setCode="missing").matches(TransferPrinting("id","Name","set","1")))
    }

    @Test fun cacheFirstAnd160IdentifiersUse75Then75Then10() = runTest {
        val store=Store((1..160).map { row(it) }); val gateway=Gateway()
        assertEquals(TransferSliceResult.FINISHED,DurableTransferResolver(store,gateway,{ 0L }).runSlice(id,owner))
        assertEquals(listOf(75,75,10),gateway.sizes)
        assertEquals(160,store.done.size)
        val cachedStore=Store((1..160).map { row(it) }); val cached=Gateway().apply { this.cached={ card(it) } }
        assertEquals(TransferSliceResult.FINISHED,DurableTransferResolver(cachedStore,cached,{ 0L }).runSlice(id,owner))
        assertTrue(cached.sizes.isEmpty())
    }

    @Test fun explicitMissingNeverFallsBackToAnotherPrinting() = runTest {
        val identifiers=listOf(CardLookupIdentifier(scryfallId="missing",name="Plains"),CardLookupIdentifier(setCode="bad",collectorNumber="1",name="Plains"),CardLookupIdentifier(name="Plains",setCode="bad"),CardLookupIdentifier(name="Plains"))
        val store=Store(identifiers.mapIndexed { index,identifier -> row(index+1,identifier) })
        val gateway=Gateway().apply { response={ TransferLookupBatch(emptyList(),it) }; nameResult={ TransferNameLookup(card=TransferPrinting("fuzzy","Plains","other","1")) } }
        DurableTransferResolver(store,gateway,{ 0L }).runSlice(id,owner)
        assertEquals(listOf("Plains"),gateway.fallback)
        assertTrue(store.done.values.take(3).all { it.error==TransferError.NOT_FOUND && it.id==null })
        assertEquals("fuzzy",store.done[4L]!!.id)
    }

    @Test fun partialResultsResumeWithoutRequestingConfirmedOrdinals() = runTest {
        val store=Store((1..75).map { row(it) }); val gateway=Gateway()
        gateway.response={ TransferLookupBatch(it.take(40).map { identifier -> card(identifier) },emptyList(),TransferLookupFailure.RETRYABLE) }
        var now=0L
        val resolver=DurableTransferResolver(store,gateway,{ now })
        assertEquals(TransferSliceResult.WAITING,resolver.runSlice(id,owner))
        assertEquals(40,store.done.size); assertEquals(1,store.status.failures)
        now=2000L
        gateway.response={ TransferLookupBatch(it.map { identifier -> card(identifier) },emptyList()) }
        assertEquals(TransferSliceResult.FINISHED,resolver.runSlice(id,owner))
        assertEquals(listOf(75,35),gateway.sizes); assertEquals(0,store.status.failures)
    }

    @Test fun offlineCooldownAndFiveRealFailuresKeepRowsPending() = runTest {
        val store=Store(listOf(row(1))); val gateway=Gateway(); var now=0L
        val resolver=DurableTransferResolver(store,gateway,{ now })
        for(failure in listOf(TransferLookupFailure.OFFLINE,TransferLookupFailure.COOLDOWN)) {
            gateway.response={ TransferLookupBatch(emptyList(),emptyList(),failure,5000L) }
            resolver.runSlice(id,owner); assertEquals(0,store.status.failures); now+=6000L
        }
        gateway.response={ TransferLookupBatch(emptyList(),emptyList(),TransferLookupFailure.RETRYABLE) }
        repeat(5) { resolver.runSlice(id,owner); now+=2000L }
        assertEquals(TransferPhase.FAILED_RETRYABLE,store.status.phase)
        assertTrue(store.done.isEmpty())
        val calls=gateway.sizes.size; resolver.runSlice(id,owner); assertEquals(calls,gateway.sizes.size)
    }

    @Test fun fallbackClaimsAreDistinctBoundedAndCancellationIsPropagated() = runTest {
        val store=Store((1..101).map { row(it,CardLookupIdentifier(name="Name $it")) })
        val gateway=Gateway().apply { response={ TransferLookupBatch(emptyList(),it) } }
        DurableTransferResolver(store,gateway,{ 0L }).runSlice(id,owner)
        assertEquals(100,gateway.fallback.size); assertEquals(100,store.names.size)
        assertEquals(101,store.done.size)
        val interrupted=Store(listOf(row(1))); gateway.response={ throw CancellationException("Fixture") }
        assertFailsWith<CancellationException> { DurableTransferResolver(interrupted,gateway,{ 0L }).runSlice(id,owner) }
        assertEquals(0,interrupted.status.failures); assertTrue(interrupted.done.isEmpty())
    }
}
