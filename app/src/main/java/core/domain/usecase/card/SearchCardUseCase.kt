package core.domain.usecase.card

import core.domain.model.Card
import core.domain.model.DataResult
import core.domain.repository.CardRepository
import javax.inject.Inject

class SearchCardUseCase @Inject constructor(
    private val repository: CardRepository,
) {
    suspend operator fun invoke(query: String): DataResult<Card> =
        repository.searchCardByName(query)
}