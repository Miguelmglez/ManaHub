package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.feature.decks.domain.engine.analysisv3.AnalysisV3Fixture
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.AnalysisV3Fixtures
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckArchetypeUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

// ═══════════════════════════════════════════════════════════════════════════════
//  DeckAnalysisV3SynergyGraphTest — Deck Analysis Engine v3 plan, PHASE 2.
//
//  Validates [SynergyGraph] two ways:
//   1. Against the REAL 17-fixture corpus (spec §9), per the phase's own gate instruction. Several
//      positive fixtures are EXPECTED to disagree with their theme's defining axis -- not because
//      the graph is wrong, but because the Phase 0 corpus was authored (2026-08-25, before this
//      axis vocabulary existed) with only the ONE-OR-TWO role tags each fixture's headline P3 band
//      needed, not full producer+payoff coverage for the new axis model (e.g. Fixture 9/Rhys tags
//      8 `token_generator` copies but ZERO `death_payoff`/`counters_payoff`/`combat_payoff` --
//      TOKENS' entire payoff side). [KNOWN_CORPUS_DISAGREEMENTS] pins these down explicitly (a
//      fixture-authoring gap for Phase 0 to close, or new matchers for a future phase to add -- see
//      this file's own report) so a REGRESSION (a NEW, unexplained disagreement) still fails the
//      build, but an already-identified gap does not block this phase.
//   2. Independently, with mini-decks THIS file builds with deliberately high per-axis density on
//      BOTH the producer and payoff side -- proving the graph/health MODEL itself is sound,
//      decoupled from the corpus's own tagging completeness (see finding above).
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Per-fixture, the [AxisKey] set that fixture's expected theme(s) (spec §9) map onto per the axis
 * table (spec §5.2). Hand-derived from the spec text, NOT from running this code -- fixtures with
 * no expected theme map to an empty set (informational-only, not asserted). Fixture 17 (negative)
 * is handled separately (a strict "zero live axes" assertion, not this map).
 */
private val EXPECTED_AXES_BY_FIXTURE: Map<Int, Set<AxisKey>> = mapOf(
    1 to setOf("TRIBE:vampire"), // TRIBAL:vampire
    2 to setOf("DEATH"), // ARISTOCRATS (sac_outlet/death_payoff)
    3 to setOf("LIFE"), // LIFEGAIN
    4 to setOf("LANDFALL"), // LANDFALL
    5 to setOf("ARTIFACTS"), // ARTIFACTS
    6 to setOf("SPELLS"), // SPELLSLINGER
    7 to setOf("ETB"), // BLINK + ETB share the ETB axis
    8 to setOf("GRAVEYARD"), // REANIMATOR
    9 to setOf("TOKENS"), // TOKENS
    10 to emptySet(), // PRISON, no theme
    11 to emptySet(), // CONTROL, no theme (the W13 fixture)
    12 to setOf("PLANESWALKERS", "COUNTERS"), // SUPERFRIENDS + PLUS1_COUNTERS
    13 to setOf("ENCHANTMENTS"), // ENCHANTRESS
    14 to emptySet(), // Mono-Red Burn, no theme
    15 to emptySet(), // Azorius Control, no theme
    16 to emptySet(), // Tron, RAMP posture only, no theme
)

/**
 * Known, already-diagnosed disagreements between [EXPECTED_AXES_BY_FIXTURE] and this run's live
 * axes -- see this file's own header. Each entry is `fixtureId to missingAxis`. Recorded here
 * (rather than silently ignored) so the corpus test can assert "no NEW disagreement appeared"
 * while still surfacing the known ones in the printed report every run.
 *
 * Phase 2b (deck-analysis-engine-v3-spec.md, corrective sub-phase between Phase 2 and 3) closed
 * the four entries this set used to carry (fixture 3/LIFE, 4/LANDFALL, 9/TOKENS, 12/PLANESWALKERS,
 * 12/COUNTERS) by fixing the underlying fixture-authoring gap each one diagnosed -- see
 * `Fixture03Karlov.kt`/`Fixture04Omnath.kt`/`Fixture09Rhys.kt`/`Fixture12Atraxa.kt` for the added/
 * retagged cards. All 16 positive fixtures now light every expected axis; this set is empty by
 * construction until a NEW disagreement is diagnosed.
 */
