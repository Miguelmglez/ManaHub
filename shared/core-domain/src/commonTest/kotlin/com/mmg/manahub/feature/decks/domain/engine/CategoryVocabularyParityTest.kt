package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.AnalysisV3Fixtures
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionRich
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionThin
import kotlin.test.Test
import kotlin.test.assertTrue

// ═══════════════════════════════════════════════════════════════════════════════
//  CategoryVocabularyParityTest — Deck Wizard Commander v5, X0 (S2 regression guard).
//
//  The v4 lesson (plan §0): a Browse-coverage test that only enumerates a lookup table's OWN keys
//  proves the table is self-consistent, never that analysis and Browse actually agree on real
//  cards. This suite runs BOTH the classifier and the Collection-tag membership predicate over
//  real corpus/mock-collection card pools and compares the resulting card SETS, not the table.
//
//  Invariant tested: "a card whose CONFIRMED tags satisfy CategoryVocabulary's membership set for
//  a role is ALWAYS classified with that role" (confirmed-tag membership subset of classify()>0).
//  This holds for every role because every RoleSpec matcher checks the tag hit FIRST, before any
//  structural/oracle fallback (ArchetypeRoleClassifier.tagMatcher short-circuits at confidence 1f).
//  The reverse direction (classify()>0 implies a confirmed tag) does NOT hold in general -- several
//  roles also fire from a structural/oracle-text fallback with no tag at all (documented, expected,
//  not a defect) -- so this suite does not assert full bidirectional equality corpus-wide; the two
//  cited H2 keys (counters_payoff/landfall_payoff) get an exact-equality check in
//  ArchetypeRoleClassifierCategoryVocabularyTest instead, over synthetic single-purpose cards.
// ═══════════════════════════════════════════════════════════════════════════════

class CategoryVocabularyParityTest {

    private val pool: List<Card> = buildList {
        AnalysisV3Fixtures.ALL.forEach { fixture -> fixture.mainboard.forEach { add(it.card) } }
        MockCollectionRich.ownedCards.forEach { add(it.card) }
        MockCollectionThin.ownedCards.forEach { add(it.card) }
    }.distinctBy { it.scryfallId }

    /** Every RoleKey [ArchetypeData] bands reference that has a real CardTag equivalent (per
     * [CategoryVocabulary]) -- the same set Browse actually offers a "Browse for X" button for. */
    private val taggedRoleKeys: List<RoleKey> = buildSet {
        add(ArchetypeData.MANA_FIX_KEY)
        ArchetypeData.generic(ArchetypeFormat.COMMANDER).roleTargets.keys.forEach { add(it) }
        ArchetypeData.generic(ArchetypeFormat.SIXTY).roleTargets.keys.forEach { add(it) }
        ArchetypeData.ARCHETYPES.values.forEach { perFormat -> perFormat.values.forEach { def -> def.roleTargets.keys.forEach { add(it) } } }
        ArchetypeData.THEMES.values.forEach { theme -> theme.adds.keys.forEach { add(it) }; theme.relaxes.keys.forEach { add(it) } }
    }.filter { CategoryVocabulary.cardTagKeysFor(it).isNotEmpty() }

    @Test
    fun confirmedTagMembership_isAlwaysASubsetOfClassifiedRole() {
        val violations = mutableListOf<String>()
        taggedRoleKeys.forEach { roleKey ->
            val membership = CategoryVocabulary.cardTagKeysFor(roleKey)
            pool.forEach { card ->
                val browseMatches = (card.tags + card.userTags).any { it.key in membership }
                if (browseMatches) {
                    val confidence = ArchetypeRoleClassifier.classify(card)[roleKey] ?: 0f
                    if (confidence <= 0f) violations += "role=$roleKey card=${card.scryfallId} tags=${card.tags.map { it.key }}"
                }
            }
        }
        assertTrue(violations.isEmpty(), "Browse would find cards the analysis never attributes:\n${violations.joinToString("\n")}")
    }

    /** Every category this table answers for must be non-degenerate (a real, non-empty set) --
     * catches a future typo that accidentally maps a role to an unrelated/empty key set. */
    @Test
    fun everyTaggedRoleKey_hasANonEmptyMembershipSet() {
        taggedRoleKeys.forEach { roleKey ->
            assertTrue(CategoryVocabulary.cardTagKeysFor(roleKey).isNotEmpty(), "role=$roleKey has an empty membership set")
        }
    }
}
