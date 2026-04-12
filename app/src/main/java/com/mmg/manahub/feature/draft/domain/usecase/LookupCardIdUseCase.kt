package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.feature.draft.domain.repository.DraftRepository
import javax.inject.Inject

class LookupCardIdUseCase @Inject constructor(
    private val repository: DraftRepository,
) {
    suspend operator fun invoke(cardName: String, setCode: String): DataResult<String> =
        repository.resolveCardId(cardName, setCode)
}
