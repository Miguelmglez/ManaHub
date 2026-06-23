package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.SetDraftGuide
import com.mmg.manahub.core.domain.repository.DraftRepository
import javax.inject.Inject

class GetSetGuideUseCase @Inject constructor(
    private val repository: DraftRepository,
) {
    suspend operator fun invoke(setCode: String): DataResult<SetDraftGuide> =
        repository.getSetGuide(setCode)
}
