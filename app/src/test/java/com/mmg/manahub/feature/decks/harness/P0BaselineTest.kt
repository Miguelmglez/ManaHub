package com.mmg.manahub.feature.decks.harness
// COMMENTS_REVIEWED: 2026-09-08

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.FindingSeverity
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.template.BuildDeckFromTemplateUseCase
import com.mmg.manahub.feature.decks.domain.template.DeckTemplateResolver
import com.mmg.manahub.feature.decks.domain.template.DeckWizardSpec
import com.mmg.manahub.feature.decks.domain.template.TemplateBuildProgress
import com.mmg.manahub.feature.decks.domain.template.TemplateBuildResult
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCollectionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import kotlin.system.measureTimeMillis

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Wizard Commander v3 plan, Phase 0 item 0.5 — baseline measurement.
//
//  Runs the CURRENT wizard build path (BuildDeckFromTemplateUseCase / Motor A, via
//  HarnessSpecs.commanderMatrix — the SAME spec matrix the legacy WizardQualityMatrixTest harness
//  already builds) over the real-collection Commander fixture, but scores each resulting build with
//  DeckAnalysisPipeline (the Phase 0/E4 shared analysis entry point) instead of the legacy
//  coherence-cuts-v2/Motor A metrics HarnessMetricsCalculator computes. This gives the "before"
//  numbers for the campaign's own target engine (Deck Analysis Engine v3), not the retired one.
//
//  This is a TEMPORARY measurement harness (plan §0.5), not new production code — a real JVM test
//  that Assume-skips (never fails) when the gitignored real-collection fixture
//  (testdata/wizard-harness/) is not checked out on this machine.
//
//  Reproduce: ./gradlew :app:testDebugUnitTest --tests
//  "com.mmg.manahub.feature.decks.harness.P0BaselineTest" (requires the harness fixture files under
//  testdata/wizard-harness/ — see docs/plans/deck-wizard-commander-progress.md item 0.2).
// ═══════════════════════════════════════════════════════════════════════════════

private object BaselineCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable) = Unit
    override fun log(message: String) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
}

private data class BaselineBuildMetrics(
    val label: String,
    val totalScore: Int,
    val hasBlocker: Boolean,
    val offplanShare: Float,
    val gappingSectionCount: Int,
    val totalMissingCount: Int,
    val runtimeMs: Long,
)

class P0BaselineTest {

