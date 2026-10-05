package com.mmg.manahub.core.domain.collection.transfer

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.QueuedCard
import kotlinx.coroutines.flow.Flow

/** References the existing queue without copying its cards into another collection. */
data class CollectionOwnershipCandidates(val queue: List<QueuedCard>,val focused: Card? = null) {
    /** Candidate cardinality is independent of the owner's collection size. */
    val size: Int get()=queue.size + if(focused==null)0 else 1
    /** Reads the original immutable queue or the current detected card. */
    fun cardAt(index: Int): Card {
        require(index in 0 until size)
        return if(index<queue.size)queue[index].card else checkNotNull(focused)
    }
}

/** Live identity badges are restricted to requested cards and the current verified owner. */
interface CollectionOwnershipRepository {
    fun observe(candidates: Flow<CollectionOwnershipCandidates>): Flow<Set<String>>
}
