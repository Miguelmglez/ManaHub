package com.mmg.manahub.core.domain.usecase.collection

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.Clock

/**
 * A single card entry queued by the scanner, ready to be committed to the collection.
 *
 * Kept presentation-agnostic so the domain layer does not depend on the scanner UI's
 * `ScannedCard` model. The scanner ViewModel maps its session entries into this shape.
 */
data class ScannedCardCommit(
    val scryfallId: String,
    val isFoil:     Boolean,
    val condition:  String,
    val language:   String,
    val quantity:   Int,
)

/**
 * Outcome of [CommitScannedCardsUseCase.invoke].
 *
 * @property committedCopies Total card copies successfully written to the collection.
 * @property failedEntries Number of [ScannedCardCommit] entries that either returned a non-success
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
 * Commits a batch of scanner-recognised cards to the collection and emits a SINGLE
 * [ProgressionEvent.CardScanned] for the whole batch after the writes succeed (ADR-002 §1).
 *
 * This is the scanner's canonical write path. It deliberately funnels every add through
 * [AddCardToCollectionUseCase] with [CollectionAddSource.SCANNER], which suppresses the
 * per-card [ProgressionEvent.CardsAdded] emission — so scanned cards are rewarded exactly once,
 * via the batched scan event (3 XP/card, capped), never double-counted as manual adds.
 *
 * Emission lives here (a use case), not in the ViewModel, and uses a freshly generated
 * `scanBatchId` per commit so the ledger dedupes accidental re-commits of the same batch.
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
        entries: List<ScannedCardCommit>,
        userId: String? = null,
    ): CommitScanResult {
        if (entries.isEmpty()) {
            return CommitScanResult(committedCopies = 0, failedEntries = 0, entrySucceeded = emptyList())
        }

        var committedCopies = 0
        var failedEntries = 0
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

        // Emit one scan event for the whole batch, only if at least one copy landed.
        if (committedCopies > 0) {
            progressionEventBus.emit(
                ProgressionEvent.CardScanned(
                    scanBatchId = Uuid.random().toString(),
                    count = committedCopies,
                    occurredAt = Clock.System.now(),
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
