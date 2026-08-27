package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.AnalysisV3Fixture
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.AnalysisV3Fixtures
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.NO_POSTURE
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.NO_THEMES
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckArchetypeUseCase
import com.mmg.manahub.feature.decks.domain.usecase.MacroResemblance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ═══════════════════════════════════════════════════════════════════════════════
//  DeckAnalysisV3CorpusTest — Deck Analysis Engine v3 plan, PHASE 0 (harness) → PHASE 3
//  (flipped into ASSERTION MODE, per the plan's own instruction: "REPLACE the PHASE0_BASELINE
//  comparison with assertions against each fixture's expectedMacro/expectedThemes").
//
//  Phase 0-2 snapshotted the CURRENT (pre-v3) engine's byte-identical output against a hardcoded
//  PHASE0_BASELINE map — that map and its byte-identical-diff assertions are GONE now that Phase 3
//  is the intended behavioral change. This file now asserts each fixture's LIVE output against the
//  spec §9 table's own documented correctness values (parsed from each fixture's own
//  expectedMacro/expectedPosture/expectedThemes free-text fields, authored in Phase 0).
//
//  Acceptance criterion (spec §9 / plan §4 gate): correct macro, correct themes (SET equality), and
//  total score in 65-95 for every positive fixture; the negative fixture (id 17) must resolve
//  ambiguous (`macro == null`) and score below 55. Full acceptance is NOT expected until Phase 3b
//  lands the P3 formula fixes (spec §6) and archetype-dependent weights (spec §8) — see this
//  phase's own memory/report for exactly which gaps are expected to close there.
// ═══════════════════════════════════════════════════════════════════════════════

/** One fixture's captured engine output. */
data class FixtureSnapshot(
    val macro: ArchetypeId?,
    val posture: PostureId?,
    val themes: List<ThemeId>,
    val confidence: Float,
    val p1ManaBase: Int,
    val p2Curve: Int,
    val p3PlanRoles: Int,
    val p4Synergy: Int,
    val p5Legality: Int,
    val total: Int,
    val findingsCount: Int,
    /** Commander-prior workstream (2026-08-26) -- the full ranked resemblance profile (see
     * [com.mmg.manahub.feature.decks.domain.usecase.ArchetypeInference.resemblance]'s KDoc). */
    val resemblance: List<MacroResemblance>,
)

/** Renders a [FixtureSnapshot.resemblance] list as `"70% Aggro · 30% Midrange"`-style text for the
 * gate report's per-fixture table. */
private fun List<MacroResemblance>.render(): String =
    joinToString(" · ") { "${(it.share * 100).toInt()}% ${it.macro.displayName}" }

/** Parses a fixture's `expectedMacro` free-text field (spec §9's table, transcribed verbatim in
 * Phase 0) into a real [ArchetypeId]. `"ambiguous / Custom"` (the negative fixture's own expected
 * value) and any other unparseable string both mean "expected null" (no confident macro). */
private fun parseExpectedMacro(raw: String): ArchetypeId? = ArchetypeId.entries.firstOrNull { it.name == raw }

/** Parses a fixture's `expectedPosture` free-text field into a real [PostureId], or `null` for
 * [NO_POSTURE] ("—"). */
private fun parseExpectedPosture(raw: String): PostureId? =
    if (raw == NO_POSTURE) null else PostureId.entries.firstOrNull { it.name == raw }

/** Parses a fixture's `expectedThemes` free-text field into a [ThemeId] set. `"TRIBAL:vampire"`-
 * style entries (spec §9's own free-text convention for a tribal theme with its dominant subtype)
 * collapse to plain [ThemeId.TRIBAL] -- the subtype itself is not asserted here (no enum member
 * could represent it; the fixture's own dominant-tribe detection is exercised elsewhere). */
private fun parseExpectedThemes(raw: String): Set<ThemeId> {
    if (raw == NO_THEMES) return emptySet()
    return raw.split(",").map { it.trim() }.mapNotNull { token ->
        val name = token.substringBefore(":")
        ThemeId.entries.firstOrNull { it.name == name }
    }.toSet()
}

class DeckAnalysisV3CorpusTest {

    private val scorer = DeckScorer(RoleClassifier())
    private val inferDeckArchetypeUseCase = InferDeckArchetypeUseCase()

