package com.mmg.manahub.feature.decks.domain.template
// COMMENTS_REVIEWED: 2026-09-17

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.BuildAnchor
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategyCatalog
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.Finding
import com.mmg.manahub.feature.decks.domain.engine.FindingSeverity
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.StrategyPick
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.AnalysisV3Fixture
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionCard
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionRich
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture14MonoRedBurn
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture15AzoriusControl
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture16Tron
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture18Whirza
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture19DeathAndTaxes
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture20Merfolk
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture21Jund
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture22GrixisControl
import com.mmg.manahub.feature.decks.domain.engine.isLegalForFormat
import com.mmg.manahub.feature.decks.domain.engine.CopyPolicy
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.RecommendWizardStrategiesUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Wizard 60-card wave (v6) plan, Phase 6.1 — harness v2's Sixty-format mock segment, the
//  60-card sibling of WizardHarnessMockSegmentsTest.kt (which stays Commander-only, untouched).
//
//  Lives in shared/core-domain's commonTest for the SAME reason WizardHarnessMockSegmentsTest.kt
//  does (see that file's own header): MockCollectionRich/the AnalysisV3 corpus fixtures already live
//  here, and KMP commonTest has no cross-module sharing into :app's test source set. The runner
//  shape (a local CrashReporter/pipeline/useCase factory, a compact HARD-metric data class, a single
//  `println` summary — no file I/O) is the SAME pattern that file established; this file does not
//  invent a second one. [WizardHarnessMockSegmentsTest] keeps its own byte-identical Commander HARD
//  set (plan rule 0.3) — nothing here touches it.
//
//  Two deliberate deviations from the plan's literal Phase 6.1 text, both documented at their use
//  site below rather than silently applied:
//  1. Seed selection ("6 highest-edhrecRank-power non-land cards per fixture"): none of fixtures
//     14-22 ever set `edhrecRank` (confirmed by inspection — zero occurrences across all 9 files), so
//     every card in this corpus resolves to the SAME EdhrecPowerResolver baseline (0.35f, no
//     gameChanger flag anywhere in this corpus either) — "highest edhrec power" is a degenerate,
//     fully-tied ordering over this fixture set, not a real signal. `sixtySeeds()` reuses the exact
//     convention BuildWizardDeckUseCaseSixtyTest already established for fixture 14 in Phase 1 (this
//     campaign's own precedent, not a new invention): the first 6 non-land entries in the fixture's
//     own authored mainboard order — each fixture's own KDoc states its cards were hand-picked as
//     that archetype's signature pieces, front-loaded in the list, so this is the actually-meaningful
//     deterministic ordering available in this corpus.
//  2. Seed quantity ("quantity 4 non-legendary / 1 legendary"): `CopyPolicy.maxSeedCopies` — the
//     ONE engine predicate for "how many copies may a seed be" (S3) — has no legendary-status branch
//     outside a Commander-shaped format (real MTG rule: the legend rule limits how many legendaries
//     may be IN PLAY at once, not how many copies go in a 60-card deck; Vintage's restricted list is
//     the only per-format 1-copy carve-out `CopyPolicy` models, see `DeckLegality.isRestricted`).
//     Hand-coding a legendary-based cap here would be exactly the "wizard-only heuristic that
//     disagrees with the engine's own contract" this campaign forbids. Every seed quantity below is
//     `CopyPolicy.maxSeedCopies(card, format)`, which resolves to 4 for every fixture 14-22 card in
//     every format this file exercises (none are Commander-shaped or Vintage-restricted).
//
//  ./gradlew :shared:core-domain:jvmTest --tests "*WizardHarnessSixtyMock*"
// ═══════════════════════════════════════════════════════════════════════════════

private object SixtyMockHarnessCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable) = Unit
    override fun log(message: String) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
}

