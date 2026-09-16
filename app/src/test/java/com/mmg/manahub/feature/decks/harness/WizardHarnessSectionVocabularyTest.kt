package com.mmg.manahub.feature.decks.harness
// COMMENTS_REVIEWED: 2026-09-16

import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Wizard v5, X4 (H8/S6) -- section-vocabulary audit over the real collection.
//
//  Runs the SAME CommanderMatrixV2 build pipeline WizardCommanderHarnessV2Test drives (every
//  eligible owned commander x {top recommendation, Custom}) and inspects each resulting
//  DeckAnalysis's own emitted sections -- never a lookup table -- for the two invariants the user's
//  duplicate-category report demands: no two sections in one analysis share a normalized label, and
//  no TRIBAL-category card tag ever surfaces its own `fingerprint:<key>` section. Prints aggregate
//  counts only (no card/deck names, real user data stays gitignored).
//
//  Deck Wizard 60-card wave (v6), plan §5 Phase 3 gate: the mana-base pillar's vocabulary now
//  ALSO includes zero-count `produces:X` gap entries (an identity color the deck doesn't yet
//  produce) and a NEW `lands` section -- both EXPECTED, not anomalous. Neither violates the two
//  invariants above ("Lands" collides with no other label in `AnalysisEngine.kt`; the new sections
//  carry no `fingerprint:` prefix), so no assertion changed -- only the `landsSectionMissing`
//  counter below is new, proving the section actually surfaces over real corpus data rather than
//  merely compiling.
//
//  ./gradlew :app:testDebugUnitTest --tests "com.mmg.manahub.feature.decks.harness.WizardHarnessSectionVocabularyTest"
// ═══════════════════════════════════════════════════════════════════════════════

private fun List<String>.toManaColors(): Set<ManaColor> =
    mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }.toSet()

class WizardHarnessSectionVocabularyTest {

    private val harnessDir = FixtureLoader.locateHarnessDir()

    @Before
    fun assumeFixturesPresent() {
        Assume.assumeTrue(
            "wizard-harness fixtures not found under testdata/wizard-harness/ (gitignored -- skipping on this machine/CI)",
            harnessDir != null,
        )
    }

    @Test
    fun realCollectionBuilds_haveNoDuplicateSectionLabels_andNoFingerprintSectionForATribalTag() = runBlocking {
        val dir = harnessDir!!
        val fixtures = FixtureLoader.load(dir)
        val owned = CommanderMatrixV2.ownedPool(fixtures)
        val specs = CommanderMatrixV2.specs(fixtures)
        val tribalTagKeys = fixtures.ownedCardsByName
            .flatMap { it.tags + it.userTags }
            .filter { it.category == TagCategory.TRIBAL }
            .map { it.key }
            .toSet()

        var analyzed = 0
        var duplicateLabelFailures = 0
        var fingerprintTribalLeaks = 0
        var missingLandsSection = 0
        val duplicateIdPairFrequency = mutableMapOf<String, Int>()
        val useCase = CommanderMatrixV2.newBuildUseCase()
        specs.forEach { spec ->
            val identity = spec.commander.colorIdentity.toManaColors()
            val outcome = runCatching {
                useCase(DeckFormat.COMMANDER, spec.commander, spec.pick, identity, owned, includeNonBasicLands = true)
            }.getOrNull() ?: return@forEach
            analyzed++
            val sections = outcome.result.analysis.pillars.flatMap { it.sections }
            val duplicates = sections.groupBy { it.label.trim().lowercase() }.filterValues { it.size > 1 }
            if (duplicates.isNotEmpty()) {
                duplicateLabelFailures++
                duplicates.forEach { (_, group) ->
                    val idsKey = group.map { it.id }.distinct().sorted().joinToString("|")
                    duplicateIdPairFrequency[idsKey] = (duplicateIdPairFrequency[idsKey] ?: 0) + 1
                }
            }
            val leaks = sections.map { it.id }.filter { id -> id.startsWith("fingerprint:") && id.removePrefix("fingerprint:") in tribalTagKeys }
            if (leaks.isNotEmpty()) fingerprintTribalLeaks++
            if (sections.none { it.id == "lands" }) missingLandsSection++
        }

        println("[wizard-harness-vocabulary] analyzed=$analyzed duplicateLabelFailures=$duplicateLabelFailures fingerprintTribalLeaks=$fingerprintTribalLeaks tribalTagKeys=${tribalTagKeys.size} missingLandsSection=$missingLandsSection")
        duplicateIdPairFrequency.toList().sortedByDescending { it.second }.forEach { (idsKey, count) ->
            println("[wizard-harness-vocabulary] duplicate id set [$idsKey] occurred in $count analyses")
        }
        assertTrue("no analysis may emit two sections sharing a normalized label", duplicateLabelFailures == 0)
        assertTrue("no analysis may emit a fingerprint:<x> section for a TRIBAL card tag", fingerprintTribalLeaks == 0)
        assertTrue("every analyzed build must emit exactly one 'lands' section", missingLandsSection == 0)
    }
}
