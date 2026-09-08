package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// ═══════════════════════════════════════════════════════════════════════════════
//  ScoreLimiterTest — Deck Analysis Engine v2, "score limiter" follow-up (2026-08-20).
//
//  Live-device finding: a Standard deck stuck at AnalysisEngine.ILLEGAL_DECK_SCORE_CAP regardless
//  of which strategy the player picked was misread as "the strategy switcher is broken" — it
//  wasn't; 3 format-illegal cards were hard-capping totalScore no matter what the (correctly
//  recomputing) pillars underneath said. [ScoreLimiter] generalizes the fix beyond the legality-cap
//  special case: ANY pillar that dominantly limits the score now gets called out explicitly. Covers
//  all 3 outcomes: [ScoreLimiter.LegalityCapped], [ScoreLimiter.DominantPillar], [ScoreLimiter.None].
// ═══════════════════════════════════════════════════════════════════════════════

class ScoreLimiterTest {

    private val scorer = DeckScorer(RoleClassifier())

    private fun profileFor(mainboard: List<DeckEntry>, colorIdentity: Set<ManaColor>): DeckProfile =
        scorer.profile(mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, seedTags = emptyList())

    private fun basics(landName: String, symbol: String, quantity: Int): DeckEntry = entry(
        card(
            id = "sl-land-$landName", name = landName, typeLine = "Basic Land — $landName",
            cmc = 0.0, colors = emptyList(), colorIdentity = listOf(symbol), producedMana = symbol,
        ),
        quantity = quantity,
    )

    /** Pads [nonland] up to exactly 100 total with a single bulk basic-land entry (mirrors
     * [DeckAnalysisEngineGoldenTest]'s own `withBasics` convention). */
    private fun withBasics(nonland: List<DeckEntry>, landName: String, symbol: String): List<DeckEntry> {
        val nonlandCount = nonland.sumOf { it.quantity }
        return nonland + basics(landName, symbol, (100 - nonlandCount).coerceAtLeast(1))
    }

    /** cmc<=3, power>=2 creature -- mirrors [DeckAnalysisEngineCalibrationTest]'s own
     * `earlyThreat` (the [ArchetypeRoleClassifier.threatEarlyMatcher] structural signal). */
    private fun earlyThreat(id: String, name: String, cmc: Double, manaCost: String, colorIdentity: List<String>): DeckEntry = entry(
        card(
            id = "sl-cal-$id", name = name, typeLine = "Creature — Warrior", cmc = cmc, manaCost = manaCost,
            colors = colorIdentity, colorIdentity = colorIdentity, power = "3", toughness = "2",
        ),
    )

    private fun taggedSpell(
        id: String, name: String, cmc: Double, manaCost: String, colorIdentity: List<String>,
        tag: CardTag, typeLine: String = "Sorcery",
    ): DeckEntry = entry(
        card(
            id = "sl-cal-$id", name = name, typeLine = typeLine, cmc = cmc, manaCost = manaCost,
            colors = colorIdentity, colorIdentity = colorIdentity, tags = listOf(tag),
        ),
    )

