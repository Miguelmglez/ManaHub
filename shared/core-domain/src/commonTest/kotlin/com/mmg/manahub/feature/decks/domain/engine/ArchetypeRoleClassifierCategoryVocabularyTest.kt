package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import kotlin.test.Test
import kotlin.test.assertTrue

// ═══════════════════════════════════════════════════════════════════════════════
//  ArchetypeRoleClassifierCategoryVocabularyTest — Deck Wizard Commander v5, X0 (H2 regression).
//
//  Before this run: a card confirmed ONLY with `plus_counters` (never `counters_payoff`) was
//  invisible to the analysis classifier (tagMatcher checked `it.key == "counters_payoff"` exactly)
//  but WAS returned by Browse's Collection filter (via `CardFunctionOption.collectionTagKeys =
//  {counters_payoff, plus_counters}`) -- the exact "shows no cards in the section, but Browse finds
//  them" defect the user reported. Same shape for the `landfall` keyword tag vs. `landfall_payoff`.
// ═══════════════════════════════════════════════════════════════════════════════

class ArchetypeRoleClassifierCategoryVocabularyTest {

    @Test
    fun cardConfirmedOnlyWithPlusCounters_countsAsCountersPayoff() {
        val cardTag = card(tags = listOf(CardTag("plus_counters", TagCategory.STRATEGY)))
        val roles = ArchetypeRoleClassifier.classify(cardTag)
        assertTrue((roles["counters_payoff"] ?: 0f) > 0f, "expected counters_payoff > 0, got $roles")
    }

    @Test
    fun cardConfirmedOnlyWithLandfallKeyword_countsAsLandfallPayoff() {
        val cardTag = card(tags = listOf(CardTag("landfall", TagCategory.KEYWORD)))
        val roles = ArchetypeRoleClassifier.classify(cardTag)
        assertTrue((roles["landfall_payoff"] ?: 0f) > 0f, "expected landfall_payoff > 0, got $roles")
    }

    /** Regression guard: the widening must not become a universal union -- a card carrying an
     * UNRELATED tag never counts. */
    @Test
    fun cardWithUnrelatedTag_doesNotCountAsCountersPayoff() {
        val cardTag = card(tags = listOf(CardTag("ramp", TagCategory.ROLE)))
        val roles = ArchetypeRoleClassifier.classify(cardTag)
        assertTrue((roles["counters_payoff"] ?: 0f) == 0f, "expected counters_payoff == 0, got $roles")
    }

    /** `tribal` (a plain, rule-less STRATEGY tag) is NOT a synonym of `tribe_payoff` -- a card
     * carrying only `tribal` must not be attributed to the Tribe Payoff role. */
    @Test
    fun cardConfirmedOnlyWithTribalTag_doesNotCountAsTribePayoff() {
        val cardTag = card(tags = listOf(CardTag("tribal", TagCategory.TRIBAL)))
        val roles = ArchetypeRoleClassifier.classify(cardTag)
        assertTrue((roles["tribe_payoff"] ?: 0f) == 0f, "expected tribe_payoff == 0, got $roles")
    }
}
