package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.engine.DraftDeckBuilder
import com.mmg.manahub.core.model.DraftResult
import com.mmg.manahub.core.model.DraftState
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Completes a draft session: builds the final deck from the human seat's pool and persists
 * the result.
 *
 * @param repository the draft simulation repository.
 * @param deckBuilder the deck builder that constructs a playable deck from the drafted pool.
 * @param ioDispatcher dispatcher for IO work (injected by the DI layer).
 * @param defaultDispatcher dispatcher for CPU-bound deck-building work (injected by the DI layer).
 */
class CompleteDraftUseCase(
    private val repository: DraftSimRepository,
    private val deckBuilder: DraftDeckBuilder,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(state: DraftState): DataResult<String> {
        val humanSeat = state.seats.first { it.isHuman }
        val deck = withContext(defaultDispatcher) { deckBuilder.build(humanSeat) }
        return withContext(ioDispatcher) {
            repository.completeAndSaveDeck(DraftResult(humanSeat, deck))
        }
    }
}
