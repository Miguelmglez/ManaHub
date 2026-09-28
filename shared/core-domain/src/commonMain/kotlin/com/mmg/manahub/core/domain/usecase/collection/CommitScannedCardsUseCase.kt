package com.mmg.manahub.core.domain.usecase.collection

import com.mmg.manahub.core.domain.repository.AddOutcome
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.model.CardAddOrigin
import com.mmg.manahub.core.model.DataResult
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A single queued card entry (Scanner or AddCard "Select multiple"), ready to be committed to the
 * collection. Presentation-agnostic so the domain layer does not depend on either screen's model.
 *
 * @property origin decides the XP event: [CardAddOrigin.SCANNED] entries count as scans,
 *   [CardAddOrigin.MANUAL] entries as manual adds. Committers that grant no XP ignore it.
 */
data class CardCommit(
    val scryfallId: String,
    val isFoil:     Boolean,
    val condition:  String,
    val language:   String,
    val quantity:   Int,
    val origin:     CardAddOrigin = CardAddOrigin.MANUAL,
)

/**
 * Outcome of [CommitScannedCardsUseCase.invoke].
 *
 * @property committedCopies Total card copies successfully written to the collection.
 * @property failedEntries Number of [CardCommit] entries that either returned a non-success
 *   [DataResult] or threw while being written — write-path hardening audit (2026-09-06): a
 *   throwing entry used to abort the WHOLE batch (an uncaught exception from
 *   [AddCardToCollectionUseCase.addReturningOutcome] propagated straight out of the `forEach`),
 *   silently stranding every entry after it. The caller (`ScannerViewModel`) surfaces a non-zero
 *   [failedEntries] as a warning naming the shortfall instead of clearing the whole scan session.
 * @property entrySucceeded Per-entry success flag, SAME ORDER AND SIZE as the `entries` list
 *   passed to [CommitScannedCardsUseCase.invoke] — lets the caller remove only the entries that
 *   actually committed (e.g. by zipping against its own queue) rather than either clearing
 *   everything (losing a failed entry's data) or nothing (risking a double-add on retry for the
 *   entries that already succeeded).
 */
data class CommitScanResult(
    val committedCopies: Int,
    val failedEntries: Int,
    val entrySucceeded: List<Boolean>,
)

/**
 * Commits a batch of queued cards to the collection (the shared Scanner / AddCard queue's canonical
 * write path) and, after the writes, emits at most one event per origin for the whole batch
 * (restore plan D9): one [ProgressionEvent.CardScanned] for the copies of [CardAddOrigin.SCANNED]
 * entries and one [ProgressionEvent.CardsAdded] for the [CardAddOrigin.MANUAL] entries, where only the
 * FIRST copy of a newly created row counts as unique and every other copy as an extra copy.
 *
 * Every add goes through [AddCardToCollectionUseCase.addReturningOutcome], which never emits, so no
 * card is rewarded twice. The scan event's fresh `scanBatchId` lets the ledger dedupe an accidental
 * re-emission of the same batch.
 */
@OptIn(ExperimentalUuidApi::class)
class CommitScannedCardsUseCase(
    private val addCardToCollection: AddCardToCollectionUseCase,
    private val progressionEventBus: ProgressionEventBus,
) {
    /**
     * Adds every entry in [entries] to the collection. Individual failures (a non-success
     * [DataResult] OR a thrown exception) are isolated per entry — see [CommitScanResult
     * .failedEntries] — so one bad entry never strands the rest of the batch. A
     * [kotlinx.coroutines.CancellationException] is never treated as a per-entry failure: it is
     * rethrown immediately so cooperative cancellation (e.g. the screen closing mid-commit)
     * propagates normally instead of being counted as a failed card.
     *
     * @param userId resolved user id (null for guest) forwarded to the collection write.
     */
    suspend operator fun invoke(
        entries: List<CardCommit>,
        userId: String? = null,
    ): CommitScanResult {
        if (entries.isEmpty()) {
            return CommitScanResult(committedCopies = 0, failedEntries = 0, entrySucceeded = emptyList())
        }

        var committedCopies = 0
        var failedEntries = 0
        var scannedCopies = 0
        var manualUnique = 0
        var manualCopies = 0
        val entrySucceeded = ArrayList<Boolean>(entries.size)
        entries.forEach { entry ->
            try {
                val result = addCardToCollection.addReturningOutcome(
                    scryfallId = entry.scryfallId,
                    isFoil     = entry.isFoil,
                    condition  = entry.condition,
                    language   = entry.language,
                    userId     = userId,
                    quantity   = entry.quantity,
                )
                if (result is DataResult.Success) {
                    committedCopies += entry.quantity
                    when (entry.origin) {
                        CardAddOrigin.SCANNED -> scannedCopies += entry.quantity
                        CardAddOrigin.MANUAL -> {
                            val createdRow = if (result.data == AddOutcome.CREATED_NEW) 1 else 0
                            manualUnique += createdRow
                            manualCopies += entry.quantity - createdRow
                        }
                    }
                    entrySucceeded.add(true)
                } else {
                    failedEntries++
                    entrySucceeded.add(false)
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                failedEntries++
                entrySucceeded.add(false)
            }
        }

        val now = Clock.System.now()
        if (scannedCopies > 0) {
            progressionEventBus.emit(
                ProgressionEvent.CardScanned(
                    scanBatchId = Uuid.random().toString(),
                    count = scannedCopies,
                    occurredAt = now,
                )
            )
        }
        if (manualUnique + manualCopies > 0) {
            progressionEventBus.emit(
                ProgressionEvent.CardsAdded(
                    addedCopies = manualCopies,
                    addedUnique = manualUnique,
                    occurredAt = now,
                )
            )
        }
        return CommitScanResult(
            committedCopies = committedCopies,
            failedEntries = failedEntries,
            entrySucceeded = entrySucceeded,
        )
    }
}
