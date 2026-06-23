package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.model.DraftState
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDraftUseCase @Inject constructor(
    private val repository: DraftSimRepository,
) {
    operator fun invoke(): Flow<DraftState?> = repository.observeActiveSession()
}