private fun newSixtyPipeline(): DeckAnalysisPipeline = DeckAnalysisPipeline(
    EvaluateDeckUseCase(DeckScorer(RoleClassifier(), NeutralPowerResolver), ProgressionEventBus()),
    InferDeckIdentityUseCase(),
    SixtyMockHarnessCrashReporter,
)

private fun newSixtyUseCase(): BuildWizardDeckUseCase = BuildWizardDeckUseCase(newSixtyPipeline(), SixtyMockHarnessCrashReporter)

private fun List<MockCollectionCard>.toOwned(): List<OwnedCard> = map { OwnedCard(it.card, it.quantity) }

private fun nonLandEntries(fixture: AnalysisV3Fixture) = fixture.mainboard.filterNot { BasicLandCalculator.isLand(it.card) }

/** Deviation 1 (see this file's header) — the fixture's own first 6 non-land cards, in authored
 * (signature-card-first) order. */
private fun sixtySeeds(fixture: AnalysisV3Fixture): List<Card> = nonLandEntries(fixture).take(6).map { it.card }

/** Plan §5 Phase 6.1: [MockCollectionRich]'s own distractor pool + this fixture's own cards (owned
 * at qty 4, deviation 2 above) + basics — a real, plentiful pool every HARD metric applies to in its
 * literal form (same rationale as [MockCollectionRich]'s own KDoc). */
private fun sixtyOwnedPool(fixture: AnalysisV3Fixture): List<OwnedCard> {
    val fixtureOwned = nonLandEntries(fixture).map { MockCollectionCard(it.card, 4) }
    return (MockCollectionRich.ownedCards + fixtureOwned + MockCollectionRich.ownedBasics)
        .distinctBy { it.card.scryfallId }
        .toOwned()
}

/** Compact HARD/TRACKED-metric report for one Sixty mock-segment build — the 60-card sibling of
 * [WizardHarnessMockSegmentsTest]'s own `MockSegmentMetrics` (same field DEFINITIONS as plan §5 /
 * HarnessMetricsV2Calculator, generalized off the Commander-only singleton assumptions). */
private data class SixtySegmentMetrics(
    val label: String,
    // ── HARD ────────────────────────────────────────────────────────────────
    val noBlockerOk: Boolean,
    val sizeOrGapsOk: Boolean,
    val determinismOk: Boolean,
    val quantityWithinMaxPlaceableOk: Boolean,
    val quantityViolations: List<String>,
    val legalityOk: Boolean,
    val legalityViolations: List<String>,
    val offplanShareOk: Boolean,
    val offplanShare: Double,
    val noAvoidableRoleOverflowOk: Boolean,
    val roleOverflowViolations: List<String>,
    val noColorSourceShortageOk: Boolean,
    val landTargetOk: Boolean,
    val landCount: Int,
    // ── TRACKED ─────────────────────────────────────────────────────────────
    val totalScore: Int,
    val distinctNames: Int,
    val fourOfCount: Int,
    val runtimeMs: Long,
    val resolvedMacro: String?,
    val expectedMacro: String,
    val macroMatch: Boolean,
    val blockerFindings: List<String> = emptyList(),
    val gapSectionsDebug: String = "",
) {
    val allHardPass get() = noBlockerOk && sizeOrGapsOk && determinismOk && quantityWithinMaxPlaceableOk &&
        legalityOk && offplanShareOk && noAvoidableRoleOverflowOk && noColorSourceShortageOk && landTargetOk
}

