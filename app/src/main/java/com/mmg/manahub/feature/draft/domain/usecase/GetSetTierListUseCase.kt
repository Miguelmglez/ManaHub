package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.feature.draft.domain.model.SetTierList
import com.mmg.manahub.feature.draft.domain.repository.DraftRepository
import javax.inject.Inject

class GetSetTierListUseCase @Inject constructor(
    private val repository: DraftRepository,
) {
    suspend operator fun invoke(setCode: String): DataResult<SetTierList> =
        repository.getSetTierList(setCode)
}
