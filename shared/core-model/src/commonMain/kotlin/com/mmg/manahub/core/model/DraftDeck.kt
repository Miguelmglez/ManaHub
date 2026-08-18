package com.mmg.manahub.core.model

/**
 * The final built deck from a draft: mainboard picks + basic lands, plus whatever pool cards the
 * user benched to the sideboard.
 *
 * Produced either blindly by [com.mmg.manahub.core.domain.engine.DraftDeckBuilder] (top-23 by
 * score + proportional lands, [sideboard] empty) or, since the Draft Simulator Deck-tab curation
 * UI (Phase D), assembled directly by the presentation layer from the human seat's full pool split
 * by the user's active/inactive (mainboard/sideboard) toggles and the editable basic-land counts —
 * see `DraftSimViewModel.onCompleteDraft`.
 */
data class DraftDeck(
    val mainboard: List<DraftCard>,
    /** Basic land slots. See [BasicLandSlot.scryfallId]. */
    val basics: List<BasicLandSlot>,
    /**
     * Pool cards the user explicitly benched (Phase D active/inactive toggle). Defaults to empty
     * so every pre-Phase-D caller/construction site (the blind [com.mmg.manahub.core.domain.engine
     * .DraftDeckBuilder] path, existing tests) keeps compiling unchanged.
     */
    val sideboard: List<DraftCard> = emptyList(),
)

/**
 * A basic-land allocation for the built deck.
 *
 * @property scryfallId empty as produced by the deck builder (which has no network access) —
 *   resolved to a real Scryfall id by the repository right before persisting
 *   (see `DraftSimRepositoryImpl.completeAndSaveDeck`). Never persist a [BasicLandSlot] whose
 *   `scryfallId` is still empty.
 */
data class BasicLandSlot(
    val scryfallId: String,
    val name: String,
    val count: Int,
)
