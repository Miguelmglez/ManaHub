package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.model.DraftState
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import kotlinx.coroutines.flow.Flow

class ObserveDraftUseCase(
    private val repository: DraftSimRepository,
) {
    operator fun invoke(): Flow<DraftState?> = repository.observeActiveSession()
}
