package com.mmg.manahub.core.model.puzzle

import kotlinx.serialization.Serializable

/**
 * Per-attribute feedback for a single guess against a [PuzzleType.GUESS_CARD] puzzle's answer,
 * consumed by the guess UI to render directional/match chips (e.g. Wordle-style hints).
 */
@Serializable
data class PuzzleAttemptFeedback(
    /** Whether the guessed card's mana value is HIGHER/LOWER than, or MATCHes, the answer's. */
    val cmc: Comparison,

    /**
     * MATCH when the guessed card's color identity is exactly the same set of colors as the
     * answer's; PARTIAL when the two sets overlap (share at least one color) without being
     * identical; NONE when they share no colors at all (including one side being colorless while
     * the other is not).
     */
    val colorIdentity: MatchLevel,

    /** MATCH/PARTIAL/NONE are identical strings — this project has a single rarity taxonomy. */
    val rarity: MatchLevel,

    /**
     * MATCH when the guessed card's full type line matches the answer's exactly; PARTIAL when the
     * two type lines share at least one card type or subtype token (e.g. both are "Creature", or
     * both share the subtype "Elf") without being identical; NONE when they share no type/subtype
     * tokens at all.
     */
    val typeLine: MatchLevel,

    /** MATCH/PARTIAL/NONE are identical set-code strings — there is no notion of a "partial" set. */
    val setCode: MatchLevel,

    /** Whether the guessed card's release year is HIGHER/LOWER than, or MATCHes, the answer's. */
    val releasedYear: Comparison,

    /**
     * Power comparison, numeric where possible. Null when either side is non-numeric (`*`, `X`, or
     * the guessed/answer card has no power, e.g. a non-creature) — the UI shows no power chip in
     * that case rather than a misleading comparison.
     */
    val power: Comparison?,

    /** Toughness comparison — same non-numeric/absent handling as [power]. */
    val toughness: Comparison?,
) {
    @Serializable
    enum class Comparison { HIGHER, LOWER, MATCH }

    @Serializable
    enum class MatchLevel { MATCH, PARTIAL, NONE }
}
