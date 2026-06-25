package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.core.domain.repository.DraftRepository

class GetDraftableSetsUseCase(
    private val repository: DraftRepository,
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): DataResult<List<DraftSet>> =
        repository.getDraftableSets(forceRefresh)
}
