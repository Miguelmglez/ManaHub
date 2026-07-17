package com.mmg.manahub.core.domain.usecase.collection

import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UpdateEntryOutcome
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult

/**
 * Card Versions & Languages, Phase 1A. Edits an existing collection entry's
 * printing/language/foil/condition/quantity in place.
 *
 * Mirrors [AddCardToCollectionUseCase]'s shape: ensure the target [com.mmg.manahub.core.model.Card]
 * is cached locally (so the language selector's chosen printing is resolvable before the write),
 * then delegate the atomic re-point/merge to [UserCardRepository.updateEntryWithMerge] — see that
 * method's KDoc for the full merge semantics (survivor-row merge, soft-deleted-row reuse, linked
 * open-for-trade re-pointing).
 */
class UpdateCollectionEntryUseCase(
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
) {
    suspend operator fun invoke(
        entryId: String,
        newScryfallId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
        quantity: Int,
        userId: String? = null,
    ): DataResult<UpdateEntryOutcome> {
        val cardResult = cardRepository.getCardById(newScryfallId)
        if (cardResult is DataResult.Error) return DataResult.Error(cardResult.message)
        warmEnglishSiblingIfForeign((cardResult as DataResult.Success).data)

        val outcome = userCardRepository.updateEntryWithMerge(
            entryId = entryId,
            newScryfallId = newScryfallId,
            isFoil = isFoil,
            condition = condition,
            language = language,
            quantity = quantity,
            userId = userId,
        )
        return DataResult.Success(outcome)
    }

    /**
     * Broken-image fix (2026-07-17). Mirrors [AddCardToCollectionUseCase]'s enrichment: best-effort
     * warm the English sibling printing (same set + collector number) into Room so collection-list
     * rendering can fall back to its image when the newly-selected non-English printing has none.
     * Failure-silent — never blocks or fails the surrounding edit.
     */
    private suspend fun warmEnglishSiblingIfForeign(card: Card) {
        if (card.lang == "en") return
        runCatching { cardRepository.getCardBySetAndNumber(card.setCode, card.collectorNumber) }
    }
}
