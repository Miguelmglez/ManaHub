package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import kotlin.test.Test
import kotlin.test.assertTrue

// ═══════════════════════════════════════════════════════════════════════════════
//  DeckAnalysisEngineCalibrationTest — Deck Analysis Engine v2 plan, Phase 4 step 1
//  (calibration sanity-check).
//
//  WHY THIS FILE EXISTS (see docs/plans/deck-analysis-engine-v2-progress.md's 2026-08-19 log
//  entries): a prior calibration pass diagnosed that ALL 6 [DeckAnalysisEngineGoldenTest] reference
//  decks score low and clustered (51-64/100). Root cause was traced to a FIXTURE artifact, not
//  necessarily an engine/weight bug -- those golden decks intentionally declare only ~17-28 distinct
//  real nonland cards + ONE bulk basic-land entry closing the gap to 100 (a documented maintainability
//  shortcut, see that file's own header). That inflates land count to ~75-83/100 (vs. a real
//  Commander deck's ~35-40) and starves PLAN_ROLES (role-band minimums are sized for a real ~60-card
//  nonland deck, unreachable with only 17-28 cards). MANA_BASE and PLAN_ROLES together carry 0.55 of
//  [AnalysisWeights]' default composition, so this fixture artifact alone explains most of the
//  clustering -- it is NOT evidence that the engine itself needs a weight/cap retune.
//
//  This file builds 2 REALISTIC-DENSITY Commander fixtures (~60-65 real nonland cards + ~35-38 real
//  lands, no bulk basic-land shortcut) for the two extremes the plan's calibration task calls out --
//  AGGRO (low curve, land-light) and CONTROL (high curve, land-heavy) -- and checks the resulting
//  scores land in a defensible "well-built, on-plan deck" band. The existing golden fixtures in
//  [DeckAnalysisEngineGoldenTest] are NOT modified (they stay exactly as they are, still validating
//  RELATIVE behavior: strategy-switch deltas, anti-role firing, illegal-cap -- never absolute score
//  bands, so the sparse-fixture shape doesn't affect their own assertions' validity).
//
//  CARD DATA NOTE: every card name below is a real, well-known Magic card (a "known staple" per the
//  plan's own calibration task, for legibility/auditability) but `manaCost`/`cmc`/`power`/`toughness`
//  are simplified/rounded to a plausible value for that card's real identity rather than transcribed
//  Scryfall-exact (mirrors the existing golden fixtures' own convention of inventing stats freely) --
//  the goal is a realistic PIP-INTENSITY DISTRIBUTION (mostly single-pip, some double/triple-pip
//  finishers/wipes/counterspells) for [ManaBaseAnalyzer]'s Karsten-table check to engage meaningfully,
//  not a byte-exact decklist reproduction.
// ═══════════════════════════════════════════════════════════════════════════════

class DeckAnalysisEngineCalibrationTest {

    private val scorer = DeckScorer(RoleClassifier())

    private fun profileFor(mainboard: List<DeckEntry>, colorIdentity: Set<ManaColor>): DeckProfile =
        scorer.profile(mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, seedTags = emptyList())

    private fun roleTag(key: String): CardTag = CardTag(key, TagCategory.ROLE)

    private fun basicLands(landName: String, symbol: String, quantity: Int): DeckEntry = entry(
        card(
            id = "cal-land-$landName", name = landName, typeLine = "Basic Land — $landName",
            cmc = 0.0, colors = emptyList(), colorIdentity = listOf(symbol), producedMana = symbol,
        ),
        quantity = quantity,
    )

    private fun dualLand(id: String, name: String, symbols: String, quantity: Int = 1): DeckEntry = entry(
        card(
            id = "cal-land-$id", name = name, typeLine = "Land",
            cmc = 0.0, colors = emptyList(), colorIdentity = symbols.map { it.toString() }, producedMana = symbols,
        ),
        quantity = quantity,
    )

    /** Wave 2 / B6 -- STANDARD sibling of [profileFor] (which hardcodes [DeckFormat.COMMANDER]).
     * [DeckScorer.profile]'s `format` param feeds [DeckProfile.skeleton]
     * ([DeckSkeletons.forFormat]), used only by the LEGACY engine path -- [AnalysisEngine] itself
     * never reads `profile.skeleton`, so this only needs to be format-correct for API contract
     * reasons, not because it changes anything this test observes. */
    private fun profileForStandard(mainboard: List<DeckEntry>, colorIdentity: Set<ManaColor>): DeckProfile =
        scorer.profile(mainboard = mainboard, format = DeckFormat.STANDARD, colorIdentity = colorIdentity, seedTags = emptyList())

    /** cmc<=3, power>=2 creature -- the [ArchetypeRoleClassifier.threatEarlyMatcher] structural signal. */
    private fun earlyThreat(id: String, name: String, cmc: Double, manaCost: String, colorIdentity: List<String>): DeckEntry = entry(
        card(
            id = "cal-$id", name = name, typeLine = "Creature — Warrior", cmc = cmc, manaCost = manaCost,
            colors = colorIdentity, colorIdentity = colorIdentity, power = "3", toughness = "2",
        ),
    )

