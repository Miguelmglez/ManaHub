package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DraftDeck
import com.mmg.manahub.core.model.DraftResult
import com.mmg.manahub.core.model.DraftState
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Persists a completed draft's final [DraftDeck] (mainboard + sideboard + basics) for the human
 * seat.
 *
 * Draft Simulator Phase D: this use case no longer builds the deck itself. Before Phase D it always
 * blindly re-derived the mainboard via `DraftDeckBuilder.build(seat)` (top-23 by score + proportional
 * lands), silently discarding any curation the user did on the Deck tab. The Deck tab now owns a
 * real active/inactive (mainboard/sideboard) toggle per pool card plus an editable basic-land count
 * (`DraftSimViewModel`'s `_inactiveCardIds`/`_basicLandCounts` state), so the ViewModel assembles the
 * final [DraftDeck] itself from that curation and hands it here ready to persist — this use case's
 * only remaining job is the IO-dispatched repository call.
 *
 * @param repository the draft simulation repository.
 * @param ioDispatcher dispatcher for IO work (injected by the DI layer).
 */
class CompleteDraftUseCase(
    private val repository: DraftSimRepository,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(state: DraftState, deck: DraftDeck): DataResult<String> {
        val humanSeat = state.seats.first { it.isHuman }
        return withContext(ioDispatcher) {
            repository.completeAndSaveDeck(DraftResult(humanSeat, deck))
        }
    }
}
