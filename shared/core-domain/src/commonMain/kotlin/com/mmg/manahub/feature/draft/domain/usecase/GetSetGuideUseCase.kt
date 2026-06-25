package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.SetDraftGuide
import com.mmg.manahub.core.domain.repository.DraftRepository

class GetSetGuideUseCase(
    private val repository: DraftRepository,
) {
    suspend operator fun invoke(setCode: String): DataResult<SetDraftGuide> =
        repository.getSetGuide(setCode)
}
