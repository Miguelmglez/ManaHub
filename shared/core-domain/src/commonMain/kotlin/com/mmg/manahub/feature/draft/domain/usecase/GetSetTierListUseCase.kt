package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.SetTierList
import com.mmg.manahub.core.domain.repository.DraftRepository

class GetSetTierListUseCase(
    private val repository: DraftRepository,
) {
    suspend operator fun invoke(setCode: String): DataResult<SetTierList> =
        repository.getSetTierList(setCode)
}
