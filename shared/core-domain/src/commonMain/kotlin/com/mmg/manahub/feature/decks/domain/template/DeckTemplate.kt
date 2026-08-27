package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.ThemeId

/**
 * Deck Builder v2 (`docs/plans/deck-builder-v2-plan.md`, Phase 1, §3.1) — the normalized build
 * target the v2 builder ([com.mmg.manahub.feature.decks.domain.template.BuildDeckFromTemplateUseCase])
 * fills against. Resolved by [DeckTemplateResolver] from either a community aggregate (D2) or a
 * synthetic fallback built from the existing archetype layer.
 */

/** Where a [DeckTemplate] ultimately came from — surfaced honestly to the result UI (§3.3 step 8). */
enum class TemplateSource { COMMUNITY, SYNTHETIC }

/**
 * One card the template wants, with a normalized [weight] the fill stage sorts by (blended from
 * `inclusionPct` x `synergy` for a community-sourced ref, or archetype-role confidence for a
 * synthetic one).
 *
 * @param copies how many copies of this card the template wants (§3.6 Casual playset-first fill:
 *   core engine/win-con = 4x, key support/removal = 3-4x, flex = 2x, top-end finishers = 1-2x).
 *   Commander/singleton templates always use `1` — the field exists on every [TemplateCardRef] (not
 *   only the Casual branch's) because [DeckTemplateResolver] is the single place that builds these
 *   refs regardless of format.
 */
data class TemplateCardRef(
    val name: String,
    val weight: Float,
    val category: SuggestionCategory,
    val copies: Int = 1,
)

/** One functional slot of the template (e.g. Removal, Ramp, Vampires), with its own target count. */
data class TemplateCategory(
    val id: String,
    val label: String,
    val targetCount: Int,
    val cards: List<TemplateCardRef>,
)

/** The archetype/theme this template resolved to — fed back into `Deck.archetypeOverride`/
 * `themesOverride` by the builder (§3.3 "Builder<->Doctor coherence") so [com.mmg.manahub.feature
 * .decks.domain.usecase.EvaluateDeckUseCase] evaluates against the SAME skeleton the builder filled. */
data class DeckTemplateArchetypeInfo(
    /** `null` = no macro pin (Deck Analysis Engine v3 removed `ArchetypeId.GENERIC`; a "no specific
     * archetype" result is now `null` rather than a neutral enum value). */
    val archetype: ArchetypeId?,
    val themes: List<ThemeId>,
)

/** The normalized build target. */
data class DeckTemplate(
    val source: TemplateSource,
    val categories: List<TemplateCategory>,
    /** Target number of lands (community `avgTypeDistribution.land`, or the resolved skeleton's
     * land ideal for a synthetic template). */
    val landTarget: Int,
    /** Normalized fraction per CMC bucket (0..7, 7 = "7+") the deck should curve toward. */
    val manaCurveTarget: Map<Int, Float>,
    val colorIdentity: Set<ManaColor>,
    val archetypeInfo: DeckTemplateArchetypeInfo,
    /** One-sentence strategy line (§3.6 "One-sentence game plan", [com.mmg.manahub.feature.decks
     * .domain.engine.SeedStrategy.description] for a synthetic template). Null for a community
     * template with no matching seed strategy. */
    val gamePlan: String? = null,
)
