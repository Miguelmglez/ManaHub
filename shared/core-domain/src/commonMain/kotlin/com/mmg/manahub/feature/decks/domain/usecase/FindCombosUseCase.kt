package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.domain.repository.CommanderSpellbookRepository
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.feature.decks.domain.model.ComboResult

/**
 * Deck Engine Unification plan D7 (Phase 4.3) — the synergy browser's "Combos" tab use case.
 * A thin wrapper over [CommanderSpellbookRepository] (which owns the cache/API/degrade dispatch,
 * mirroring [com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCommunityUseCase]'s own
 * "use case delegates entirely to the repository" precedent): this use case's only job is
 * shaping the INPUT into what the Spellbook API can accept.
 *
 * @param dedupeAndCap Commander Spellbook's `main` array is capped at 600 entries -- collapses
 *   duplicate names (case-insensitive) and truncates deterministically (alphabetical, so the same
 *   oversized collection always caps to the SAME 600 names -- stable cache keys, stable results)
 *   rather than depending on Set/Map iteration order.
 */
class FindCombosUseCase(
    private val commanderSpellbookRepository: CommanderSpellbookRepository,
) {
    suspend operator fun invoke(
        cardNames: List<String>,
        commanderNames: List<String> = emptyList(),
    ): DataResult<ComboResult> {
        val commanders = commanderNames.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        val main = cardNames.map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { name -> commanders.none { it.equals(name, ignoreCase = true) } }
            .distinctBy { it.lowercase() }
            .sorted()
            .take(MAX_MAIN_CARDS)
        if (main.isEmpty() && commanders.isEmpty()) return DataResult.Success(ComboResult.EMPTY)
        return commanderSpellbookRepository.findCombos(cardNames = main, commanderNames = commanders)
    }

    private companion object {
        /** Commander Spellbook's documented `DeckRequest.main` cap. */
        const val MAX_MAIN_CARDS = 600
    }
}
