package com.mmg.manahub.core.domain.model

/** Lightweight deck info used in the deck list — includes aggregated card data. */
data class DeckSummary(
    val id: Long,
    val name: String,
    val description: String?,
    val format: String,
    val coverCardId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** Total quantity of mainboard cards. */
    val cardCount: Int,
    /** Union of all mainboard cards' color identity codes (e.g. "W", "U", "B", "R", "G"). */
    val colorIdentity: Set<String>,
    /** Art-crop URL of the cover card, or the first mainboard card if no cover is set. */
    val coverImageUrl: String?,
)
