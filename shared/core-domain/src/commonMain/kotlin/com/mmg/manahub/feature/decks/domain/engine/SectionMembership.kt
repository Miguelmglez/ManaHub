package com.mmg.manahub.feature.decks.domain.engine
// COMMENTS_REVIEWED: 2026-09-20

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.Card

/**
 * ONE per-card membership predicate per [CardSection.id] — Deck Wizard UX polish plan, Run 1 §1.2.
 * Both [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel] (the Analysis tab's "Browse
 * for X" Collection-tab filter) and the wizard's PLAN_SECTIONS Collection browse call this, so a
 * card the engine's own attribution counts for a section is EXACTLY the set Browse's Collection tab
 * surfaces — replacing the old `StructuredCardSearch.matchesForCategoryBrowse` OR-with-tags
 * approximation (ADR-009's known gap).
 *
 * Deliberately lives in `engine/` (never `core/domain/search`, which must stay engine-import-free):
 * every non-null branch below reads the SAME classifier [AnalysisEngine] itself attributes that
 * section's `contributions` from — [ArchetypeRoleClassifier], [SynergyGraph.cardAxisProfile],
 * [TribeDeriver], or a direct [Card] field — never a re-approximation via Scryfall/tag-search syntax.
 *
 * Two branches below deliberately DIVERGE from an earlier draft of this spec after checking the
 * actual [AnalysisEngine] source (not just its plan text) — see [predicate]'s own `when` comments:
 * `produces:<X>` reads [Card.producedMana] directly (matching `evaluateManaBase`'s own
 * `producedMana.contains(symbol)` check, D14) rather than [ManaBaseAnalyzer.producedColors]'s oracle-
 * text heuristic; `mana_rock`/`mana_dork` read the card's own persisted [Card.tags]/[Card.userTags]
 * directly (matching `evaluateManaBase`'s own tag check) — [ArchetypeRoleClassifier] has no
 * `mana_rock`/`mana_dork` [RoleKey] at all, so classifying by role would silently match nothing.
 */
object SectionMembership {

    /**
     * @return a membership test for [sectionId], or `null` when the section is a relative/derived
     *   per-deck classification rather than a fact about one candidate card in isolation (`mv:*`
     *   mana-value buckets, `legal`/`illegal`, `interaction`/`standalone`/`offplan` — SYNERGY's
     *   residual buckets read the WHOLE deck's edge graph, not a single card). Also `null` for
     *   `role:tribe_members` on a deck with no dominant tribe, and for `engine:<axis>:*` on a format
     *   with no [ArchetypeFormat] (Draft) — both match the section's own "no sensible Browse button"
     *   precedent in [SectionSearchQuery.fragmentFor].
     */
    @Suppress("UNUSED_PARAMETER")
    fun predicate(
        sectionId: String,
        context: SectionQueryContext,
        // Reserved for a future card-level mana-base predicate; unused today -- see class KDoc for
        // why `produces:<X>` reads Card.producedMana directly instead.
        manaBaseAnalyzer: ManaBaseAnalyzer = ManaBaseAnalyzer(),
    ): ((Card) -> Boolean)? = when {
        sectionId == "lands" -> { card -> BasicLandCalculator.isLand(card) }
        // AnalysisEngine.evaluateManaBase reads the persisted tag directly -- ArchetypeRoleClassifier
        // has no "mana_rock"/"mana_dork" RoleKey to classify against.
        sectionId == "mana_rock" -> { card -> card.hasTagKey("mana_rock") }
        sectionId == "mana_dork" -> { card -> card.hasTagKey("mana_dork") }
        sectionId.startsWith("produces:") -> producesPredicate(sectionId.removePrefix("produces:"))
        sectionId.startsWith("role:") -> rolePredicate(sectionId.removePrefix("role:"), context)
        sectionId.startsWith("engine:") -> enginePredicate(sectionId, context)
        // Mirrors evaluateSynergy's alignedEntries: TribeDeriver.tribeKeys (own subtypes UNION
        // oracle-text payoff mentions), not the narrower subtypeKeys alone -- a payoff card (e.g.
        // "create a 1/1 Elf token") that never LISTS an Elf subtype on its own type line still
        // legitimately counts toward the deck's tribe:elf section.
        sectionId.startsWith(TribeDeriver.TRIBE_PREFIX) -> { card -> sectionId in TribeDeriver.tribeKeys(card) }
        else -> null // mv:*, legal, illegal, interaction, standalone, offplan -- no card-level fact.
    }

    private fun Card.hasTagKey(key: String): Boolean = (tags + userTags).any { it.key == key }

    private fun producesPredicate(symbol: String): (Card) -> Boolean =
        { card -> BasicLandCalculator.isLand(card) && card.producedMana.contains(symbol) }

    /** Mirrors [ArchetypeRoleClassifier.deckRoleAttribution]'s own per-card classification pass,
     * including its `tribe_members` special case (a card qualifies iff its OWN subtypes include the
     * deck's dominant tribe — the same [TribeDeriver.subtypeKeys] source `deckRoleAttribution` reads). */
    private fun rolePredicate(key: RoleKey, context: SectionQueryContext): ((Card) -> Boolean)? {
        if (key == "tribe_members") {
            val dominantTribeKey = context.dominantTribe?.let { TribeDeriver.TRIBE_PREFIX + it } ?: return null
            return { card -> dominantTribeKey in TribeDeriver.subtypeKeys(card) }
        }
        return { card -> (ArchetypeRoleClassifier.classify(card)[key] ?: 0f) > 0f }
    }

    /** Mirrors [AnalysisEngine.evaluateSynergy]'s `axisBreakdown[axis].producers`/`.payoffs`
     * construction — both are `profiles.filter { (it.produces/consumes[axis] ?: 0f) > 0f }` over the
     * SAME per-card [SynergyGraph.cardAxisProfile] this reuses directly (its own KDoc confirms it is
     * "computed the SAME way build() computes it per-card internally"). */
    private fun enginePredicate(sectionId: String, context: SectionQueryContext): ((Card) -> Boolean)? {
        val (axis, side) = parseEngineSectionId(sectionId) ?: return null
        val archetypeFormat = ArchetypeFormat.of(context.format) ?: return null
        val isProducerSide = side == "producers"
        val dominantTribeAxis = context.dominantTribe?.let { "TRIBE:$it" }
        val dominantTribeKey = context.dominantTribe?.let { TribeDeriver.TRIBE_PREFIX + it }
        return { card ->
            val profile = SynergyGraph.cardAxisProfile(card, archetypeFormat, dominantTribeAxis, dominantTribeKey)
            val axisMap = if (isProducerSide) profile.produces else profile.consumes
            (axisMap[axis] ?: 0f) > 0f
        }
    }

    private fun parseEngineSectionId(sectionId: String): Pair<AxisKey, String>? {
        val body = sectionId.removePrefix("engine:")
        val separatorIndex = body.lastIndexOf(':')
        if (separatorIndex <= 0) return null
        return body.substring(0, separatorIndex) to body.substring(separatorIndex + 1)
    }
}
