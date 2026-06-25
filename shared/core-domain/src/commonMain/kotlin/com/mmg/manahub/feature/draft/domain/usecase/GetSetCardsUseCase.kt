package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.repository.DraftRepository

class GetSetCardsUseCase(
    private val repository: DraftRepository,
) {
    suspend operator fun invoke(setCode: String, page: Int = 1): DataResult<List<Card>> =
        repository.getSetCards(setCode, page)
}
