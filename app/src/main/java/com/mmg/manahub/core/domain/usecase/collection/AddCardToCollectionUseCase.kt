package com.mmg.manahub.core.domain.usecase.collection

import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import javax.inject.Inject

/**
 * Ensures the CardEntity is cached in Room first, then inserts or increments
 * the UserCardCollectionEntity. Single entry point for adding cards to the
 * collection — whether from search, scanner, or manual entry.
 */
class AddCardToCollectionUseCase @Inject constructor(
    private val cardRepository:     CardRepository,
    private val userCardRepository: UserCardRepository,
) {
    suspend operator fun invoke(
        scryfallId:       String,
        isFoil:           Boolean = false,
        isAlternativeArt: Boolean = false,
        condition:        String  = "NM",
        language:         String  = "en",
        isForTrade:       Boolean = false,
        isInWishlist:     Boolean = false,
        userId:           String? = null,
    ): DataResult<Unit> {
        val cardResult = cardRepository.getCardById(scryfallId)
        if (cardResult is DataResult.Error) return DataResult.Error(cardResult.message)
        userCardRepository.addOrIncrement(
            scryfallId       = scryfallId,
            isFoil           = isFoil,
            isAlternativeArt = isAlternativeArt,
            condition        = condition,
            language         = language,
            isForTrade       = isForTrade,
            isInWishlist     = isInWishlist,
            userId           = userId,
        )
        return DataResult.Success(Unit)
    }
}