private suspend fun computeSixtyMetrics(
    label: String,
    format: DeckFormat,
    identity: Set<ManaColor>,
    seeds: List<Card>,
    ownedPool: List<OwnedCard>,
    pick: StrategyPick,
    expectedMacro: String,
): SixtySegmentMetrics {
    val anchor = BuildAnchor.Sixty(identity, seeds)
    val mark = TimeSource.Monotonic.markNow()
    val useCase1 = newSixtyUseCase()
    val draft1 = useCase1.buildWithGroups(format, anchor, pick, ownedPool, includeNonBasicLands = true)
    val outcome = useCase1.finalize(draft1, resolutions = emptyMap(), fillLands = true)
    val runtimeMs = mark.elapsedNow().inWholeMilliseconds

    val useCase2 = newSixtyUseCase()
    val draft2 = useCase2.buildWithGroups(format, anchor, pick, ownedPool, includeNonBasicLands = true)
    val second = useCase2.finalize(draft2, resolutions = emptyMap(), fillLands = true)

    val result = outcome.result
    val analysis = result.analysis
    val plan = outcome.plan
    val entries = result.entries
    val nonLand = entries.filterNot { BasicLandCalculator.isLand(it.card) }
    val lands = entries.filter { BasicLandCalculator.isLand(it.card) }

    val noBlockerOk = analysis.pillars.none { pillar -> pillar.findings.any { it.severity == FindingSeverity.BLOCKER } }

    val actualSize = entries.sumOf { it.quantity }
    val gapsTotal = result.gapSections.sumOf { section -> (section.min ?: 0) - section.current }
    val sizeOrGapsOk = actualSize + gapsTotal >= format.targetDeckSize

    val determinismOk = entries.map { it.card.scryfallId to it.quantity }.sortedBy { it.first } ==
        second.result.entries.map { it.card.scryfallId to it.quantity }.sortedBy { it.first }

    val ownedByName: Map<String, Int> = ownedPool.groupBy { it.card.name }.mapValues { (_, rows) -> rows.sumOf { it.quantity } }
    val quantityViolations = nonLand.filter { entry ->
        entry.quantity > CopyPolicy.maxPlaceable(entry.card, format, ownedByName[entry.card.name])
    }.map { "${it.card.name}=${it.quantity}" }
    val quantityWithinMaxPlaceableOk = quantityViolations.isEmpty()

    val legalityViolations = if (format == DeckFormat.CASUAL) {
        emptyList()
    } else {
        nonLand.filterNot { isLegalForFormat(it.card, format) }.map { it.card.name }
    }
    val legalityOk = legalityViolations.isEmpty()

    val offplanIds = analysis.pillars.flatMap { it.sections }.filter { it.id == "offplan" }
        .flatMap { section -> section.contributions.map { it.scryfallId } }.toSet()
    val wizardCopies = nonLand.sumOf { it.quantity }
    val offplanCopies = nonLand.filter { it.card.scryfallId in offplanIds }.sumOf { it.quantity }
    val offplanShare = if (wizardCopies > 0) offplanCopies.toDouble() / wizardCopies else 0.0
    val offplanShareOk = offplanShare <= 0.15

    val fallbackIds = (result.fallbackStandaloneIds + result.fallbackOffPlanIds).toSet()
    val mainLoopNonLand = nonLand.filterNot { it.card.scryfallId in fallbackIds }
    val mainLoopRoleCounts = ArchetypeRoleClassifier.deckRoleCounts(mainLoopNonLand)
    val roleOverflowViolations = plan.skeleton.roleTargets
        .filterKeys { it !in plan.skeleton.antiRoles }
        .filter { (role, target) -> (mainLoopRoleCounts[role] ?: 0) > target.max }
        .map { (role, target) -> "$role: ${mainLoopRoleCounts[role]}/${target.max}" }
    val noAvoidableRoleOverflowOk = roleOverflowViolations.isEmpty()

    val noColorSourceShortageOk = analysis.pillars.flatMap { it.findings }.none { it is Finding.ColorSourceShortage }

    val landCount = lands.sumOf { it.quantity }
    val landTargetOk = landCount in plan.skeleton.lands.min..plan.skeleton.lands.max

    val resolvedMacro = analysis.strategy.archetype?.name

    return SixtySegmentMetrics(
        label = label,
        noBlockerOk = noBlockerOk,
        sizeOrGapsOk = sizeOrGapsOk,
        determinismOk = determinismOk,
        quantityWithinMaxPlaceableOk = quantityWithinMaxPlaceableOk,
        quantityViolations = quantityViolations,
        legalityOk = legalityOk,
        legalityViolations = legalityViolations,
        offplanShareOk = offplanShareOk,
        offplanShare = offplanShare,
        noAvoidableRoleOverflowOk = noAvoidableRoleOverflowOk,
        roleOverflowViolations = roleOverflowViolations,
        noColorSourceShortageOk = noColorSourceShortageOk,
        landTargetOk = landTargetOk,
        landCount = landCount,
        totalScore = analysis.totalScore,
        distinctNames = result.fillStats.distinctNames,
        fourOfCount = result.fillStats.fourOfCount,
        runtimeMs = runtimeMs,
        resolvedMacro = resolvedMacro,
        expectedMacro = expectedMacro,
        macroMatch = resolvedMacro == expectedMacro,
        blockerFindings = analysis.pillars.flatMap { it.findings }.filter { it.severity == FindingSeverity.BLOCKER }.map { it.toString() },
        gapSectionsDebug = "actualSize=$actualSize gapsTotal=$gapsTotal fillStats=${result.fillStats}",
    )
}

