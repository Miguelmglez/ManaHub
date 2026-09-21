package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.QueuedCard
import kotlinx.coroutines.flow.StateFlow

/**
 * The single app-wide, persisted card queue shared by the Scanner and AddCard "Select multiple".
 *
 * Every mutation is applied synchronously to [queue] and persisted before returning, so a caller
 * can read [queue] right after a mutation and observe the new list.
 */
interface CardQueueRepository {

    /** Current queue, in insertion order. */
    val queue: StateFlow<List<QueuedCard>>

    /** Appends [entry] as a new row. */
    fun add(entry: QueuedCard)

    /**
     * Adds [entry]'s quantity to an existing row with the same scryfallId + foil + language +
     * condition, or appends [entry] when none matches.
     */
    fun addOrMerge(entry: QueuedCard)

    /** Removes the row with [id]; no-op when absent. */
    fun remove(id: String)

    /** Removes every row whose id is in [ids]. */
    fun removeAll(ids: Collection<String>)

    /** Removes every row of the printing [scryfallId], whatever its attributes. */
    fun removeByScryfallId(scryfallId: String)

    /** Removes every row whose printing is in [scryfallIds]. */
    fun removeByScryfallIds(scryfallIds: Collection<String>)

    /** Replaces the row sharing [entry]'s id; no-op when absent. */
    fun update(entry: QueuedCard)

    /** Adds one copy to the row with [id]. */
    fun incrementQuantity(id: String)

    /** Removes one copy from the row with [id], removing the row when it held a single copy. */
    fun decrementQuantity(id: String)

    /**
     * Inserts a copy of [entry] (fresh id and timestamp) right after the row sharing its id, or at
     * the end when that row is gone. Returns the inserted copy.
     */
    fun duplicate(entry: QueuedCard): QueuedCard

    /** Empties the queue. */
    fun clear()

    /** Replaces the whole queue with [entries]. */
    fun replaceAll(entries: List<QueuedCard>)
}
