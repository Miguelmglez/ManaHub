package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.feature.draft.domain.model.DraftState
import com.mmg.manahub.feature.draft.domain.repository.DraftSimRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDraftUseCase @Inject constructor(
    private val repository: DraftSimRepository,
) {
    operator fun invoke(): Flow<DraftState?> = repository.observeActiveSession()
}
