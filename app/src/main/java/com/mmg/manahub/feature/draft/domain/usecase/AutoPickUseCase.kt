package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.feature.draft.domain.engine.DraftEngine
import com.mmg.manahub.feature.draft.domain.model.DraftState
import com.mmg.manahub.feature.draft.domain.repository.DraftSimRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AutoPickUseCase @Inject constructor(
    private val repository: DraftSimRepository,
    private val engine: DraftEngine,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(state: DraftState): DataResult<DraftState> {
        val newState = withContext(Dispatchers.Default) { engine.autoPick(state) }
        withContext(ioDispatcher) { repository.saveSession(newState) }
        return DataResult.Success(newState)
    }
}
