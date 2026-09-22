package com.mmg.manahub.core.domain.usecase.queue

import com.mmg.manahub.core.domain.repository.CardQueueRepository
import com.mmg.manahub.core.domain.usecase.collection.CardBatchCommitter
import com.mmg.manahub.core.domain.usecase.collection.CardCommit
import com.mmg.manahub.core.domain.usecase.collection.CommitScannedCardsUseCase
import com.mmg.manahub.feature.trades.domain.usecase.WishlistBatchWriter
import com.mmg.manahub.core.model.QueuedCard
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.feature.trades.domain.usecase.AddToWishlistUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Outcome of [CardQueueActions.addAllToCollection]. */
sealed interface AddAllToCollectionResult {

    /** Every entry of the snapshot committed; they were all removed from the queue. */
    data class Success(val committedEntries: Int) : AddAllToCollectionResult

    /** Only some entries committed; those were removed, the failed ones stay queued. */
    data class PartialFailure(val failedEntries: Int, val totalEntries: Int) : AddAllToCollectionResult
}

/**
 * The single implementation of the queue commit actions shared by every [CardQueueRepository]
 * host screen (Scanner, AddCard "Select multiple"). App-wide singleton: its guards protect the
 * shared queue against concurrent writes started from any screen.
 *
 * Every action claims its guard synchronously before launching, so a second tap before the first
 * write resolves is rejected. Callers must pass an app-lifetime scope: a screen scope cancelled
 * mid-batch would leave already-written entries queued, to be written again by the next commit.
 */