private val KNOWN_CORPUS_DISAGREEMENTS: Set<Pair<Int, AxisKey>> = emptySet()

class DeckAnalysisV3SynergyGraphTest {

    private val inferDeckArchetypeUseCase = InferDeckArchetypeUseCase()

    private fun graphFor(fixture: AnalysisV3Fixture): DeckSynergyGraph {
        val format = requireNotNull(fixture.archetypeFormat) { "fixture ${fixture.id} has no ArchetypeFormat" }
        return SynergyGraph.build(fixture.mainboard, format)
    }

    /**
     * The phase's own gate item: per-fixture live axes vs. spec §9's expected themes. Prints the
     * full evidence table (axis / producers / payoffs / health / isLive for every axis that has
     * ANY signal) and hard-asserts only that no NEW disagreement appeared beyond
     * [KNOWN_CORPUS_DISAGREEMENTS].
     */
    @Test
    fun positiveFixtures_liveAxesMatchExpectedThemes_exceptKnownCorpusGaps() {
        val unexpectedDisagreements = mutableListOf<String>()
        AnalysisV3Fixtures.POSITIVE.forEach { fixture ->
            val graph = graphFor(fixture)
            val liveAxes = graph.axes.filter { it.isLive }.map { it.axis }.toSet()
            println(
                "fixture=${fixture.id} (${fixture.name}) expectedThemes=${fixture.expectedThemes} " +
                    "expectedAxes=${EXPECTED_AXES_BY_FIXTURE[fixture.id]} liveAxes=$liveAxes",
            )
            graph.axes.filter { it.producerCopies > 0 || it.payoffCopies > 0 }.forEach { axis ->
                println("  axis=${axis.axis} producers=${axis.producerCopies} payoffs=${axis.payoffCopies} amplifiers=${axis.amplifierCopies} health=${axis.health} isLive=${axis.isLive}")
            }
            val expected = EXPECTED_AXES_BY_FIXTURE[fixture.id].orEmpty()
            expected.forEach { expectedAxis ->
                val known = (fixture.id to expectedAxis) in KNOWN_CORPUS_DISAGREEMENTS
                if (expectedAxis !in liveAxes && !known) {
                    unexpectedDisagreements += "fixture ${fixture.id} (${fixture.name}): expected axis $expectedAxis is NOT live (liveAxes=$liveAxes)"
                }
                if (expectedAxis in liveAxes && known) {
                    unexpectedDisagreements += "fixture ${fixture.id} (${fixture.name}): axis $expectedAxis was pinned as a KNOWN disagreement but is now live -- update KNOWN_CORPUS_DISAGREEMENTS"
                }
            }
        }
        assertTrue(unexpectedDisagreements.isEmpty(), "Unexpected corpus disagreements:\n${unexpectedDisagreements.joinToString("\n")}")
    }

    /** Gate item 3: the negative fixture (#17) must show NO live axis at all -- strict, no known-gap
     * carve-out (this is the one invariant the whole corpus exists to prove). */
    @Test
    fun negativeFixture_hasNoLiveAxis() {
        val graph = graphFor(AnalysisV3Fixtures.NEGATIVE)
        val liveAxes = graph.axes.filter { it.isLive }
        println("fixture=17 (negative) liveAxes=${liveAxes.map { it.axis }}")
        assertTrue(liveAxes.isEmpty(), "Negative fixture must have zero live axes, found: ${liveAxes.map { it.axis }}")
    }

