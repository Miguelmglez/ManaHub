package com.mmg.manahub.core.model

/** Card slot inside the deck builder (with quantity and ownership status). */
data class DeckCard(
    val card: Card,
    val quantity: Int = 1,
    val isOwned: Boolean = false,
)

/** Computed distribution of basic land counts for a deck's mana base. */
data class BasicLandDistribution(
    val plains: Int = 0,
    val islands: Int = 0,
    val swamps: Int = 0,
    val mountains: Int = 0,
    val forests: Int = 0,
) {
    val total: Int get() = plains + islands + swamps + mountains + forests

    fun toMap(): Map<String, Int> = mapOf(
        "W" to plains,
        "U" to islands,
        "B" to swamps,
        "R" to mountains,
        "G" to forests,
    ).filter { it.value > 0 }
}
