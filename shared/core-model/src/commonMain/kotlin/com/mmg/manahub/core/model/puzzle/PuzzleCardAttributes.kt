package com.mmg.manahub.core.model.puzzle

import kotlinx.serialization.Serializable

/**
 * The answer card's clue attributes for a [PuzzleType.GUESS_CARD] puzzle.
 *
 * Field names deliberately mirror [com.mmg.manahub.core.model.Card]'s own field names (`cmc`,
 * `colorIdentity`, `rarity`, `typeLine`, `setCode`, `power`, `toughness`) so a comparison function
 * consuming both a [Card][com.mmg.manahub.core.model.Card] and a [PuzzleCardAttributes] needs no
 * name-translation layer.
 */
@Serializable
data class PuzzleCardAttributes(
    val cmc: Double,
    val colorIdentity: List<String>,
    val rarity: String,
    val typeLine: String,
    val setCode: String,
    val releasedYear: Int,
    val power: String?,
    val toughness: String?,
)
