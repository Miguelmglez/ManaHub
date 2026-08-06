package com.mmg.manahub.feature.puzzle.presentation

import com.mmg.manahub.core.model.puzzle.Puzzle
import com.mmg.manahub.core.model.puzzle.PuzzleGuessResult
import com.mmg.manahub.core.model.puzzle.PuzzleResult

/**
 * UI state for the Daily Puzzle screen, driven by [PuzzleViewModel].
 *
 * The state machine is intentionally flat (no nested loading/error flags inside [Playing]) — every
 * top-level phase of an attempt is its own variant, so the screen can render each with a `when`
 * that the compiler keeps exhaustive.
 */
sealed interface PuzzleUiState {

    /** Initial state while resolving whether today's attempt is fresh or resumable. */
    object Loading : PuzzleUiState

    /**
     * Today's puzzle could not be loaded and there is nothing playable to fall back to.
     *
     * In practice this fires on ANY [com.mmg.manahub.feature.puzzle.domain.usecase
     * .GetTodayPuzzleUseCase] failure, not only "no network and nothing resumable locally": the
     * puzzle's payload (clue attributes, salted answer hash) is never persisted locally — only the
     * submitted guesses/outcome are (see `PuzzleResultEntity`) — so even a locally-resumable
     * attempt still requires a successful network fetch before it can continue. There is no
     * local-only playable state.
     */
    object Offline : PuzzleUiState

    /**
     * An attempt is in progress (fresh or resumed). [guesses] is the ordered history of guesses
     * submitted so far this attempt; [resumedFromCache] is true when [guesses] was seeded from a
     * locally-persisted in-progress [PuzzleResult] rather than starting empty. [maxGuesses] is the
     * server-authoritative guess budget resolved from [puzzle]'s own payload (see
     * [com.mmg.manahub.feature.puzzle.presentation.PuzzleViewModel.resolveMaxGuesses]) — NEVER a
     * client-side constant, so the app always enforces the same budget the generator published.
     */
    data class Playing(
        val puzzle: Puzzle,
        val guesses: List<PuzzleGuessResult>,
        val resumedFromCache: Boolean,
        val maxGuesses: Int,
    ) : PuzzleUiState

    /**
     * The attempt has ended — either solved ([PuzzleResult.solved] true) or the guess budget was
     * exhausted without a correct guess ([PuzzleResult.solved] false). Both outcomes share this one
     * variant; the screen distinguishes them by reading [PuzzleResult.solved].
     */
    data class Solved(val result: PuzzleResult) : PuzzleUiState

    /** An unexpected error (not a plain network/content failure — see [Offline] for that). */
    data class Failed(val message: String) : PuzzleUiState
}