    /**
     * REALISTIC-density mono-Red AGGRO Commander fixture -- mirrors
     * [DeckAnalysisEngineCalibrationTest.buildAggroFixture] verbatim in shape (26 early threats, 13
     * finishers, 7 removal_spot, 6 ramp, 8 card_draw, 4 utility, + commander, 35 Mountains = exactly
     * 100), which that file's own calibration run confirmed scores 94/100 (MANA_BASE 100, CURVE 100,
     * PLAN_ROLES 100, SYNERGY 60, LEGALITY 100) -- i.e. only ONE pillar (SYNERGY) is even slightly
     * imperfect, and by a small enough margin ([AnalysisWeights.synergy]'s default 0.15 weight ×
     * (100-60) lost points = 6, under the documented 10-point [ScoreLimiter.DominantPillar] floor) that
     * no pillar should ever be singled out. [AnalysisEngineLegalityP5Test]/[DeckAnalysisEngineGoldenTest]
     * demonstrate the SPARSE-fixture shape starves PLAN_ROLES/MANA_BASE and would make a "no dominant
     * pillar" / "the cap actually reduced a high score" assertion meaningless -- this REALISTIC-density
     * shape is required for both this file's [wellBuiltOnPlanDeck_reportsNoDominantLimiter] and
     * [legalityCap_binding_reportsCorrectUncappedScore] to mean what they claim to mean.
     *
     * @param bannedCard when non-null, appended as one extra nonland entry (banned in Commander) with
     *        the Mountain count reduced by 1 to keep the total at exactly 100.
     */
    private fun buildAggroFixture(bannedCard: com.mmg.manahub.core.model.Card? = null): List<DeckEntry> {
        val commander = card(
            id = "sl-cal-cmd-aggro", name = "Krenko, Mob Boss", typeLine = "Legendary Creature — Goblin",
            cmc = 3.0, manaCost = "{2}{R}", colors = listOf("R"), colorIdentity = listOf("R"), power = "3", toughness = "3",
        )
        val threats = listOf(
            Triple("Goblin Guide", 1.0, "{R}"), Triple("Zurgo Bellstriker", 1.0, "{R}"),
            Triple("Falkenrath Gorger", 1.0, "{R}"), Triple("Bomat Courier", 1.0, "{1}"),
            Triple("Kari Zev, Skyship Raider", 2.0, "{1}{R}"), Triple("Ash Zealot", 2.0, "{1}{R}"),
            Triple("Vexing Devil", 1.0, "{R}"), Triple("Hellspark Elemental", 1.0, "{R}"),
            Triple("Mogg War Marshal", 2.0, "{1}{R}"), Triple("Robber of the Rich", 2.0, "{1}{R}"),
            Triple("Reckless Bushwhacker", 2.0, "{1}{R}"), Triple("Goblin Chieftain", 2.0, "{1}{R}"),
            Triple("Krenko, Tin Street Kingpin", 2.0, "{1}{R}"), Triple("Firebrand Archer", 2.0, "{1}{R}"),
            Triple("Hellrider", 3.0, "{2}{R}"), Triple("Goblin Rabblemaster", 2.0, "{1}{R}"),
            Triple("Legion Loyalist", 2.0, "{1}{R}"), Triple("Zo-Zu the Punisher", 2.0, "{1}{R}"),
            Triple("Frenzied Goblin", 1.0, "{R}"), Triple("Ember Hauler", 2.0, "{1}{R}"),
            Triple("Kiln Fiend", 1.0, "{R}"), Triple("Goblin Piker", 2.0, "{1}{R}"),
            Triple("Jackal Pup", 1.0, "{R}"), Triple("Wild Nacatl", 1.0, "{R}"),
            Triple("Fireblade Charger", 2.0, "{1}{R}"), Triple("Akroan Crusader", 1.0, "{R}"),
        ).mapIndexed { i, (name, cmc, cost) -> earlyThreat("threat-$i", name, cmc, cost, listOf("R")) }
        val finishers = listOf(
            Triple("Purphoros, God of the Forge", 5.0, "{3}{R}{R}"), Triple("Hazoret the Fervent", 3.0, "{R}{R}{R}"),
            Triple("Torbran, Thane of Red Fell", 4.0, "{2}{R}{R}"), Triple("Chandra, Torch of Defiance", 4.0, "{2}{R}{R}"),
            Triple("Embercleave", 6.0, "{4}{R}{R}"), Triple("Zealous Conscripts", 4.0, "{2}{R}{R}"),
            Triple("Etali, Primal Storm", 6.0, "{4}{R}{R}"), Triple("Kiki-Jiki, Mirror Breaker", 4.0, "{2}{R}{R}"),
            Triple("Furnace of Rath", 3.0, "{2}{R}"), Triple("Gratuitous Violence", 3.0, "{2}{R}"),
            Triple("Thundermaw Hellkite", 5.0, "{3}{R}{R}"), Triple("Balefire Dragon", 6.0, "{4}{R}{R}"),
            Triple("Zealous Feud", 4.0, "{2}{R}{R}"),
        ).mapIndexed { i, (name, cmc, cost) -> taggedSpell("fin-$i", name, cmc, cost, listOf("R"), CardTag.WIN_CON, "Creature — Elemental") }
        val removal = listOf(
            Triple("Lightning Bolt", 1.0, "{R}"), Triple("Abrade", 2.0, "{1}{R}"),
            Triple("Skewer the Critics", 2.0, "{1}{R}"), Triple("Chain Lightning", 1.0, "{R}"),
            Triple("Fireblast", 6.0, "{4}{R}{R}"), Triple("Shock", 1.0, "{R}"),
            Triple("Char", 2.0, "{1}{R}"),
        ).mapIndexed { i, (name, cmc, cost) -> taggedSpell("rem-$i", name, cmc, cost, listOf("R"), CardTag.REMOVAL, "Instant") }
        val ramp = listOf(
            Triple("Sol Ring", 1.0, "{1}"), Triple("Arcane Signet", 1.0, "{1}"),
            Triple("Mind Stone", 2.0, "{2}"), Triple("Thran Dynamo", 4.0, "{4}"),
            Triple("Skirk Prospector", 1.0, "{R}"), Triple("Gilded Lotus", 5.0, "{5}"),
        ).mapIndexed { i, (name, cmc, cost) ->
            taggedSpell("ramp-$i", name, cmc, cost, if (cost.contains("R")) listOf("R") else emptyList(), CardTag.RAMP, "Artifact")
        }
        val utility = listOf(
            taggedSpell("tutor-0", "Gamble", 1.0, "{R}", listOf("R"), CardTag.TUTOR, "Sorcery"),
            taggedSpell("tutor-1", "Goblin Engineer", 2.0, "{1}{R}", listOf("R"), CardTag.TUTOR, "Creature — Goblin Artificer"),
            taggedSpell("recur-0", "Feldon of the Third Path", 3.0, "{2}{R}", listOf("R"), roleTag("recursion"), "Legendary Creature — Dwarf Shaman"),
            taggedSpell("recur-1", "Goblin Welder", 2.0, "{1}{R}", listOf("R"), roleTag("recursion"), "Creature — Goblin"),
        )
        val draw = listOf(
            Triple("Wheel of Fortune", 3.0, "{2}{R}"), Triple("Faithless Looting", 1.0, "{R}"),
            Triple("Light Up the Stage", 2.0, "{1}{R}"), Triple("Reforge the Soul", 4.0, "{2}{R}{R}"),
            Triple("Outpost Siege", 3.0, "{2}{R}"), Triple("Commune with Lava", 3.0, "{2}{R}"),
            Triple("Wild Guess", 2.0, "{1}{R}"), Triple("Cathartic Reunion", 2.0, "{1}{R}"),
        ).mapIndexed { i, (name, cmc, cost) -> taggedSpell("draw-$i", name, cmc, cost, listOf("R"), CardTag.DRAW_ENGINE, "Sorcery") }

        val bannedEntries = bannedCard?.let { listOf(entry(it)) } ?: emptyList()
        val nonland = listOf(entry(commander)) + threats + finishers + removal + ramp + draw + utility + bannedEntries
        val mountainCount = 35 - bannedEntries.sumOf { it.quantity }
        return nonland + basics("Mountain", "R", mountainCount)
    }

