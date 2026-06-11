package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.di.DefaultDispatcher
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.feature.draft.domain.engine.DraftEngine
import com.mmg.manahub.feature.draft.domain.model.DraftConfig
import com.mmg.manahub.feature.draft.domain.model.DraftState
import com.mmg.manahub.feature.draft.domain.repository.DraftSimRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class StartDraftUseCase @Inject constructor(
    private val repository: DraftSimRepository,
    private val engine: DraftEngine,
    @IoDispatcher      private val ioDispatcher:      CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
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
