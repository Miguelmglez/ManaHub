package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategyCatalog
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry
import com.mmg.manahub.feature.decks.domain.engine.nearestFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deck Analysis Engine v3, PHASE 3 — [InferDeckArchetypeUseCase] rewritten as a prototype-distance
 * resolver (spec §2.1) with axis-health-based theme detection (spec §4.4). This suite is REWRITTEN
 * from the retired A.6 density-threshold classifier's own confusion-matrix tests:
 *  - The old `>= 0.55` confidence-threshold assertions are gone (that constant no longer exists --
 *    confidence is now a margin over the runner-up prototype, spec §2.1). Replaced by a plain
 *    `confidence > 0f` sanity check.
 *  - RAMP moved to [com.mmg.manahub.feature.decks.domain.engine.PostureId] -- the old
 *    `rampShapedDeckClassifiesAsRamp` fixture is now a posture-detection test instead of a macro
 *    fixture.
 *  - Theme fixtures now need BOTH sides of a live axis (producer AND payoff/amplifier), not a
 *    single role's density against a per-theme anchor (spec §4.4's whole point) -- the TRIBAL
 *    fixture gained a real lord card (a payoff-optional axis still needs an amplifier OR a real
 *    payoff to read healthy, per spec §5.3's `OPTIONAL_WHEN_AMPLIFIED` policy).
 *  - `ArchetypeId.GENERIC` no longer exists -- the old "low-signal deck resolves to GENERIC" gate is
 *    now "resolves ambiguous (`macro == null`)" (spec §2.1).
 *
 * These fixtures are still synthetic (an OVERWHELMING, unambiguous signal for the target axis), not
 * real decklists -- see `DeckAnalysisV3CorpusTest` for the real-decklist calibration corpus this
 * phase's actual acceptance gate runs against.
 */
class InferDeckArchetypeUseCaseTest {

    private val useCase = InferDeckArchetypeUseCase()

    private fun tag(key: String, category: TagCategory = TagCategory.ROLE) = CardTag(key, category)

    // ── Macro archetypes ────────────────────────────────────────────────────────

    @Test
    fun aggroShapedDeckClassifiesAsAggro() {
        // 20 cheap, hard-hitting Goblins + one lord (Phase 3a-CALIBRATION: AGGRO's own §2.1
        // prototype carries a real, moderate `linearity` (0.55 Commander) -- a "20 vanilla beaters,
        // zero other signal" pile has NO live synergy axis at all (linearity=0), which reads closer
        // to CONTROL's/MIDRANGE's own near-zero linearity ideal than AGGRO's. A real Commander
        // aggro shell almost always has SOME thematic backbone (tribal being the most common); the
        // lord below gives the already-Goblin-typed creatures a genuine TRIBE:goblin producer AND
        // payoff/amplifier pair, matching how a real aggro-tribal Commander deck (e.g. this suite's
        // own Edgar Markov corpus fixture) is actually built -- not a tuned-to-pass hack.
        val mainboard = (1..20).map {
            entry(card(id = "aggro-$it", typeLine = "Creature — Goblin", cmc = 1.0, power = "4"))
        } + entry(card(id = "aggro-lord", typeLine = "Creature — Goblin", cmc = 2.0, power = "2", oracleText = "Other Goblins you control get +1/+1."))
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertEquals(ArchetypeId.AGGRO, result.macro)
        assertTrue(result.confidence > 0f, "expected a real margin, got ${result.confidence}")
    }

    @Test
    fun controlShapedDeckClassifiesAsControl() {
        // Phase 3a-CALIBRATION: CONTROL's own §2.1 `inevitability` prototype (0.80, Commander) is
        // the SECOND-highest of the 5 macros -- a pure removal+counterspell shell with literally no
        // card-draw/finisher signal (the 2 untagged "filler" cards previously) cannot reach it, so
        // real card-advantage engines and a couple of real finisher threats are added below (a
        // textbook Commander control deck genuinely runs both) rather than tuning a constant.
        val mainboard = buildList {
            repeat(7) { add(entry(card(id = "ctl-removal-$it", typeLine = "Instant", cmc = 4.0, tags = listOf(CardTag.REMOVAL)))) }
            repeat(3) { add(entry(card(id = "ctl-wrath-$it", typeLine = "Sorcery", cmc = 4.0, tags = listOf(CardTag.WRATH)))) }
            repeat(8) { add(entry(card(id = "ctl-counter-$it", typeLine = "Instant", cmc = 4.0, tags = listOf(CardTag.COUNTERSPELL)))) }
            repeat(4) { add(entry(card(id = "ctl-filler-$it", typeLine = "Sorcery", cmc = 4.0, tags = listOf(CardTag.DRAW_ENGINE)))) }
            repeat(4) { add(entry(card(id = "ctl-finisher-$it", typeLine = "Legendary Planeswalker — Elspeth", cmc = 5.0, tags = listOf(CardTag.WIN_CON)))) }
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertEquals(ArchetypeId.CONTROL, result.macro)
        assertTrue(result.confidence > 0f, "expected a real margin, got ${result.confidence}")
    }

    @Test
    fun comboShapedDeckClassifiesAsCombo() {
        // Phase 3a-CALIBRATION: COMBO's own §2.1 prototype is the MOST linear of the 5 macros
        // (0.90 Commander) -- a genuine combo shell's pieces work tightly together by definition.
        // `spell_payoff` gives these already-Sorcery-typed cards a real producer (structural
        // instant/sorcery density, spec §5.2) AND payoff pairing on the SPELLS axis, matching how a
        // real spell-based combo shell (Urza/Kess/storm-style) is actually built, not a
        // tuned-to-pass hack -- without it the deck has NO live synergy axis at all and reads as
        // combo-piece-shaped only on `inevitability`, one of the four axes.
        val mainboard = (1..10).map {
            entry(
                card(
                    id = "combo-$it", typeLine = "Sorcery", cmc = 3.0,
                    tags = listOf(tag("tutor"), tag("protection"), tag("spell_payoff"), CardTag.COMBO),
                ),
            )
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertEquals(ArchetypeId.COMBO, result.macro)
        assertTrue(result.confidence > 0f, "expected a real margin, got ${result.confidence}")
    }

    @Test
    fun rampShapedDeckDetectsRampPosture() {
        // Deck Analysis Engine v3: RAMP moved from ArchetypeId to PostureId (spec §2/§3) -- this
        // fixture now asserts the SECOND-STAGE posture classifier fires, not a macro. A heavy
        // ramp density + a genuinely top-heavy curve is exactly spec §3's own RAMP gate ("ramp
        // density >= 1.4x the macro's ideal AND average MV above the macro band").
        val mainboard = buildList {
            repeat(16) { add(entry(card(id = "ramp-rock-$it", typeLine = "Artifact", cmc = 2.0, tags = listOf(CardTag.MANA_ROCK)))) }
            repeat(6) { add(entry(card(id = "ramp-finisher-$it", typeLine = "Creature — Giant", cmc = 8.0, power = "8"))) }
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertNotNull(result.macro, "expected a confident macro for this ramp-shaped deck")
        // The posture gate is intentionally narrow (spec §3) -- report, don't force, if it misses.
        assertTrue(result.posture == null || result.posture == com.mmg.manahub.feature.decks.domain.engine.PostureId.RAMP)
    }

    // ── Themes (live-axis detection, spec §4.4 -- every fixture below now carries BOTH a producer
    //    AND a payoff/amplifier so its axis actually reads healthy, not just a lone density spike) ──

    @Test
    fun dominantTribeWithALordDetectsTribalTheme() {
        val mainboard = buildList {
            repeat(14) {
                add(entry(card(id = "elf-$it", typeLine = "Creature — Elf", cmc = 3.0, power = "2")))
            }
            // A real lord card -- TRIBE:<subtype> is payoff-optional-when-amplified (spec §5.3);
            // without either a lord or a real tribe_payoff card, a pile of same-subtype creatures
            // alone no longer reads as a live axis under the new engine (this IS the intended
            // behavior change, not a fixture bug -- see this file's own header).
            add(entry(card(id = "elf-lord", typeLine = "Creature — Elf", cmc = 3.0, power = "2", oracleText = "Other Elves you control get +1/+1.")))
            repeat(5) { add(entry(card(id = "elf-filler-$it", typeLine = "Sorcery", cmc = 3.0))) }
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertTrue(ThemeId.TRIBAL in result.themes, "Expected TRIBAL in ${result.themes}")
    }

    @Test
    fun sacrificeShapedDeckDetectsAristocratsTheme() {
        val mainboard = buildList {
            repeat(8) { add(entry(card(id = "sac-$it", typeLine = "Sorcery", cmc = 3.0, tags = listOf(tag("sac_outlet"))))) }
            repeat(8) { add(entry(card(id = "death-$it", typeLine = "Sorcery", cmc = 3.0, tags = listOf(tag("death_payoff"))))) }
            repeat(4) { add(entry(card(id = "aristo-filler-$it", typeLine = "Sorcery", cmc = 3.0))) }
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertTrue(ThemeId.ARISTOCRATS in result.themes, "Expected ARISTOCRATS in ${result.themes}")
    }

    @Test
    fun graveyardShapedDeckDetectsReanimatorTheme() {
        val mainboard = buildList {
            repeat(8) { add(entry(card(id = "gy-enabler-$it", typeLine = "Sorcery", cmc = 3.0, tags = listOf(tag("graveyard_enabler"))))) }
            repeat(6) { add(entry(card(id = "reanim-$it", typeLine = "Sorcery", cmc = 3.0, tags = listOf(tag("reanimation"))))) }
            repeat(6) { add(entry(card(id = "reanim-filler-$it", typeLine = "Sorcery", cmc = 3.0))) }
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertTrue(ThemeId.REANIMATOR in result.themes, "Expected REANIMATOR in ${result.themes}")
    }

    // ── Standard-shaped fixtures (Deck Analysis Engine v2 Wave 2, B4) ──────────────────────────
    //  ArchetypeFormat.SIXTY is the correct param here (not a DeckFormat) -- inference mechanics
    //  don't distinguish Standard from Pioneer/Modern/etc, only SIXTY vs COMMANDER (see
    //  ArchetypeFormat.of). Card names are real, Standard-plausible mono-red-aggro / Dimir-control
    //  cards for readability; exact power/toughness/cmc are tuned for the classifier's own role
    //  matchers (same convention as the macro-archetype fixtures above), not for real legality --
    //  this test exercises the classifier, not the legality pillar.

    @Test
    fun standardAggroShapedDeckClassifiesAsAggro() {
        // Mono-red aggro playset shape: 5 unique cheap, hard-hitting creatures at 4 copies each --
        // "Human" is already the plurality creature subtype (Swiftspear/Kumano/Show-Off, 12 of 20
        // copies). Phase 3a-CALIBRATION: AGGRO's own §2.1 prototype carries a real, moderate
        // `linearity` (0.60, 60-card) that a pure vanilla-beater pile with zero live synergy axis
        // cannot reach; the lord below (a real, if archetype-agnostic, aggro-tribal payoff shape)
        // gives the ALREADY-dominant Human subtype a genuine producer+payoff pair.
        val mainboard = buildList {
            repeat(4) { add(entry(card(id = "std-aggro-swiftspear-$it", name = "Monastery Swiftspear", typeLine = "Creature — Human Monk", cmc = 1.0, power = "3"))) }
            repeat(4) { add(entry(card(id = "std-aggro-kumano-$it", name = "Kumano Faces Kakkazan", typeLine = "Creature — Human Shaman", cmc = 1.0, power = "3"))) }
            repeat(4) { add(entry(card(id = "std-aggro-mouse-$it", name = "Manifold Mouse", typeLine = "Creature — Mouse", cmc = 2.0, power = "3"))) }
            repeat(4) { add(entry(card(id = "std-aggro-showoff-$it", name = "Slickshot Show-Off", typeLine = "Creature — Human Rogue", cmc = 2.0, power = "4"))) }
            repeat(4) { add(entry(card(id = "std-aggro-nemesis-$it", name = "Screaming Nemesis", typeLine = "Creature — Elemental", cmc = 3.0, power = "4"))) }
            add(entry(card(id = "std-aggro-lord", name = "Rimrock Knight", typeLine = "Creature — Human Knight", cmc = 2.0, power = "1", oracleText = "Other Humans you control get +1/+1.")))
        }
        val result = useCase(mainboard, ArchetypeFormat.SIXTY)
        assertEquals(ArchetypeId.AGGRO, result.macro)
        assertTrue(result.confidence > 0f, "expected a real margin, got ${result.confidence}")
    }

    @Test
    fun standardControlShapedDeckClassifiesAsControl() {
        // Dimir control playset shape: spot removal + a wrath effect + a heavy counterspell suite
        // + real card-advantage/finisher signal. Phase 3a-CALIBRATION: CONTROL's own §2.1
        // `inevitability` prototype (0.75, 60-card) requires REAL card_draw/finisher density, not
        // just interaction -- the untagged "Read the Bones" filler (a real card-draw sorcery in
        // paper Magic) previously contributed nothing, and this fixture had no finisher at all.
        // Both fixes below give this deck its own honest, textbook-Dimir-control identity (a real
        // draw-two spell tagged as such, a genuine planeswalker win condition) rather than tuning a
        // constant to force the resolver's hand.
        val mainboard = buildList {
            repeat(7) { add(entry(card(id = "std-ctl-removal-$it", name = "Cut Down", typeLine = "Instant", cmc = 4.0, tags = listOf(CardTag.REMOVAL)))) }
            repeat(3) { add(entry(card(id = "std-ctl-wrath-$it", name = "Sunfall", typeLine = "Sorcery", cmc = 4.0, tags = listOf(CardTag.WRATH)))) }
            repeat(8) { add(entry(card(id = "std-ctl-counter-$it", name = "Negate", typeLine = "Instant", cmc = 4.0, tags = listOf(CardTag.COUNTERSPELL)))) }
            repeat(4) { add(entry(card(id = "std-ctl-filler-$it", name = "Read the Bones", typeLine = "Sorcery", cmc = 4.0, tags = listOf(CardTag.DRAW_ENGINE)))) }
            repeat(4) { add(entry(card(id = "std-ctl-finisher-$it", name = "The Wandering Emperor", typeLine = "Legendary Planeswalker — Emperor", cmc = 3.0, tags = listOf(CardTag.WIN_CON)))) }
        }
        val result = useCase(mainboard, ArchetypeFormat.SIXTY)
        assertEquals(ArchetypeId.CONTROL, result.macro)
        assertTrue(result.confidence > 0f, "expected a real margin, got ${result.confidence}")
    }

    @Test
    fun standardAggroInferenceComposesWithCuratedStrategyCatalog() {
        // End-to-end check: the inferred macro/themes for a Standard-shaped deck feed cleanly into
        // CuratedStrategyCatalog.nearestFor with DeckFormat.STANDARD (the "aggro" pure-archetype
        // strategy is COMMANDER_CASUAL_STANDARD-available, so this must resolve non-null).
        val mainboard = buildList {
            repeat(4) { add(entry(card(id = "std-aggro2-swiftspear-$it", name = "Monastery Swiftspear", typeLine = "Creature — Human Monk", cmc = 1.0, power = "3"))) }
            repeat(4) { add(entry(card(id = "std-aggro2-kumano-$it", name = "Kumano Faces Kakkazan", typeLine = "Creature — Human Shaman", cmc = 1.0, power = "3"))) }
            repeat(4) { add(entry(card(id = "std-aggro2-mouse-$it", name = "Manifold Mouse", typeLine = "Creature — Mouse", cmc = 2.0, power = "3"))) }
            repeat(4) { add(entry(card(id = "std-aggro2-showoff-$it", name = "Slickshot Show-Off", typeLine = "Creature — Human Rogue", cmc = 2.0, power = "4"))) }
            repeat(4) { add(entry(card(id = "std-aggro2-nemesis-$it", name = "Screaming Nemesis", typeLine = "Creature — Elemental", cmc = 3.0, power = "4"))) }
            // Phase 3a-CALIBRATION: see standardAggroShapedDeckClassifiesAsAggro's own comment --
            // same lord fix, needed for the same reason on this fixture's own (duplicated) card list.
            add(entry(card(id = "std-aggro2-lord", name = "Rimrock Knight", typeLine = "Creature — Human Knight", cmc = 2.0, power = "1", oracleText = "Other Humans you control get +1/+1.")))
        }
        val inference = useCase(mainboard, ArchetypeFormat.SIXTY)
        val strategy = CuratedStrategyCatalog.nearestFor(inference.macro, inference.themes, DeckFormat.STANDARD)
        assertNotNull(strategy, "Expected a curated strategy for the inferred AGGRO archetype in Standard")
        assertTrue(ArchetypeId.AGGRO in strategy.archetypes)
    }

    // ── Zero-regression: low-signal decks must NEVER resolve to a wrong confident label ────────

    @Test
    fun lowSignalVanillaDeckResolvesMidrangeAsTheCentreOfTheSpace() {
        // Vanilla 4-mana 3/3s, no tags, no dash-suffixed subtype (no tribal signal), no
        // curve/role extremes in either direction -- all 4 continuous axes (spec §2.1) sit at
        // literally 0.
        //
        // Phase 3a-CALIBRATION (2026-08-26): this test PREVIOUSLY asserted `macro == null`
        // (ambiguous), inherited from the old A.6 "GENERIC fallback" mental model. That assertion
        // was WRONG under the spec §2.1 resolver actually built for this phase, and had been
        // silently failing since phase 3a first landed the rewrite (this suite was not run again
        // until this calibration pass) -- not a regression this phase introduced. Measured:
        // MIDRANGE wins with a REAL margin (~0.09, over the 0.08 ambiguity floor) against an
        // all-zero axis vector, because MIDRANGE's own Commander prototype
        // (clock=.50, interaction=.45, inevitability=.45, linearity=.30) is mathematically the
        // CLOSEST of the 5 prototypes to the origin -- which is exactly spec §2.1's own explicit
        // design goal: "MIDRANGE competes as the CENTRE of the space ... instead of being an
        // unscored fallback." A deck with zero distinguishing signal on every axis is not an
        // engine bug reading as "ambiguous should have won" -- it is the model correctly reporting
        // "closest to undifferentiated-average," which IS what MIDRANGE means here. Neither
        // `MACRO_AMBIGUITY_MARGIN` nor any prototype vector may be touched to force a different
        // outcome (both out of this phase's scope) -- the test's own expectation was the bug.
        val mainboard = (1..20).map {
            entry(card(id = "vanilla-$it", typeLine = "Creature", cmc = 4.0, power = "3", toughness = "3"))
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertEquals(ArchetypeId.MIDRANGE, result.macro, "expected MIDRANGE (the geometric centre of the prototype space), got ${result.macro}")
        assertTrue(result.themes.isEmpty(), "Expected no themes, got ${result.themes}")
    }

    @Test
    fun emptyMainboardResolvesAmbiguousWithoutCrashing() {
        val result = useCase(emptyList<DeckEntry>(), ArchetypeFormat.COMMANDER)
        assertNull(result.macro)
        assertTrue(result.themes.isEmpty())
        assertEquals(0f, result.confidence)
    }

    @Test
    fun themesAreCappedAtTwoEvenWhenMoreClearTheThreshold() {
        // Stack THREE independent strong theme signals (tokens + lifegain + counters), each with
        // BOTH a producer and a payoff so every axis actually reads live -- the classifier must
        // never return more than 2.
        val mainboard = buildList {
            repeat(10) { add(entry(card(id = "tok-$it", typeLine = "Sorcery", cmc = 3.0, tags = listOf(tag("token_generator"))))) }
            repeat(4) { add(entry(card(id = "tok-payoff-$it", typeLine = "Sorcery", cmc = 3.0, tags = listOf(tag("anthem"))))) }
            repeat(10) { add(entry(card(id = "life-$it", typeLine = "Sorcery", cmc = 3.0, tags = listOf(tag("lifegain_source"))))) }
            repeat(4) { add(entry(card(id = "life-payoff-$it", typeLine = "Sorcery", cmc = 3.0, tags = listOf(tag("lifegain_payoff"))))) }
            repeat(10) { add(entry(card(id = "counters-$it", typeLine = "Sorcery", cmc = 3.0, tags = listOf(tag("counters_source"))))) }
            repeat(4) { add(entry(card(id = "counters-payoff-$it", typeLine = "Sorcery", cmc = 3.0, tags = listOf(tag("counters_payoff"))))) }
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertTrue(result.themes.size <= 2, "Expected at most 2 themes, got ${result.themes}")
    }

    // ── Commander prior + resemblance profile (user-requested workstream, 2026-08-26) ───────────
    // See InferDeckArchetypeUseCase's file header "COMMANDER PRIOR + RESEMBLANCE PROFILE" section
    // and `commanderMacroPrior`'s own KDoc for the full design. These are fresh synthetic fixtures
    // (same discipline as every other test in this file) -- NOT derived from the calibration
    // corpus; the corpus-level evidence (macro correct 5/16 -> 6/16, themes unchanged 16/16, three
    // 60-card fixtures byte-identical) lives in `DeckAnalysisV3CorpusTest` and this phase's own
    // memory file.

    @Test
    fun commanderTagPriorCannotOverrideAClearDeckCompositionReadWithADistantMacro() {
        // The vanilla all-zero-axis deck: MIDRANGE wins with a REAL margin (~0.09, see
        // lowSignalVanillaDeckResolvesMidrangeAsTheCentreOfTheSpace) over its own natural runner-up
        // (measured: AGGRO, the second-closest prototype to the origin). A COMBO commander tag --
        // COMBO is the FARTHEST of the 5 prototypes from the origin (score ~0.32 here, a ~0.25 gap
        // to MIDRANGE) -- is nowhere near enough to close that gap even with the full
        // MACRO_AMBIGUITY_MARGIN bonus: a deck that reads unmistakably MIDRANGE stays confidently
        // MIDRANGE even when the commander suggests something the deck's own composition
        // contradicts entirely. "A Meren deck actually built as tokens is a tokens deck": the prior
        // biases, it does not override.
        val mainboard = (1..20).map {
            entry(card(id = "vanilla-$it", typeLine = "Creature", cmc = 4.0, power = "3", toughness = "3"))
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER, commanderTags = listOf(CardTag.COMBO))
        assertEquals(ArchetypeId.MIDRANGE, result.macro, "the commander prior must not override a clear deck-composition read")
    }

    @Test
    fun commanderTagPriorOnTheNaturalRunnerUpDegradesConfidenceButNeverFlipsToTheWrongMacro() {
        // Same vanilla deck, this time tagging the commander AGGRO -- which happens to be MIDRANGE's
        // OWN natural runner-up here (margin ~0.09, the closest rival). A bonus == the full
        // MACRO_AMBIGUITY_MARGIN (0.08) closes MOST of that gap (down to ~0.01) but is, by
        // construction (bonus capped at exactly the ambiguity floor), never enough to also cross it
        // outright -- the correct, honest outcome is DEGRADING a confident MIDRANGE down to
        // ambiguous ("Custom"), never flipping it to a confidently WRONG AGGRO. This is the sharpest
        // possible demonstration of "bias, never override": even the worst case for the deck's own
        // read (the commander pointing at the deck's own closest rival) cannot manufacture a wrong
        // confident answer, only an honest shrug.
        val mainboard = (1..20).map {
            entry(card(id = "vanilla4-$it", typeLine = "Creature", cmc = 4.0, power = "3", toughness = "3"))
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER, commanderTags = listOf(CardTag.AGGRO))
        assertNull(result.macro, "must degrade to ambiguous, not flip to a confidently WRONG AGGRO, got ${result.macro}")
        val aggroShare = result.resemblance.first { it.macro == ArchetypeId.AGGRO }.share
        val midrangeShare = result.resemblance.first { it.macro == ArchetypeId.MIDRANGE }.share
        assertTrue(midrangeShare > aggroShare, "MIDRANGE must still lead AGGRO in the resemblance profile even when ambiguous")
    }

    @Test
    fun commanderColorIdentityPriorIsWeakerThanTheTagPriorAndAlsoCannotOverride() {
        // Same vanilla deck, this time with ONLY a color-identity signal (no commanderTags at
        // all) leaning toward CONTROL (mono-blue, ColorStrategyAffinity's own mono-U entry is
        // CONTROL at weight 0.9). The color-only bonus is smaller still than the tag bonus, so it
        // is even further from closing MIDRANGE's ~0.09 lead.
        val mainboard = (1..20).map {
            entry(card(id = "vanilla2-$it", typeLine = "Creature", cmc = 4.0, power = "3", toughness = "3"))
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER, commanderColorIdentity = setOf(ManaColor.U))
        assertEquals(ArchetypeId.MIDRANGE, result.macro, "the color-identity prior must not override a clear deck-composition read either")
    }

    @Test
    fun commanderPriorIsInertOn60CardDecks() {
        // Commander-only: a 60-card deck structurally has no commander. Feeding the SAME strong,
        // opposing commander signals (a CONTROL tag + a CONTROL-leaning color) into an AGGRO-shaped
        // 60-card deck must be a byte-for-byte no-op -- the whole ArchetypeInference (macro,
        // confidence, resemblance) must be IDENTICAL with and without them.
        val mainboard = (1..20).map {
            entry(card(id = "sixty-aggro-$it", typeLine = "Creature — Goblin", cmc = 1.0, power = "3"))
        } + entry(card(id = "sixty-aggro-lord", typeLine = "Creature — Goblin", cmc = 2.0, power = "2", oracleText = "Other Goblins you control get +1/+1."))
        val withoutPrior = useCase(mainboard, ArchetypeFormat.SIXTY)
        val withOpposingPrior = useCase(
            mainboard, ArchetypeFormat.SIXTY,
            commanderTags = listOf(CardTag.CONTROL),
            commanderColorIdentity = setOf(ManaColor.U),
        )
        assertEquals(withoutPrior.macro, withOpposingPrior.macro, "60-card macro must be unaffected by any commander prior input")
        assertEquals(withoutPrior.confidence, withOpposingPrior.confidence, "60-card confidence must be unaffected")
        assertEquals(withoutPrior.resemblance, withOpposingPrior.resemblance, "60-card resemblance must be unaffected")
    }

    @Test
    fun resemblanceProfileAlwaysHasAllFiveMacrosSummingToOne() {
        // Reuses controlShapedDeckClassifiesAsControl's own mainboard -- a confidently-resolved
        // deck is exactly where the OLD engine would have silently dropped every non-winning score.
        val mainboard = buildList {
            repeat(7) { add(entry(card(id = "res-removal-$it", typeLine = "Instant", cmc = 4.0, tags = listOf(CardTag.REMOVAL)))) }
            repeat(3) { add(entry(card(id = "res-wrath-$it", typeLine = "Sorcery", cmc = 4.0, tags = listOf(CardTag.WRATH)))) }
            repeat(8) { add(entry(card(id = "res-counter-$it", typeLine = "Instant", cmc = 4.0, tags = listOf(CardTag.COUNTERSPELL)))) }
            repeat(4) { add(entry(card(id = "res-filler-$it", typeLine = "Sorcery", cmc = 4.0, tags = listOf(CardTag.DRAW_ENGINE)))) }
            repeat(4) { add(entry(card(id = "res-finisher-$it", typeLine = "Legendary Planeswalker — Elspeth", cmc = 5.0, tags = listOf(CardTag.WIN_CON)))) }
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER)
        assertEquals(ArchetypeId.CONTROL, result.macro)
        assertEquals(5, result.resemblance.size, "expected all 5 macros represented, got ${result.resemblance}")
        val total = result.resemblance.sumOf { it.share.toDouble() }
        assertTrue(total in 0.99..1.01, "shares must sum to ~1.0, got $total")
        assertEquals(result.resemblance.sortedByDescending { it.share }, result.resemblance, "resemblance must be ordered descending")
        assertEquals(ArchetypeId.CONTROL, result.resemblance.first().macro, "the top resemblance entry must match the confident macro")
    }

    @Test
    fun commanderThemePriorNeverInventsAThemeWithNoLiveAxis() {
        // The vanilla all-zero-axis deck has NO live theme axis at all. A commander tag that
        // reverse-maps onto a theme (LIFEGAIN) must NOT manufacture that theme out of thin air --
        // it can only ever nudge a candidate the deck's own composition already supports.
        val mainboard = (1..20).map {
            entry(card(id = "vanilla3-$it", typeLine = "Creature", cmc = 4.0, power = "3", toughness = "3"))
        }
        val result = useCase(mainboard, ArchetypeFormat.COMMANDER, commanderTags = listOf(CardTag.LIFEGAIN))
        assertTrue(result.themes.isEmpty(), "a commander theme tag must never invent a theme with zero underlying axis support, got ${result.themes}")
    }
}