    private fun snapshotFor(fixture: AnalysisV3Fixture): FixtureSnapshot {
        val archetypeFormat = requireNotNull(fixture.archetypeFormat) {
            "Fixture ${fixture.id} (${fixture.name}) resolved to a null ArchetypeFormat -- DRAFT is never used by this corpus."
        }
        val profile = scorer.profile(
            mainboard = fixture.mainboard,
            format = fixture.format,
            colorIdentity = fixture.colorIdentity,
            seedTags = emptyList(),
        )
        val inferred = inferDeckArchetypeUseCase(fixture.mainboard, archetypeFormat, fixture.commanderTags, fixture.colorIdentity)
        val analysis = AnalysisEngine.evaluate(
            mainboard = fixture.mainboard,
            format = fixture.format,
            colorIdentity = fixture.colorIdentity,
            profile = profile,
            archetype = inferred.macro,
            posture = inferred.posture,
            themes = inferred.themes,
            isManualOverride = false,
            confidence = inferred.confidence,
        )
        fun subscore(id: PillarId) = analysis.pillars.first { it.id == id }.subscore
        return FixtureSnapshot(
            macro = inferred.macro,
            posture = inferred.posture,
            themes = inferred.themes,
            confidence = inferred.confidence,
            p1ManaBase = subscore(PillarId.MANA_BASE),
            p2Curve = subscore(PillarId.CURVE),
            p3PlanRoles = subscore(PillarId.PLAN_ROLES),
            p4Synergy = subscore(PillarId.SYNERGY),
            p5Legality = subscore(PillarId.LEGALITY),
            total = analysis.totalScore,
            findingsCount = analysis.pillars.sumOf { it.findings.size },
            resemblance = inferred.resemblance,
        )
    }

    /**
     * Sanity gate over every fixture -- structural invariants ONLY (5 pillars present, every
     * subscore/total in range). Deliberately NOT the correctness check -- see
     * [everyFixture_matchesSpecExpectations] for that.
     */
    @Test
    fun everyFixture_evaluatesStructurallyCleanly() {
        AnalysisV3Fixtures.ALL.forEach { fixture ->
            val archetypeFormat = requireNotNull(fixture.archetypeFormat)
            val profile = scorer.profile(fixture.mainboard, fixture.format, fixture.colorIdentity, emptyList())
            val inferred = inferDeckArchetypeUseCase(fixture.mainboard, archetypeFormat, fixture.commanderTags, fixture.colorIdentity)
            val analysis = AnalysisEngine.evaluate(
                mainboard = fixture.mainboard, format = fixture.format, colorIdentity = fixture.colorIdentity,
                profile = profile, archetype = inferred.macro, posture = inferred.posture, themes = inferred.themes,
                isManualOverride = false, confidence = inferred.confidence,
            )
            assertEquals(5, analysis.pillars.size, "fixture ${fixture.id} (${fixture.name})")
            assertTrue(analysis.totalScore in 0..100, "fixture ${fixture.id} (${fixture.name}) total out of range: ${analysis.totalScore}")
            analysis.pillars.forEach { pillar ->
                assertTrue(pillar.subscore in 0..100, "fixture ${fixture.id} pillar ${pillar.id} subscore out of range: ${pillar.subscore}")
            }
        }
    }

