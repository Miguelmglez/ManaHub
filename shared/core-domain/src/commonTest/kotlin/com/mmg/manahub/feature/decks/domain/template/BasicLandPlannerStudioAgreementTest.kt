package com.mmg.manahub.feature.decks.domain.template
// COMMENTS_REVIEWED: 2026-09-21

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.model.DeckCard
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.BasicLandPlanner
import com.mmg.manahub.feature.decks.domain.engine.BuildAnchor
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.LandTargetResolver
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.ResolvedArchetypeSkeleton
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.StrategyPick
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionCard
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionRich
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture16Tron
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.RecommendWizardStrategiesUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private object AgreementHarnessCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable) = Unit
    override fun log(message: String) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
}

private fun newAgreementPipeline(): DeckAnalysisPipeline = DeckAnalysisPipeline(
    EvaluateDeckUseCase(DeckScorer(RoleClassifier(), NeutralPowerResolver), ProgressionEventBus()),
    InferDeckIdentityUseCase(),
    AgreementHarnessCrashReporter,
)

private fun newAgreementUseCase(): BuildWizardDeckUseCase = BuildWizardDeckUseCase(newAgreementPipeline(), AgreementHarnessCrashReporter)

private fun List<MockCollectionCard>.toOwned(): List<OwnedCard> = map { OwnedCard(it.card, it.quantity) }

/**
 * Deck Wizard UX polish plan, Run 1 §1.1 — proves `DeckStudioViewModel`'s land-delta math (skeleton
 * via the SAME pinned build-time skeleton, identity via the union of non-land colorIdentity +
 * commander, [BasicLandPlanner] over the persisted mainboard) reproduces exactly what the wizard
 * placed, for both a Commander and a 60-card build. If this test ever needs a real delta to pass,
 * Studio and the wizard have drifted apart again.
 */
class BasicLandPlannerStudioAgreementTest {

    private val manaBaseAnalyzer = ManaBaseAnalyzer()

