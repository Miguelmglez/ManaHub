package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.repository.DraftRepository

class LookupCardIdUseCase(
    private val repository: DraftRepository,
) {
    suspend operator fun invoke(cardName: String, setCode: String): DataResult<String> =
        repository.resolveCardId(cardName, setCode)
}