    /**
     * PHASE 3 assertion-mode gate (plan §4 "Run DeckAnalysisV3CorpusTest in assertion mode now").
     * Prints the full per-fixture evidence table (macro/posture/themes/confidence/subscores/total,
     * expected vs actual) to stdout for the phase report, then asserts macro + theme-set correctness
     * for every fixture.
     *
     * RE-BASELINED (Phase 3a-FIX run C, 2026-08-26) after two spec-amended engine fixes landed on
     * top of the fully density-expanded corpus (batches A/B): removing `counterspell` from the
     * `SPELLS` axis payoffs (spec §5.2 amendment) and decoupling posture detection from macro
     * confidence (spec §3 amendment). Every floor below is a MEASURED result of this run, not a
     * target tuned toward -- see this phase's own memory file for the full per-fixture table.
     * [AnalysisWeights]/P3's three formula bugs (spec §6) and archetype-dependent weights (spec §8)
     * are still Phase 3b's own work, so the negative fixture's score-band criterion (<55, spec §9)
     * and full 16/16 macro correctness are NOT expected yet and stay unasserted -- everything else
     * measurable today is now a hard floor so a future change cannot silently regress it.
     */
    @Test
    fun everyFixture_matchesSpecExpectations() {
        println("fixture_id,name,expected_macro,actual_macro,expected_posture,actual_posture,expected_themes,actual_themes,confidence,resemblance,P1,P2,P3,P4,P5,total,findings")
        // Split original-vs-new (60-card corpus expansion workstream, 2026-08-26): fixtures 1-17
        // are the ORIGINAL spec §9 corpus (its own hard floors below are UNCHANGED, re-verified
        // byte-identical this run); fixtures 18-22 are this workstream's own addition (its own
        // floors are freshly measured this run, not carried over from any prior phase). Keeping the
        // two counted separately is deliberate -- a single merged assertEquals(16, themesCorrect...)
        // would have broken the moment ANY new fixture (even a correct one) was added, and a single
        // merged >= floor would silently stop proving the ORIGINAL 16/17's own byte-identical
        // invariant (headroom from new correct fixtures could mask a real regression on the old set).
        var macroCorrectOriginal = 0
        var themesCorrectOriginal = 0
        var postureCorrectOriginal = 0
        var inBandCountOriginal = 0
        var macroCorrectNew = 0
        var themesCorrectNew = 0
        var postureCorrectNew = 0
        var inBandCountNew = 0
        val originalPositives = AnalysisV3Fixtures.ALL.filter { it.id in 1..16 }
        val newFixtures = AnalysisV3Fixtures.ALL.filter { it.id in NEW_FIXTURE_IDS }

        AnalysisV3Fixtures.ALL.forEach { fixture ->
            val snap = snapshotFor(fixture)
            val expectedMacro = parseExpectedMacro(fixture.expectedMacro)
            val expectedPosture = parseExpectedPosture(fixture.expectedPosture)
            val expectedThemes = parseExpectedThemes(fixture.expectedThemes)
            val actualThemeSet = snap.themes.toSet()

            println(
                "${fixture.id},${fixture.name},${fixture.expectedMacro},${snap.macro}," +
                    "${fixture.expectedPosture},${snap.posture},${fixture.expectedThemes},${actualThemeSet.joinToString("+")}," +
                    "${snap.confidence},${snap.resemblance.render()},${snap.p1ManaBase},${snap.p2Curve},${snap.p3PlanRoles}," +
                    "${snap.p4Synergy},${snap.p5Legality},${snap.total},${snap.findingsCount}",
            )

            // Posture correctness is measured over every fixture including the negative one --
            // unlike macro/themes, a "no posture expected, none detected" match is a real correct
            // result even for a fixture with no macro plan at all.
            val postureHit = snap.posture == expectedPosture
            when (fixture.id) {
                in 1..17 -> if (postureHit) postureCorrectOriginal++
                in NEW_FIXTURE_IDS -> if (postureHit) postureCorrectNew++
            }

            if (fixture.id != NEGATIVE_FIXTURE_ID) {
                val macroHit = snap.macro == expectedMacro
                val themesHit = actualThemeSet == expectedThemes
                val inBand = snap.total in 65..95
                when (fixture.id) {
                    in 1..16 -> {
                        if (macroHit) macroCorrectOriginal++
                        if (themesHit) themesCorrectOriginal++
                        if (inBand) inBandCountOriginal++
                    }
                    in NEW_FIXTURE_IDS -> {
                        if (macroHit) macroCorrectNew++
                        if (themesHit) themesCorrectNew++
                        if (inBand) inBandCountNew++
                    }
                }
            }
        }

        println(
            "ORIGINAL (1-16/17) -- macro correct: $macroCorrectOriginal/${originalPositives.size}; " +
                "themes correct: $themesCorrectOriginal/${originalPositives.size}; posture correct: $postureCorrectOriginal/17; " +
                "in-band (65-95): $inBandCountOriginal/${originalPositives.size}",
        )
        println(
            "NEW 60-CARD (18-22) -- macro correct: $macroCorrectNew/${newFixtures.size}; " +
                "themes correct: $themesCorrectNew/${newFixtures.size}; posture correct: $postureCorrectNew/${newFixtures.size}; " +
                "in-band (65-95): $inBandCountNew/${newFixtures.size}",
        )

        // Negative fixture (id 17): must read ambiguous, never a confident macro, and must NOT
        // score as a confident well-built deck. This is the one invariant this phase treats as a
        // hard failure -- everything else is reported, not gated, until Phase 3b lands.
        val negative = AnalysisV3Fixtures.ALL.first { it.id == NEGATIVE_FIXTURE_ID }
        val negativeSnap = snapshotFor(negative)
        assertEquals(null, negativeSnap.macro, "negative fixture must resolve ambiguous (macro == null), got ${negativeSnap.macro}")

        // ── ORIGINAL corpus floors (Phase 3a-CALIBRATION + commander-prior workstream,
        //    2026-08-26) — UNCHANGED, re-verified byte-identical this run (the resemblance-profile
        //    rescale touches only PRESENTATION of ArchetypeInference.resemblance, never macro/
        //    posture/themes/P1-P5/total; the 60-card corpus expansion adds fixtures, it does not
        //    touch the macro resolver, prototypes, or any existing fixture file). See
        //    `project_deck_analysis_v3_commander_prior_resemblance` memory for the full derivation
        //    of each number below -- not re-derived here.
        assertEquals(16, themesCorrectOriginal, "ORIGINAL theme-set correctness regressed below the full 16/16 the counterspell/SPELLS axis fix established")
        assertTrue(macroCorrectOriginal >= 6, "ORIGINAL macro correctness regressed below the commander-prior floor of 6/16 (fixtures 2/3/5/11/12/13)")
        assertTrue(postureCorrectOriginal >= 14, "ORIGINAL posture correctness regressed below the phase 3a-calibration floor of 14/17")
        assertTrue(inBandCountOriginal >= 13, "ORIGINAL in-band (65-95) count regressed below the 13/16 floor")

        // ── NEW 60-card floors (60-card-coverage-expansion workstream, 2026-08-26) — freshly
        //    measured this run, five real archetype-canonical Modern decklists (spec §9's own
        //    "long-stable Modern staples" guidance) covering the two macros the 60-card slice
        //    previously had ZERO witnesses for (COMBO: fixture 18 Whirza; PRISON: fixture 19 Death
        //    and Taxes) plus a second AGGRO/MIDRANGE/CONTROL witness each (fixtures 20/21/22).
        //    macro: 3/5 (18 Whirza COMBO, 20 Merfolk AGGRO, 21 Jund MIDRANGE all correct; 19 Death
        //    and Taxes resolves MIDRANGE not PRISON -- PRISON's resemblance share is only 4th of 5,
        //    18%, CORROBORATING the Commander-side finding that PRISON's own linearity prototype
        //    (`stax_piece` forms no producer/payoff axis edge) is structurally unreachable
        //    regardless of format, not a Commander-only artifact; 22 Grixis Control resolves
        //    ambiguous/null, not CONTROL -- margin over runner-up MIDRANGE is only .059, just under
        //    MACRO_AMBIGUITY_MARGIN's 0.08 -- see this run's own gate report for the full diagnosis
        //    of both misses; NOT fixed here, corpus is a regression harness, not a training set).
        //    themes: 5/5 -- fixture 18 correctly reads ARTIFACTS, fixture 20 correctly reads
        //    TRIBAL (TRIBE:merfolk lights exactly like TRIBE:vampire did in Commander, fixture 1),
        //    fixtures 19/21/22 correctly read no themes (none intended, none fabricated).
        //    posture: 5/5 -- fixture 22 correctly reads TEMPO (2 distinct counterspells x4 = 8 raw
        //    copies clears the spec §3 TEMPO_COUNTERSPELL_MIN of 6, and fires even though that same
        //    fixture's OWN macro is ambiguous -- the spec §3 AMENDMENT decoupling posture from macro
        //    confidence, already proven on the Commander side, holds at 60-card too); the other 4
        //    correctly read no posture.
        //    in-band: 5/5 -- every new fixture lands inside the spec §9 acceptance band (65-95).
        assertTrue(macroCorrectNew >= 3, "NEW 60-card macro correctness regressed below the measured floor of 3/5 (fixtures 18/20/21)")
        assertEquals(5, themesCorrectNew, "NEW 60-card theme-set correctness regressed below the measured floor of 5/5")
        assertEquals(5, postureCorrectNew, "NEW 60-card posture correctness regressed below the measured floor of 5/5")
        assertEquals(5, inBandCountNew, "NEW 60-card in-band (65-95) count regressed below the measured floor of 5/5")
    }

