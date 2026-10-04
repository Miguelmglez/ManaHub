package com.mmg.manahub.core.domain.collection.transfer

import com.mmg.manahub.core.domain.repository.CardLookupIdentifier
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Lightweight cache/network identity; preparation never chooses a collection destination. */
data class TransferPrinting(val id: String, val name: String, val set: String, val collector: String)

/** A source record stays independent of any later aggregate or per-entry decision. */
data class TransferResolutionRow(val file: TransferFileId, val ordinal: Long, val identifier: CardLookupIdentifier)

/** Persisted preparation gates distinguish delayed work from exhausted real attempts. */
data class TransferPreparationState(val phase: TransferPhase, val nextAttemptAt: Long, val failures: Int)

/** Failures preserve pending records and contain no provider/server exception text. */
enum class TransferLookupFailure { OFFLINE, COOLDOWN, RETRYABLE }

/** Successful misses are explicit; an omitted response is a retryable protocol failure. */
data class TransferLookupBatch(val cards: List<TransferPrinting>, val missing: List<CardLookupIdentifier>, val failure: TransferLookupFailure? = null, val retryAfterMillis: Long = 0L)

/** Name fallback is restricted to sources without an explicit printing or set. */
data class TransferNameLookup(val card: TransferPrinting? = null, val notFound: Boolean = false, val failure: TransferLookupFailure? = null, val retryAfterMillis: Long = 0L)

/** A durable outcome is either an exact printing or a confirmed successful miss. */
data class TransferResolutionOutcome(val row: TransferResolutionRow, val id: String? = null, val error: TransferError? = null)

/** The Android gateway uses the existing global Scryfall queue, never a new limiter. */
interface TransferResolutionGateway {
    suspend fun cached(identifier: CardLookupIdentifier): TransferPrinting?
    suspend fun lookup(identifiers: List<CardLookupIdentifier>): TransferLookupBatch
    suspend fun fallbackName(name: String): TransferNameLookup
}

/** Implementations atomically commit source outcomes, cursor and successful retry reset. */
interface TransferResolutionStore {
    suspend fun state(id: TransferJobId, owner: TransferOwner): TransferPreparationState?
    suspend fun pending(id: TransferJobId, owner: TransferOwner): List<TransferResolutionRow>
    suspend fun commit(id: TransferJobId, owner: TransferOwner, outcomes: List<TransferResolutionOutcome>, now: Long): Boolean
    suspend fun claimName(id: TransferJobId, owner: TransferOwner, name: String, now: Long): Boolean
    suspend fun wait(id: TransferJobId, owner: TransferOwner, nextAttemptAt: Long, realFailure: Boolean): Boolean
    suspend fun finishEmptyFiles(id: TransferJobId, owner: TransferOwner): Boolean
}

