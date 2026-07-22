package com.mmg.manahub.core.domain.usecase.collection

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.repository.AddOutcome
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.model.Card
import kotlinx.datetime.Clock

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
class AddCardToCollectionUseCase(
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
        warmEnglishSiblingIfForeign((cardResult as DataResult.Success).data)
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
                    occurredAt = Clock.System.now(),
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
        warmEnglishSiblingIfForeign((cardResult as DataResult.Success).data)
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

    /**
     * Broken-image fix (2026-07-17). Non-English Scryfall printings frequently have no native
     * image — the English printing of the SAME set + collector number always shares the same
     * illustration and is guaranteed to have one. Best-effort warm the English sibling into Room
     * so [com.mmg.manahub.core.domain.repository.CardRepository.getCachedEnglishSiblings] can
     * resolve it later for collection-list image rendering (Room-only, no network there). Pure
     * enrichment: any failure (network, 404, rate limit) is swallowed and must never fail the
     * surrounding add.
     */
    private suspend fun warmEnglishSiblingIfForeign(card: Card) {
        if (card.lang == "en") return
        runCatching { cardRepository.getCardBySetAndNumber(card.setCode, card.collectorNumber) }
    }
}