    private fun roleTag(key: String): CardTag = CardTag(key, com.mmg.manahub.core.model.TagCategory.ROLE)

    /** Recomputes [AnalysisEngine.compose]'s PRE-cap weighted sum straight from the observed
     * [DeckAnalysis.pillars] under the same [weights] the engine was invoked with — lets these
     * tests assert an exact [ScoreLimiter.LegalityCapped.uncappedScore] without hardcoding an
     * engine-internal subscore number that would be brittle to unrelated pillar-tuning changes.
     *
     * Scoring-semantics fix (2026-08-27) -- mirrors [AnalysisEngine.compose]'s OWN weight
     * redistribution exactly: when SYNERGY is [PillarResult.notApplicable] (a deck with zero
     * synergy edges), its weight is zeroed and the rest renormalized, same as the production
     * formula this helper's whole purpose is to shadow. Keeping this in sync with a legitimate
     * engine formula change is this helper's own designed contract, not test-specific tuning. */
    private fun weightedScore(analysis: DeckAnalysis, weights: AnalysisWeights): Int {
        val byId = analysis.pillars.associateBy { it.id }
        val p4NotApplicable = byId.getValue(PillarId.SYNERGY).notApplicable
        val w = (if (p4NotApplicable) weights.copy(synergy = 0f) else weights).normalized()
        val weighted = byId.getValue(PillarId.MANA_BASE).subscore * w.manaBase +
            byId.getValue(PillarId.CURVE).subscore * w.curve +
            byId.getValue(PillarId.PLAN_ROLES).subscore * w.planRoles +
            byId.getValue(PillarId.SYNERGY).subscore * w.synergy +
            byId.getValue(PillarId.LEGALITY).subscore * w.legality
        return weighted.roundToInt().coerceIn(0, 100)
    }

