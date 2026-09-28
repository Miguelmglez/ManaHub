package com.mmg.manahub.feature.decks.domain.inspirations

import com.mmg.manahub.core.domain.repository.CommanderSpellbookRepository
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.model.CardCombo
import com.mmg.manahub.feature.decks.domain.model.CardComboPage

/** Every Commander Spellbook combo that includes one card, narrowed to what a 60-card deck of [DeckFormat] can run. */
class FindCombosWithCardUseCase(
    private val commanderSpellbookRepository: CommanderSpellbookRepository,
) {

    suspend operator fun invoke(cardName: String, format: DeckFormat, page: Int): DataResult<CardComboPage> =
        when (val result = commanderSpellbookRepository.findCombosWithCard(cardName, page)) {
            is DataResult.Success -> DataResult.Success(
                result.data.copy(combos = result.data.combos.filter { isPlayableIn(it, format) }),
                isStale = result.isStale,
            )
            is DataResult.Error -> result
        }

    internal fun isPlayableIn(combo: CardCombo, format: DeckFormat): Boolean {
        // A 60-card deck has no command zone, so these combos can never assemble.
        if (combo.requiresCommandZone) return false
        val key = spellbookFormatKey(format) ?: return true
        return combo.legalities[key] != false
    }

    private fun spellbookFormatKey(format: DeckFormat): String? = when (format) {
        DeckFormat.STANDARD -> "standard"
        DeckFormat.PIONEER -> "pioneer"
        DeckFormat.MODERN -> "modern"
        DeckFormat.LEGACY -> "legacy"
        DeckFormat.VINTAGE -> "vintage"
        DeckFormat.PAUPER -> "pauper"
        DeckFormat.COMMANDER, DeckFormat.COMMANDER_CASUAL, DeckFormat.CASUAL, DeckFormat.DRAFT -> null
    }
}
