package com.mmg.manahub.core.domain.usecase.queue

import com.mmg.manahub.core.domain.repository.CardQueueRepository
import com.mmg.manahub.core.domain.usecase.collection.CommitScannedCardsUseCase
import com.mmg.manahub.core.domain.usecase.collection.ScannedCardCommit
import com.mmg.manahub.core.model.QueuedCard
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.feature.trades.domain.usecase.AddToWishlistUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * host screen (Scanner, AddCard "Select multiple"). App-wide singleton: [isCommitting] guards the
 * shared queue against a concurrent "add all" from any screen.
 */
@OptIn(ExperimentalUuidApi::class)
class CardQueueActions(
    private val queueRepository: CardQueueRepository,
    private val commitScannedCards: CommitScannedCardsUseCase,
    private val addToWishlist: AddToWishlistUseCase,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private val _isCommitting = MutableStateFlow(false)

    /** True while an [addAllToCollection] commit is in flight. */
    val isCommitting: StateFlow<Boolean> = _isCommitting.asStateFlow()

    /**
     * Commits the current queue snapshot to the collection in ONE [CommitScannedCardsUseCase] batch
     * (counts as a scan: one batched CardScanned XP event, never per-card manual adds).
     *
     * The re-entrancy guard is claimed synchronously before launching, so a second tap before the
     * first commit resolves is rejected. Only entries that actually committed are removed (by
     * stable id), so a partial failure never loses a failed entry nor double-adds on retry.
     *
     * @return false when the queue is empty or a commit is already in flight (nothing launched).
     *   Otherwise [onComplete] runs in [scope] after the guard is released.
     */
    fun addAllToCollection(
        scope: CoroutineScope,
        onComplete: (AddAllToCollectionResult) -> Unit,
    ): Boolean {
        val snapshot = queueRepository.queue.value
        if (snapshot.isEmpty() || !_isCommitting.compareAndSet(expect = false, update = true)) return false

        scope.launch {
            val result = try {
                commitSnapshot(snapshot)
            } finally {
                _isCommitting.value = false
            }
            onComplete(result)
        }
        return true
    }

    private suspend fun commitSnapshot(snapshot: List<QueuedCard>): AddAllToCollectionResult {
        val result = commitScannedCards(snapshot.map { it.toCommit() })
        val succeededIds = snapshot
            .filterIndexed { index, _ -> result.entrySucceeded.getOrElse(index) { false } }
            .mapTo(mutableSetOf()) { it.id }
        queueRepository.removeAll(succeededIds)
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
     * Commits a single entry to the collection (through the scan path, so it is rewarded as a scan).
     * A failed write never removes the entry.
     *
     * @return true when the entry committed.
     */
    suspend fun addEntryToCollection(entry: QueuedCard, removeOnSuccess: Boolean): Boolean {
        val succeeded = commitScannedCards(listOf(entry.toCommit())).failedEntries == 0
        if (succeeded && removeOnSuccess) queueRepository.remove(entry.id)
        return succeeded
    }

    /** Adds a single entry to the wishlist (local-only for guests, synced when signed in). */
    suspend fun addEntryToWishlist(entry: QueuedCard, removeOnSuccess: Boolean): Result<Unit> {
        val result = addToWishlist(entry.toWishlistEntry())
        if (result.isSuccess && removeOnSuccess) queueRepository.remove(entry.id)
        return result
    }

    /**
     * Adds every entry of the current queue snapshot to the wishlist, one by one, without removing
     * them from the queue. [onEntryAdded] runs after each entry.
     *
     * @return the number of entries processed.
     */
    suspend fun addAllToWishlist(
        onEntryAdded: suspend (entry: QueuedCard, result: Result<Unit>) -> Unit = { _, _ -> },
    ): Int {
        val snapshot = queueRepository.queue.value
        snapshot.forEach { entry -> onEntryAdded(entry, addToWishlist(entry.toWishlistEntry())) }
        return snapshot.size
    }

    private fun QueuedCard.toCommit(): ScannedCardCommit = ScannedCardCommit(
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
        matchAnyVariant = false,
        isFoil = isFoil,
        condition = condition.uppercase().trim(),
        language = language.lowercase().trim(),
        createdAt = nowMillis(),
        card = card,
    )
}
