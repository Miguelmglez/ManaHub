package com.mmg.manahub.feature.puzzle.domain.usecase

import com.mmg.manahub.core.domain.repository.PuzzleRepository
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.puzzle.Puzzle

/** Thin wrapper over [PuzzleRepository.getTodayPuzzle]. */
class GetTodayPuzzleUseCase(
    private val puzzleRepository: PuzzleRepository,
) {
    suspend operator fun invoke(): DataResult<Puzzle> = puzzleRepository.getTodayPuzzle()
}
