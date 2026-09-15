package com.mmg.manahub.feature.decks.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals

// ═══════════════════════════════════════════════════════════════════════════════
//  CategoryVocabularyTest — Deck Wizard Commander v5, X0.
//  Locks in the widened/narrowed membership decisions cited in CategoryVocabulary's own KDoc.
// ═══════════════════════════════════════════════════════════════════════════════

class CategoryVocabularyTest {

    @Test
    fun countersPayoff_widenedToIncludePlusCounters() {
        assertEquals(setOf("counters_payoff", "plus_counters"), CategoryVocabulary.cardTagKeysFor("counters_payoff"))
    }

    @Test
    fun landfallPayoff_widenedToIncludeLandfallKeyword() {
        assertEquals(setOf("landfall_payoff", "landfall"), CategoryVocabulary.cardTagKeysFor("landfall_payoff"))
    }

    /** Different detection rules (see CategoryVocabulary KDoc) -- must NOT be widened. */
    @Test
    fun tribePayoff_staysNarrow_excludesGenericTribalTag() {
        assertEquals(setOf("tribe_payoff"), CategoryVocabulary.cardTagKeysFor("tribe_payoff"))
    }

    @Test
    fun deathPayoff_staysNarrow_excludesGenericDeathTriggersTag() {
        assertEquals(setOf("death_payoff"), CategoryVocabulary.cardTagKeysFor("death_payoff"))
    }

    @Test
    fun reanimation_staysNarrow_excludesReanimatorStrategyTag() {
        assertEquals(setOf("reanimation"), CategoryVocabulary.cardTagKeysFor("reanimation"))
    }

    @Test
    fun millEngine_staysNarrow_excludesGenericMillTag() {
        assertEquals(setOf("mill_engine"), CategoryVocabulary.cardTagKeysFor("mill_engine"))
    }

    @Test
    fun everyOrdinaryDirectTagRole_defaultsToItsOwnBareKey() {
        listOf(
            "sac_outlet", "graveyard_enabler", "stax_piece", "lifegain_payoff", "spell_payoff",
            "token_generator", "wheel", "etb_payoff", "planeswalker", "vehicle", "anthem",
        ).forEach { key -> assertEquals(setOf(key), CategoryVocabulary.cardTagKeysFor(key), "key=$key") }
    }

    @Test
    fun noTagEquivalentRoles_returnEmpty() {
        listOf("removal_spot", "removal_mass", "finisher", "equipment_or_aura", "tribe_members")
            .forEach { key -> assertEquals(emptySet(), CategoryVocabulary.cardTagKeysFor(key), "key=$key") }
    }
}
