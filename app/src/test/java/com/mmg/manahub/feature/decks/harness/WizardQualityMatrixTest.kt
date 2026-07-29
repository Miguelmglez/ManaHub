package com.mmg.manahub.feature.decks.harness

import com.mmg.manahub.core.model.DeckFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Wizard Quality Campaign, Phase H -- the real-collection build/evaluate matrix harness.
 *
 * NOT part of any default test run: `feedback_targeted_testing` already forbids running the full
 * suite by default in this project, and this class is additionally guarded by an [Assume] skip
 * when the gitignored `testdata/wizard-harness/` fixtures are absent (a fresh checkout, CI, or any
 * machine without the real-collection download). Invoke explicitly, one segment at a time or all
 * three together:
 *
 * ```
 * ./gradlew :app:testDebugUnitTest --tests "com.mmg.manahub.feature.decks.harness.WizardQualityMatrixTest"
 * ./gradlew :app:testDebugUnitTest --tests "com.mmg.manahub.feature.decks.harness.WizardQualityMatrixTest.runCasualSegment"
 * ./gradlew :app:testDebugUnitTest --tests "com.mmg.manahub.feature.decks.harness.WizardQualityMatrixTest.runCommanderSegment"
 * ./gradlew :app:testDebugUnitTest --tests "com.mmg.manahub.feature.decks.harness.WizardQualityMatrixTest.runSeedsSegment"
 * ./gradlew :app:testDebugUnitTest --tests "com.mmg.manahub.feature.decks.harness.WizardQualityMatrixTest.runEntryFlowsSegment"
 * ```
 *
 * Pass `-Dharness.runLabel=wave1` (default `"baseline"`) to tag a run -- reports land under
 * `testdata/wizard-harness/reports/<timestamp>-<runLabel>-<segment>/`.
 *
 * These are plain JUnit assertions (`assertTrue(..., "worst offenders: ...")`) rather than the
 * usual per-behavior test style: this class's job is to produce the report, and a HARD-metric
 * regression should fail loudly (not just get silently buried in a report file nobody reads).
 */
class WizardQualityMatrixTest {

    private val harnessDir: File? = FixtureLoader.locateHarnessDir()
    private val runLabel: String = System.getProperty("harness.runLabel", "baseline")

    @Before
    fun assumeFixturesPresent() {
        Assume.assumeTrue(
            "wizard-harness fixtures not found under testdata/wizard-harness/ (gitignored -- skipping on this machine/CI)",
            harnessDir != null,
        )
    }

    @Test
    fun runCasualSegment() = runBlocking {
        val dir = harnessDir!!
        val fixtures = FixtureLoader.load(dir)
        val specs = HarnessSpecs.casualMatrix()
        val metrics = MatrixRunner.run(specs, fixtures)
        HarnessReportWriter.write(dir, runId("casual"), runLabel, metrics)
        reportSummaryOnFailure(metrics)

        // Deck Engine Unification RUN 7a (plan §5 Phase 6.1 -- "gap-report specs"): `casual_colorless`
        // is a deliberately thin spec (near-zero owned colorless nonland cards) that D3 ("gaps beat
        // weak fills") REQUIRES to declare a structured DeckGap rather than silently returning a
        // short/weak-filled deck. This asserts that requirement explicitly and by name, rather than
        // relying only on the aggregate HARD-metric pass count to have happened to catch a regression.
        val gapSpec = metrics.firstOrNull { it.label == "casual_colorless" }
        assertTrue("expected the casual_colorless spec to be present in the casual matrix", gapSpec != null)
        assertTrue(
            "casual_colorless must produce a declared gap (D3: thin collections leave gaps, never a " +
                "silently-short deck) -- got shortfall=${gapSpec?.shortfall}",
            (gapSpec?.shortfall ?: 0) > 0,
        )
        assertTrue(
            "casual_colorless's declared gap must still satisfy the HARD size metric " +
                "(cards + gaps == target) -- got actual=${gapSpec?.actualFullSize} target=${gapSpec?.targetFullSize} " +
                "shortfall=${gapSpec?.shortfall}",
            gapSpec?.sizeOk == true,
        )
        assertRoundTripInvariant(metrics)
    }