private fun recommendedOrCustom(format: DeckFormat, anchor: BuildAnchor.Sixty, ownedPool: List<OwnedCard>): StrategyPick {
    val recommendations = runCatching {
        RecommendWizardStrategiesUseCase()(format, anchor, ownedCollection = ownedPool)
    }.getOrElse { emptyList() }
    val top = recommendations.firstOrNull()
    return top?.let { StrategyPick.Curated(it.strategy, it.tribe) } ?: StrategyPick.Custom
}

/**
 * Plan §5 Phase 6.1 — the Sixty-format mock segment: fixtures 14-22 (fixture 17 excluded, see this
 * class's own KDoc), each built #1-recommended AND Custom, in its own [DeckFormat] plus the plan's
 * extra format widenings (14/15 also STANDARD, 20 also CASUAL).
 */
class WizardHarnessSixtyMockSegmentsTest {

    /**
     * Fixture 17 (the deliberately-incoherent Commander negative fixture, 99 non-land cards, its own
     * pinned Commander) is NOT one of this segment's 8 cases: `AnalysisV3Fixture.format` for it is
     * [DeckFormat.COMMANDER] (verified by reading the fixture source, not assumed from the plan's own
     * "14-22, MODERN" text) — it is structurally a Commander-format fixture the corpus happens to
     * number in this range, not a Sixty-card one. [BuildWizardDeckUseCase.buildWithGroups] itself
     * `require()`s a non-Commander format for a [BuildAnchor.Sixty] anchor, so attempting it here
     * would be a caller bug, not a build the engine could ever accept. A diagnosed exclusion (this
     * campaign's own established precedent — see [MockCollectionRich]'s own harness test KDoc for
     * the prior instance of this exact pattern), not a loosened metric.
     */
    private data class SixtyCase(val fixture: AnalysisV3Fixture, val extraFormats: List<DeckFormat> = emptyList())

    private val cases: List<SixtyCase> by lazy {
        listOf(
            SixtyCase(fixture14MonoRedBurn(), listOf(DeckFormat.STANDARD)),
            SixtyCase(fixture15AzoriusControl(), listOf(DeckFormat.STANDARD)),
            SixtyCase(fixture16Tron()),
            SixtyCase(fixture18Whirza()),
            SixtyCase(fixture19DeathAndTaxes()),
            SixtyCase(fixture20Merfolk(), listOf(DeckFormat.CASUAL)),
            SixtyCase(fixture21Jund()),
            SixtyCase(fixture22GrixisControl()),
        )
    }

