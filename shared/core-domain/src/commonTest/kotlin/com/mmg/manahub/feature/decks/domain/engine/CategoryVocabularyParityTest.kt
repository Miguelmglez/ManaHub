package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.AnalysisV3Fixtures
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionRich
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionThin
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CategoryVocabularyParityTest {

    private val pool: List<Card> = buildList {
        AnalysisV3Fixtures.ALL.forEach { fixture -> fixture.mainboard.forEach { add(it.card) } }
        MockCollectionRich.ownedCards.forEach { add(it.card) }
        MockCollectionThin.ownedCards.forEach { add(it.card) }
    }.distinctBy { it.scryfallId }

    // The RoleKeys with a real CardTag equivalent -- the same set Browse offers a button for.
    private val taggedRoleKeys: List<RoleKey> = buildSet {
        add(ArchetypeData.MANA_FIX_KEY)
        ArchetypeData.generic(ArchetypeFormat.COMMANDER).roleTargets.keys.forEach { add(it) }
        ArchetypeData.generic(ArchetypeFormat.SIXTY).roleTargets.keys.forEach { add(it) }
        ArchetypeData.ARCHETYPES.values.forEach { perFormat -> perFormat.values.forEach { def -> def.roleTargets.keys.forEach { add(it) } } }
        ArchetypeData.THEMES.values.forEach { theme -> theme.adds.keys.forEach { add(it) }; theme.relaxes.keys.forEach { add(it) } }
    }.filter { CategoryVocabulary.cardTagKeysFor(it).isNotEmpty() }

    // Confirmed-tag membership is always a subset of classify()>0 -- every RoleSpec checks the
    // tag hit first. The reverse doesn't hold for roles with a structural/oracle fallback (see
    // legacyOracleOnlyRampCard_isInvisibleToVocabularyMembership below for the accepted gap).
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

    @Test
    fun everyTaggedRoleKey_hasANonEmptyMembershipSet() {
        taggedRoleKeys.forEach { roleKey ->
            assertTrue(CategoryVocabulary.cardTagKeysFor(roleKey).isNotEmpty(), "role=$roleKey has an empty membership set")
        }
    }

    // Documents the accepted asymmetry (ADR-009): RoleClassifier's oracle-only fallback counts for
    // the analysis but has no tag for Browse to key off, so a tag-less land-fetch ramp card is
    // attributed by classify() yet invisible to CategoryVocabulary's own membership predicate.
    @Test
    fun legacyOracleOnlyRampCard_isInvisibleToVocabularyMembership() {
        val rampCard = card(
            typeLine = "Sorcery",
            oracleText = "Search your library for a basic land card and put it onto the battlefield.",
        )
        val confidence = ArchetypeRoleClassifier.classify(rampCard)["ramp"] ?: 0f
        assertTrue(confidence > 0f, "expected the oracle fallback to attribute ramp, got $confidence")
        val membership = CategoryVocabulary.cardTagKeysFor("ramp")
        val browseMatches = (rampCard.tags + rampCard.userTags).any { it.key in membership }
        assertFalse(browseMatches, "Browse should NOT see this card -- it carries no ramp tag")
    }
}
