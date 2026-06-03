package com.mmg.manahub.feature.draft.presentation.viewmodel

import com.mmg.manahub.feature.draft.domain.model.DraftCard
import com.mmg.manahub.feature.draft.domain.model.DraftError
import com.mmg.manahub.feature.draft.domain.model.DraftState

/**
 * Single source of truth for the Draft Simulator presentation layer. The same
 * sealed hierarchy backs the Setup, Drafting, and Result screens — each screen
 * renders only the states it cares about and falls back to a neutral loading /
 * error UI for the rest.
 */
sealed interface DraftSimUiState {

    /** Initial / transitional loading (set assembly, draft start, deck build kickoff). */
    data object Loading : DraftSimUiState

    /**
     * Long-running download of the set card pool with a determinate [progress] (0f..1f)
     * and a human-readable [message] describing the current step.
     */
    data class Downloading(val progress: Float, val message: String) : DraftSimUiState

    /**
     * The set is resolved and ready to configure. Shown by the Setup screen.
     *
     * @property boosterVersion non-null booster config version; presence implies the set is simulable.
     */
    data class SetupReady(
        val setCode: String,
        val setName: String,
        val boosterVersion: String,
    ) : DraftSimUiState

    /**
     * An active draft in progress. Shown by the Drafting screen.
     *
     * @property currentPack the cards in the pack currently in front of the human seat.
     * @property poolSize number of cards the human has drafted so far.
     * @property timerSecondsLeft remaining seconds on the pick timer, or null when no timer is configured.
     */
    data class Drafting(
        val state: DraftState,
        val currentPack: List<DraftCard>,
        val poolSize: Int,
        val timerSecondsLeft: Int?,
    ) : DraftSimUiState

    /**
     * All packs are drafted; the deck is being assembled / previewed. Shown by the Result screen.
     */
    data class Building(val state: DraftState) : DraftSimUiState

    /** The deck was saved successfully; [deckId] is the UUID of the created deck. */
    data class Complete(val deckId: String) : DraftSimUiState

    /** A recoverable or terminal error. [retryable] controls whether a retry action is offered. */
    data class Error(val error: DraftError, val retryable: Boolean = true) : DraftSimUiState
}
