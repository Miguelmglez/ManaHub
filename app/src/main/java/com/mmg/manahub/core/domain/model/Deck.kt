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

/**
 * Represents a card slot inside a persisted deck with the full card data.
 *
 * Named [DeckSlotEntry] to avoid collision with the domain-level
 * [com.mmg.manahub.core.domain.model.DeckCard] used by the deck builder.
 */
data class DeckSlotEntry(
    val scryfallId: String,
    val quantity: Int,
    val isSideboard: Boolean,
    val card: Card?,
)

val BASIC_LAND_NAMES = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")

data class AddCardRow(
    val card: Card,
    val quantityInDeck: Int,
    val isOwned: Boolean,
)
