package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.di.DefaultDispatcher
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.engine.DraftEngine
import com.mmg.manahub.core.model.DraftState
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MakePickUseCase @Inject constructor(
    private val repository: DraftSimRepository,
    private val engine: DraftEngine,
    @IoDispatcher      private val ioDispatcher:      CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(state: DraftState, scryfallId: String): DataResult<DraftState> {
        // Best-effort engine load; null (no engine.json or any failure) → heuristic fallback.
        val engineConfig = withContext(ioDispatcher) {
            repository.getEngineConfig(state.config.setCode)
        }
        val newState = withContext(defaultDispatcher) {
            engine.applyHumanPick(state, scryfallId, engineConfig)
        }
        withContext(ioDispatcher) { repository.saveSession(newState) }
        return DataResult.Success(newState)
    }
}
