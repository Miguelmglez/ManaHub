package com.mmg.manahub.core.domain.collection.transfer

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Identity requests invalidate visible results immediately while their transition waits for a batch. */
class TransferSessionGate {
    private data class Identity(val generation: Long, val owner: TransferOwner?) {
        fun session(): TransferSession = owner?.let { TransferSession.Available(it,generation) } ?: TransferSession.Loading
    }
    private val identity=MutableStateFlow(Identity(0L,null))
    private val mutex=Mutex()
    val currentGeneration: Long get()=identity.value.generation
    val currentSession: TransferSession get()=identity.value.session()
    val sessions: Flow<TransferSession> = identity.map { it.session() }.distinctUntilChanged()

    suspend fun changeOwner(owner: TransferOwner?, onPrevious: suspend (TransferOwner?) -> Unit = {}) {
        val previous=identity.value
        val requested=identity.updateAndGet { previous ->
            if(previous.owner==owner) previous else {
                check(previous.generation<Long.MAX_VALUE)
                Identity(previous.generation+1L,owner)
            }
        }
        mutex.withLock { if(identity.value==requested && previous.owner!=owner)onPrevious(previous.owner) }
    }

    suspend fun <T> withOwner(owner: TransferOwner, block: suspend (TransferSession.Available, () -> Unit) -> T): T? = mutex.withLock {
        val captured=identity.value
        if(captured.owner!=owner) return@withLock null
        val ensure={ if(identity.value!=captured) throw TransferSessionChangedException() }
        try {
            val result=block(captured.session() as TransferSession.Available,ensure)
            if(identity.value==captured)result else null
        } catch(_: TransferSessionChangedException) { null }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun <T> observeOwned(owner: TransferOwner, source: () -> Flow<T>): Flow<T?> = identity.flatMapLatest { captured ->
        flow {
            emit(null)
            if(captured.owner==owner) source().collect { value -> if(identity.value==captured)emit(value) }
        }
    }
}

/** A bounded owner-change signal carries no account identifier or external exception chain. */
class TransferSessionChangedException : Exception("Transfer session changed")

/** Persisted ownership quantities must fit the model exactly; tombstones restart at incoming copies. */
fun transferCollectionQuantity(existing: Int?, deleted: Boolean, incoming: Long): Int {
    if(incoming !in 1L..Int.MAX_VALUE.toLong() || (existing!=null && !deleted && existing<0)) throw TransferQuantityOverflowException()
    val total=if(existing==null || deleted)incoming else existing.toLong()+incoming
    if(total !in 1L..Int.MAX_VALUE.toLong()) throw TransferQuantityOverflowException()
    return total.toInt()
}

/** Overflow is a review conflict, never a saturating write. */
class TransferQuantityOverflowException : Exception("Transfer quantity overflow")

/** Collection commands cannot execute another destination or treat a pending wishlist as success. */
interface TransferWishlistExecutor {
    suspend fun runSlice(action: TransferActionId, owner: TransferOwner): TransferWishlistApplyResult
}

/** Local completion is independent of remote delivery and collection counters. */
enum class TransferWishlistApplyResult { CONTINUE, LOCAL_FINISHED, WAITING, REVIEW_REQUIRED, CONFIRMATION_REQUIRED, WRONG_DESTINATION, FAILED_RETRYABLE, UNAVAILABLE }