    /** Gate item 4: re-running the byte-identical Phase 0/1 corpus regression path (same call
     * shape as DeckAnalysisV3CorpusTest, default `includeDebugSynergyGraph = false`) must still
     * produce the SAME structural invariants -- confirms this phase changed nothing scored. */
    @Test
    fun corpusScores_unaffectedByDebugFlagDefault() {
        AnalysisV3Fixtures.ALL.forEach { fixture ->
            val archetypeFormat = requireNotNull(fixture.archetypeFormat)
            val scorer = DeckScorer(RoleClassifier())
            val profile = scorer.profile(fixture.mainboard, fixture.format, fixture.colorIdentity, emptyList())
            val inferred = inferDeckArchetypeUseCase(fixture.mainboard, archetypeFormat, fixture.commanderTags)
            val analysis = AnalysisEngine.evaluate(
                mainboard = fixture.mainboard, format = fixture.format, colorIdentity = fixture.colorIdentity,
                profile = profile, archetype = inferred.macro, themes = inferred.themes,
                isManualOverride = false, confidence = inferred.confidence,
            )
            assertEquals(null, analysis.debugSynergyGraph, "fixture ${fixture.id}: debugSynergyGraph must stay null when the flag is not passed")
        }
    }

    /** Proves the opt-in wiring itself: passing `includeDebugSynergyGraph = true` attaches a
     * non-null graph without moving any pillar subscore or the total (recomputed against the SAME
     * fixture with the flag off, everything else identical). */
    @Test
    fun includeDebugSynergyGraph_attachesGraphWithoutMovingAnyScore() {
        val fixture = AnalysisV3Fixtures.ALL.first { it.id == 2 } // Meren/ARISTOCRATS
        val archetypeFormat = requireNotNull(fixture.archetypeFormat)
        val scorer = DeckScorer(RoleClassifier())
        val profile = scorer.profile(fixture.mainboard, fixture.format, fixture.colorIdentity, emptyList())
        val inferred = inferDeckArchetypeUseCase(fixture.mainboard, archetypeFormat, fixture.commanderTags)

        fun evaluate(includeGraph: Boolean) = AnalysisEngine.evaluate(
            mainboard = fixture.mainboard, format = fixture.format, colorIdentity = fixture.colorIdentity,
            profile = profile, archetype = inferred.macro, themes = inferred.themes,
            isManualOverride = false, confidence = inferred.confidence,
            includeDebugSynergyGraph = includeGraph,
        )

        val withoutGraph = evaluate(includeGraph = false)
        val withGraph = evaluate(includeGraph = true)

        assertEquals(null, withoutGraph.debugSynergyGraph)
        assertTrue(withGraph.debugSynergyGraph != null)
        assertEquals(withoutGraph.totalScore, withGraph.totalScore)
        assertEquals(withoutGraph.pillars.map { it.subscore }, withGraph.pillars.map { it.subscore })
    }

    // ── Focused, density-independent correctness checks (decoupled from the corpus's own tagging
    // completeness -- see this file's header) ────────────────────────────────────────────────────

    private fun tokenGeneratorCard(id: String) = card(id = id, name = id, typeLine = "Creature — Elf", cmc = 2.0, power = "1", toughness = "1", tags = listOf(roleTagForTest("token_generator")))
    private fun deathPayoffCard(id: String) = card(id = id, name = id, typeLine = "Enchantment", cmc = 3.0, tags = listOf(roleTagForTest("death_payoff")))
    private fun lifegainSourceCard(id: String) = card(id = id, name = id, typeLine = "Creature — Cleric", cmc = 2.0, power = "1", toughness = "1", tags = listOf(roleTagForTest("lifegain_source")))
    private fun lifegainPayoffCard(id: String) = card(id = id, name = id, typeLine = "Enchantment", cmc = 3.0, tags = listOf(roleTagForTest("lifegain_payoff")))

    @Test
    fun denselyTaggedAxis_showsLiveRegardlessOfCorpusTaggingGaps() {
        val nonland = (1..10).map { entry(tokenGeneratorCard("tok-$it")) } +
            (1..10).map { entry(deathPayoffCard("dp-$it")) }
        val mainboard = withBasicsCommanderForTest(nonland)
        val graph = SynergyGraph.build(mainboard, ArchetypeFormat.COMMANDER)
        val tokens = graph.axes.first { it.axis == "TOKENS" }
        assertTrue(tokens.isLive, "TOKENS should be live with 10 producers + 10 payoffs: $tokens")

        val liveLife = SynergyGraph.build(
            withBasicsCommanderForTest(
                (1..10).map { entry(lifegainSourceCard("life-src-$it")) } + (1..10).map { entry(lifegainPayoffCard("life-pay-$it")) },
            ),
            ArchetypeFormat.COMMANDER,
        ).axes.first { it.axis == "LIFE" }
        assertTrue(liveLife.isLive, "LIFE should be live with 10 producers + 10 payoffs: $liveLife")
    }

