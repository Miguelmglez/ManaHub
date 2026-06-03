package com.mmg.manahub.feature.draft.domain.model

/**
 * Represents one seat (player or bot) in a draft.
 *
 * [colorCommitment] and [seenSignal] use single-letter color keys ("W", "U", "B", "R", "G")
 * matching [Card.colors]. Conversion to [ManaColor] happens only when the deck is built.
 */
data class DraftSeat(
    val index: Int,
    val isHuman: Boolean,
    val pool: List<DraftCard> = emptyList(),
    /** Accumulated weight per color letter, updated after each pick. */
    val colorCommitment: Map<String, Float> = emptyMap(),
    /** Strength of good cards seen but not picked, per color letter. */
    val seenSignal: Map<String, Float> = emptyMap(),
)
