package com.mmg.manahub.core.domain.collection.transfer

/**
 * Persistence for the import lines no card could be resolved for. They are shown next to the review
 * queue, so they have to survive process death exactly as the queue does — otherwise a restored
 * queue offers "show N lines not found" for lines that no longer exist.
 *
 * Implementations cap what they keep (see [MAX_PERSISTED_UNRESOLVED_LINES]): the list is a hint for
 * the user, never a payload worth an MB of storage.
 */
interface CollectionImportUnresolvedStore {

    /** Returns the persisted lines, or an empty list when nothing was saved. */
    fun read(): List<String>

    /** Persists [lines], replacing any previous ones; an empty list clears the entry. */
    fun write(lines: List<String>)
}

/** How many unresolved lines are kept across process death. */
const val MAX_PERSISTED_UNRESOLVED_LINES = 500
