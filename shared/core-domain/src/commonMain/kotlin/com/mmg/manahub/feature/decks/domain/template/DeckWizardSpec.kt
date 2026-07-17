package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.SeedStrategy

/**
 * Deck Builder v2 wizard input (plan §3.2). Built by the (Phase 3, out of scope here) wizard
 * screens; consumed by [DeckTemplateResolver] and [BuildDeckFromTemplateUseCase].
 *
 * @param format v1 targets [DeckFormat.COMMANDER] and [DeckFormat.CASUAL] only (plan D1) — the
 *   restored 60-card competitive formats (Phase 0) are accepted by the domain layer (nothing here
 *   enforces the v1 restriction; that is a wizard-UI concern for Phase 3) but have no dedicated
 *   template quality yet.
 * @param commander required only when [format] is [DeckFormat.COMMANDER]; picked from the user's
 *   owned legendaries or searched.
 * @param strategyHint optional; suggested from the user's own collection stats
 *   ([CollectionProfileUseCase]) or picked explicitly in the wizard.
 * @param themeHint optional EDHREC theme tag string (from a fetched aggregate's
 *   [com.mmg.manahub.core.model.CommunityAggregate.Commander.themeTags]).
 * @param colorIdentity pre-filled from the commander/seeds' union; user-editable for Casual (D9 —
 *   Casual has no color-IDENTITY rule, this set is a spell-color FILTER instead).
 * @param seeds optional; every seed is always kept in the built deck (merged into its resolved
 *   category even when that pushes the category over its target count — plan §3.3 step 4).
 * @param fillLands whether the FILLING_LANDS stage materializes basics (plan D7 default true).
 */
data class DeckWizardSpec(
    val format: DeckFormat,
    val commander: Card? = null,
    val strategyHint: SeedStrategy? = null,
    val themeHint: String? = null,
    val colorIdentity: Set<ManaColor> = emptySet(),
    val seeds: List<Card> = emptyList(),
    val fillLands: Boolean = true,
)