    // ── 1. LegalityCapped ───────────────────────────────────────────────────────────────────

    /** REALISTIC-density build (see [buildAggroFixture]'s KDoc for why sparse fixtures can't
     * exercise this meaningfully) plus ONE banned-in-Commander card — the live-device finding this
     * feature exists to explain: the underlying build is genuinely strong (94/100 pre-cap, per
     * [DeckAnalysisEngineCalibrationTest]'s own calibration of this exact shape), so the cap
     * visibly, meaningfully reduces the score, and [ScoreLimiter.LegalityCapped.uncappedScore] must
     * report the CORRECT pre-cap weighted total, not just "some value less than 100". */
    @Test
    fun legalityCap_binding_reportsCorrectUncappedScore() {
        val bannedCard = card(
            id = "sl-banned", name = "Sway of the Stars", typeLine = "Sorcery", cmc = 7.0, colorIdentity = listOf("W"),
            legalityCommander = "banned",
        )
        val mainboard = buildAggroFixture(bannedCard = bannedCard)
        val colorIdentity = setOf(ManaColor.R, ManaColor.W)
        val profile = profileFor(mainboard, colorIdentity)

        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, profile = profile,
            archetype = ArchetypeId.AGGRO, themes = emptyList(), isManualOverride = true, confidence = 1f,
        )