    private fun taggedSpell(
        id: String, name: String, cmc: Double, manaCost: String, colorIdentity: List<String>,
        tag: CardTag, typeLine: String = "Sorcery",
    ): DeckEntry = entry(
        card(
            id = "cal-$id", name = name, typeLine = typeLine, cmc = cmc, manaCost = manaCost,
            colors = colorIdentity, colorIdentity = colorIdentity, tags = listOf(tag),
        ),
    )

    private fun assertEquals100(mainboard: List<DeckEntry>) {
        val total = mainboard.sumOf { it.quantity }
        assertTrue(total == 100, "fixture must total exactly 100 cards (Commander incl. commander), was $total")
    }

    private fun assertNoBlockers(analysis: DeckAnalysis) {
        val legality = analysis.pillars.first { it.id == PillarId.LEGALITY }
        assertTrue(legality.findings.none { it.severity == FindingSeverity.BLOCKER }, "legal, correctly-built fixture must carry no P5 BLOCKER: ${legality.findings}")
    }

    private fun averageNonLandCmc(mainboard: List<DeckEntry>): Double {
        val nonLand = mainboard.filterNot { BasicLandCalculator.isLand(it.card) }
        val count = nonLand.sumOf { it.quantity }
        return if (count == 0) 0.0 else nonLand.sumOf { it.card.cmc * it.quantity } / count
    }

    private fun landCount(mainboard: List<DeckEntry>): Int =
        mainboard.filter { BasicLandCalculator.isLand(it.card) }.sumOf { it.quantity }

    // ── AGGRO (mono-Red, Commander) — low curve, land-light, wide early-threat plan ─────────────

    /**
     * 26 distinct real cheap red creatures (threat_early's structural signal: cmc<=3, power>=2),
     * 13 finishers, 6 removal_spot, 11 ramp, 8 card_draw, + commander, closed with exactly 35
     * Mountains (AGGRO Commander's own land band: min33, ideal35, max37) -- totals exactly 100.
     */
    private fun buildAggroFixture(): List<DeckEntry> {
        val commander = card(
            id = "cal-cmd-aggro", name = "Krenko, Mob Boss", typeLine = "Legendary Creature — Goblin",
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

        // 7 removal_spot (band min4, ideal6, max8) -- 1 above the archetype's own ideal, still
        // under max (see the note below on why removal_spot absorbs ramp's freed slots).
        val removal = listOf(
            Triple("Lightning Bolt", 1.0, "{R}"), Triple("Abrade", 2.0, "{1}{R}"),
            Triple("Skewer the Critics", 2.0, "{1}{R}"), Triple("Chain Lightning", 1.0, "{R}"),
            Triple("Fireblast", 6.0, "{4}{R}{R}"), Triple("Shock", 1.0, "{R}"),
            Triple("Char", 2.0, "{1}{R}"),
        ).mapIndexed { i, (name, cmc, cost) -> taggedSpell("rem-$i", name, cmc, cost, listOf("R"), CardTag.REMOVAL, "Instant") }

        // Only 6 ramp (NOT AGGRO's own printed band max11) -- [ArchetypeSkeletonResolver
        // .applyIdentityModulation] (WS9.2 `ColorRoleAffinity`) treats "ramp" as SUBSTITUTE-ONLY for
        // a mono-Red identity (red's land-ramp access is real but narrower than green's), scaling
        // the resolved band down to (6,6,6) for THIS identity specifically -- confirmed by running
        // this fixture and reading the actual [PillarResult.roleCoverage] rather than assuming the
        // raw [ArchetypeData.ARCHETYPES] table applies unmodulated. 11 ramp pieces would overshoot
        // the resolved max and get penalized; 6 sits exactly at the resolved ideal.
        val ramp = listOf(
            Triple("Sol Ring", 1.0, "{1}"), Triple("Arcane Signet", 1.0, "{1}"),
            Triple("Mind Stone", 2.0, "{2}"), Triple("Thran Dynamo", 4.0, "{4}"),
            Triple("Skirk Prospector", 1.0, "{R}"), Triple("Gilded Lotus", 5.0, "{5}"),
        ).mapIndexed { i, (name, cmc, cost) ->
            taggedSpell("ramp-$i", name, cmc, cost, if (cost.contains("R")) listOf("R") else emptyList(), CardTag.RAMP, "Artifact")
        }

        // 2 tutor + 2 recursion -- AGGRO's own [ArchetypeData.ARCHETYPES] entry does NOT override
        // these 2 keys, so they survive from [ArchetypeData.generic]'s own band (min0, ideal2) UNLIKE
        // removal_mass (an EXPLICIT anti-role AGGRO does define). A well-built aggro Commander deck
        // commonly runs a couple of cheap tutors (find the key piece) and a little recursion even
        // though neither is its core identity -- 2 of each exactly clears GENERIC's inherited ideal.
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

        val nonland = listOf(entry(commander)) + threats + finishers + removal + ramp + draw + utility
        return nonland + basicLands("Mountain", "R", 35)
    }

    @Test
    fun aggroRealisticDensity_scoresInWellBuiltDeckBand() {
        val mainboard = buildAggroFixture()
        assertEquals100(mainboard)

        val colorIdentity = setOf(ManaColor.R)
        val profile = profileFor(mainboard, colorIdentity)
        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, profile = profile,
            archetype = ArchetypeId.AGGRO, themes = emptyList(), isManualOverride = true, confidence = 1f,
        )

        assertNoBlockers(analysis)
        // JUDGMENT CALL: the "well-built, on-plan deck" band for this calibration pass is documented
        // as 65-95/100 -- wide enough to tolerate the engine's own P2 curve tension noted below,
        // narrow enough to reject the pre-fix clustering (51-64) this file exists to disprove.
        // Deck Analysis Engine v3, Phase 3b (spec §8, 2026-08-26): AnalysisWeights became a function
        // of macro. No-theme AGGRO shifts to manaBase=0.18/curve=0.22/planRoles=0.35/synergy=0.10/
        // legality=0.15 (curve weighted up, since spec §8's own rationale is "the curve IS the deck"
        // for AGGRO) -- on THIS fixture (MANA_BASE 100, CURVE 100, PLAN_ROLES 100, SYNERGY 60,
        // LEGALITY 100, all UNCHANGED by 4.4's P3 formula fixes since every role here already sat at
        // its band ideal or above) the total legitimately moved 94 -> 96, 1 point past the pre-4.5
        // upper bound. Widened to 97, not re-derived from this fixture alone -- the archetype-
        // dependent weight table is a spec-given constant (§8), and rewarding a maximally clean
        // AGGRO curve/mana build with a near-ceiling score is the INTENDED effect of that table, not
        // a regression to paper over.
        //
        // Scoring-semantics fix (2026-08-27) -- widened again, to 65-100. This fixture has ZERO
        // producer/payoff synergy edges (mono-red, no counterspell/protection/live theme -- same
        // structural shape as the corpus's own Mono-Red Burn/Tron/Jund fixtures), so SYNERGY is now
        // correctly [PillarResult.notApplicable] rather than falsely scored 0 (see that property's
        // own KDoc). Its weight redistributes across the other 4 pillars -- MANA_BASE/CURVE/
        // PLAN_ROLES/LEGALITY, all already 100 for this maximally clean build -- so the total is
        // now EXACTLY 100 (verified: pillars are MANA_BASE 100/CURVE 100/PLAN_ROLES 100/SYNERGY
        // not-applicable/LEGALITY 100). This is the ceiling working correctly, not a fixture-fit
        // widening: a real deck this clean, once no longer penalized on a pillar that cannot even
        // see it, SHOULD read 100.
        assertTrue(analysis.totalScore in 65..100, "AGGRO realistic-density score ${analysis.totalScore} outside the defensible well-built band")
        val planRoles = analysis.pillars.first { it.id == PillarId.PLAN_ROLES }
        // Directly disproves the prior diagnostic's hypothesis: at realistic density, PLAN_ROLES is
        // no longer starved by unreachable band minimums.
        assertTrue(planRoles.subscore >= 70, "PLAN_ROLES should no longer be starved at realistic density, was ${planRoles.subscore}")
        val manaBase = analysis.pillars.first { it.id == PillarId.MANA_BASE }
        assertTrue(manaBase.subscore >= 85, "mono-color MANA_BASE should score cleanly, was ${manaBase.subscore}")
    }

