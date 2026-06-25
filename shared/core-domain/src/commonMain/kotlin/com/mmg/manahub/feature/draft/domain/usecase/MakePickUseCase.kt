package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.engine.DraftEngine
import com.mmg.manahub.core.model.DraftState
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Applies the human player's card pick and advances the draft: loads the engine config, applies
 * the pick via the engine, and persists the updated state.
 *
 * @param repository the draft simulation repository.
 * @param engine the draft engine that processes picks.
 * @param ioDispatcher dispatcher for IO work (injected by the DI layer).
 * @param defaultDispatcher dispatcher for CPU-bound engine work (injected by the DI layer).
 */
class MakePickUseCase(
    private val repository: DraftSimRepository,
    private val engine: DraftEngine,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher,
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
