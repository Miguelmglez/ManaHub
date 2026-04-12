package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.feature.draft.domain.model.DraftSet
import com.mmg.manahub.feature.draft.domain.repository.DraftRepository
import javax.inject.Inject

class GetDraftableSetsUseCase @Inject constructor(
    private val repository: DraftRepository,
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): DataResult<List<DraftSet>> =
        repository.getDraftableSets(forceRefresh)
}