    @Test
    fun measureCommanderMatrixAgainstDeckAnalysisPipeline() = runBlocking {
        val harnessDir = FixtureLoader.locateHarnessDir()
        Assume.assumeNotNull("real-collection harness fixture not checked out on this machine", harnessDir)
        val fixtures = FixtureLoader.load(harnessDir!!)

        // Deck Wizard Commander v3 plan (0.2 fact): 93 commander-eligible owned cards -> the full
        // commanderMatrix is 93 (no-hint) + 6 (strategy-hint) + 2 (seeds) = 101 builds. Measured at
        // ~101 builds x (one BuildDeckFromTemplateUseCase build + one DeckAnalysisPipeline.analyze)
        // this completed well inside a single JVM test run -- no sampling was needed. If a future
        // re-run on a much larger fixture becomes too slow, sample deterministically (e.g. every
        // Nth candidate by the SAME `sortedBy { it.name }` order commanderMatrix already uses) and
        // document the stride here.
        val specs = HarnessSpecs.commanderMatrix(fixtures)

        val cardRepository = FixtureCardRepository(fixtures.cardsByName, fixtures.basicsByName)
        val communityRepository = NoCommunityAggregateRepository()
        val deckScorer = DeckScorer(RoleClassifier(), NeutralPowerResolver)
        val buildUseCase = BuildDeckFromTemplateUseCase(
            deckTemplateResolver = DeckTemplateResolver(communityRepository, ioDispatcher = Dispatchers.Default),
            deckScorer = deckScorer,
            cardRepository = cardRepository,
            suggestAddsFromCollectionUseCase = SuggestAddsFromCollectionUseCase(deckScorer),
            ioDispatcher = Dispatchers.Default,
        )
        val evaluateDeckUseCase = EvaluateDeckUseCase(deckScorer, ProgressionEventBus())
        val pipeline = DeckAnalysisPipeline(evaluateDeckUseCase, InferDeckIdentityUseCase(), BaselineCrashReporter)

        val metrics = mutableListOf<BaselineBuildMetrics>()
        var failures = 0

        specs.forEach { spec ->
            val elapsed: Long
            var built: TemplateBuildResult? = null
            elapsed = measureTimeMillis {
                built = runCatching { build(buildUseCase, spec.wizardSpec, fixtures.collection) }.getOrNull()
            }
            val result = built
            if (result == null) {
                failures++
                return@forEach
            }

            val commander = spec.wizardSpec.commander
            val commanderEntry = if (spec.wizardSpec.format == DeckFormat.COMMANDER && commander != null) {
                DeckEntry(card = commander, quantity = 1, isOwned = true, isSideboard = false)
            } else {
                null
            }
            val mainboard = listOfNotNull(commanderEntry) + result.deckCards

            var analysisElapsed = 0L
            val health = try {
                var h: com.mmg.manahub.feature.decks.domain.usecase.DeckHealth? = null
                analysisElapsed = measureTimeMillis {
                    h = pipeline.analyze(
                        mainboard = mainboard,
                        format = spec.wizardSpec.format,
                        commander = commander,
                        archetypeOverride = result.archetypeOverride,
                        themesOverride = result.themesOverride,
                        emitProgression = false,
                    )
                }
                h
            } catch (t: Throwable) {
                null
            }
            val analysis = health?.analysis
            if (analysis == null) {
                failures++
                return@forEach
            }

            val hasBlocker = analysis.pillars.any { pillar -> pillar.findings.any { it.severity == FindingSeverity.BLOCKER } }
            val nonLandCount = mainboard.filterNot { com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator.isLand(it.card) }
                .sumOf { it.quantity }
            val offplanCurrent = analysis.pillars
                .flatMap { it.sections }
                .firstOrNull { it.id == "offplan" }
                ?.current ?: 0
            val offplanShare = if (nonLandCount > 0) offplanCurrent.toFloat() / nonLandCount else 0f

            val gappingSections = analysis.pillars.flatMap { it.sections }
                .filter { section -> val min = section.min; min != null && section.current < min }
            val totalMissing = gappingSections.sumOf { (it.min ?: 0) - it.current }

            metrics += BaselineBuildMetrics(
                label = spec.label,
                totalScore = analysis.totalScore,
                hasBlocker = hasBlocker,
                offplanShare = offplanShare,
                gappingSectionCount = gappingSections.size,
                totalMissingCount = totalMissing,
                runtimeMs = elapsed + analysisElapsed,
            )
        }

        printSummary(specs.size, metrics, failures)
    }

    private fun printSummary(specCount: Int, metrics: List<BaselineBuildMetrics>, failures: Int) {
        if (metrics.isEmpty()) {
            println("[P0 baseline] no successful builds out of $specCount specs ($failures failures)")
            return
        }
        val scores = metrics.map { it.totalScore }.sorted()
        val offplan = metrics.map { it.offplanShare }.sorted()
        val runtimes = metrics.map { it.runtimeMs }.sorted()

        fun percentile(sorted: List<Int>, p: Double): Double {
            val idx = (p * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
            return sorted[idx].toDouble()
        }
        fun percentileF(sorted: List<Float>, p: Double): Double {
            val idx = (p * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
            return sorted[idx].toDouble()
        }
        fun percentileL(sorted: List<Long>, p: Double): Double {
            val idx = (p * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
            return sorted[idx].toDouble()
        }

        val blockerShare = metrics.count { it.hasBlocker }.toDouble() / metrics.size
        val avgGapSections = metrics.map { it.gappingSectionCount }.average()
        val avgMissing = metrics.map { it.totalMissingCount }.average()

        println(
            """
            |[P0 baseline] specs=$specCount evaluated=${metrics.size} failures=$failures
            |  totalScore: min=${scores.first()} p25=${percentile(scores, 0.25)} median=${percentile(scores, 0.5)} p75=${percentile(scores, 0.75)} max=${scores.last()}
            |  share with >=1 BLOCKER finding: ${"%.3f".format(blockerShare)}
            |  offplan share (wizard-placed non-land, P4): min=${offplan.first()} median=${percentileF(offplan, 0.5)} max=${offplan.last()}
            |  gap sections (current < min): avg count=${"%.2f".format(avgGapSections)} avg total missing=${"%.2f".format(avgMissing)}
            |  runtime ms (build + analyze): min=${runtimes.first()} median=${percentileL(runtimes, 0.5)} max=${runtimes.last()}
            """.trimMargin()
        )
    }

    private suspend fun build(
        useCase: BuildDeckFromTemplateUseCase,
        spec: DeckWizardSpec,
        collection: List<UserCardWithCard>,
    ): TemplateBuildResult? {
        var result: TemplateBuildResult? = null
        useCase(spec, collection).collect { progress ->
            if (progress is TemplateBuildProgress.Complete) result = progress.result
        }
        return result
    }
}
