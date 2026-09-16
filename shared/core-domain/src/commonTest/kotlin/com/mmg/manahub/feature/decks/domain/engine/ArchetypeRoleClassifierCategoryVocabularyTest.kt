package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import kotlin.test.Test
import kotlin.test.assertTrue

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

    @Test
    fun cardWithUnrelatedTag_doesNotCountAsCountersPayoff() {
        val cardTag = card(tags = listOf(CardTag("ramp", TagCategory.ROLE)))
        val roles = ArchetypeRoleClassifier.classify(cardTag)
        assertTrue((roles["counters_payoff"] ?: 0f) == 0f, "expected counters_payoff == 0, got $roles")
    }

    @Test
    fun cardConfirmedOnlyWithTribalTag_doesNotCountAsTribePayoff() {
        val cardTag = card(tags = listOf(CardTag("tribal", TagCategory.TRIBAL)))
        val roles = ArchetypeRoleClassifier.classify(cardTag)
        assertTrue((roles["tribe_payoff"] ?: 0f) == 0f, "expected tribe_payoff == 0, got $roles")
    }
}
