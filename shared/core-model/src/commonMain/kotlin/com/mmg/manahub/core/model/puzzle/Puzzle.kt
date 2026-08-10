package com.mmg.manahub.core.model.puzzle

import kotlinx.datetime.LocalDate

/**
 * A daily puzzle as served by the puzzle-generation backend.
 *
 * [payloadJson] is kept as a raw JSON string at this layer rather than a typed model:
 * `core-model` has no Scryfall/attribute-specific coupling here, and a puzzle's payload shape is
 * defined per [PuzzleType] (e.g. [GuessCardPayload] for [PuzzleType.GUESS_CARD]). The use case that
 * knows how to interpret a given [type] parses [payloadJson] itself.
 */
data class Puzzle(
    val date: LocalDate,
    val type: PuzzleType,
    val payloadJson: String,
)