    /** Fixture-hygiene regression gate: every non-land entry's card color identity must be a
     * subset of its fixture's declared deck color identity. A violation here silently caps
     * [PillarId.LEGALITY] to 0 via [Finding.OffColorIdentity] for Commander fixtures, which would
     * corrupt the whole evidence table with an authoring mistake rather than a real engine
     * finding -- keep this asserting empty, not just printing, so a future edit cannot
     * reintroduce one unnoticed. */
    @Test
    fun everyFixture_hasNoColorIdentityViolations() {
        val violations = AnalysisV3Fixtures.ALL.flatMap { fixture ->
            val allowed = fixture.colorIdentity.map { it.symbol }.toSet()
            fixture.mainboard
                .filter { e -> !allowed.containsAll(e.card.colorIdentity.toSet()) }
                .map { e -> "fixture=${fixture.id} card=${e.card.name} cardIdentity=${e.card.colorIdentity} allowed=$allowed" }
        }
        assertTrue(violations.isEmpty(), "Off-identity cards found (would silently zero P5 LEGALITY): $violations")
    }

    /** Fixture-hygiene regression gate: no fixture may contain the SAME card `name` twice.
     * [evaluateLegality] groups by `card.name`, not `id`, so an intra-file duplicate silently
     * triggers [Finding.SingletonViolation] and zeroes P5 for a Commander fixture without any
     * other existing gate catching it -- Phase 3a-FIX batch A hit this twice during authoring
     * (Vindicate in fixture 3, Lightning Bolt in fixture 6) and it was only caught by a manual
     * `grep -o 'name = "[^"]*"' <file> | sort | uniq -d` sweep, not a test. This test makes that
     * sweep permanent so a future edit cannot reintroduce the mistake unnoticed. Reusing a card
     * NAME ACROSS different fixture files is fine (real decks share staples) -- only a duplicate
     * WITHIN one fixture's own mainboard is a legality bug. */
    @Test
    fun everyFixture_hasNoDuplicateCardNames() {
        val violations = AnalysisV3Fixtures.ALL.mapNotNull { fixture ->
            val duplicateNames = fixture.mainboard
                .groupingBy { it.card.name }
                .eachCount()
                .filterValues { it > 1 }
                .keys
            if (duplicateNames.isEmpty()) null else "fixture=${fixture.id} (${fixture.name}) duplicates=$duplicateNames"
        }
        assertTrue(violations.isEmpty(), "Duplicate card names found (would silently zero P5 LEGALITY via SingletonViolation): $violations")
    }

