package com.mmg.manahub.feature.decks.domain.template
// COMMENTS_REVIEWED: 2026-09-09

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategyCatalog
import com.mmg.manahub.feature.decks.domain.engine.availableIn
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.StrategyPick
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionRich
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionThin
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object TestCrashReporter : CrashReporter {
    val exceptions = mutableListOf<Throwable>()
    override fun recordException(throwable: Throwable) { exceptions += throwable }
    override fun log(message: String) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
}

/**
 * Deck Wizard Commander v3 plan, Phase 2 gate: [BuildCommanderDeckUseCase] over the committed
 * [MockCollectionRich]/[MockCollectionThin] fixtures (Phase 0.3) — no gitignored real-collection
 * data needed, so this suite runs everywhere `commonTest` runs.
 */
class BuildCommanderDeckUseCaseTest {

    private fun newUseCase(): BuildCommanderDeckUseCase {
        val pipeline = DeckAnalysisPipeline(
            EvaluateDeckUseCase(DeckScorer(RoleClassifier(), NeutralPowerResolver), ProgressionEventBus()),
            InferDeckIdentityUseCase(),
            TestCrashReporter,
        )
        return BuildCommanderDeckUseCase(pipeline, TestCrashReporter)
    }

    private fun ownedFrom(vararg pools: List<com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionCard>): List<OwnedCard> =
        pools.flatMap { pool -> pool.map { OwnedCard(it.card, it.quantity) } }

    @Test
    fun `determinism -- two builds of the same spec are byte-identical`() = runTest {
        val useCase = newUseCase()
        val commander = MockCollectionThin.commander
        val owned = ownedFrom(MockCollectionThin.ownedCards, MockCollectionThin.ownedBasics)

        val first = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, commander.colorIdentity.toManaColors(), owned)
        val second = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, commander.colorIdentity.toManaColors(), owned)

        val firstIds = first.result.entries.map { it.card.scryfallId to it.quantity }.sortedBy { it.first }
        val secondIds = second.result.entries.map { it.card.scryfallId to it.quantity }.sortedBy { it.first }
        assertEquals(firstIds, secondIds, "two builds of the identical spec must place identical cards")
        assertEquals(first.result.analysis.totalScore, second.result.analysis.totalScore)
    }

    @Test
    fun `manual adds -- owned, unowned, off-plan, and a land are all preserved`() = runTest {
        val useCase = newUseCase()
        val commander = MockCollectionThin.commander
        val identity = commander.colorIdentity.toManaColors()
        val owned = ownedFrom(MockCollectionThin.ownedCards, MockCollectionThin.ownedBasics)

        val unownedOffPlan = card(id = "manual-unowned-1", name = "Manual Unowned Off Plan", typeLine = "Artifact", cmc = 1.0, colors = emptyList(), colorIdentity = emptyList())
        val ownedManualLand = card(id = "manual-owned-land", name = "Manual Owned Land", typeLine = "Land", cmc = 0.0, colors = emptyList(), colorIdentity = identity.map { it.symbol })
        val manualAdds = listOf(
            ManualAdd(unownedOffPlan, isOwned = false),
            ManualAdd(ownedManualLand, isOwned = true),
        )

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned, manualAdds = manualAdds)
        val placedIds = outcome.result.entries.map { it.card.scryfallId }

        assertTrue(unownedOffPlan.scryfallId in placedIds, "an unowned, off-plan manual add must still be kept (D7/R5)")
        assertTrue(ownedManualLand.scryfallId in placedIds, "a manual land add must still be kept")
    }

    @Test
    fun `MockCollectionThin -- builds without crashing, declares gaps, still fills lands`() = runTest {
        val useCase = newUseCase()
        val commander = MockCollectionThin.commander
        val identity = commander.colorIdentity.toManaColors()
        val owned = ownedFrom(MockCollectionThin.ownedCards, MockCollectionThin.ownedBasics)

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned)

        val landCount = outcome.result.entries
            .filter { BasicLandCalculator.isLand(it.card) }
            .sumOf { it.quantity }
        assertTrue(landCount > 0, "a thin collection must still get SOME lands filled from its 8 owned basics")
        assertTrue(outcome.result.entries.size < 100, "a 31-card owned pool cannot fill a 100-card mainboard -- gaps are expected, not filler")
    }

    @Test
    fun `Custom strategy pick resolves the non-null generic baseline (F2 stays dead)`() = runTest {
        val useCase = newUseCase()
        val commander = MockCollectionThin.commander
        val identity = commander.colorIdentity.toManaColors()
        val owned = ownedFrom(MockCollectionThin.ownedCards, MockCollectionThin.ownedBasics)

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned)
        assertEquals(null, outcome.plan.skeleton.archetype, "Custom resolves the generic baseline, archetype == null")
        assertTrue(outcome.result.analysis.pillars.isNotEmpty(), "Custom must still target real bands, not zero targets (F2)")
    }

    @Test
    fun `round-trip identity -- the returned analysis matches re-analyzing the assembled mainboard`() = runTest {
        val useCase = newUseCase()
        val commander = MockCollectionRich.targetFixtures.first().mainboard
            .first { it.card.typeLine.contains("Legendary", ignoreCase = true) }.card
        val identity = commander.colorIdentity.toManaColors()
        val owned = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)

        val curated = CuratedStrategyCatalog.ALL.firstOrNull { it.availableIn(DeckFormat.COMMANDER) && !it.requiresTribe }
        val pick = curated?.let { StrategyPick.Curated(it, null) } ?: StrategyPick.Custom

        val outcome = useCase(DeckFormat.COMMANDER, commander, pick, identity, owned)

        val pipeline = DeckAnalysisPipeline(
            EvaluateDeckUseCase(DeckScorer(RoleClassifier(), NeutralPowerResolver), ProgressionEventBus()),
            InferDeckIdentityUseCase(),
            TestCrashReporter,
        )
        val rebuiltHealth = pipeline.analyze(
            mainboard = outcome.result.entries,
            format = DeckFormat.COMMANDER,
            commander = commander,
            archetypeOverride = outcome.pin.archetype?.name,
            themesOverride = outcome.pin.themes.map { it.name },
            tribeOverride = outcome.pin.tribe,
            postureOverride = outcome.pin.posture?.name,
            emitProgression = false,
        )
        val rebuiltAnalysis = rebuiltHealth.analysis?.copy(debugSynergyGraph = null)
        val originalAnalysis = outcome.result.analysis.copy(debugSynergyGraph = null)

        assertEquals(
            originalAnalysis.totalScore,
            rebuiltAnalysis?.totalScore,
            "round_trip_identity: the wizard's own returned score must equal re-analyzing the persisted deck",
        )
        assertEquals(originalAnalysis, rebuiltAnalysis, "round_trip_identity: full DeckAnalysis must match, not just totalScore")
    }
}

private fun List<String>.toManaColors(): Set<ManaColor> =
    mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }.toSet()
