package core.domain.usecase.collection

import core.domain.model.DataResult
import core.domain.model.UserCard
import core.domain.repository.CardRepository
import core.domain.repository.UserCardRepository
import javax.inject.Inject

/**
 * Ensures the CardEntity is cached in Room first, then inserts or increments
 * the UserCardEntity. This is the single entry point for adding cards to
 * the collection — whether from search, scanner, or manual entry.
 */
class AddCardToCollectionUseCase @Inject constructor(
    private val cardRepository:     CardRepository,
    private val userCardRepository: UserCardRepository,
) {
    suspend operator fun invoke(
        scryfallId: String,
        isFoil:     Boolean = false,
        condition:  String  = "NM",
        language:   String  = "en",
        quantity:   Int     = 1,
    ): DataResult<Unit> {
        val cardResult = cardRepository.getCardById(scryfallId)
        if (cardResult is DataResult.Error) return DataResult.Error(cardResult.message)
        userCardRepository.addOrIncrement(
            UserCard(
                scryfallId = scryfallId,
                isFoil     = isFoil,
                condition  = condition,
                language   = language,
                quantity   = quantity,
            )
        )
        return DataResult.Success(Unit)
    }
}