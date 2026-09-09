package com.mmg.manahub.feature.decks.harness
// COMMENTS_REVIEWED: 2026-09-09

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Wizard Commander v3 plan, Phase 7.1 — harness v2, real-collection segment.
//
//  The Commander-only successor to the legacy WizardQualityMatrixTest.runCommanderSegment (F12:
//  that class scored the RETIRED Motor A/DeckScorer engine). This class drives EVERY eligible owned
//  commander through BuildCommanderDeckUseCase (the NEW engine, D1) twice -- top recommendation and
//  Custom -- and asserts the plan §5 HARD metric set via HarnessMetricsV2Calculator.
//
//  Casual's own WizardQualityMatrixTest segments (casual/seeds/entryflow) are DELIBERATELY left
//  untouched: this campaign's scope is Commander/Commander Casual only (see the plan's own "Non-goals"
//  -- Motor A/DeckScorer retirement is explicitly out of scope, Casual still builds through it). The
//  plan's 7.1 literally reads "legacy metrics deleted along with HarnessDoctorPipeline's legacy half"
//  -- doing that literally would also delete the ONLY automated coverage the Casual wizard path has
//  today, which this campaign does not touch. Kept, not deleted; this is the new Commander harness,
//  additive alongside it. See docs/plans/deck-wizard-commander-progress.md's Phase 7 run log for the
//  full rationale.
//
//  ./gradlew :app:testDebugUnitTest --tests "com.mmg.manahub.feature.decks.harness.WizardCommanderHarnessV2Test"
// ═══════════════════════════════════════════════════════════════════════════════

class WizardCommanderHarnessV2Test {

    private val harnessDir = FixtureLoader.locateHarnessDir()
    private val runLabel: String = System.getProperty("harness.runLabel", "v2")

    @Before
    fun assumeFixturesPresent() {
        Assume.assumeTrue(
            "wizard-harness fixtures not found under testdata/wizard-harness/ (gitignored -- skipping on this machine/CI)",
            harnessDir != null,
        )
    }

    @Test
    fun runRealCollectionSegment() = runBlocking {
        val dir = harnessDir!!
        val fixtures = FixtureLoader.load(dir)
        val owned = CommanderMatrixV2.ownedPool(fixtures)
        val specs = CommanderMatrixV2.specs(fixtures)

        val metrics = specs.map { spec -> CommanderMatrixV2.run(spec, owned) }
        val runId = "${System.currentTimeMillis()}-$runLabel-commander-v2"
        HarnessReportWriterV2.write(dir, runId, runLabel, "real_collection", metrics)
        printSummary(metrics)

        assertHardMetrics(metrics)
    }

    private fun printSummary(metrics: List<V2BuildMetrics>) {
        val passed = metrics.count { it.allHardMetricsPass }
        println("[wizard-harness-v2] ${metrics.size} specs, $passed passed all HARD metrics, ${metrics.size - passed} failed.")
        val nonFailed = metrics.filterNot { it.buildFailed }
        if (nonFailed.isNotEmpty()) {
            val scores = nonFailed.map { it.totalScore }.sorted()
            fun pct(p: Double) = scores[(p * (scores.size - 1)).toInt().coerceIn(0, scores.size - 1)]
            println(
                "[wizard-harness-v2] score min=${scores.first()} p25=${pct(0.25)} median=${pct(0.5)} p75=${pct(0.75)} max=${scores.last()}",
            )
            val runtimes = nonFailed.map { it.runtimeMs }.sorted()
            println("[wizard-harness-v2] runtime ms min=${runtimes.first()} median=${runtimes[runtimes.size / 2]} max=${runtimes.last()}")
        }
        metrics.filterNot { it.allHardMetricsPass }.take(30).forEach { m ->
            println(
                "[wizard-harness-v2]   FAIL ${m.label}: failed=${m.buildFailed} blocker=${!m.noBlockerOk} " +
                    "size_or_gaps=${!m.sizeOrGapsOk} determinism=${!m.determinismOk} commander_once=${!m.commanderOnceOk} " +
                    "manual_adds=${!m.manualAddsKeptOk} legality_identity=${!m.legalityIdentityOk} " +
                    "anti_role=${m.antiRoleViolations} offplan=${"%.2f".format(m.offplanShare)} " +
                    "round_trip=${!m.roundTripIdentityOk} mana_sources=${m.manaSourcesViolations} land_target=${!m.landTargetOk}(${m.landCount})",
            )
        }
    }

    private fun assertHardMetrics(metrics: List<V2BuildMetrics>) {
        val nonFailed = metrics.filterNot { it.buildFailed }
        assertTrue("expected at least one successful Commander v2 build", nonFailed.isNotEmpty())

        fun failing(name: String, predicate: (V2BuildMetrics) -> Boolean) {
            val offenders = nonFailed.filterNot(predicate).map { it.label }
            assertTrue("HARD metric '$name' failed for: $offenders", offenders.isEmpty())
        }

        val failedBuilds = metrics.filter { it.buildFailed }
        assertTrue("builds must not throw: ${failedBuilds.map { it.label to it.failureMessage }}", failedBuilds.isEmpty())

        failing("no_blocker") { it.noBlockerOk }
        failing("size_or_gaps") { it.sizeOrGapsOk }
        failing("determinism") { it.determinismOk }
        failing("commander_once") { it.commanderOnceOk }
        failing("manual_adds_kept") { it.manualAddsKeptOk }
        failing("legality_identity") { it.legalityIdentityOk }
        failing("no_engine_anti_role") { it.noEngineAntiRoleOk }
        failing("offplan_share") { it.offplanShareOk }
        failing("round_trip_identity") { it.roundTripIdentityOk }
        failing("mana_sources") { it.manaSourcesOk }
        failing("land_target") { it.landTargetOk }
    }
}
