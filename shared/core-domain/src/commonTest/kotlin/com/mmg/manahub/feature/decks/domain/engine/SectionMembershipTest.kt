package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.feature.decks.domain.engine.analysisv3.AnalysisV3Fixtures
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckArchetypeUseCase
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Deck Wizard UX polish plan, Run 1 §1.2 — for every AnalysisV3 corpus fixture and every section
 * [AnalysisEngine] emits, [SectionMembership.predicate] must select EXACTLY the same mainboard cards
 * the engine itself attributed to that section's `contributions` — proving `CardSectionRow` (which
 * renders `contributions`) and the Collection tab's Browse filter (which uses this predicate) can
 * never disagree.
 */
class SectionMembershipTest {

    private val scorer = DeckScorer(RoleClassifier())
    private val inferDeckArchetypeUseCase = InferDeckArchetypeUseCase()

    @Test
    fun everySectionsPredicateMatchesItsOwnContributionsAcrossTheV3Corpus() {
        val mismatches = mutableListOf<String>()
        var predicatedSectionCount = 0
        AnalysisV3Fixtures.ALL.forEach { fixture ->
            val archetypeFormat = requireNotNull(fixture.archetypeFormat) {
                "Fixture ${fixture.id} (${fixture.name}) resolved to a null ArchetypeFormat -- DRAFT is never used by this corpus."
            }
            val profile = scorer.profile(fixture.mainboard, fixture.format, fixture.colorIdentity, emptyList())
            val inferred = inferDeckArchetypeUseCase(fixture.mainboard, archetypeFormat, fixture.commanderTags, fixture.colorIdentity)
            val analysis = AnalysisEngine.evaluate(
                mainboard = fixture.mainboard, format = fixture.format, colorIdentity = fixture.colorIdentity,
                profile = profile, archetype = inferred.macro, posture = inferred.posture, themes = inferred.themes,
                isManualOverride = false, confidence = inferred.confidence,
            )
            // Mirrors DeckStudioViewModel.sectionQueryContext() exactly.
            val dominantTribe = ArchetypeRoleClassifier.dominantTribeKey(fixture.mainboard)?.removePrefix(TribeDeriver.TRIBE_PREFIX)
            val context = SectionQueryContext(colorIdentity = fixture.colorIdentity, format = fixture.format, dominantTribe = dominantTribe)

            analysis.pillars.forEach { pillar ->
                pillar.sections.forEach { section ->
                    val predicate = SectionMembership.predicate(section.id, context) ?: return@forEach
                    predicatedSectionCount++
                    val expected = section.contributions.map { it.scryfallId }.toSet()
                    val actual = fixture.mainboard.filter { predicate(it.card) }.map { it.card.scryfallId }.toSet()
                    if (expected != actual) {
                        mismatches += "fixture=${fixture.id}(${fixture.name}) pillar=${pillar.id} section=${section.id} " +
                            "missingFromPredicate=${expected - actual} extraInPredicate=${actual - expected}"
                    }
                }
            }
        }
        // Sanity check: this corpus must actually exercise a meaningful number of non-null
        // predicates, or the assertion below would pass vacuously.
        assertTrue(predicatedSectionCount > 50, "expected the v3 corpus to exercise many non-null section predicates, got $predicatedSectionCount")
        assertTrue(mismatches.isEmpty(), "SectionMembership disagreed with the engine's own attribution:\n${mismatches.joinToString("\n")}")
    }
}
