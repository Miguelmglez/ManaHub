package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.engine.DraftEngine
import com.mmg.manahub.core.model.DraftState
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Auto-picks a card for the human player using the draft engine (used when the timer expires or
 * the player skips). Loads the engine config, runs the auto-pick, and persists the updated state.
 *
 * @param repository the draft simulation repository.
 * @param engine the draft engine that selects the auto-pick.
 * @param ioDispatcher dispatcher for IO work (injected by the DI layer).
 * @param defaultDispatcher dispatcher for CPU-bound engine work (injected by the DI layer).
 */
class AutoPickUseCase(
    private val repository: DraftSimRepository,
    private val engine: DraftEngine,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(state: DraftState): DataResult<DraftState> {
        // Best-effort engine load; null (no engine.json or any failure) → heuristic fallback.
        val engineConfig = withContext(ioDispatcher) {
            repository.getEngineConfig(state.config.setCode)
        }
        val newState = withContext(defaultDispatcher) { engine.autoPick(state, engineConfig) }
        withContext(ioDispatcher) { repository.saveSession(newState) }
        return DataResult.Success(newState)
    }
}
