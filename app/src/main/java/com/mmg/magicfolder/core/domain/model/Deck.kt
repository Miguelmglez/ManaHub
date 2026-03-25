package com.mmg.magicfolder.core.domain.model

data class Deck(
    val id:          Long    = 0,
    val name:        String,
    val description: String? = null,
    val format:      String  = "casual",
    val coverCardId: String? = null,
    val createdAt:   Long    = System.currentTimeMillis(),
    val updatedAt:   Long    = System.currentTimeMillis(),
)

data class DeckSlot(
    val scryfallId: String,
    val quantity:   Int,
)

data class DeckWithCards(
    val deck:      Deck,
    val mainboard: List<DeckSlot>,
    val sideboard: List<DeckSlot>,
) {
    val totalCards: Int get() = mainboard.sumOf { it.quantity }
}
