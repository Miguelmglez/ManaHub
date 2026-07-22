package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.StrategyProfile

/**
 * Deck Builder v2 wizard input (plan §3.2). Built by the wizard screens; consumed by
 * [DeckTemplateResolver] and [BuildDeckFromTemplateUseCase].
 *
 * @param format v1 targets [DeckFormat.COMMANDER] and [DeckFormat.CASUAL] only (plan D1) — the
 *   restored 60-card competitive formats (Phase 0) are accepted by the domain layer (nothing here
 *   enforces the v1 restriction; that is a wizard-UI concern) but have no dedicated template
 *   quality yet.
 * @param commander required only when [format] is [DeckFormat.COMMANDER]; picked from the user's
 *   owned legendaries or searched.
 * @param strategyProfile Deck Engine Unification plan (D2) — the SINGLE strategy taxonomy pick,
 *   replacing the pre-unification `strategyHint`/`tagHint`/`themeHint`/`tribeHint` quartet. Folded
 *   into [BuildDeckFromTemplateUseCase]'s seed tags via [com.mmg.manahub.feature.decks.domain.engine
 *   .DeckIdentitySeedTags] — the ONLY profile → tag bridge.
 * @param colorIdentity pre-filled from the commander/seeds' union; user-editable for Casual (D9 —
 *   Casual has no color-IDENTITY rule, this set is a spell-color FILTER instead). Stays the
 *   AUTHORITATIVE color set every build-engine call site reads — [StrategyProfile.colors] is a
 *   metadata echo of the same value in RUN 1 (becomes load-bearing for the colors-first Flow B,
 *   plan Phase 3, not yet implemented).
 * @param seeds optional; every seed is always kept in the built deck (merged into its resolved
 *   category even when that pushes the category over its target count — plan §3.3 step 4).
 * @param fillLands whether the FILLING_LANDS stage materializes basics (plan D7 default true).
 * @param useCommunityData Deck Engine Unification plan (§5 Phase 3.5) — the wizard's own per-build
 *   "also use community trends" toggle (Review step). Gates [BuildDeckFromTemplateUseCase]'s Motor B
 *   fetch TOGETHER WITH (not instead of) its constructor-level `isCommunityEngineEnabled` global
 *   flag check — both must be true for Motor B to run. Motor B only ever re-ranks/prioritizes cards
 *   the user already OWNS (see [BuildDeckFromTemplateUseCase]'s Motor B KDoc); there is no "community
 *   only" build mode, so this is a plain on/off rather than a 3-way source selector.
 */
data class DeckWizardSpec(
    val format: DeckFormat,
    val commander: Card? = null,
    val strategyProfile: StrategyProfile = StrategyProfile.EMPTY,
    val colorIdentity: Set<ManaColor> = emptySet(),
    val seeds: List<Card> = emptyList(),
    val fillLands: Boolean = true,
    val useCommunityData: Boolean = false,
)
