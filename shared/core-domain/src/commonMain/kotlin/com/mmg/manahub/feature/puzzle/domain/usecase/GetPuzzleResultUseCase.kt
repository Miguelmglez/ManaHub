package com.mmg.manahub.feature.puzzle.domain.usecase

import com.mmg.manahub.core.domain.repository.PuzzleRepository
import com.mmg.manahub.core.model.puzzle.PuzzleResult
import kotlinx.datetime.LocalDate

/**
 * Thin wrapper over [PuzzleRepository.getPuzzleResult].
 *
 * Added in Batch B2 (presentation layer) — not part of Batch B1's original three use cases. The
 * presentation layer's resume-check ("is there an in-progress attempt for today already?", see
 * `com.mmg.manahub.feature.puzzle.presentation.PuzzleViewModel`) needs this read, and the
 * ViewModel must not reach past the use-case layer to call [PuzzleRepository] directly — so this
 * mirrors [GetTodayPuzzleUseCase]/[SubmitPuzzleGuessUseCase]/[SavePuzzleResultUseCase]'s existing
 * one-thing-per-use-case convention rather than being a one-off exception to it.
 */
class GetPuzzleResultUseCase(
    private val puzzleRepository: PuzzleRepository,
) {
    suspend operator fun invoke(date: LocalDate): PuzzleResult? = puzzleRepository.getPuzzleResult(date)
}
