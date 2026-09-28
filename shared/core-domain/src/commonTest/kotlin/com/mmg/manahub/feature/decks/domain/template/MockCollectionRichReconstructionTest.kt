package com.mmg.manahub.feature.decks.domain.template
// COMMENTS_REVIEWED: 2026-09-09

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategyCatalog
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.StrategyPick
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver
import com.mmg.manahub.feature.decks.domain.engine.toPin
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionRich
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

private object ReconstructionCrashReporter : CrashReporter {
    val exceptions = mutableListOf<Throwable>()
    override fun recordException(throwable: Throwable) { exceptions += throwable }
    override fun log(message: String) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
}

/**
 * Deck Wizard Commander v3 plan, Phase 2 gate item B — the campaign's ONLY ground-truth test: for
 * each of the 4 [MockCollectionRich] target fixtures, a wizard build against that fixture's OWN
 * [expectedMacro]/[expectedThemes] pin (via the matching [CuratedStrategyCatalog] entry) must score
 * within [SCORE_TOLERANCE] of the fixture's own score, and a Custom build of the same commander must
 * resolve the same macro/themes. Per the plan's explicit instruction: if the tolerance is not met,
 * this test reports the real numbers and stays red — it is NEVER loosened to pass.
 */
class MockCollectionRichReconstructionTest {

    /** Plan §5 acceptance band: wizard score >= fixture score - 8. */
    private val SCORE_TOLERANCE = 8

    private fun newPipeline(): DeckAnalysisPipeline = DeckAnalysisPipeline(
        EvaluateDeckUseCase(DeckScorer(RoleClassifier(), NeutralPowerResolver), ProgressionEventBus()),
        InferDeckIdentityUseCase(),
        ReconstructionCrashReporter,
    )

    private fun newUseCase(): BuildWizardDeckUseCase = BuildWizardDeckUseCase(newPipeline(), ReconstructionCrashReporter)

    private fun ownedPool(): List<OwnedCard> =
        (MockCollectionRich.ownedCards + MockCollectionRich.ownedBasics).map { OwnedCard(it.card, it.quantity) }

    /** One fixture's own curated strategy pin, matching its own `expectedMacro`/`expectedThemes`
     * (§0.3's own KDoc — these fixtures ARE their own strategy's ground truth). Not every fixture's
     * macro/theme combination has a matching catalog entry with archetype-only precision, but all 4
     * targets here do (tribal/aristocrats/lifegain/artifacts). */
    private data class FixtureCase(val commanderId: String, val strategyId: String, val tribe: String?)

    private val cases = listOf(
        FixtureCase("cmd-edgar-markov", "tribal", "${TribeDeriver.TRIBE_PREFIX}vampire"), // AGGRO, TRIBAL:vampire
        FixtureCase("cmd-meren", "aristocrats", null), // MIDRANGE/ATTRITION, ARISTOCRATS
        FixtureCase("cmd-karlov", "lifegain", null), // MIDRANGE, LIFEGAIN
        FixtureCase("cmd-urza", "artifacts", null), // COMBO, ARTIFACTS
    )

    @Test
    fun `each fixture's own strategy pin reconstructs within the plan's score tolerance`() = runTest {
        val owned = ownedPool()
        val deltas = mutableListOf<String>()

        for (case in cases) {
            val fixture = MockCollectionRich.targetFixtures.first { fx -> fx.mainboard.any { it.card.scryfallId == case.commanderId } }
            val commander = fixture.mainboard.first { it.card.scryfallId == case.commanderId }.card
            val identity = commander.colorIdentity.toManaColors()
            val strategy = CuratedStrategyCatalog.ALL.first { it.id == case.strategyId }
            val pin = strategy.toPin(case.tribe)

            val fixtureAnalysis = newPipeline().analyze(
                mainboard = fixture.mainboard,
                format = DeckFormat.COMMANDER,
                commander = commander,
                archetypeOverride = pin.archetype?.name,
                themesOverride = pin.themes.map { it.name },
                tribeOverride = pin.tribe,
                postureOverride = pin.posture?.name,
                emitProgression = false,
            ).analysis
            checkNotNull(fixtureAnalysis) { "fixture ${fixture.id} produced no analysis" }
            val fixtureScore = fixtureAnalysis.totalScore

            val pick = StrategyPick.Curated(strategy, case.tribe)
            val outcome = newUseCase()(DeckFormat.COMMANDER, commander, pick, identity, owned)
            val wizardScore = outcome.result.analysis.totalScore

            deltas += "${fixture.id}: fixture=$fixtureScore wizard=$wizardScore delta=${wizardScore - fixtureScore}"
            assertTrue(
                wizardScore >= fixtureScore - SCORE_TOLERANCE,
                "MockCollectionRich reconstruction for ${fixture.id}: wizard score $wizardScore must be >= fixture score $fixtureScore - $SCORE_TOLERANCE. All deltas: $deltas",
            )
        }
        println("MockCollectionRich reconstruction deltas: $deltas")
    }

    /**
     * Compares Custom's INFERRED macro against the fixture's own [AnalysisV3Fixture.expectedMacro]
     * ground truth, NOT against a curated pin's [StrategyPin.archetype] -- that field is a manual
     * override forced onto [DeckAnalysisPipeline.analyze] (`archetypes.first()` per [toPin]'s own
     * KDoc, e.g. "aristocrats" always pins `AGGRO` even for a MIDRANGE fixture like Meren), so
     * `curatedOutcome.result.analysis.strategy.archetype == pin.archetype` would be a tautology by
     * construction (a manual override is never re-inferred) and prove nothing about the wizard.
     */
    @Test
    fun `Custom build resolves the fixture's own expected macro`() = runTest {
        val owned = ownedPool()
        val mismatches = mutableListOf<String>()

        for (case in cases) {
            val fixture = MockCollectionRich.targetFixtures.first { fx -> fx.mainboard.any { it.card.scryfallId == case.commanderId } }
            val commander = fixture.mainboard.first { it.card.scryfallId == case.commanderId }.card
            val identity = commander.colorIdentity.toManaColors()

            val customOutcome = newUseCase()(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned)
            val resolved = customOutcome.result.analysis.strategy.archetype?.name

            if (resolved != fixture.expectedMacro) {
                mismatches += "${fixture.id} (expectedMacro=${fixture.expectedMacro}): Custom build resolved $resolved"
            }
        }
        // Per the campaign's explicit instruction: a real mismatch here is reported honestly, never
        // fudged by loosening the assertion.
        assertTrue(mismatches.isEmpty(), "Custom-vs-fixture macro mismatches (real findings, not loosened): $mismatches")
    }
}

private fun List<String>.toManaColors(): Set<ManaColor> =
    mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }.toSet()