    /** Commander-prior workstream (2026-08-26), Task 2's core invariant, corpus-wide: EVERY
     * fixture (including the negative one, whose macro must stay `null`) gets a full 5-macro
     * resemblance profile summing to ~1.0 -- there is never a bare "no plan" bucket, only a
     * nearest plan with an honest confidence beside it. */
    @Test
    fun everyFixture_hasAFullResemblanceProfile() {
        AnalysisV3Fixtures.ALL.forEach { fixture ->
            val archetypeFormat = requireNotNull(fixture.archetypeFormat)
            val inferred = inferDeckArchetypeUseCase(fixture.mainboard, archetypeFormat, fixture.commanderTags, fixture.colorIdentity)
            assertEquals(5, inferred.resemblance.size, "fixture ${fixture.id} (${fixture.name}) resemblance size")
            val total = inferred.resemblance.sumOf { it.share.toDouble() }
            assertTrue(total in 0.99..1.01, "fixture ${fixture.id} (${fixture.name}) resemblance shares must sum to ~1.0, got $total")
        }
    }

    /** Commander-prior workstream (2026-08-26), Task 1's hard requirement, corpus-wide: every
     * 60-card fixture carries no commander at all, so feeding a strong, opposing commander signal
     * into the resolver must be a byte-for-byte no-op. Filters by [ArchetypeFormat.SIXTY]
     * dynamically (not a hardcoded id set) so the 60-card-coverage-expansion workstream's 5 new
     * fixtures (18-22) are automatically covered without a second edit here. Uses real fixture
     * data (not synthetic) as the strongest possible confirmation this phase's gate report cites. */
    @Test
    fun sixtyCardFixtures_areInertToAnyCommanderPrior() {
        AnalysisV3Fixtures.ALL.filter { it.archetypeFormat == ArchetypeFormat.SIXTY }.forEach { fixture ->
            val archetypeFormat = requireNotNull(fixture.archetypeFormat)
            val withoutPrior = inferDeckArchetypeUseCase(fixture.mainboard, archetypeFormat, emptyList(), emptySet())
            val withOpposingPrior = inferDeckArchetypeUseCase(
                fixture.mainboard, archetypeFormat,
                listOf(CardTag.STAX), setOf(ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.R, ManaColor.G),
            )
            assertEquals(withoutPrior, withOpposingPrior, "fixture ${fixture.id} (${fixture.name}) must be fully inert to any commander prior")
        }
    }

    private companion object {
        const val NEGATIVE_FIXTURE_ID = 17

        /** 60-card-coverage-expansion workstream (2026-08-26): fixtures 18-22, added on top of the
         * original spec §9 corpus (1-17) -- see [AnalysisV3Fixtures.ALL]'s own KDoc. */
        val NEW_FIXTURE_IDS = 18..22
    }
}