    // ── CONTROL (Azorius/UW, Commander) — high curve, land-heavy, answer-dense plan ────────────

    /**
     * 13 removal_spot, 9 removal_mass, 11 counterspell, 13 card_draw, 6 finisher, 11 ramp,
     * + commander, closed with 10 real UW duals + 28 basics = 38 lands (CONTROL Commander's own
     * land band: min36, ideal38, max40) -- totals exactly 100.
     */
    private fun buildControlFixture(): List<DeckEntry> {
        val commander = card(
            id = "cal-cmd-control", name = "Grand Arbiter Augustin IV", typeLine = "Legendary Creature — Human Wizard",
            cmc = 3.0, manaCost = "{1}{U}{W}", colors = listOf("U", "W"), colorIdentity = listOf("U", "W"), power = "2", toughness = "2",
        )

        val removal = listOf(
            Triple("Swords to Plowshares", 1.0, "{W}") to "W", Triple("Path to Exile", 1.0, "{W}") to "W",
            Triple("Prison Realm", 3.0, "{2}{W}") to "W", Triple("Council's Judgment", 4.0, "{2}{W}{W}") to "W",
            Triple("Fateful Absence", 2.0, "{1}{W}") to "W", Triple("Skyclave Apparition", 2.0, "{1}{W}") to "W",
            Triple("Portable Hole", 1.0, "{W}") to "W", Triple("Ossification", 2.0, "{1}{W}") to "W",
            Triple("Banishing Light", 3.0, "{2}{W}") to "W", Triple("On Thin Ice", 2.0, "{1}{U}") to "U",
            Triple("Oblivion Ring", 3.0, "{2}{W}") to "W", Triple("Pacifism", 2.0, "{1}{W}") to "W",
            Triple("Stasis Snare", 3.0, "{2}{W}") to "W",
        ).mapIndexed { i, (t, color) -> taggedSpell("rem-$i", t.first, t.second, t.third, listOf(color), CardTag.REMOVAL, "Instant") }

        // 7 removal_mass (band min5, ideal7, max9) -- a real "PLAN here" per the ep.658 anchor
        // comment on CONTROL's own skeleton, not insurance-level.
        val wipes = listOf(
            Triple("Wrath of God", 4.0, "{2}{W}{W}"), Triple("Supreme Verdict", 4.0, "{1}{W}{W}{U}"),
            Triple("Farewell", 5.0, "{3}{W}{W}"), Triple("Terminus", 5.0, "{3}{W}{W}"),
            Triple("Austere Command", 6.0, "{4}{W}{W}"), Triple("Fumigate", 5.0, "{3}{W}{W}"),
            Triple("Winds of Abandon", 2.0, "{1}{W}"),
        ).mapIndexed { i, (name, cmc, cost) -> taggedSpell("wipe-$i", name, cmc, cost, listOf("W"), CardTag.WRATH, "Sorcery") }

        val counters = listOf(
            Triple("Counterspell", 2.0, "{U}{U}"), Triple("Mana Drain", 2.0, "{U}{U}"),
            Triple("Cryptic Command", 4.0, "{1}{U}{U}{U}"), Triple("Swan Song", 1.0, "{U}"),
            Triple("Dovin's Veto", 2.0, "{1}{W}{U}"), Triple("Negate", 2.0, "{1}{U}"),
            Triple("Mystic Confluence", 5.0, "{2}{U}{U}{U}"), Triple("Render Silent", 4.0, "{2}{U}{U}"),
            Triple("Absorb", 3.0, "{1}{W}{U}{U}"), Triple("Arcane Denial", 2.0, "{U}{U}"),
            Triple("Delay", 2.0, "{1}{U}"),
        ).mapIndexed { i, (name, cmc, cost) -> taggedSpell("ctr-$i", name, cmc, cost, listOf("U"), roleTag("counterspell"), "Instant") }

        val draw = listOf(
            Triple("Rhystic Study", 3.0, "{2}{U}"), Triple("Mystic Remora", 1.0, "{U}"),
            Triple("Fact or Fiction", 3.0, "{2}{U}"), Triple("Consecrated Sphinx", 6.0, "{4}{U}{U}"),
            Triple("Dig Through Time", 8.0, "{6}{U}{U}"), Triple("Treasure Cruise", 8.0, "{7}{U}"),
            Triple("Windfall", 3.0, "{2}{U}"), Triple("Blue Sun's Zenith", 4.0, "{2}{U}{U}"),
            Triple("Stroke of Genius", 3.0, "{2}{U}"), Triple("Frantic Search", 3.0, "{2}{U}"),
            Triple("Deep Analysis", 3.0, "{2}{U}"), Triple("Impulse", 2.0, "{1}{U}"),
            Triple("Braingeyser", 5.0, "{3}{U}{U}"),
        ).mapIndexed { i, (name, cmc, cost) -> taggedSpell("draw-$i", name, cmc, cost, listOf("U"), CardTag.DRAW_ENGINE, "Sorcery") }

        val finishers = listOf(
            Triple("Approach of the Second Sun", 7.0, "{5}{W}{W}") to "W", Triple("Elspeth, Sun's Champion", 6.0, "{4}{W}{W}") to "W",
            Triple("Jace, the Mind Sculptor", 4.0, "{2}{U}{U}") to "U", Triple("Frost Titan", 6.0, "{4}{U}{U}") to "U",
            Triple("Elesh Norn, Grand Cenobite", 6.0, "{4}{W}{W}") to "W", Triple("Iona, Shield of Emeria", 7.0, "{4}{W}{W}{W}") to "W",
        ).mapIndexed { i, (t, color) -> taggedSpell("fin-$i", t.first, t.second, t.third, listOf(color), CardTag.WIN_CON, "Creature — Sphinx") }

        val ramp = listOf(
            Triple("Sol Ring", 1.0, "{1}"), Triple("Arcane Signet", 1.0, "{1}"),
            Triple("Mind Stone", 2.0, "{2}"), Triple("Fellwar Stone", 2.0, "{2}"),
            Triple("Coldsteel Heart", 2.0, "{2}"), Triple("Solemn Simulacrum", 4.0, "{4}"),
            Triple("Wayfarer's Bauble", 1.0, "{1}"), Triple("Thran Dynamo", 4.0, "{4}"),
            Triple("Gilded Lotus", 5.0, "{5}"), Triple("Hedron Archive", 4.0, "{4}"),
            Triple("Prismatic Lens", 2.0, "{2}"),
        ).mapIndexed { i, (name, cmc, cost) -> taggedSpell("ramp-$i", name, cmc, cost, emptyList(), CardTag.RAMP, "Artifact") }

        val nonland = listOf(entry(commander)) + removal + wipes + counters + draw + finishers + ramp

        // 10 real UW duals (mana_fix full-confidence source, sits at the 2-color band's own max --
        // see ArchetypeData.COLOR_MODULATION) + 28 basics (14 Island/14 Plains).
        // KNOWN, ACCEPTED DEVIATION (documented, not "fixed"): this basics/dual split puts
        // basicsRatio at ~0.74, well above ArchetypeData.LAND_MIX's 2-color guideline (0.22-0.32),
        // which assumes a real Commander manabase's non-fixing "utility" nonbasic lands (the
        // colorless/single-color, non-Basic-typed lands real lists run alongside true duals) -- this
        // simplified fixture only models basics + true duals, so it under-represents that middle
        // category on purpose (keeping the fixture legible) rather than inventing filler land names.
        // The resulting Finding.LandMixOffTarget is INFO severity and only 10% of MANA_BASE's own
        // weight, so it's an accepted, minor, and INFORMATIVE gap, not a blocker.
        val duals = listOf(
            "Hallowed Fountain", "Tundra", "Prairie Stream", "Adarkar Wastes", "Irrigated Farmland",
            "Glacial Fortress", "Port Town", "Meandering River", "Azorius Chancery", "Azorius Guildgate",
        ).mapIndexed { i, name -> dualLand("dual-$i", name, "UW") }
        val basics = listOf(basicLands("Island", "U", 14), basicLands("Plains", "W", 14))
        return nonland + duals + basics
    }

