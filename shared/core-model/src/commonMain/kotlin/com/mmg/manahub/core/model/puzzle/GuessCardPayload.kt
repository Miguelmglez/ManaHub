package com.mmg.manahub.core.model.puzzle

import kotlinx.serialization.Serializable

/**
 * Parsed [PuzzleType.GUESS_CARD] payload — the shape of [Puzzle.payloadJson] for this puzzle type.
 *
 * The answer card's name is never shipped in the clear: [answerNameHash] is a
 * SHA-256(`normalizedName + dailySalt`) digest (see
 * [com.mmg.manahub.core.model.puzzle.PuzzleNameNormalizer]) so a client cannot trivially read the
 * answer out of the network response. [dailySalt] is per-puzzle so the same card name never hashes
 * to the same digest across two different days. [canonicalScryfallId] identifies the answer's
 * canonical printing for post-solve UI (card image, link) — it is safe to ship in the clear because
 * it alone does not reveal the answer without also knowing which of the day's candidate cards it
 * points to.
 *
 * @param maxGuesses the server-authoritative guess budget for this puzzle (published by
 *   `tools/puzzle-generator/stages/emit.mjs`'s `DEFAULT_MAX_GUESSES`, required by
 *   `schemaValidate.mjs`). The client MUST read this rather than hardcoding its own budget — see
 *   [com.mmg.manahub.feature.puzzle.presentation.PuzzleViewModel] for the consumer.
 */
@Serializable
data class GuessCardPayload(
    val answerNameHash: String,
    val dailySalt: String,
    val canonicalScryfallId: String,
    val attributes: PuzzleCardAttributes,
    val maxGuesses: Int,
)
