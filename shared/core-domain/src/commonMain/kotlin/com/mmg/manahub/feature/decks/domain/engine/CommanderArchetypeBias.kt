package com.mmg.manahub.feature.decks.domain.engine
// COMMENTS_REVIEWED: 2026-09-14

import com.mmg.manahub.core.model.CardTag

/**
 * Shared commander-derived macro bias, extracted from
 * [com.mmg.manahub.feature.decks.domain.usecase.InferDeckArchetypeUseCase] (Deck Wizard Commander
 * v4, W6 Task 1 / decision E12) so [CommanderPlanResolver]'s Custom build path can reuse the exact
 * same bias the Analysis engine already applies to its post-build macro resolution, instead of a
 * second hand-written copy.
 *
 * Two tiers, tag-based strictly preferred over color-based: a commander whose own resolved tags
 * map onto an [ArchetypeId] via [DeckIdentitySeedTags.archetypeForTag] wins outright (bonus =
 * [MACRO_AMBIGUITY_MARGIN] — capped at exactly the model's own "decisive margin" definition, so it
 * biases without manufacturing a win a deck's own composition would not otherwise support); only
 * when no tag signal exists does color identity contribute a much smaller nudge via
 * [ColorStrategyAffinity.forColors].
 */
data class MacroPrior(val archetype: ArchetypeId, val bonus: Float)

object CommanderArchetypeBias {

    /** Spec §2.1 (Deck Analysis Engine v3): "confidence < MACRO_AMBIGUITY_MARGIN (start at 0.08)
     * surfaces as Custom or a hybrid label." Reused verbatim as the tag-based commander-prior bonus
     * — capping the prior at exactly this value is what makes it bias rather than override. */
    const val MACRO_AMBIGUITY_MARGIN = 0.08f

    /** Color-identity commander-prior bonus — deliberately much smaller than
     * [MACRO_AMBIGUITY_MARGIN] (roughly a third of it): color identity alone is the weakest signal,
     * a tiebreak, never a driver. */
    const val MACRO_PRIOR_COLOR_BONUS = 0.03f

    /**
     * Resolves [commanderTags]/[commanderColorIdentity] into a bounded bias over exactly one
     * macro, or `null` when the commander carries no usable signal at all.
     */
    fun commanderMacroPrior(commanderTags: List<CardTag>, commanderColorIdentity: Set<ManaColor>): MacroPrior? {
        val tagArchetype = commanderTagArchetype(commanderTags)
        if (tagArchetype != null) return MacroPrior(tagArchetype, MACRO_AMBIGUITY_MARGIN)
        if (commanderColorIdentity.isEmpty()) return null
        val colorArchetype = ColorStrategyAffinity.forColors(commanderColorIdentity).firstOrNull()?.archetype ?: return null
        return MacroPrior(colorArchetype, MACRO_PRIOR_COLOR_BONUS)
    }

    /**
     * Tag-tier ONLY (no color-identity fallback) — for a caller picking a single DISCRETE skeleton
     * target (e.g. [CommanderPlanResolver]'s Custom build hint, F18/E12) rather than nudging an
     * already-continuous score. Color identity alone is deliberately excluded here: it is "a
     * tiebreak, never a driver" (this object's own KDoc) and is far too weak a signal to justify
     * switching a build's ENTIRE target skeleton away from the generic baseline — verified by hand
     * (W6 Task 1): letting color identity drive the build target regressed a real corpus fixture
     * (Meren/Golgari) from a correct MIDRANGE read to an ambiguous one, because MIDRANGE sits at the
     * centre of the prototype space and building explicitly toward its OWN bands pulls a deck's
     * continuous axis position toward that same centre — i.e. exactly the position least likely to
     * separate from its neighbours by the ambiguity margin.
     *
     * W6b: resolves by majority vote over ALL of [commanderTags], not the first match — a
     * commander whose tags map to more than one archetype must not depend on tag list order
     * (`firstNotNullOfOrNull` did). A tie is broken by [ArchetypeId.name] alphabetically, which is
     * likewise independent of input order.
     */
    fun commanderTagArchetype(commanderTags: List<CardTag>): ArchetypeId? {
        val votes = commanderTags.mapNotNull { DeckIdentitySeedTags.archetypeForTag(it) }
        if (votes.isEmpty()) return null
        val counts = votes.groupingBy { it }.eachCount()
        val topCount = counts.values.max()
        return counts.filterValues { it == topCount }.keys.minByOrNull { it.name }
    }
}
