package com.mmg.manahub.core.model.puzzle

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Domain mirror of the persisted `puzzle_results` Room row (one per solved/attempted day, keyed by
 * [puzzleDate]). [guesses] round-trips through Room as JSON — see the `guesses_json` column on
 * `PuzzleResultEntity` in `:app`.
 */
@Serializable
data class PuzzleResult(
    val puzzleDate: LocalDate,
    val type: PuzzleType,
    val attempts: Int,
    val solved: Boolean,
    val perfect: Boolean,
    val elapsedMs: Long,
    val startedAt: Instant,
    val guesses: List<PuzzleGuessResult>,
    val completedAt: Instant?,
)
