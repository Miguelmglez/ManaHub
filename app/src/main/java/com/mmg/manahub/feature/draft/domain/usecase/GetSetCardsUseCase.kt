package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.feature.draft.domain.repository.DraftRepository
import javax.inject.Inject

class GetSetCardsUseCase @Inject constructor(
    private val repository: DraftRepository,
) {
    suspend operator fun invoke(setCode: String, page: Int = 1): DataResult<List<Card>> =
        repository.getSetCards(setCode, page)
}
