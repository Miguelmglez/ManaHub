package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.feature.decks.domain.engine.DeckImportExportHelper
import javax.inject.Inject

/**
 * Imports a Moxfield / MTG Arena text deck list INTO an already-existing deck.
 *
 * Extracted from `DeckViewModel.importDeck` so the unified Deck Studio can import
 * straight into its live draft deck (rather than always creating a brand-new deck).
 * The parsed lines are resolved against Scryfall by name and written through the
 * [DeckRepository]; a single unresolvable line is SKIPPED (it never aborts the whole
 * import — mirrors the legacy [com.mmg.manahub.feature.decks.presentation.DeckViewModel]
 * behaviour).
 *
 * Note: [DeckImportExportHelper.ParsedDeckList] exposes no deck name, so this use case
 * does NOT rename the target deck — the caller keeps the live deck's existing name.
 */
class ImportDeckUseCase @Inject constructor(
    private val cardRepository: CardRepository,
    private val deckRepository: DeckRepository,
) {

    /**
     * Parses [text] and writes the resolved cards into the deck identified by [deckId].
     *
     * @param deckId the live deck to import into (must already exist).
     * @param text the raw pasted deck list (Moxfield / Arena format).
     * @return [Result.success] once parsing + all resolvable writes complete (even when
     *         some individual lines failed to resolve); [Result.failure] only when parsing
     *         or a repository write throws.
     */
    suspend operator fun invoke(deckId: String, text: String): Result<Unit> = runCatching {
        val parsed = DeckImportExportHelper.parse(text)

        parsed.mainboard.forEach { line -> addLine(deckId, line.name, line.quantity, isSideboard = false) }
        parsed.sideboard.forEach { line -> addLine(deckId, line.name, line.quantity, isSideboard = true) }
        parsed.commander?.let { line -> addLine(deckId, line.name, line.quantity, isSideboard = false) }
    }

    /**
     * Resolves a single parsed line by name and writes it to the deck. A failed name
     * resolution is a no-op (skip + continue) — never an aborting error.
     */
    private suspend fun addLine(deckId: String, name: String, quantity: Int, isSideboard: Boolean) {
        val result = cardRepository.searchCardByName(name)
        if (result is DataResult.Success) {
            deckRepository.addCardToDeck(
                deckId = deckId,
                scryfallId = result.data.scryfallId,
                quantity = quantity,
                isSideboard = isSideboard,
            )
        }
    }
}
