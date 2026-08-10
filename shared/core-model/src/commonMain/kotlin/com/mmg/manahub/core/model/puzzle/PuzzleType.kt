package com.mmg.manahub.core.model.puzzle

/**
 * The kind of daily puzzle. New puzzle types are expected to ship in later phases without a client
 * update on every device landing simultaneously — [fromWire] NEVER throws on an unrecognized wire
 * value, it degrades to [UNKNOWN] so an older client can still render a graceful "unsupported puzzle
 * type" state instead of crashing when the puzzle-generation backend starts emitting a newer type.
 */
enum class PuzzleType {
    /** Guess the card from a set of progressively-revealed attribute clues. */
    GUESS_CARD,

    /** Forward-compat fallback for any puzzle type this client build does not recognize. */
    UNKNOWN,
    ;

    companion object {
        /**
         * Maps a raw wire value (as sent by the puzzle-generation backend) to a [PuzzleType].
         * Matching is case-sensitive against the enum's own wire names — any value that does not
         * match a known [PuzzleType] (including a genuinely new type from a future phase) maps to
         * [UNKNOWN] rather than throwing.
         */
        fun fromWire(raw: String): PuzzleType =
            entries.firstOrNull { it.name == raw && it != UNKNOWN } ?: UNKNOWN
    }
}
