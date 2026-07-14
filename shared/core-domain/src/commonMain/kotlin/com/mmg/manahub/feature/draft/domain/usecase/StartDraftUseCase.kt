package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.engine.DraftEngine
import com.mmg.manahub.core.model.DraftConfig
import com.mmg.manahub.core.model.DraftState
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Starts a new draft session: fetches the draftable set, runs the engine, and persists the initial state.
 *
 * @param repository the draft simulation repository.
 * @param engine the draft engine that generates boosters and initial state.
 * @param ioDispatcher dispatcher for IO work (injected by the DI layer).
 * @param defaultDispatcher dispatcher for CPU-bound engine work (injected by the DI layer).
 */
class StartDraftUseCase(
    private val repository: DraftSimRepository,
    private val engine: DraftEngine,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(setCode: String, config: DraftConfig): DataResult<DraftState> {
        val setResult = withContext(ioDispatcher) { repository.getDraftableSimSet(setCode) }
        if (setResult is DataResult.Error) return setResult
        val set = (setResult as DataResult.Success).data
        val state = withContext(defaultDispatcher) { engine.start(set, config) }
        withContext(ioDispatcher) { repository.saveSession(state) }
        return DataResult.Success(state)
    }
}