        // Deck Analysis Engine v3, Phase 3b (spec §8, 2026-08-26): AnalysisEngine.evaluate's own
        // `weights` default is no longer the flat AnalysisWeights() -- it is
        // AnalysisWeights.forMacro(archetype, themes.size), evaluated against THIS call's own
        // archetype=AGGRO/themes=emptyList() above. Recomputing the expected pre-cap weighted sum
        // against the SAME weights the engine actually defaulted to (not the old flat constant) is
        // the fix -- this helper's whole POINT is "never hardcode an engine-internal number", and
        // the weights themselves are now one of those engine-internal defaults.
        val expectedUncapped = weightedScore(analysis, AnalysisWeights.forMacro(ArchetypeId.AGGRO, 0))
        val limiter = assertIs<ScoreLimiter.LegalityCapped>(analysis.limiter, "an illegal-deck cap must report LegalityCapped: ${analysis.limiter}")
        assertEquals(expectedUncapped, limiter.uncappedScore, "uncappedScore must equal the pre-cap weighted sum of the pillars")
        assertTrue(limiter.uncappedScore > analysis.totalScore, "the cap must actually be reducing the score for this to be worth reporting")
        assertTrue(analysis.totalScore <= AnalysisEngine.ILLEGAL_DECK_SCORE_CAP)
    }

    // ── 2. DominantPillar ───────────────────────────────────────────────────────────────────

    /** No legality issue at all — a mono-red aggro pile (correct land count/curve for AGGRO, zero
     * removal/card-draw/counterspell/wipe roles) evaluated against a badly-mismatched CONTROL
     * strategy, which starves PLAN_ROLES specifically (counterspell/removal_mass/card_draw bands
     * all unmet). Weights are skewed heavily toward [AnalysisWeights.planRoles] so the DOMINANCE
     * detection itself is exercised deterministically, independent of exactly how far the OTHER
     * (tiny-weighted) pillars happen to drift under an off-strategy read. */
    @Test
    fun dominantPillar_detectsSingleWeightedBottleneck() {
        val commander = card(id = "sl-dp-cmd", name = "Krenko, Mob Boss", typeLine = "Legendary Creature — Goblin", cmc = 3.0, colorIdentity = listOf("R"))
        val threats = (1..14).map { i ->
            entry(card(id = "sl-dp-threat-$i", name = "Filler Goblin $i", typeLine = "Creature — Goblin", cmc = 2.0, colorIdentity = listOf("R"), power = "2", toughness = "2"))
        }
        val nonland = listOf(entry(commander)) + threats
        val mainboard = withBasics(nonland, "Mountain", "R")
        val colorIdentity = setOf(ManaColor.R)
        val profile = profileFor(mainboard, colorIdentity)

        val skewedWeights = AnalysisWeights(manaBase = 0.05f, curve = 0.05f, planRoles = 0.8f, synergy = 0.05f, legality = 0.05f)
        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, profile = profile,
            archetype = ArchetypeId.CONTROL, themes = emptyList(), isManualOverride = true, confidence = 1f,
            weights = skewedWeights,
        )

        // No BLOCKER on this build -- the cap path must not have engaged.
        assertTrue(analysis.pillars.first { it.id == PillarId.LEGALITY }.findings.none { it.severity == FindingSeverity.BLOCKER })

        val planRoles = analysis.pillars.first { it.id == PillarId.PLAN_ROLES }
        val limiter = assertIs<ScoreLimiter.DominantPillar>(analysis.limiter, "a role-starved off-strategy read under planRoles-heavy weights must report DominantPillar: ${analysis.limiter}")
        assertEquals(PillarId.PLAN_ROLES, limiter.pillarId)
        // Scoring-semantics fix (2026-08-27) -- this fixture (vanilla goblins, no roles/tags at
        // all) has zero synergy edges, so SYNERGY is [PillarResult.notApplicable] and its weight
        // redistributes across the other 4 (see AnalysisEngine.compose's own KDoc) -- reproduced
        // here so the expectation matches the SAME formula the engine actually applies, not the
        // pre-fix flat `skewedWeights.normalized()`.
        val synergyNotApplicable = analysis.pillars.first { it.id == PillarId.SYNERGY }.notApplicable
        val effectiveWeights = (if (synergyNotApplicable) skewedWeights.copy(synergy = 0f) else skewedWeights).normalized()
        val expectedLostPoints = (effectiveWeights.planRoles * (100 - planRoles.subscore)).roundToInt()
        assertEquals(expectedLostPoints, limiter.lostPoints)
        assertTrue(limiter.lostPoints >= 10, "the detected bottleneck must clear the documented 10-point floor: ${limiter.lostPoints}")
    }

    // ── 3. None ─────────────────────────────────────────────────────────────────────────────

    /** [buildAggroFixture] with no banned card: a clean, on-plan, REALISTIC-density build scoring
     * 94/100 (only SYNERGY imperfect, and only by 6 lost points — well under the documented 10-point
     * floor). See that fixture's own KDoc for why a SPARSE fixture (as used by
     * [AnalysisEngineLegalityP5Test]/[DeckAnalysisEngineGoldenTest]) would make this assertion
     * meaningless: those fixtures' land-count skew starves MANA_BASE/PLAN_ROLES together, which can
     * legitimately trip the 1.5x-margin threshold and report a spurious DominantPillar that says
     * more about fixture density than about the deck.
     *
     * ## Deck Analysis Engine v3, PHASE 4 (spec §7) — retitled/re-asserted, engine NOT the bug
     * Before Phase 4, SYNERGY here was 60 (tag-fingerprint density, "only slightly imperfect"), so
     * no pillar dominated. Phase 4 rescored SYNERGY onto the [SynergyGraph]-derived
     * coverage/axisHealth/connectivity/consistency composite (spec §7) — and this fixture, for all
     * its real deckbuilding quality, has ZERO producer→payoff synergy PAIRS anywhere: its identity
     * is `threat_early`/`finisher`/`removal_spot`/`ramp`/`card_draw`/`tutor`/`recursion`, and of
     * those only `threat_early` participates in an axis at all (ATTACK, producer-only — nothing here
     * tags `combat_payoff`/`evasion`). SYNERGY now correctly measures 0, which — CONFIRMED live,
     * SAME root cause, on the corpus's OWN real UW Control fixture (spec §7's own worked example) —
     * is not an isolated fixture quirk: a macro's OWN core role vocabulary (interaction/ramp/draw/
     * tutors/threats) is almost entirely axis-inert by Phase 1/2's own design (the 15 axes model
     * THEMES — Aristocrats/Tokens/Lifegain/etc. — not macro identity), so a themeless deck's
     * `coverage` structurally cannot approach ANY macro's coverage-band ideal without live theme
     * support. This is a genuine, reported, NOT-tuned-around finding (see this phase's own gate
     * report) — a well-built, themeless "good stuff" pile SHOULD now show low SYNERGY under the
     * new model (that is the whole point of separating card quality from coherence), and at
     * SYNERGY's own resolved weight (0.10 here — AGGRO's 0.15 base minus the 0-live-theme P4→P3
     * shift, spec §8) that shortfall is large enough (10 of 100 points) to be this deck's ONE real,
     * correctly-flagged bottleneck. Fixed the assertion, not the engine — mirrors the Phase 3a
     * precedent (`lowSignalVanillaDeckResolvesAmbiguousWithNoThemes`) for a test whose expectation
     * encoded a retired mental model.
     *
     * ## Scoring-semantics fix (2026-08-27) — RETITLED AGAIN, `DominantPillar(SYNERGY)` was itself
     * the retired mental model this time
     * Phase 4's own framing above ("SYNERGY now correctly measures 0... this deck's ONE real,
     * correctly-flagged bottleneck") is exactly the false precision the current fix corrects: a
     * deck with ZERO producer/payoff synergy PAIRS gives P4 nothing to measure, so scoring it 0 and
     * then blaming it as a "real shortfall" was never honest — it asserted "maximally incoherent"
     * for a pillar that structurally cannot see this deck's plan at all (no counterspell/protection,
     * no live theme). P4 for this fixture is now [PillarResult.notApplicable], its weight
     * redistributes across the other 4 (all already 100 for this maximally clean build — see
     * [DeckAnalysisEngineCalibrationTest]'s sibling AGGRO fixture, same shape), and the total reads
     * a clean 100 with NO dominant bottleneck at all. This test's role flips back to what
     * [themedRealisticDeck_stillReportsNoDominantLimiter] below also demonstrates: a well-built,
     * themeless deck now correctly reports [ScoreLimiter.None] once SYNERGY stops being falsely
     * penalized for a plan it cannot measure. */
    @Test
    fun themelessAggroPile_reportsNoDominantLimiterOnceSynergyIsMarkedNotApplicable() {
        val mainboard = buildAggroFixture()
        val colorIdentity = setOf(ManaColor.R)
        val profile = profileFor(mainboard, colorIdentity)

        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, profile = profile,
            archetype = ArchetypeId.AGGRO, themes = emptyList(), isManualOverride = true, confidence = 1f,
        )

        assertTrue(analysis.pillars.first { it.id == PillarId.LEGALITY }.findings.none { it.severity == FindingSeverity.BLOCKER })
        val synergy = analysis.pillars.first { it.id == PillarId.SYNERGY }
        assertTrue(synergy.notApplicable, "this fixture has zero producer/payoff synergy pairs — P4 must be marked not-applicable, not scored 0: $synergy")
        assertEquals(
            ScoreLimiter.None, analysis.limiter,
            "a themeless deck's own unmeasurable SYNERGY must no longer be reported as a false bottleneck: ${analysis.limiter}, pillars=${analysis.pillars.map { it.id to it.subscore }}",
        )
    }

    /**
     * Restores this file's "None" coverage after [themelessAggroPile_correctlyFlagsSynergyAsTheDominantLimiter]
     * above stopped demonstrating it (Deck Analysis Engine v3, PHASE 4 — that fixture's own SYNERGY
     * genuinely became the dominant limiter under the new formula, so it can no longer serve double
     * duty as the "no single bottleneck" example).
     *
     * SWAPPED away from the calibration corpus's Sythis fixture (`analysisv3` Fixture 13,
     * ENCHANTRESS) by the commander-prior workstream (2026-08-26): that fixture's commander now
     * carries an honest MIDRANGE tag (see `Fixture13Sythis.kt`), which correctly flips its
     * previously-ambiguous macro resolution (margin .066, under the 0.08 floor) to a CONFIDENT
     * MIDRANGE. That is a genuine improvement (the whole point of the commander-prior workstream),
     * but it means Sythis is no longer graded against the bare generic baseline skeleton — it is
     * now graded against MIDRANGE's own, more demanding role skeleton, which surfaces a REAL 33-point
     * PLAN_ROLES gap this fixture's actual enchantress-focused build has always had (not a defect
     * this run introduced — the bare/generic skeleton was simply hiding it). This test's own PURPOSE
     * ("prove `ScoreLimiter.None` is reachable through the real inference pipeline, not just via a
     * hardcoded manual override") needed a fixture whose real inferred read is a genuinely
     * well-rounded build, not one whose good score depended on the pre-commander-prior hedge.
     *
     * Previously reused [buildAggroFixture] under the REAL inference pipeline (not
     * `isManualOverride = true`): the real resolver read that build as MIDRANGE, not AGGRO, and
     * under Phase 4's own P4 formula that mismatch happened to land close enough to [PLAN_ROLES]'s
     * own real shortfall that neither pillar cleared the 1.5x dominance margin over the other —
     * `ScoreLimiter.None` held, but for the WRONG reason (an accidental near-tie between a real
     * gap and a spurious one), not because the deck was actually free of a dominant bottleneck.
     *
     * ## Scoring-semantics fix (2026-08-27) — SWAPPED again, the old fixture's `None` was accidental
     * Once SYNERGY correctly reads [PillarResult.notApplicable] for that fixture (zero synergy
     * edges) instead of a false ~0, its weight no longer masks anything — and PLAN_ROLES' real
     * 73/100 shortfall under MIDRANGE's own stricter skeleton (a pre-existing macro-resolution
     * mismatch this fix does not touch or explain away) now legitimately clears the dominance
     * margin on its own, correctly reporting `DominantPillar(PLAN_ROLES)`. That fixture no longer
     * demonstrates "no dominant bottleneck" — it demonstrates a real one, once no longer hidden.
     * SWAPPED to `analysisv3` Fixture 2 (Meren of Clan Nel Toth, Aristocrats) — an ALREADY-VETTED
     * corpus fixture (`DeckAnalysisV3CorpusTest`'s own floors: macro correctly resolves MIDRANGE,
     * themes correctly resolve ARISTOCRATS, in-band) with REAL, non-empty synergy edges (P4 was
     * already 100 pre- and post-fix, unaffected by this run's change), so this test's own PURPOSE
     * ("prove `ScoreLimiter.None` is reachable through the real inference pipeline, not just via a
     * hardcoded manual override") is demonstrated by a build that is genuinely well-rounded under
     * its own real inferred macro, not one whose `None` result depended on two separate flaws
     * happening to cancel out. */
    @Test
    fun themedRealisticDeck_stillReportsNoDominantLimiter() {
        val fixture = com.mmg.manahub.feature.decks.domain.engine.analysisv3.AnalysisV3Fixtures.ALL.first { it.id == 2 }
        val format = requireNotNull(fixture.archetypeFormat)
        val profile = profileFor(fixture.mainboard, fixture.colorIdentity)
        val inferred = com.mmg.manahub.feature.decks.domain.usecase.InferDeckArchetypeUseCase()(
            fixture.mainboard, format, fixture.commanderTags, fixture.colorIdentity,
        )
        val analysis = AnalysisEngine.evaluate(
            mainboard = fixture.mainboard, format = fixture.format, colorIdentity = fixture.colorIdentity, profile = profile,
            archetype = inferred.macro, posture = inferred.posture, themes = inferred.themes,
            isManualOverride = false, confidence = inferred.confidence,
        )

        assertTrue(analysis.pillars.first { it.id == PillarId.LEGALITY }.findings.none { it.severity == FindingSeverity.BLOCKER })
        assertEquals(ScoreLimiter.None, analysis.limiter, "a real, well-built deck confidently inferred (no manual override) must still report None, pillars=${analysis.pillars.map { it.id to it.subscore }}")
    }
}