    @Test
    fun `every Sixty mock fixture passes every HARD metric, curated and Custom, across every tested format`() = runTest {
        val failures = mutableListOf<String>()
        val allMetrics = mutableListOf<SixtySegmentMetrics>()

        for (case in cases) {
            val fixture = case.fixture
            val seeds = sixtySeeds(fixture)
            val ownedPool = sixtyOwnedPool(fixture)
            val formats = listOf(fixture.format) + case.extraFormats

            for (format in formats) {
                val anchor = BuildAnchor.Sixty(fixture.colorIdentity, seeds)
                val recommendedPick = recommendedOrCustom(format, anchor, ownedPool)

                val recommended = computeSixtyMetrics(
                    "${fixture.id}_${format.name.lowercase()}_recommended", format, fixture.colorIdentity, seeds, ownedPool, recommendedPick, fixture.expectedMacro,
                )
                val custom = computeSixtyMetrics(
                    "${fixture.id}_${format.name.lowercase()}_custom", format, fixture.colorIdentity, seeds, ownedPool, StrategyPick.Custom, fixture.expectedMacro,
                )

                for (m in listOf(recommended, custom)) {
                    allMetrics += m
                    if (!m.allHardPass) {
                        failures += "${m.label}: blocker=${!m.noBlockerOk} size_or_gaps=${!m.sizeOrGapsOk} " +
                            "determinism=${!m.determinismOk} quantity=${m.quantityViolations} " +
                            "legality=${m.legalityViolations} offplan_share=${m.offplanShare} " +
                            "role_overflow=${m.roleOverflowViolations} color_source_shortage=${!m.noColorSourceShortageOk} " +
                            "land_target=${!m.landTargetOk}(${m.landCount}) score=${m.totalScore} " +
                            "blockers=${m.blockerFindings} gaps=${m.gapSectionsDebug}"
                    }
                }
            }
        }

        val scores = allMetrics.map { it.totalScore }.sorted()
        val distinctNames = allMetrics.map { it.distinctNames }.sorted()
        val fourOfCounts = allMetrics.map { it.fourOfCount }.sorted()
        val runtimes = allMetrics.map { it.runtimeMs }.sorted()
        val macroMatches = allMetrics.count { it.macroMatch }
        println(
            "[wizard-harness-sixty-mock] ${allMetrics.size} specs, score min=${scores.firstOrNull()} " +
                "median=${scores.getOrNull(scores.size / 2)} max=${scores.lastOrNull()}",
        )
        println(
            "[wizard-harness-sixty-mock] distinctNames min=${distinctNames.firstOrNull()} median=${distinctNames.getOrNull(distinctNames.size / 2)} max=${distinctNames.lastOrNull()} " +
                "fourOfCount min=${fourOfCounts.firstOrNull()} median=${fourOfCounts.getOrNull(fourOfCounts.size / 2)} max=${fourOfCounts.lastOrNull()}",
        )
        println("[wizard-harness-sixty-mock] runtime ms min=${runtimes.firstOrNull()} median=${runtimes.getOrNull(runtimes.size / 2)} max=${runtimes.lastOrNull()}")
        println("[wizard-harness-sixty-mock] resolved macro == fixture expectedMacro (TRACKED, not HARD): $macroMatches/${allMetrics.size}")
        println("[wizard-harness-sixty-mock] failures: $failures")
        // Phase 6.3 (CONSISTENCY_CREDIT calibration): one parseable line per spec, RESOLVED macro +
        // score + fourOfCount -- the calibration sweep pools these across every CONSISTENCY_CREDIT
        // candidate run rather than re-deriving a second data source (ADR-007 §4: only this harness's
        // own data may calibrate the constant).
        allMetrics.forEach { m -> println("[wizard-harness-sixty-mock-calib] macro=${m.resolvedMacro} score=${m.totalScore} fourOfCount=${m.fourOfCount} label=${m.label}") }

        assertTrue(failures.isEmpty(), "Sixty mock-segment HARD-metric failures: $failures")
    }
}