    @Test
    fun runCommanderSegment() = runBlocking {
        val dir = harnessDir!!
        val fixtures = FixtureLoader.load(dir)
        val specs = HarnessSpecs.commanderMatrix(fixtures)
        val metrics = MatrixRunner.run(specs, fixtures)
        HarnessReportWriter.write(dir, runId("commander"), runLabel, metrics)
        reportSummaryOnFailure(metrics)

        // Deck Engine Unification RUN 7a (plan §7 acceptance criterion 4 -- "Commander decks contain
        // their commander"): explicit, named assertion over EVERY Commander-format spec, not just the
        // folded-in commanderPresentOk contribution to allHardMetricsPass -- a BUG-1 regression here
        // should fail loudly and name the offending specs, not get buried in an aggregate count.
        val commanderSpecs = metrics.filter { it.format == DeckFormat.COMMANDER && !it.buildFailed }
        assertTrue("expected at least one successful Commander-format spec", commanderSpecs.isNotEmpty())
        val missingCommander = commanderSpecs.filterNot { it.commanderPresentOk }.map { it.label }
        assertTrue(
            "BUG-1 regression: commander missing/duplicated in the built deck for: $missingCommander",
            missingCommander.isEmpty(),
        )
        assertRoundTripInvariant(metrics)
    }

    @Test
    fun runSeedsSegment() = runBlocking {
        val dir = harnessDir!!
        val fixtures = FixtureLoader.load(dir)
        val specs = HarnessSpecs.seedsMatrix(fixtures)
        val metrics = MatrixRunner.run(specs, fixtures)
        HarnessReportWriter.write(dir, runId("seeds"), runLabel, metrics)
        reportSummaryOnFailure(metrics)
        assertRoundTripInvariant(metrics)
    }

    /** Deck Engine Unification RUN 7a (plan §5 Phase 6.1 -- "3-flow coverage"): drives the ACTUAL
     * RUN 3 wizard entry-path use cases (SuggestStrategiesForSeedsUseCase / ColorStrategyAffinity /
     * RankOwnedCardsForProfileUseCase) through to a built deck, not just a hand-built StrategyProfile
     * fed straight into the build call like every other segment's specs. See [HarnessSpecs
     * .entryFlowMatrix]'s KDoc for the exact per-flow mapping. */
    @Test
    fun runEntryFlowsSegment() = runBlocking {
        val dir = harnessDir!!
        val fixtures = FixtureLoader.load(dir)
        val specs = HarnessSpecs.entryFlowMatrix(fixtures)
        val metrics = MatrixRunner.run(specs, fixtures)
        HarnessReportWriter.write(dir, runId("entryflows"), runLabel, metrics)
        reportSummaryOnFailure(metrics)

        assertTrue("expected exactly 3 entry-flow specs (Flow A/B/C)", metrics.size == 3)
        assertRoundTripInvariant(metrics)
    }

    private fun runId(segment: String): String = "${System.currentTimeMillis()}-$runLabel-$segment"

    /**
     * Deck Wizard & Engine Rework plan Workstream 8.1 -- the single most important assertion in
     * this whole campaign. Named and explicit (not just folded into [BuildMetrics
     * .allHardMetricsPass]'s aggregate count) so a regression here fails loudly and names the
     * offending specs + cards, exactly like the BUG-1 commander check above: a wizard-placed card
     * ranking as a top UNLOCKED cut under its own build-time profile is a BASIS DIVERGENCE bug by
     * definition (pin folding / cached seedTags / trim reordering — see the plan's own triage
     * rule), never a "the wizard's choice just wasn't optimal" case to shrug off.
     */
    private fun assertRoundTripInvariant(metrics: List<BuildMetrics>) {
        val violations = metrics.filter { !it.buildFailed && !it.roundTripUnlockedOk }
        assertTrue(
            "WS8.1 round-trip invariant (UNLOCKED): a wizard-placed card cleared the fit floor at " +
                "build time yet ranks as a top cut under the SAME profile with no structural " +
                "protection -- this is a basis-divergence bug, fix the mechanism, never the ranking. " +
                "Offending specs: ${violations.map { it.label to it.roundTripUnlockedViolationNames }}",
            violations.isEmpty(),
        )
    }

    /** Prints (never fails the run -- Wave 1 is EXPECTED to still show failures) a one-line summary
     * to stdout so `--tests` output is legible without opening the JSON. */
    private fun reportSummaryOnFailure(metrics: List<BuildMetrics>) {
        val passed = metrics.count { it.allHardMetricsPass }
        println("[wizard-harness] ${metrics.size} specs, $passed passed all HARD metrics, ${metrics.size - passed} failed.")
        metrics.filterNot { it.allHardMetricsPass }.forEach { m ->
            println(
                "[wizard-harness]   FAIL ${m.label}: failed=${m.buildFailed} size=${m.sizeOk} legal=${m.legalityOk} " +
                    "determinism=${m.determinismOk} lands=${m.landsOk} cutsV2=${m.coherenceCutsV2Ok} " +
                    "roundTripUnlocked=${m.roundTripUnlockedOk} swaps=${m.coherenceSwapsOk} " +
                    "adds=${m.coherenceAddsOk} commander=${m.commanderPresentOk}"
            )
        }
    }
}
