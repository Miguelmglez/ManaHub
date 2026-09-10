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
    fun `MockCollectionThin -- builds without crashing, declares gaps, places no filler, still fills lands, reports the score honestly`() = runTest {
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

        // D8: no filler placed -- every placed non-land, non-commander entry is EITHER a candidate
        // the placement loop chose from the owned pool (never exceeds the 30-card owned non-land
        // pool size) or the commander itself. The loop can place AT MOST 30 non-land candidates --
        // if it ever placed more, that would mean it invented cards outside the collection (D7) or
        // placed something the filler floor should have rejected.
        val nonLandNonCommanderCount = outcome.result.entries.count {
            !BasicLandCalculator.isLand(it.card) && it.card.scryfallId != commander.scryfallId
        }
        assertTrue(
            nonLandNonCommanderCount <= MockCollectionThin.ownedCards.size,
            "the placement loop must never place more non-land cards than the thin pool actually owns (${MockCollectionThin.ownedCards.size}), got $nonLandNonCommanderCount -- D8's filler floor exists precisely to prevent this",
        )

        // Score is reported honestly, not inflated to hide the gap: a 39-card (approx) mainboard
        // must show a DeckTooSmall finding and a total score that is NOT the perfect/near-perfect
        // number a full 100-card deck could reach.
        val findings = outcome.result.analysis.pillars.flatMap { it.findings }
        assertTrue(
            findings.any { it is com.mmg.manahub.feature.decks.domain.engine.Finding.DeckTooSmall },
            "a mainboard this thin must surface a DeckTooSmall finding, not hide the shortfall: $findings",
        )
        assertTrue(outcome.result.analysis.totalScore < 90, "a genuinely gappy thin-collection build must not score as if it were complete, got ${outcome.result.analysis.totalScore}")
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
    fun `W0_1 -- a Commander-banned owned card is structurally excluded from the COMMANDER candidate pool`() = runTest {
        val useCase = newUseCase()
        val commander = MockCollectionThin.commander
        val identity = commander.colorIdentity.toManaColors()
        val bannedOwned = com.mmg.manahub.feature.decks.domain.engine.card(
            id = "banned-owned-1",
            name = "Banned Owned Candidate",
            typeLine = "Creature — Vampire",
            cmc = 2.0,
            colors = identity.map { it.symbol },
            colorIdentity = identity.map { it.symbol },
            legalityCommander = "banned",
        )
        val owned = ownedFrom(MockCollectionThin.ownedCards, MockCollectionThin.ownedBasics) +
            OwnedCard(bannedOwned, 1)

        val commanderOutcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned)
        assertTrue(
            commanderOutcome.result.entries.none { it.card.scryfallId == bannedOwned.scryfallId },
            "a banned owned card must never enter a COMMANDER build's candidate pool",
        )
    }

    @Test
    fun `W0_1 -- builder and analysis agree on the same legality verdict per format`() = runTest {
        val commander = MockCollectionThin.commander
        val bannedCard = com.mmg.manahub.feature.decks.domain.engine.card(
            id = "banned-analysis-1",
            name = "Banned Analysis Candidate",
            typeLine = "Sorcery",
            cmc = 2.0,
            colors = emptyList(),
            colorIdentity = emptyList(),
            legalityCommander = "banned",
        )
        val mainboard = listOf(com.mmg.manahub.feature.decks.domain.engine.DeckEntry(bannedCard, 1, isOwned = true))
        val pipeline = DeckAnalysisPipeline(
            EvaluateDeckUseCase(DeckScorer(RoleClassifier(), NeutralPowerResolver), ProgressionEventBus()),
            InferDeckIdentityUseCase(),
            TestCrashReporter,
        )

        val commanderHealth = pipeline.analyze(
            mainboard = mainboard, format = DeckFormat.COMMANDER, commander = commander,
            archetypeOverride = null, themesOverride = emptyList(), emitProgression = false,
        )
        val casualHealth = pipeline.analyze(
            mainboard = mainboard, format = DeckFormat.COMMANDER_CASUAL, commander = commander,
            archetypeOverride = null, themesOverride = emptyList(), emitProgression = false,
        )

        val commanderIllegal = commanderHealth.analysis?.pillars.orEmpty().flatMap { it.findings }
            .any { it is com.mmg.manahub.feature.decks.domain.engine.Finding.IllegalCard }
        val casualIllegal = casualHealth.analysis?.pillars.orEmpty().flatMap { it.findings }
            .any { it is com.mmg.manahub.feature.decks.domain.engine.Finding.IllegalCard }

        assertTrue(commanderIllegal, "AnalysisEngine must flag a banned card as illegal for COMMANDER (matches the builder's own exclusion)")
        assertTrue(!casualIllegal, "AnalysisEngine must NOT flag a banned card as illegal for COMMANDER_CASUAL (R7)")
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