    /** Mirrors `DeckStudioViewModel.deriveStudioColorIdentity`. */
    private fun deriveIdentity(nonLandMainboard: List<DeckEntry>, commanderIdentitySymbols: Set<String>?): Set<ManaColor> {
        val symbols = buildSet {
            nonLandMainboard.forEach { addAll(it.card.colorIdentity) }
            commanderIdentitySymbols?.let { addAll(it) }
        }
        return symbols.mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) } }.toSet()
    }

    /** Mirrors `DeckStudioViewModel.calculateLandDeltas`'s basic-count comparison, run over a wizard
     * build's own persisted [entries]. Asserts every WUBRG/Wastes delta is zero. */
    private fun assertZeroDeltas(
        entries: List<DeckEntry>,
        skeleton: ResolvedArchetypeSkeleton,
        format: DeckFormat,
        commanderIdentitySymbols: Set<String>?,
    ) {
        val nonBasicLands = entries.filter { BasicLandCalculator.isLand(it.card) && !BasicLandCalculator.isBasicLand(it.card) }
            .map { DeckCard(it.card, it.quantity, isOwned = true) }
        val nonLandMainboard = entries.filterNot { BasicLandCalculator.isLand(it.card) }
        val identity = deriveIdentity(nonLandMainboard, commanderIdentitySymbols)

        val landTarget = LandTargetResolver.resolve(format = format, archetypeSkeleton = skeleton, profile = null, manaBaseAnalyzer = manaBaseAnalyzer)
        val basicCounts = BasicLandPlanner.planBasics(
            identity = identity,
            landTarget = landTarget,
            nonLandMainboard = nonLandMainboard,
            nonBasicLands = nonBasicLands,
            manaBaseAnalyzer = manaBaseAnalyzer,
        )

        // Keyed by colour, like Studio: a snow-covered basic counts toward its colour's allocation.
        val currentCounts = mutableMapOf<String, Int>()
        entries.filter { BasicLandCalculator.isBasicLand(it.card) }
            .forEach { entry ->
                val colorKey = BasicLandCalculator.getProducedColors(entry.card).singleOrNull() ?: ManaColor.C.symbol
                currentCounts[colorKey] = (currentCounts[colorKey] ?: 0) + entry.quantity
            }

        ManaColor.entries.forEach { color ->
            assertEquals(
                currentCounts[color.symbol] ?: 0,
                basicCounts[color] ?: 0,
                "Studio's re-derived ${color.displayName} basic count must equal what the wizard actually placed",
            )
        }
    }

    @Test
    fun `Studio's land-delta math agrees with a 60-card wizard build that seeded snow-covered basics`() = runTest {
        val fixture = fixture16Tron()
        val fixtureNonLand = fixture.mainboard.filterNot { BasicLandCalculator.isLand(it.card) }
        val seeds = fixtureNonLand.take(6).map { it.card }
        val snowForest = card(id = "agree-snow-forest", name = "Snow-Covered Forest", typeLine = "Basic Snow Land — Forest", cmc = 0.0, colors = emptyList(), colorIdentity = listOf("G"), producedMana = "G")
        val ownedPool = (
            MockCollectionRich.ownedCards +
                fixtureNonLand.map { MockCollectionCard(it.card, 4) } +
                MockCollectionRich.ownedBasics +
                MockCollectionCard(snowForest, 4)
            ).distinctBy { it.card.scryfallId }.toOwned()
        val anchor = BuildAnchor.Sixty(fixture.colorIdentity, seeds + snowForest)
        val manualAdds = seeds.map { ManualAdd(it, isOwned = true, quantity = 4) } + ManualAdd(snowForest, isOwned = true, quantity = 4)

        val useCase = newAgreementUseCase()
        val draft = useCase.buildWithGroups(fixture.format, anchor, StrategyPick.Custom, ownedPool, manualAdds = manualAdds, includeNonBasicLands = true)
        val outcome = useCase.finalize(draft, resolutions = emptyMap(), fillLands = true)

        assertEquals(60, outcome.result.entries.sumOf { it.quantity })
        assertEquals(4, outcome.result.entries.first { it.card.scryfallId == snowForest.scryfallId }.quantity)
        assertZeroDeltas(outcome.result.entries, outcome.plan.skeleton, fixture.format, commanderIdentitySymbols = null)
    }

    @Test
    fun `Studio's land-delta math agrees with a Commander wizard build`() = runTest {
        val owned = (MockCollectionRich.ownedCards + MockCollectionRich.ownedBasics).toOwned()
        val fixture = MockCollectionRich.targetFixtures.first { fx -> fx.mainboard.any { it.card.scryfallId == "cmd-meren" } }
        val commander = fixture.mainboard.first { it.card.scryfallId == "cmd-meren" }.card
        val identity = commander.colorIdentity.mapNotNull { s -> ManaColor.entries.firstOrNull { it.symbol == s } }.toSet()

        val outcome = newAgreementUseCase()(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned)

        assertZeroDeltas(outcome.result.entries, outcome.plan.skeleton, DeckFormat.COMMANDER, commander.colorIdentity.toSet())
    }

    @Test
    fun `Studio's land-delta math agrees with a 60-card wizard build`() = runTest {
        val fixture = fixture16Tron()
        val fixtureNonLand = fixture.mainboard.filterNot { BasicLandCalculator.isLand(it.card) }
        val seeds = fixtureNonLand.take(6).map { it.card }
        val ownedPool = (
            MockCollectionRich.ownedCards +
                fixtureNonLand.map { MockCollectionCard(it.card, 4) } +
                MockCollectionRich.ownedBasics
            ).distinctBy { it.card.scryfallId }.toOwned()
        val anchor = BuildAnchor.Sixty(fixture.colorIdentity, seeds)
        val pick = runCatching { RecommendWizardStrategiesUseCase()(fixture.format, anchor, ownedCollection = ownedPool) }
            .getOrElse { emptyList() }
            .firstOrNull()
            ?.let { StrategyPick.Curated(it.strategy, it.tribe) }
            ?: StrategyPick.Custom

        val useCase = newAgreementUseCase()
        val draft = useCase.buildWithGroups(fixture.format, anchor, pick, ownedPool, includeNonBasicLands = true)
        val outcome = useCase.finalize(draft, resolutions = emptyMap(), fillLands = true)

        assertZeroDeltas(outcome.result.entries, outcome.plan.skeleton, fixture.format, commanderIdentitySymbols = null)
    }

    @Test
    fun `Studio's land-delta math agrees with a 60-card wizard build that seeded a basic AND a non-basic land`() = runTest {
        val fixture = fixture16Tron()
        val fixtureNonLand = fixture.mainboard.filterNot { BasicLandCalculator.isLand(it.card) }
        val seeds = fixtureNonLand.take(6).map { it.card }
        val forest = MockCollectionRich.ownedBasics.first { it.card.name == "Forest" }.card
        val utilityLand = card(id = "agree-utility-land", name = "Agreement Utility Land", typeLine = "Land", cmc = 0.0, colors = emptyList(), colorIdentity = listOf("G"), producedMana = "G")
        val ownedPool = (
            MockCollectionRich.ownedCards +
                fixtureNonLand.map { MockCollectionCard(it.card, 4) } +
                MockCollectionRich.ownedBasics +
                MockCollectionCard(utilityLand, 1)
            ).distinctBy { it.card.scryfallId }.toOwned()
        val anchor = BuildAnchor.Sixty(fixture.colorIdentity, seeds + forest + utilityLand)
        val manualAdds = seeds.map { ManualAdd(it, isOwned = true, quantity = 4) } +
            ManualAdd(forest, isOwned = true, quantity = 4) +
            ManualAdd(utilityLand, isOwned = true, quantity = 1)

        val useCase = newAgreementUseCase()
        val draft = useCase.buildWithGroups(fixture.format, anchor, StrategyPick.Custom, ownedPool, manualAdds = manualAdds, includeNonBasicLands = true)
        val outcome = useCase.finalize(draft, resolutions = emptyMap(), fillLands = true)

        assertEquals(60, outcome.result.entries.sumOf { it.quantity })
        assertEquals(1, outcome.result.entries.count { it.card.scryfallId == forest.scryfallId }, "a seeded basic and the same basic placed by the fill must persist as ONE slot")
        assertZeroDeltas(outcome.result.entries, outcome.plan.skeleton, fixture.format, commanderIdentitySymbols = null)
    }
}
