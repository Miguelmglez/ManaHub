package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.di.DefaultDispatcher
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.feature.draft.domain.engine.DraftDeckBuilder
import com.mmg.manahub.feature.draft.domain.model.DraftResult
import com.mmg.manahub.feature.draft.domain.model.DraftState
import com.mmg.manahub.feature.draft.domain.repository.DraftSimRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CompleteDraftUseCase @Inject constructor(
    private val repository: DraftSimRepository,
    private val deckBuilder: DraftDeckBuilder,
    @IoDispatcher      private val ioDispatcher:      CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(state: DraftState): DataResult<String> {
        val humanSeat = state.seats.first { it.isHuman }
        val deck = withContext(defaultDispatcher) { deckBuilder.build(humanSeat) }
        return withContext(ioDispatcher) {
            repository.completeAndSaveDeck(DraftResult(humanSeat, deck))
        }
    }
}
