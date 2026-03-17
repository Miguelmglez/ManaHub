package com.mmg.magicfolder.code.core.domain.usecase.card

import com.mmg.magicfolder.code.core.domain.model.Card
import com.mmg.magicfolder.code.core.domain.model.DataResult
import com.mmg.magicfolder.code.core.domain.repository.CardRepository
import javax.inject.Inject

class SearchCardUseCase @Inject constructor(
    private val repository: CardRepository,
) {
    suspend operator fun invoke(query: String): DataResult<Card> =
        repository.searchCardByName(query)
}