    @Test
    fun controlRealisticDensity_scoresInWellBuiltDeckBand() {
        val mainboard = buildControlFixture()
        assertEquals100(mainboard)

        val colorIdentity = setOf(ManaColor.U, ManaColor.W)
        val profile = profileFor(mainboard, colorIdentity)
        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, profile = profile,
            archetype = ArchetypeId.CONTROL, themes = emptyList(), isManualOverride = true, confidence = 1f,
        )

        assertNoBlockers(analysis)
        // Actual achieved score: 77/100 (MANA_BASE 84 -- a genuine ColorSourceShortage WARNING on
        // both U/W from this fixture's deliberately mana-hungry cards, Cryptic Command UUU / Iona
        // WWW, against 24 sources each; CURVE 94; PLAN_ROLES 76; SYNERGY 27 -- this fixture was not
        // tuned for tag-fingerprint alignment, an accepted gap for a mana-base/curve calibration
        // fixture; LEGALITY 100).
        assertTrue(analysis.totalScore in 65..95, "CONTROL realistic-density score ${analysis.totalScore} outside the defensible well-built band")
        val planRoles = analysis.pillars.first { it.id == PillarId.PLAN_ROLES }
        assertTrue(planRoles.subscore >= 70, "PLAN_ROLES should no longer be starved at realistic density, was ${planRoles.subscore}")
    }

    /**
     * Karsten/curve-shape spot-check (calibration task item 5): CONTROL's real, higher-curve answer
     * suite (double/triple-pip wipes+counterspells+finishers) must average meaningfully higher CMC
     * than AGGRO's low-curve early-threat plan, and CONTROL must run more lands than AGGRO -- this is
     * the qualitative claim every published Commander archetype primer (Command Zone ep.658, EDHREC
     * archetype pages) makes, and the two resolved [CurveBand]/land anchors already encode it (AGGRO
     * curve ideal 2.1 / lands ideal 35 vs. CONTROL curve ideal 3.4 / lands ideal 38 -- see
     * [ArchetypeData.ARCHETYPES]). Computed directly off the mainboards (not the engine's own
     * subscores) so this is an independent check, not circular.
     */
    @Test
    fun controlVsAggro_curveAndLandCountDifferAsExpected() {
        val aggroBoard = buildAggroFixture()
        val controlBoard = buildControlFixture()

        val aggroAvgCmc = averageNonLandCmc(aggroBoard)
        val controlAvgCmc = averageNonLandCmc(controlBoard)
        assertTrue(
            controlAvgCmc > aggroAvgCmc,
            "CONTROL's avg CMC ($controlAvgCmc) should exceed AGGRO's ($aggroAvgCmc) -- higher curve is CONTROL's defining trait",
        )

        val aggroLands = landCount(aggroBoard)
        val controlLands = landCount(controlBoard)
        assertTrue(
            controlLands > aggroLands,
            "CONTROL should run more lands than AGGRO ($controlLands vs $aggroLands) -- documented Commander convention (control wants to hold up answers, aggro wants to spend mana on threats)",
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  Wave 2 / B6 — STANDARD (60-card) realistic-density calibration fixtures.
    //
    //  SAME discipline as the Commander fixtures above, adapted to a NON-singleton 60-card format
    //  (Standard's maxCopies=4 — see [DeckFormat.STANDARD]): each fixture uses a small number of
    //  DISTINCT real cards at up to 4 copies each (the natural shape of an actual Standard
    //  decklist — see the sourced example decklist below), not 37 singleton entries. Both fixtures
    //  total EXACTLY 60 cards and land inside the SAME documented 65-95 "well-built, on-plan deck"
    //  band the Commander fixtures use (default [AnalysisWeights], untouched by this task per its
    //  own constraint — this file only BUILDS/VERIFIES fixtures, it never retunes weights/caps/
    //  [SixtyFormatProfile.STANDARD]'s deltas).
    //
    //  CARD LEGALITY / DATING NOTE (task requirement — "note set codes/approximate authoring date
    //  so a future rotation explains any legality-test drift"): every card name below was
    //  cross-checked via a live web search against a dated, real decklist source at authoring time
    //  (2026-08-20) rather than invented or reused from a different format:
    //   - AGGRO (mono-Red): card names + roster shape sourced from a live mtggoldfish-derived
    //     Standard Mono-Red Aggro snapshot (per-card play-rate data, Aug 2026) surfaced via web
    //     search — mtggoldfish's own decklist pages themselves returned HTTP 403 to automated
    //     fetch (same block the B2 agent hit per this plan's own progress log), so the exact
    //     printed set code of each card was NOT independently re-verified against Scryfall; only
    //     the card's presence in a live, dated Aug-2026 Standard-legal decklist was confirmed.
    //   - CONTROL (Azorius/UW): card names + full 60-card decklist (creatures/instants/sorceries/
    //     enchantments/lands, quantities included) sourced from a dated (2026-08-07) CoolStuffInc
    //     article discussing an MTGO Standard Challenge decklist by pilot "BrianG88" — fetched
    //     directly (not blocked), so this list is the closest thing to a byte-exact real source
    //     among the two fixtures. Several of its cards belong to recent Universes Beyond
    //     crossover sets (Marvel / Avatar: The Last Airbender) that were genuinely Standard-legal
    //     at that date — real, currently-printed Magic cards, not invented names.
    //  If a future Standard rotation makes any of these cards illegal, that is EXPECTED drift, not
    //  a bug in this test — see [assertNoBlockers]'s own message and re-anchor the fixture against
    //  a fresh metagame snapshot rather than "fixing" the assertion in place.
    //
    //  Per this file's own established convention (see the class-level KDoc above), CMC/mana cost/
    //  power/toughness/typeLine are SIMPLIFIED to a plausible value for each card's real identity
    //  (not transcribed Scryfall-exact) — the goal is a realistic pip-intensity/role/curve
    //  DISTRIBUTION for the engine's Karsten/role/curve-shape checks to engage meaningfully, same
    //  as every other fixture in this file.
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Mono-Red Standard AGGRO — 21 creature copies (7 distinct real staples, up to 4 copies each)
     * + 16 spell copies (4 distinct real staples x4) + 23 Mountain, totalling exactly 60. Resolved
     * SIXTY/STANDARD AGGRO skeleton (Wave 2 B2: [SixtyFormatProfile.STANDARD], landsDelta=+2,
     * curveDelta=+0.3, layered on [ArchetypeData.ARCHETYPES]'s SIXTY AGGRO row): lands (21,23,25),
     * curve (1.7,2.1,2.5), threat_early (16,20,28), finisher (8,12,20), removal_spot (2,5,8),
     * card_draw (0,2,5), removal_mass anti-role tolerance max 1 — mono-color (bucket 1) draws no
     * [ColorRoleAffinity] identity modulation (every tabled role Red touches here is SECONDARY-
     * feasible, confirmed by reading the actual resolved skeleton, not assumed).
     */
    private fun buildStandardAggroFixture(): List<DeckEntry> {
        // 7 real cheap red creatures (structural threat_early signal: cmc<=3, power simplified to
        // 3 uniformly per this file's convention -- 0.9 confidence each). 4 of the 7 ALSO carry
        // CardTag.WIN_CON (Bloodthirsty Adversary/Charming Scoundrel/Feldon/Squee -- each has a
        // real "closes the game" mode: kicker reach damage, life-loss drain, or recursive
        // inevitability) so the deck clears SIXTY AGGRO's own finisher band without inventing
        // separate big-mana top-end cards a real lean aggro list would never run.
        data class AggroCreature(val name: String, val cmc: Double, val manaCost: String, val quantity: Int, val winCon: Boolean)
        val creatures = listOf(
            AggroCreature("Monastery Swiftspear", 1.0, "{R}", 4, false),
            AggroCreature("Phoenix Chick", 1.0, "{R}", 4, false),
            AggroCreature("Bloodthirsty Adversary", 2.0, "{1}{R}", 4, true),
            AggroCreature("Charming Scoundrel", 1.0, "{R}", 2, true),
            AggroCreature("Feldon, Ronom Excavator", 3.0, "{2}{R}", 2, true),
            AggroCreature("Squee, Dubious Monarch", 2.0, "{1}{R}", 3, true),
            AggroCreature("Goddric, Cloaked Reveler", 3.0, "{2}{R}", 2, false),
        ).mapIndexed { i, c ->
            entry(
                card(
                    id = "std-aggro-crea-$i", name = c.name, typeLine = "Creature — Warrior",
                    cmc = c.cmc, manaCost = c.manaCost, colors = listOf("R"), colorIdentity = listOf("R"),
                    power = "3", toughness = "2", tags = if (c.winCon) listOf(CardTag.WIN_CON) else emptyList(),
                ),
                quantity = c.quantity,
            )
        }

        // 4 real burn/tempo spells x4 -- removal_spot (Play with Fire / Lightning Strike) and 2
        // curve-fillers with no dedicated RoleKey (Witchstalker Frenzy / Kumano Faces Kakkazan --
        // both real Standard-legal cards per the sourced snapshot, included for a realistic curve
        // shape/count even though neither maps onto an Appendix A role this archetype tracks).
        data class AggroSpell(val name: String, val cmc: Double, val manaCost: String, val typeLine: String, val tags: List<CardTag>)
        val spells = listOf(
            AggroSpell("Play with Fire", 1.0, "{R}", "Instant", listOf(CardTag.REMOVAL)),
            AggroSpell("Lightning Strike", 2.0, "{1}{R}", "Instant", listOf(CardTag.REMOVAL)),
            AggroSpell("Witchstalker Frenzy", 3.0, "{2}{R}", "Sorcery", emptyList()),
            AggroSpell("Kumano Faces Kakkazan", 1.0, "{R}", "Enchantment — Saga", emptyList()),
        ).mapIndexed { i, s ->
            entry(
                card(
                    id = "std-aggro-spell-$i", name = s.name, typeLine = s.typeLine, cmc = s.cmc,
                    manaCost = s.manaCost, colors = listOf("R"), colorIdentity = listOf("R"), tags = s.tags,
                ),
                quantity = 4,
            )
        }

        return creatures + spells + basicLands("Mountain", "R", 23)
    }

    @Test
    fun standardAggroRealisticDensity_scoresInWellBuiltDeckBand() {
        val mainboard = buildStandardAggroFixture()
        assertTrue(mainboard.sumOf { it.quantity } == 60, "Standard fixture must total exactly 60 cards, was ${mainboard.sumOf { it.quantity }}")

        val colorIdentity = setOf(ManaColor.R)
        val profile = profileForStandard(mainboard, colorIdentity)
        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.STANDARD, colorIdentity = colorIdentity, profile = profile,
            archetype = ArchetypeId.AGGRO, themes = emptyList(), isManualOverride = true, confidence = 1f,
        )

        assertNoBlockers(analysis)
        // Same documented 65-95 "well-built, on-plan deck" band as the Commander fixtures (B6 task
        // constraint: this band is NOT retuned for Standard in this pass).
        // Actual achieved score: 86/100 (MANA_BASE 100, CURVE 100, PLAN_ROLES 77, SYNERGY 57,
        // LEGALITY 100). SYNERGY recorded here for the future P4/synergy retune workstream (Wave 2
        // plan A4/B6.5) -- NOT retuned in this pass.
        //
        // Scoring-semantics fix (2026-08-27) -- widened to 65-98. Like the Commander AGGRO sibling
        // above, this fixture has ZERO synergy edges (mono-red, no counterspell/protection/live
        // theme), so SYNERGY now correctly reads [PillarResult.notApplicable] instead of a false 0,
        // and its weight redistributes across the other 4. MEASURED (not re-derived to fit): MANA_BASE
        // 100, CURVE 100, PLAN_ROLES 95, SYNERGY not-applicable, LEGALITY 100 -> total 98. PLAN_ROLES
        // is the only pillar not already at the ceiling, which is why this fixture lands 2 points
        // under the Commander sibling's clean 100 rather than reaching it too.
        assertTrue(analysis.totalScore in 65..98, "Standard AGGRO realistic-density score ${analysis.totalScore} outside the defensible well-built band")
    }

    /**
     * Azorius/UW Standard CONTROL — full 60-card decklist (13 distinct real staples at realistic
     * quantities, sourced verbatim from a dated live decklist — see the class comment above) + 28
     * lands (12 real nonbasic UW-producing lands + 16 basics), totalling exactly 60. Resolved
     * SIXTY/STANDARD CONTROL skeleton: lands (26,28,30), curve (3.0,3.5,4.2), counterspell
     * (10,13,16), removal_spot (5,8,12), removal_mass (4,5,7), card_draw (8,11,14), finisher
     * (3,5,8), threat_early (0,0,2) -- 2-color Azorius identity draws no [ColorRoleAffinity]
     * modulation either (every tabled role here is PRIMARY-feasible for at least one of U/W,
     * confirmed by reading the actual resolved skeleton).
     */
    private fun buildStandardControlFixture(): List<DeckEntry> {
        val creatures = listOf(
            entry(
                card(
                    id = "std-ctrl-wst", name = "Wan Shi Tong, Librarian", typeLine = "Legendary Creature — Owl Spirit",
                    cmc = 4.0, manaCost = "{2}{U}{U}", colors = listOf("U"), colorIdentity = listOf("U"),
                    power = "3", toughness = "4", tags = listOf(CardTag.DRAW_ENGINE, CardTag.WIN_CON),
                ),
                quantity = 2,
            ),
            entry(
                card(
                    id = "std-ctrl-capm", name = "Captain Marvel, Earth's Protector", typeLine = "Legendary Creature — Human Avenger",
                    cmc = 5.0, manaCost = "{3}{W}{W}", colors = listOf("W"), colorIdentity = listOf("W"),
                    power = "5", toughness = "5", tags = listOf(CardTag.WIN_CON),
                ),
                quantity = 2,
            ),
            entry(
                card(
                    id = "std-ctrl-ktc", name = "King T'Challa", typeLine = "Legendary Creature — Human Noble",
                    cmc = 4.0, manaCost = "{2}{W}{W}", colors = listOf("W"), colorIdentity = listOf("W"),
                    power = "4", toughness = "4", tags = listOf(CardTag.WIN_CON),
                ),
                quantity = 1,
            ),
            entry(
                card(
                    id = "std-ctrl-eoi", name = "Emeritus of Ideation", typeLine = "Creature — Human Wizard",
                    cmc = 2.0, manaCost = "{1}{U}", colors = listOf("U"), colorIdentity = listOf("U"),
                    power = "1", toughness = "3", tags = listOf(CardTag.DRAW_ENGINE),
                ),
                quantity = 1,
            ),
        )

        val counterspells = listOf(
            Triple("No More Lies", 3.0, "{1}{U}{U}") to 4,
            Triple("Spell Snare", 1.0, "{U}") to 3,
            Triple("Three Steps Ahead", 3.0, "{1}{U}{U}") to 3,
        ).mapIndexed { i, (t, qty) ->
            entry(
                card(
                    id = "std-ctrl-ctr-$i", name = t.first, typeLine = "Instant", cmc = t.second, manaCost = t.third,
                    colors = listOf("U"), colorIdentity = listOf("U"), tags = listOf(roleTag("counterspell")),
                ),
                quantity = qty,
            )
        }

        val removal = listOf(
            Triple("Get Lost", 2.0, "{1}{W}") to 4,
            Triple("Erode", 2.0, "{1}{W}") to 2,
        ).mapIndexed { i, (t, qty) ->
            entry(
                card(
                    id = "std-ctrl-rem-$i", name = t.first, typeLine = "Instant", cmc = t.second, manaCost = t.third,
                    colors = listOf("W"), colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL),
                ),
                quantity = qty,
            )
        }

        val wipes = listOf(
            Triple("Day of Judgment", 4.0, "{2}{W}{W}") to 3,
            Triple("Ultima", 5.0, "{3}{W}{W}") to 2,
        ).mapIndexed { i, (t, qty) ->
            entry(
                card(
                    id = "std-ctrl-wipe-$i", name = t.first, typeLine = "Sorcery", cmc = t.second, manaCost = t.third,
                    colors = listOf("W"), colorIdentity = listOf("W"), tags = listOf(CardTag.WRATH),
                ),
                quantity = qty,
            )
        }

        val draw = listOf(
            Triple("Stock Up", 2.0, "{1}{U}") to 4,
            Triple("Consult the Star Charts", 2.0, "{1}{U}") to 1,
        ).mapIndexed { i, (t, qty) ->
            entry(
                card(
                    id = "std-ctrl-draw-$i", name = t.first, typeLine = "Sorcery", cmc = t.second, manaCost = t.third,
                    colors = listOf("U"), colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE),
                ),
                quantity = qty,
            )
        }

        val nonland = creatures + counterspells + removal + wipes + draw

        // 12 real nonbasic UW-producing lands (4 distinct names, per the sourced decklist) + 16
        // basics (8 Island/8 Plains) = 28 lands, matching the resolved skeleton's own ideal.
        // KNOWN, ACCEPTED DEVIATION (documented, not "fixed" -- same convention as the Commander
        // CONTROL fixture above): basicsRatio ~0.571, slightly above [ArchetypeData.LAND_MIX]'s
        // SIXTY 2-color guideline (0.35-0.50) -- an INFO-severity, 10%-of-MANA_BASE-weight gap.
        val duals = listOf(
            "Hallowed Fountain" to 4, "Floodfarm Verge" to 4, "Gleaming Bastion" to 2, "Secluded Starforge" to 2,
        ).mapIndexed { i, (name, qty) -> dualLand("std-ctrl-dual-$i", name, "UW", quantity = qty) }
        val basics = listOf(basicLands("Island", "U", 8), basicLands("Plains", "W", 8))

        return nonland + duals + basics
    }

    @Test
    fun standardControlRealisticDensity_scoresInWellBuiltDeckBand() {
        val mainboard = buildStandardControlFixture()
        assertTrue(mainboard.sumOf { it.quantity } == 60, "Standard fixture must total exactly 60 cards, was ${mainboard.sumOf { it.quantity }}")

        val colorIdentity = setOf(ManaColor.U, ManaColor.W)
        val profile = profileForStandard(mainboard, colorIdentity)
        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.STANDARD, colorIdentity = colorIdentity, profile = profile,
            archetype = ArchetypeId.CONTROL, themes = emptyList(), isManualOverride = true, confidence = 1f,
        )

        assertNoBlockers(analysis)
        // Actual achieved score: 90/100 (MANA_BASE 99 -- a minor, accepted LandMixOffTarget INFO
        // from the basics/true-dual simplification noted above; CURVE 91 -- a genuine CurveOffBand
        // + CurveShapeMismatch(BACK) from this fixture's realistically cheap counterspell/removal
        // suite pulling the curve below CONTROL's resolved BACK-shape expectation, an accepted
        // minor deviation, same discipline as the Commander CONTROL fixture's own accepted gaps;
        // PLAN_ROLES 81 -- Phase 3b's weighted-mean/min==0 P3 rewrite moved this since the number
        // above was last measured, not touched by Phase 4, not re-derived here; SYNERGY 85 --
        // Deck Analysis Engine v3, PHASE 4 (spec §7) rescored this pillar onto the SynergyGraph
        // coverage/axisHealth/connectivity/consistency composite; the old "LowSynergyDensity"
        // finding this comment used to cite no longer exists (removed this phase, replaced by
        // spec §5.4's anti-synergy conflicts) -- this fixture's own SPELLS axis (26 producer
        // copies from its instant/sorcery-heavy removal/counterspell suite, 0 dedicated spell
        // payoffs) trips ONE OrphanProducers finding, same root cause already diagnosed on the
        // Commander UW Control fixture (spec §7's own worked example) in this phase's own gate
        // report; LEGALITY 100). SYNERGY is no longer flagged for a "future retune workstream" --
        // Phase 4 IS that retune.
        assertTrue(analysis.totalScore in 65..95, "Standard CONTROL realistic-density score ${analysis.totalScore} outside the defensible well-built band")
    }
}
