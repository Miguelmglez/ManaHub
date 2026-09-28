package com.mmg.manahub.feature.decks.domain.usecase
// COMMENTS_REVIEWED: 2026-09-16

import com.mmg.manahub.core.domain.repository.DeckCardAddition
import com.mmg.manahub.core.domain.repository.DeckRepository

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
    val oracleId: String = "",
)

/** The entries and copy count committed by one scanner bulk action. */
data class AddScannedCardsToDeckResult(
    val committedEntryIds: Set<String>,
    val blockedCommanderEntryIds: Set<String>,
    val committedCopies: Int,
)

/** Validates scanner inputs and delegates their atomic merge to deck persistence. */
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

        val persistenceResult = deckRepository.mergeScannerCards(
            deckId = deckId,
            additions = entries.map { entry ->
                DeckCardAddition(
                    entryId = entry.entryId,
                    scryfallId = entry.scryfallId,
                    oracleId = entry.oracleId,
                    quantity = entry.quantity,
                    isSideboard = board == DeckBoard.SIDEBOARD,
                )
            },
        )
        return AddScannedCardsToDeckResult(
            committedEntryIds = persistenceResult.committedEntryIds,
            blockedCommanderEntryIds = persistenceResult.blockedCommanderEntryIds,
            committedCopies = persistenceResult.committedCopies,
        )
    }
}
