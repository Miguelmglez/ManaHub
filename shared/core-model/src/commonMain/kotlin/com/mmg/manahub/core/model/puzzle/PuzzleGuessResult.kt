package com.mmg.manahub.core.model.puzzle

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** A single submitted guess and its resulting feedback, recorded as part of a [PuzzleResult]. */
@Serializable
data class PuzzleGuessResult(
    val guessedName: String,
    val guessedScryfallId: String,
    val feedback: PuzzleAttemptFeedback,
    val isCorrect: Boolean,
    val guessedAt: Instant,
)
