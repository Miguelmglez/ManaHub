package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.feature.draft.domain.repository.DraftRepository
import javax.inject.Inject

class GetCardByNameUseCase @Inject constructor(
    private val repository: DraftRepository,
) {
    suspend operator fun invoke(name: String, setCode: String): DataResult<Card> =
        repository.getCardByName(name, setCode)
}