/**
 * Plan §5 Phase 6.1 — the dedicated colorless case: identity `{}`, strategy `big_mana`, pool = the
 * Tron fixture (16) + [MockCollectionRich]'s own colorless cards. Seeds are fixture 16's own 6
 * colorless non-land cards (Expedition Map / Karn Liberated / Wurmcoil Engine / Ulamog / Oblivion
 * Stone / Relic of Progenitus — exactly 6, confirmed by inspection, so [sixtySeeds]'s own "first 6"
 * convention needs no special-casing here beyond filtering to colorless first).
 */
class WizardHarnessSixtyColorlessSegmentTest {

    /** Deviation 3 (colorless-only, not part of this file's shared header — scoped to this single
     * test): [MockCollectionRich.ownedBasics] carries only the WUBRG five, never Wastes (it backs
     * ONLY the 4 target-fixture Commander identities, all colored) — a colorless build needs its own
     * Wastes [Card] in the pool, or [BuildWizardDeckUseCase.resolveBasicCard] finds none and silently
     * drops the whole basics stage (its own KDoc documents this as the intended defensive behavior
     * for a genuinely missing basic, with a non-fatal breadcrumb — not a build crash). Mirrors
     * [MockCollections.basicLand]'s own private convention (`"Basic Land — $name"` type line) rather
     * than inventing a second shape. */
    private val wastes = card(id = "mock-wastes-colorless-harness", name = "Wastes", typeLine = "Basic Land — Wastes", cmc = 0.0, colors = emptyList(), colorIdentity = emptyList())

    @Test
    fun `colorless Sixty build stays colorless — every entry empty identity, basics are Wastes only`() = runTest {
        val fixture = fixture16Tron()
        val colorlessSeeds = nonLandEntries(fixture).filter { it.card.colorIdentity.isEmpty() }.take(6).map { it.card }
        val ownedPool = (
            MockCollectionRich.ownedCards.filter { it.card.colorIdentity.isEmpty() } +
                nonLandEntries(fixture).map { MockCollectionCard(it.card, 4) } +
                MockCollectionRich.ownedBasics +
                listOf(MockCollectionCard(wastes, 40))
            ).distinctBy { it.card.scryfallId }.toOwned()
        val bigMana = CuratedStrategyCatalog.ALL.first { it.id == "big_mana" }
        val pick = StrategyPick.Curated(bigMana, tribe = null)

        val metrics = computeSixtyMetrics(
            "16_colorless_big_mana", fixture.format, emptySet(), colorlessSeeds, ownedPool, pick, fixture.expectedMacro,
        )
        println("[wizard-harness-sixty-colorless] $metrics")
        assertTrue(metrics.allHardPass, "colorless Sixty build failed a HARD metric: $metrics")

        // Colorless-specific checks (S9): every placed card genuinely has an empty color identity,
        // and every land is Wastes (BasicLandCalculator's own colourless basic — F17's fix).
        val anchor = BuildAnchor.Sixty(emptySet(), colorlessSeeds)
        val draft = newSixtyUseCase().buildWithGroups(fixture.format, anchor, pick, ownedPool, includeNonBasicLands = true)
        val outcome = newSixtyUseCase().finalize(draft, resolutions = emptyMap(), fillLands = true)
        val entries = outcome.result.entries
        val nonLand = entries.filterNot { BasicLandCalculator.isLand(it.card) }
        val lands = entries.filter { BasicLandCalculator.isLand(it.card) }

        val offIdentity = nonLand.filterNot { it.card.colorIdentity.isEmpty() }.map { it.card.name }
        assertTrue(offIdentity.isEmpty(), "colorless build placed a non-colorless card: $offIdentity")

        val nonWastesLands = lands.filterNot { it.card.name == "Wastes" }.map { it.card.name }
        assertTrue(nonWastesLands.isEmpty(), "colorless build's land base must be Wastes only, found: $nonWastesLands")
    }
}
