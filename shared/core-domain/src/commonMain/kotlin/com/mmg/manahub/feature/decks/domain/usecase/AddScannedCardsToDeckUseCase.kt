package com.mmg.manahub.feature.decks.domain.usecase
// COMMENTS_REVIEWED: 2026-09-16

import com.mmg.manahub.core.domain.repository.CardSlotWrite
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.model.DeckCardSource
import kotlinx.coroutines.flow.first

/** The deck board that receives cards from the scanner queue. */
enum class DeckBoard {
    MAINBOARD,
    SIDEBOARD,
}

/** A stable scanner-queue entry ready to be added to one deck board. */
data class ScannedDeckCardInput(
    val entryId: String,
    val scryfallId: String,
    val quantity: Int,
)

/** The entries and copy count committed by one scanner bulk action. */
data class AddScannedCardsToDeckResult(
    val committedEntryIds: Set<String>,
    val blockedCommanderEntryIds: Set<String>,
    val committedCopies: Int,
)

/** Merges scanner entries into one deck board and persists the complete slot list once. */
class AddScannedCardsToDeckUseCase(
    private val deckRepository: DeckRepository,
) {

    /** Adds [entries] to [board], preserving existing slot provenance and the opposite board. */
    suspend operator fun invoke(
        deckId: String,
        entries: List<ScannedDeckCardInput>,
        board: DeckBoard,
    ): AddScannedCardsToDeckResult {
        require(deckId.isNotBlank()) { "deckId must not be blank" }
        entries.forEach { entry ->
            require(entry.quantity > 0) { "quantity must be greater than zero" }
        }

        val deckWithCards = deckRepository.observeDeckWithCards(deckId).first()
            ?: error("Deck not found")
        val commanderId = deckWithCards.deck.commanderCardId
        val blockedCommanderEntryIds = entries.asSequence()
            .filter { board == DeckBoard.MAINBOARD && it.scryfallId == commanderId }
            .mapTo(LinkedHashSet()) { it.entryId }

        val permittedEntries = entries.filter { it.entryId !in blockedCommanderEntryIds }
        if (permittedEntries.isEmpty()) {
            return AddScannedCardsToDeckResult(
                committedEntryIds = emptySet(),
                blockedCommanderEntryIds = blockedCommanderEntryIds,
                committedCopies = 0,
            )
        }

        val groupedQuantities = LinkedHashMap<String, Long>()
        permittedEntries.forEach { entry ->
            val quantity = entry.quantity.toLong()
            val current = groupedQuantities[entry.scryfallId] ?: 0L
            val next = current + quantity
            check(next >= current) { "scanned quantity overflow" }
            check(next <= Int.MAX_VALUE.toLong()) { "scanned quantity exceeds Int range" }
            groupedQuantities[entry.scryfallId] = next
        }

        val committedCopiesLong = permittedEntries.sumOf { it.quantity.toLong() }
        check(committedCopiesLong <= Int.MAX_VALUE.toLong()) { "committed copies exceed Int range" }

        val mergedSlots = LinkedHashMap<BoardSlotKey, CardSlotWrite>()
        deckWithCards.mainboard.forEach { slot ->
            mergedSlots[BoardSlotKey(slot.scryfallId, isSideboard = false)] = CardSlotWrite(
                scryfallId = slot.scryfallId,
                quantity = slot.quantity,
                isSideboard = false,
                source = slot.source,
            )
        }
        deckWithCards.sideboard.forEach { slot ->
            mergedSlots[BoardSlotKey(slot.scryfallId, isSideboard = true)] = CardSlotWrite(
                scryfallId = slot.scryfallId,
                quantity = slot.quantity,
                isSideboard = true,
                source = slot.source,
            )
        }

        val targetIsSideboard = board == DeckBoard.SIDEBOARD
        groupedQuantities.forEach { (scryfallId, quantity) ->
            val key = BoardSlotKey(scryfallId, targetIsSideboard)
            val existing = mergedSlots[key]
            if (existing == null) {
                mergedSlots[key] = CardSlotWrite(
                    scryfallId = scryfallId,
                    quantity = quantity.toInt(),
                    isSideboard = targetIsSideboard,
                    source = DeckCardSource.USER,
                )
            } else {
                val mergedQuantity = existing.quantity.toLong() + quantity
                check(mergedQuantity <= Int.MAX_VALUE.toLong()) { "merged quantity exceeds Int range" }
                mergedSlots[key] = existing.copy(quantity = mergedQuantity.toInt())
            }
        }

        deckRepository.replaceAllCardsWithSource(deckId, mergedSlots.values.toList())

        return AddScannedCardsToDeckResult(
            committedEntryIds = permittedEntries.mapTo(LinkedHashSet()) { it.entryId },
            blockedCommanderEntryIds = blockedCommanderEntryIds,
            committedCopies = committedCopiesLong.toInt(),
        )
    }

    private data class BoardSlotKey(
        val scryfallId: String,
        val isSideboard: Boolean,
    )
}
