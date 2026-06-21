package com.mmg.manahub.feature.playtest.domain.usecase

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DeckSlot

/**
 * Builds and shuffles the library for a playtest session.
 *
 * The library is constructed by expanding each [DeckSlot] into [DeckSlot.quantity]
 * individual [Card] copies. For commander format, the commander card is excluded
 * and handled separately in the "command zone". Sideboard slots are always excluded.
 *
 * @see DrawHandUseCase
 */
class BuildLibraryUseCase {

    /**
     * Builds a shuffled library from the mainboard slots.
     *
     * @param mainboardSlots The deck's mainboard slots (DeckSlot list with quantities).
     * @param cardLookup Function to resolve a scryfallId to a [Card]; returns null if
     *   the card is not locally cached. Missing cards are skipped with a placeholder.
     * @param commanderScryfallId If non-null, exclude this scryfallId from the library
     *   (commander format only — the commander lives in the command zone).
     * @return A freshly shuffled list of [Card] objects ready to draw from.
     */
    operator fun invoke(
        mainboardSlots: List<DeckSlot>,
        cardLookup: Map<String, Card>,
        commanderScryfallId: String? = null,
    ): List<Card> {
        val library = mutableListOf<Card>()

        for (slot in mainboardSlots) {
            // Exclude the commander from the library — it lives in the command zone.
            if (slot.scryfallId == commanderScryfallId) continue

            val card = cardLookup[slot.scryfallId] ?: continue
            repeat(slot.quantity) { library.add(card) }
        }

        // Shuffle in place — standard Fisher-Yates via Kotlin's built-in.
        library.shuffle()
        return library
    }
}
