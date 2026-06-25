package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DraftableSet
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Fetches a single draftable set by [setCode] from the repository.
 *
 * @param repository the draft simulation repository.
 * @param ioDispatcher dispatcher for IO work (injected by the DI layer).
 */
class GetDraftableSimSetUseCase(
    private val repository: DraftSimRepository,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(setCode: String): DataResult<DraftableSet> =
        withContext(ioDispatcher) { repository.getDraftableSimSet(setCode) }
}
