package com.mmg.manahub.core.domain.model

data class Deck(
    val id:          String,                                   // UUID, client-generated
    val userId:      String? = null,
    val name:        String,
    val description: String  = "",
    val format:      String  = "casual",
    val coverCardId: String? = null,
    val isDeleted:   Boolean = false,
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