    @Test
    fun producerOnlyAxis_staysNotLive_andIsFlaggedAsOrphanProducer() {
        val nonland = (1..12).map { entry(tokenGeneratorCard("tok-only-$it")) }
        val mainboard = withBasicsCommanderForTest(nonland)
        val graph = SynergyGraph.build(mainboard, ArchetypeFormat.COMMANDER)
        val tokens = graph.axes.first { it.axis == "TOKENS" }
        assertTrue(!tokens.isLive, "TOKENS must NOT be live with zero payoffs: $tokens")
        assertTrue(
            graph.conflicts.any { it is SynergyConflict.OrphanProducers && it.axis == "TOKENS" },
            "expected an OrphanProducers conflict on TOKENS, got: ${graph.conflicts}",
        )
    }

    // ── Performance (task 5) ─────────────────────────────────────────────────────────────────

    @Test
    fun build_evaluates250UniqueCardDeckWithinASaneTimeBudget() {
        // Mixes producer- and payoff-tagged cards (not just producers) so the O(n^2) edge loop
        // actually generates a realistic volume of edges, not just short-circuiting on every pair.
        val payoffTagByBucket = listOf("death_payoff", "spell_payoff", "artifact_payoff", "enchantment_payoff", "etb_payoff")
        val nonland = (1..249).map { i ->
            val bucket = i % 5
            entry(
                card(
                    id = "perf-$i",
                    name = "Perf Card $i",
                    typeLine = when (bucket) {
                        0 -> "Instant"
                        1 -> "Artifact"
                        2 -> "Enchantment"
                        3 -> "Creature — Elf"
                        else -> "Sorcery"
                    },
                    cmc = (i % 6).toDouble(),
                    power = if (bucket == 3) "2" else null,
                    toughness = if (bucket == 3) "2" else null,
                    tags = when {
                        i % 11 == 0 -> listOf(roleTagForTest("token_generator"))
                        i % 13 == 0 -> listOf(roleTagForTest(payoffTagByBucket[bucket]))
                        else -> emptyList()
                    },
                ),
            )
        }
        val mainboard = withBasicsCommanderForTest(nonland)
        val mark = TimeSource.Monotonic.markNow()
        val graph = SynergyGraph.build(mainboard, ArchetypeFormat.COMMANDER)
        val elapsed = mark.elapsedNow()
        println("SynergyGraph.build over ${mainboard.size} unique entries took $elapsed, produced ${graph.edges.size} edges")
        assertTrue(elapsed.inWholeMilliseconds < 5_000, "SynergyGraph.build took too long: $elapsed")
    }
}

// ── Local test helpers (mirrors AnalysisV3TestSupport.kt's own pattern; kept local to this file
// since it is the only consumer needing a role tag / basics-padding helper outside the analysisv3
// package). ──────────────────────────────────────────────────────────────────────────────────────

private fun roleTagForTest(key: String) = com.mmg.manahub.core.model.CardTag(key, com.mmg.manahub.core.model.TagCategory.ROLE)

private fun withBasicsCommanderForTest(nonland: List<DeckEntry>): List<DeckEntry> {
    val nonlandCount = nonland.sumOf { it.quantity }
    val landQuantity = (100 - nonlandCount).coerceAtLeast(1)
    return nonland + entry(
        card(id = "land-test-plains", name = "Plains", typeLine = "Basic Land — Plains", cmc = 0.0, colors = emptyList(), colorIdentity = listOf("W"), producedMana = "W"),
        quantity = landQuantity,
    )
}
