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
     * Adds every entry in [entries] to the collection. Returns the total number of card copies
     * successfully committed. Individual failures are skipped so one bad entry never aborts the
     * batch; the scan event reports only the copies that actually landed.
     *
     * @param userId resolved user id (null for guest) forwarded to the collection write.
     */
    suspend operator fun invoke(
        entries: List<ScannedCardCommit>,
        userId: String? = null,
    ): Int {
        if (entries.isEmpty()) return 0

        var committedCopies = 0
        entries.forEach { entry ->
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
        return committedCopies
    }
}
