package com.mmg.manahub.feature.decks.domain.model

/**
 * A single named combo, fully found within the queried card set (Deck Engine Unification plan
 * D7, Phase 4.3 — Commander Spellbook `find-my-combos`). All card names are the exact Scryfall
 * (Oracle) names as returned by the Spellbook API — matched case-insensitively against the
 * user's own collection by [com.mmg.manahub.feature.decks.domain.usecase.FindCombosUseCase]'s
 * callers when handing seeds to the wizard.
 *
 * @param id the Spellbook variant id — stable, used as the Compose list key.
 * @param cardNames every card the combo needs, in API order (never re-sorted — the order often
 *   reflects the combo's own step sequence).
 * @param description short human-readable explanation of how the combo works.
 * @param produces the effect(s) the combo produces (e.g. "Infinite mana", "Win the game").
 */
data class Combo(
    val id: String,
    val cardNames: List<String>,
    val description: String,
    val produces: List<String>,
)

/**
 * A combo missing exactly one card from the queried card set — the Spellbook API's own
 * `almostIncluded` semantics (Deck Engine Unification plan D7). Surfaced separately from
 * [Combo] so the UI can render "you're 1 card away" distinctly from "you already have this".
 *
 * @param ownedCardNames the combo's OTHER cards (already present in the queried set).
 * @param missingCardName the one card the user doesn't have. Defensive: if the Spellbook API's
 *   `almostIncluded` classification ever includes more than one missing card for a given variant
 *   (not expected per its documented semantics, but this is an unofficial-adjacent third party --
 *   see [com.mmg.manahub.feature.decks.domain.usecase.FindCombosUseCase]), only the first missing
 *   card is surfaced here and the variant is otherwise treated normally rather than dropped.
 */
data class AlmostCombo(
    val id: String,
    val ownedCardNames: List<String>,
    val missingCardName: String,
    val description: String,
    val produces: List<String>,
)

/**
 * Aggregate result of a Commander Spellbook `find-my-combos` query (plan D7): combos the queried
 * card set already completes, and combos it is exactly one card away from completing.
 */
data class ComboResult(
    val complete: List<Combo>,
    val almostThere: List<AlmostCombo>,
) {
    companion object {
        val EMPTY = ComboResult(complete = emptyList(), almostThere = emptyList())
    }
}