@OptIn(ExperimentalUuidApi::class)
class CardQueueActions(
    private val queueRepository: CardQueueRepository,
    private val committer: CardBatchCommitter,
    private val addToWishlist: AddToWishlistUseCase,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** When set, [addAllToWishlist] writes the snapshot in one batch instead of entry by entry. */
    private val wishlistBatchWriter: WishlistBatchWriter? = null,
) {

    private val _isCommitting = MutableStateFlow(false)

    /** True while an [addAllToCollection] commit is in flight. */
    val isCommitting: StateFlow<Boolean> = _isCommitting.asStateFlow()

    private val _isAddingAllToWishlist = MutableStateFlow(false)

    /** True while an [addAllToWishlist] batch is in flight. */
    val isAddingAllToWishlist: StateFlow<Boolean> = _isAddingAllToWishlist.asStateFlow()

    private val _inFlightIds = MutableStateFlow<Set<String>>(emptySet())

    /** Ids of the queue entries being written right now (per-entry adds and the add-all snapshot). */
    val inFlightIds: StateFlow<Set<String>> = _inFlightIds.asStateFlow()

    /**
     * Commits the current queue snapshot to the collection in ONE [CardBatchCommitter] batch (the
     * scan committer rewards it as one CardScanned event; the import committer grants no XP).
     *
     * Entries already in flight (a per-entry add) are left out of the snapshot. Committed entries
     * are removed through [CardQueueRepository.removeCommitted], so copies added or edits made while
     * the batch runs stay queued, and a failed entry is never lost.
     *
     * @return false when nothing is committable or a commit is already in flight (nothing launched).
     *   Otherwise [onComplete] runs in [scope] after the guards are released.
     */
    fun addAllToCollection(
        scope: CoroutineScope,
        onComplete: (AddAllToCollectionResult) -> Unit,
    ): Boolean {
        if (!_isCommitting.compareAndSet(expect = false, update = true)) return false
        val snapshot = claimAvailable(queueRepository.queue.value)
        if (snapshot.isEmpty()) {
            _isCommitting.value = false
            return false
        }

        scope.launch {
            val result = try {
                commitSnapshot(snapshot)
            } finally {
                release(snapshot)
                _isCommitting.value = false
            }
            onComplete(result)
        }
        return true
    }

    private suspend fun commitSnapshot(snapshot: List<QueuedCard>): AddAllToCollectionResult {
        val result = committer.commit(snapshot.map { it.toCommit() })
        queueRepository.removeCommitted(
            snapshot.filterIndexed { index, _ -> result.entrySucceeded.getOrElse(index) { false } }
        )
        return if (result.failedEntries == 0) {
            AddAllToCollectionResult.Success(committedEntries = snapshot.size)
        } else {
            AddAllToCollectionResult.PartialFailure(
                failedEntries = result.failedEntries,
                totalEntries = snapshot.size,
            )
        }
    }

    /**
     * Commits a single entry to the collection through the same [CardBatchCommitter].
     * A failed write never removes the entry.
     *
     * @return false when [entry] is already being written (nothing launched). Otherwise
     *   [onComplete] runs in [scope] with whether the entry committed.
     */
    fun addEntryToCollection(
        scope: CoroutineScope,
        entry: QueuedCard,
        removeOnSuccess: Boolean,
        onComplete: (succeeded: Boolean) -> Unit,
    ): Boolean {
        if (claimAvailable(listOf(entry)).isEmpty()) return false
        scope.launch {
            val succeeded = try {
                val committed = committer.commit(listOf(entry.toCommit())).failedEntries == 0
                if (committed && removeOnSuccess) queueRepository.removeCommitted(listOf(entry))
                committed
            } finally {
                release(listOf(entry))
            }
            onComplete(succeeded)
        }
        return true
    }

    /**
     * Adds a single entry, with its quantity, to the wishlist (local-only for guests, synced when
     * signed in).
     *
     * @return false when [entry] is already being written (nothing launched). Otherwise
     *   [onComplete] runs in [scope] with the result.
     */
    fun addEntryToWishlist(
        scope: CoroutineScope,
        entry: QueuedCard,
        removeOnSuccess: Boolean,
        onComplete: (Result<Unit>) -> Unit,
    ): Boolean {
        if (claimAvailable(listOf(entry)).isEmpty()) return false
        scope.launch {
            val result = try {
                addToWishlist(entry.toWishlistEntry()).also {
                    if (it.isSuccess && removeOnSuccess) queueRepository.removeCommitted(listOf(entry))
                }
            } finally {
                release(listOf(entry))
            }
            onComplete(result)
        }
        return true
    }

    /**
     * Adds every entry of the current queue snapshot to the wishlist, each with its quantity, without
     * removing them from the queue. [onEntryAdded] runs after each entry (with the batch result when
     * a [WishlistBatchWriter] is set).
     *
     * @return false when the queue is empty or a batch is already in flight (nothing launched).
     *   Otherwise [onComplete] runs in [scope] with the number of entries processed.
     */
    fun addAllToWishlist(
        scope: CoroutineScope,
        onEntryAdded: suspend (entry: QueuedCard, result: Result<Unit>) -> Unit = { _, _ -> },
        onComplete: (processedEntries: Int) -> Unit,
    ): Boolean {
        val snapshot = queueRepository.queue.value
        if (snapshot.isEmpty() || !_isAddingAllToWishlist.compareAndSet(expect = false, update = true)) return false
        scope.launch {
            try {
                val batchWriter = wishlistBatchWriter
                if (batchWriter != null) {
                    val result = batchWriter.addAll(snapshot.map { it.toWishlistEntry() })
                    snapshot.forEach { entry -> onEntryAdded(entry, result) }
                } else {
                    snapshot.forEach { entry -> onEntryAdded(entry, addToWishlist(entry.toWishlistEntry())) }
                }
            } finally {
                _isAddingAllToWishlist.value = false
            }
            onComplete(snapshot.size)
        }
        return true
    }

    private fun claimAvailable(entries: List<QueuedCard>): List<QueuedCard> {
        while (true) {
            val current = _inFlightIds.value
            val available = entries.filter { it.id !in current }
            if (available.isEmpty()) return emptyList()
            if (_inFlightIds.compareAndSet(current, current + available.map { it.id })) return available
        }
    }

    private fun release(entries: List<QueuedCard>) {
        val ids = entries.mapTo(HashSet()) { it.id }
        _inFlightIds.update { it - ids }
    }

    private fun QueuedCard.toCommit(): CardCommit = CardCommit(
        scryfallId = card.scryfallId,
        isFoil = isFoil,
        condition = condition,
        language = language,
        quantity = quantity,
    )

    private fun QueuedCard.toWishlistEntry(): WishlistEntry = WishlistEntry(
        id = Uuid.random().toString(),
        userId = "",
        cardId = card.scryfallId,
        quantity = quantity,
        matchAnyVariant = false,
        isFoil = isFoil,
        condition = condition.uppercase().trim(),
        language = language.lowercase().trim(),
        createdAt = nowMillis(),
        card = card,
    )

    companion object {
        /**
         * The Scanner / AddCard queue: commits count as scans (batched CardScanned XP).
         *
         * A named factory rather than a second constructor: the two differ only by the type of
         * their second argument, so a positional call would pick a committer — and therefore an XP
         * outcome — that the reader cannot see. Imports must NOT use this (see the Collection
         * feature's "imports grant no XP" invariant).
         */
        fun forScannedCards(
            queueRepository: CardQueueRepository,
            commitScannedCards: CommitScannedCardsUseCase,
            addToWishlist: AddToWishlistUseCase,
            nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
        ): CardQueueActions = CardQueueActions(
            queueRepository = queueRepository,
            committer = CardBatchCommitter { entries -> commitScannedCards(entries) },
            addToWishlist = addToWishlist,
            nowMillis = nowMillis,
        )
    }
}
