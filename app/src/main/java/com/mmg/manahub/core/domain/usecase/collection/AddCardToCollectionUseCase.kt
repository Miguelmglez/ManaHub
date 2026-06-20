package com.mmg.manahub.core.domain.usecase.collection

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.repository.AddOutcome
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import java.time.Instant
import javax.inject.Inject

/**
 * Identifies where a collection add originated, so the gamification layer can attribute the
 * resulting XP to the correct source and avoid double-counting.
 *
 * - [MANUAL] adds (search, card detail, manual entry) emit a [ProgressionEvent.CardsAdded].
 * - [SCANNER] adds are aggregated by [CommitScannedCardsUseCase] into a single
 *   [ProgressionEvent.CardScanned]; this use case therefore suppresses its own emission for
 *   scanner adds so the same card is never rewarded twice.
 */
enum class CollectionAddSource {
    MANUAL,
    SCANNER,
}

/**
 * Ensures the CardEntity is cached in Room first, then inserts or increments
 * the UserCardCollectionEntity. Single entry point for adding cards to the
 * collection — whether from search, scanner, or manual entry.
 *
 * After a successful commit, a [ProgressionEvent.CardsAdded] is emitted on the
 * [ProgressionEventBus] for [CollectionAddSource.MANUAL] adds only (the scanner path emits a
 * batched [ProgressionEvent.CardScanned] instead). The emission happens strictly after the
 * write succeeds and never blocks the caller (see ADR-002 §1).
 */
class AddCardToCollectionUseCase @Inject constructor(
    private val cardRepository:     CardRepository,
    private val userCardRepository: UserCardRepository,
    private val progressionEventBus: ProgressionEventBus,
) {
    suspend operator fun invoke(
        scryfallId:       String,
        isFoil:           Boolean = false,
        condition:        String  = "NM",
        language:         String  = "en",
        isForTrade:       Boolean = false,
        userId:           String? = null,
        quantity:         Int     = 1,
        source:           CollectionAddSource = CollectionAddSource.MANUAL,
    ): DataResult<Unit> {
        val cardResult = cardRepository.getCardById(scryfallId)
        if (cardResult is DataResult.Error) return DataResult.Error(cardResult.message)
        val outcome = userCardRepository.addOrIncrement(
            scryfallId       = scryfallId,
            isFoil           = isFoil,
            condition        = condition,
            language         = language,
            isForTrade       = isForTrade,
            userId           = userId,
            quantity         = quantity,
        )

        // Emit only for manual adds; scanner adds are batched by CommitScannedCardsUseCase.
        if (source == CollectionAddSource.MANUAL) {
            progressionEventBus.emit(
                ProgressionEvent.CardsAdded(
                    addedCopies = quantity,
                    addedUnique = if (outcome == AddOutcome.CREATED_NEW) 1 else 0,
                    occurredAt = Instant.now(),
                )
            )
        }
        return DataResult.Success(Unit)
    }

    /**
     * Adds a card exactly like [invoke] but returns the underlying [AddOutcome] so a caller
     * (e.g. [CommitScannedCardsUseCase]) can aggregate unique/copy counts across a batch. Never
     * emits a progression event itself — the batching caller owns the single emission.
     */
    suspend fun addReturningOutcome(
        scryfallId:       String,
        isFoil:           Boolean = false,
        condition:        String  = "NM",
        language:         String  = "en",
        isForTrade:       Boolean = false,
        userId:           String? = null,
        quantity:         Int     = 1,
    ): DataResult<AddOutcome> {
        val cardResult = cardRepository.getCardById(scryfallId)
        if (cardResult is DataResult.Error) return DataResult.Error(cardResult.message)
        val outcome = userCardRepository.addOrIncrement(
            scryfallId  = scryfallId,
            isFoil      = isFoil,
            condition   = condition,
            language    = language,
            isForTrade  = isForTrade,
            userId      = userId,
            quantity    = quantity,
        )
        return DataResult.Success(outcome)
    }
}