/** Bounded slices persist successes before continuing to another request or waiting for retry. */
class DurableTransferResolver(
    private val store: TransferResolutionStore,
    private val gateway: TransferResolutionGateway,
    private val nowMillis: () -> Long,
) {
    suspend fun runSlice(id: TransferJobId, owner: TransferOwner, maxBatches: Int = 3): TransferSliceResult {
        require(maxBatches in 1..100)
        val initial=store.state(id,owner) ?: return TransferSliceResult.WAITING
        if(initial.phase in setOf(TransferPhase.FAILED_RETRYABLE,TransferPhase.PAUSED_BY_USER,TransferPhase.PAUSED_OWNER,TransferPhase.DISCARDED) || initial.nextAttemptAt>nowMillis()) return TransferSliceResult.WAITING
        repeat(maxBatches) {
            currentCoroutineContext().ensureActive()
            if(!store.finishEmptyFiles(id,owner)) return TransferSliceResult.WAITING
            val rows=store.pending(id,owner)
            require(rows.size<=75)
            if(rows.isEmpty()) return if(store.state(id,owner)!=null)TransferSliceResult.FINISHED else TransferSliceResult.WAITING
            val cached=mutableListOf<TransferResolutionOutcome>()
            val remaining=mutableListOf<TransferResolutionRow>()
            for(row in rows) {
                currentCoroutineContext().ensureActive()
                val card=gateway.cached(row.identifier)
                if(card!=null && row.identifier.matches(card)) cached+=TransferResolutionOutcome(row,id=card.id) else remaining+=row
            }
            if(cached.isNotEmpty() && !store.commit(id,owner,cached,nowMillis())) return TransferSliceResult.WAITING
            if(remaining.isEmpty()) return@repeat
            val identifiers=remaining.map { it.identifier }.distinctBy { it.key() }
            val response=gateway.lookup(identifiers)
            val outcomes=mutableListOf<TransferResolutionOutcome>()
            val nameRows=mutableListOf<TransferResolutionRow>()
            var incomplete=false
            for(row in remaining) {
                val card=response.cards.firstOrNull { row.identifier.matches(it) }
                if(card!=null) outcomes+=TransferResolutionOutcome(row,id=card.id)
                else if(response.missing.any { it.key()==row.identifier.key() }) {
                    if(row.identifier.explicitPrinting()) outcomes+=TransferResolutionOutcome(row,error=TransferError.NOT_FOUND)
                    else nameRows+=row
                } else incomplete=true
            }
            if(outcomes.isNotEmpty() && !store.commit(id,owner,outcomes,nowMillis())) return TransferSliceResult.WAITING
            if(response.failure!=null || incomplete) return pause(id,owner,response.failure ?: TransferLookupFailure.RETRYABLE,response.retryAfterMillis)
            for(group in nameRows.groupBy { it.identifier.name!!.trim().lowercase() }.values) {
                currentCoroutineContext().ensureActive()
                val name=group.first().identifier.name!!.trim()
                if(!store.claimName(id,owner,name.lowercase(),nowMillis())) {
                    if(!store.commit(id,owner,group.map { TransferResolutionOutcome(it,error=TransferError.NOT_FOUND) },nowMillis())) return TransferSliceResult.WAITING
                    continue
                }
                val fallback=gateway.fallbackName(name)
                if(fallback.failure!=null || (fallback.card==null && !fallback.notFound)) return pause(id,owner,fallback.failure ?: TransferLookupFailure.RETRYABLE,fallback.retryAfterMillis)
                val completed=group.map { row -> TransferResolutionOutcome(row,id=fallback.card?.id,error=if(fallback.card==null)TransferError.NOT_FOUND else null) }
                if(!store.commit(id,owner,completed,nowMillis())) return TransferSliceResult.WAITING
            }
        }
        return if(store.finishEmptyFiles(id,owner) && store.pending(id,owner).isEmpty() && store.state(id,owner)!=null) TransferSliceResult.FINISHED else TransferSliceResult.CONTINUE
    }

    private suspend fun pause(id: TransferJobId, owner: TransferOwner, failure: TransferLookupFailure, retry: Long): TransferSliceResult {
        val now=nowMillis()
        val delay=maxOf(retry,if(failure==TransferLookupFailure.COOLDOWN)1L else 1000L)
        val next=if(delay>Long.MAX_VALUE-now)Long.MAX_VALUE else now+delay
        store.wait(id,owner,next,failure==TransferLookupFailure.RETRYABLE)
        return TransferSliceResult.WAITING
    }
}

/** Priority preserves an explicit printing even when its supplied name disagrees. */
fun transferIdentifier(id: String?, set: String?, collector: String?, name: String?): CardLookupIdentifier = when {
    !id.isNullOrBlank() -> CardLookupIdentifier(scryfallId=id.trim())
    !set.isNullOrBlank() && !collector.isNullOrBlank() -> CardLookupIdentifier(setCode=set.trim(),collectorNumber=collector.trim())
    !set.isNullOrBlank() -> CardLookupIdentifier(name=name?.trim(),setCode=set.trim())
    else -> CardLookupIdentifier(name=name?.trim())
}

/** Keys are normalized consistently for replay, response matching and durable fallback claims. */
fun CardLookupIdentifier.key(): String = listOf(scryfallId,name,setCode,collectorNumber).joinToString("|") { it.orEmpty().trim().lowercase() }

/** Set-qualified requests must never reach fuzzy name fallback. */
fun CardLookupIdentifier.explicitPrinting(): Boolean = scryfallId!=null || setCode!=null || collectorNumber!=null

/** Full and face names may identify the same printing; set/id constraints remain exact. */
fun CardLookupIdentifier.matches(card: TransferPrinting): Boolean = when {
    scryfallId!=null -> card.id.equals(scryfallId,true)
    setCode!=null && collectorNumber!=null -> card.set.equals(setCode,true) && card.collector.equals(collectorNumber,true)
    name!=null -> (card.name.equals(name.trim(),true) || card.name.split(" // ").any { it.equals(name.trim(),true) }) && (setCode==null || card.set.equals(setCode,true))
    else -> false
}
