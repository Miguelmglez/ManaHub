package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.feature.draft.domain.model.DraftableSet
import com.mmg.manahub.feature.draft.domain.repository.DraftSimRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetDraftableSimSetUseCase @Inject constructor(
    private val repository: DraftSimRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(setCode: String): DataResult<DraftableSet> =
        withContext(dispatcher) { repository.getDraftableSimSet(setCode) }
}
