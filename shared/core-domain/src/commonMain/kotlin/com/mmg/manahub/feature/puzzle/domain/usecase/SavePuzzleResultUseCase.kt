package com.mmg.manahub.feature.puzzle.domain.usecase

import com.mmg.manahub.core.domain.repository.PuzzleRepository
import com.mmg.manahub.core.model.puzzle.PuzzleResult

/**
 * Thin wrapper over [PuzzleRepository.savePuzzleResult]. The [com.mmg.manahub.core.gamification
 * .domain.event.ProgressionEvent.PuzzleSolved] emission on a solved result happens inside the
 * repository's write path, not here — see [PuzzleRepository.savePuzzleResult]'s KDoc.
 */
class SavePuzzleResultUseCase(
    private val puzzleRepository: PuzzleRepository,
) {
    suspend operator fun invoke(result: PuzzleResult) = puzzleRepository.savePuzzleResult(result)
}
