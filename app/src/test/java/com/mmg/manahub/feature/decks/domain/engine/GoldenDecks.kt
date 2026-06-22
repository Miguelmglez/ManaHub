package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory

// ═══════════════════════════════════════════════════════════════════════════════
//  GoldenDecks — Phase 0 golden-deck fixture library
//
//  A small corpus of hand-built, realistic decks used by GoldenDeckHarnessTest to
//  assert RELATIVE quality invariants (e.g. "broken < tuned") rather than exact
//  numbers. Cards are built from the shared `card()` / `landCard()` helpers in
//  EngineFixtures with realistic oracleText / typeLine / cmc / colors / tags so the
//  engine (RoleClassifier + DeckScorer) sees production-like input.
//
//  Power signal: the harness uses EdhrecPowerResolver backed by Card.edhrecRank so
//  that staple-vs-jank ordering is meaningful. Where a deck needs an EDHREC rank,
//  the fixture sets it explicitly via `goldenCard(edhrecRank = ...)`.
//
//  All cards/decks here are intentionally minimal but coherent: enough role/tag/
//  color signal for the engine to classify and score them, not a literal Scryfall
//  dump.
// ═══════════════════════════════════════════════════════════════════════════════

/** A GoldenDeck bundles a mainboard with the format + color identity to score it. */
data class GoldenDeck(
    val name: String,
    val format: DeckFormat,
    val colorIdentity: Set<ManaColor>,
    val mainboard: List<DeckEntry>,
    /** Optional strategy seed tags (e.g. an inferred commander identity). */
    val seedTags: List<CardTag> = emptyList(),
) {
    /** Non-land entries only — convenience for evaluate() / density assertions. */
    val nonLand: List<DeckEntry>
        get() = mainboard.filterNot {
            it.card.typeLine.contains("Land", ignoreCase = true)
        }

    /** Flattened scryfallIds of every entry (each id appears once). */
    val cardIds: List<String> get() = mainboard.map { it.card.scryfallId }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Card helper with an EDHREC rank (EngineFixtures.card() does not expose it).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Like [card] but also sets [edhrecRank] (lower = more played) so the harness can
 * use [EdhrecPowerResolver]. Staples get a low rank; filler gets a high rank.
 */
fun goldenCard(
    id: String,
    name: String = id,
    typeLine: String = "Instant",
    cmc: Double = 2.0,
    colors: List<String> = listOf("U"),
    colorIdentity: List<String> = colors,
    oracleText: String? = null,
    power: String? = null,
    toughness: String? = null,
    tags: List<CardTag> = emptyList(),
    legalityStandard: String = "legal",
    legalityCommander: String = "legal",
    legalityPioneer: String = "legal",
    legalityModern: String = "legal",
    legalityLegacy: String = "legal",
    legalityVintage: String = "legal",
    legalityPauper: String = "legal",
    gameChanger: Boolean = false,
    manaCost: String? = null,
    edhrecRank: Int? = null,
) = card(
    id = id,
    name = name,
    typeLine = typeLine,
    cmc = cmc,
    colors = colors,
    colorIdentity = colorIdentity,
    oracleText = oracleText,
    power = power,
    toughness = toughness,
    tags = tags,
    legalityStandard = legalityStandard,
    legalityCommander = legalityCommander,
    legalityPioneer = legalityPioneer,
    legalityModern = legalityModern,
    legalityLegacy = legalityLegacy,
    legalityVintage = legalityVintage,
    legalityPauper = legalityPauper,
    gameChanger = gameChanger,
    manaCost = manaCost,
).copy(edhrecRank = edhrecRank)

/** A creature subtype tribal tag (the per-tribe CardTags are commented out upstream). */
fun tribeTag(tribe: String): CardTag = CardTag(tribe.lowercase(), TagCategory.TRIBAL)

// ─────────────────────────────────────────────────────────────────────────────
//  Reusable, realistically-tagged staple cards (well-known Commander pieces).
//  Each carries the tag that production tagging would assign, plus realistic
//  oracle text so the oracle fallback is also exercised.
// ─────────────────────────────────────────────────────────────────────────────

object GoldenStaples {

    // ── Ramp ────────────────────────────────────────────────────────────────
    fun solRing() = goldenCard(
        id = "sol-ring", name = "Sol Ring", typeLine = "Artifact",
        cmc = 1.0, colors = emptyList(), colorIdentity = emptyList(),
        oracleText = "{T}: Add {C}{C}.", tags = listOf(CardTag.MANA_ROCK),
        gameChanger = true, edhrecRank = 1, manaCost = "{1}",
    )

    fun arcaneSignet() = goldenCard(
        id = "arcane-signet", name = "Arcane Signet", typeLine = "Artifact",
        cmc = 2.0, colors = emptyList(), colorIdentity = emptyList(),
        oracleText = "{T}: Add one mana of any color in your commander's color identity.",
        tags = listOf(CardTag.MANA_ROCK), edhrecRank = 2, manaCost = "{2}",
    )

    fun cultivate() = goldenCard(
        id = "cultivate", name = "Cultivate", typeLine = "Sorcery",
        cmc = 3.0, colors = listOf("G"), colorIdentity = listOf("G"),
        oracleText = "Search your library for up to two basic land cards, reveal " +
            "those cards, put one onto the battlefield tapped and the other into " +
            "your hand, then shuffle.",
        tags = listOf(CardTag.RAMP), edhrecRank = 12, manaCost = "{2}{G}",
    )

    fun llanowarElves() = goldenCard(
        id = "llanowar-elves", name = "Llanowar Elves", typeLine = "Creature — Elf Druid",
        cmc = 1.0, colors = listOf("G"), colorIdentity = listOf("G"), power = "1", toughness = "1",
        oracleText = "{T}: Add {G}.", tags = listOf(CardTag.MANA_DORK, tribeTag("elf")),
        edhrecRank = 80, manaCost = "{G}",
    )

    // ── Card advantage ────────────────────────────────────────────────────────
    fun rhysticStudy() = goldenCard(
        id = "rhystic-study", name = "Rhystic Study", typeLine = "Enchantment",
        cmc = 3.0, colors = listOf("U"), colorIdentity = listOf("U"),
        oracleText = "Whenever an opponent casts a spell, you may draw a card unless " +
            "that player pays {1}.",
        tags = listOf(CardTag.DRAW_ENGINE), edhrecRank = 5, manaCost = "{2}{U}",
    )

    fun divination() = goldenCard(
        id = "divination", name = "Divination", typeLine = "Sorcery",
        cmc = 3.0, colors = listOf("U"), colorIdentity = listOf("U"),
        oracleText = "Draw two cards.", tags = listOf(CardTag.DRAW_ENGINE),
        edhrecRank = 600, manaCost = "{2}{U}",
    )

    // ── Spot removal ────────────────────────────────────────────────────────
    fun swordsToPlowshares() = goldenCard(
        id = "swords", name = "Swords to Plowshares", typeLine = "Instant",
        cmc = 1.0, colors = listOf("W"), colorIdentity = listOf("W"),
        oracleText = "Exile target creature. Its controller gains life equal to its power.",
        tags = listOf(CardTag.REMOVAL), edhrecRank = 3, manaCost = "{W}",
    )

    fun beastWithin() = goldenCard(
        id = "beast-within", name = "Beast Within", typeLine = "Instant",
        cmc = 3.0, colors = listOf("G"), colorIdentity = listOf("G"),
        oracleText = "Destroy target permanent. Its controller creates a 3/3 green " +
            "Beast creature token.",
        tags = listOf(CardTag.REMOVAL), edhrecRank = 40, manaCost = "{2}{G}",
    )

    // ── Board wipe ────────────────────────────────────────────────────────────
    fun wrathOfGod() = goldenCard(
        id = "wrath", name = "Wrath of God", typeLine = "Sorcery",
        cmc = 4.0, colors = listOf("W"), colorIdentity = listOf("W"),
        oracleText = "Destroy all creatures. They can't be regenerated.",
        tags = listOf(CardTag.WRATH), edhrecRank = 50, manaCost = "{2}{W}{W}",
    )

    // ── Interaction ───────────────────────────────────────────────────────────
    fun counterspell() = goldenCard(
        id = "counterspell", name = "Counterspell", typeLine = "Instant",
        cmc = 2.0, colors = listOf("U"), colorIdentity = listOf("U"),
        oracleText = "Counter target spell.", tags = listOf(CardTag.COUNTERSPELL),
        edhrecRank = 30, manaCost = "{U}{U}",
    )

    // ── Tutor ──────────────────────────────────────────────────────────────────
    fun demonicTutor() = goldenCard(
        id = "demonic-tutor", name = "Demonic Tutor", typeLine = "Sorcery",
        cmc = 2.0, colors = listOf("B"), colorIdentity = listOf("B"),
        oracleText = "Search your library for a card, put that card into your hand, " +
            "then shuffle.",
        tags = listOf(CardTag.TUTOR), edhrecRank = 8, manaCost = "{1}{B}",
    )

    // ── Payoff / win condition ──────────────────────────────────────────────────
    fun craterhoofBehemoth() = goldenCard(
        id = "craterhoof", name = "Craterhoof Behemoth", typeLine = "Creature — Beast",
        cmc = 8.0, colors = listOf("G"), colorIdentity = listOf("G"), power = "5", toughness = "5",
        oracleText = "When Craterhoof Behemoth enters the battlefield, creatures you " +
            "control gain trample and get +X/+X until end of turn, where X is the " +
            "number of creatures you control.",
        tags = listOf(CardTag.WIN_CON), edhrecRank = 120, manaCost = "{5}{G}{G}{G}",
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Land helpers — quantity packs.
// ─────────────────────────────────────────────────────────────────────────────

/** Basic-land subtype + name for a WUBRG colour symbol (drives production heuristics). */
private val BASIC_FOR_COLOR = mapOf(
    "W" to "Plains", "U" to "Island", "B" to "Swamp", "R" to "Mountain", "G" to "Forest",
)

/**
 * A correct basic land for [color]: the typeLine carries the real subtype (e.g.
 * "Basic Land — Plains") so [BasicLandCalculator.getProducedColors] / the mana-base
 * analyzer derive the right produced colour. (EngineFixtures.landCard hardcodes Island.)
 */
private fun basicLandCard(color: String, idPrefix: String): Card {
    val subtype = BASIC_FOR_COLOR[color] ?: "Wastes"
    return card(
        id = "$idPrefix-land",
        name = subtype,
        typeLine = "Basic Land — $subtype",
        cmc = 0.0,
        colors = emptyList(),
        colorIdentity = listOf(color),
    )
}

private fun lands(count: Int, color: String, idPrefix: String): List<DeckEntry> =
    listOf(entry(basicLandCard(color, idPrefix), quantity = count))

// ═══════════════════════════════════════════════════════════════════════════════
//  The golden decks
// ═══════════════════════════════════════════════════════════════════════════════

object GoldenDecks {

    // ── 1. A tuned, well-rounded Commander deck ───────────────────────────────
    //  Covers every Commander skeleton slot near its ideal: ramp, draw, spot
    //  removal, wipes, interaction, tutor, payoffs + 37 lands. This is the "good"
    //  baseline every broken deck must score below.
    fun tunedCommander(): GoldenDeck {
        val s = GoldenStaples
        val mainboard = buildList {
            // Ramp (~11)
            add(entry(s.solRing()))
            add(entry(s.arcaneSignet()))
            add(entry(s.cultivate()))
            add(entry(s.llanowarElves()))
            repeat(7) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "ramp-rock-$i", name = "Mana Rock $i", typeLine = "Artifact",
                            cmc = 3.0, colors = emptyList(), colorIdentity = emptyList(),
                            oracleText = "{T}: Add one mana of any color.",
                            tags = listOf(CardTag.MANA_ROCK), edhrecRank = 300 + i,
                            manaCost = "{3}",
                        ),
                    ),
                )
            }
            // Card advantage (~11)
            add(entry(s.rhysticStudy()))
            add(entry(s.divination()))
            repeat(9) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "draw-$i", name = "Draw Engine $i", typeLine = "Enchantment",
                            cmc = 3.0, colors = listOf("U"), colorIdentity = listOf("U"),
                            oracleText = "At the beginning of your upkeep, draw a card.",
                            tags = listOf(CardTag.DRAW_ENGINE), edhrecRank = 400 + i,
                            manaCost = "{2}{U}",
                        ),
                    ),
                )
            }
            // Spot removal (~8)
            add(entry(s.swordsToPlowshares()))
            add(entry(s.beastWithin()))
            repeat(6) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "removal-$i", name = "Removal $i", typeLine = "Instant",
                            cmc = 2.0, colors = listOf("B"), colorIdentity = listOf("B"),
                            oracleText = "Destroy target creature.",
                            tags = listOf(CardTag.REMOVAL), edhrecRank = 500 + i,
                            manaCost = "{1}{B}",
                        ),
                    ),
                )
            }
            // Board wipes (~4)
            add(entry(s.wrathOfGod()))
            repeat(3) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "wipe-$i", name = "Board Wipe $i", typeLine = "Sorcery",
                            cmc = 5.0, colors = listOf("W"), colorIdentity = listOf("W"),
                            oracleText = "Destroy all creatures.",
                            tags = listOf(CardTag.WRATH), edhrecRank = 600 + i,
                            manaCost = "{3}{W}{W}",
                        ),
                    ),
                )
            }
            // Interaction (~5)
            add(entry(s.counterspell()))
            repeat(4) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "interaction-$i", name = "Counter $i", typeLine = "Instant",
                            cmc = 2.0, colors = listOf("U"), colorIdentity = listOf("U"),
                            oracleText = "Counter target spell.",
                            tags = listOf(CardTag.COUNTERSPELL), edhrecRank = 700 + i,
                            manaCost = "{1}{U}",
                        ),
                    ),
                )
            }
            // Tutors (~2)
            add(entry(s.demonicTutor()))
            add(
                entry(
                    goldenCard(
                        id = "tutor-2", name = "Tutor 2", typeLine = "Sorcery",
                        cmc = 3.0, colors = listOf("B"), colorIdentity = listOf("B"),
                        oracleText = "Search your library for a card and put it into your hand.",
                        tags = listOf(CardTag.TUTOR), edhrecRank = 800,
                        manaCost = "{2}{B}",
                    ),
                ),
            )
            // Payoffs / threats (fill the rest of the 99)
            add(entry(s.craterhoofBehemoth()))
            repeat(7) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "payoff-$i", name = "Threat $i", typeLine = "Creature — Avatar",
                            cmc = (3 + i % 3).toDouble(), colors = listOf("G"),
                            colorIdentity = listOf("G"), power = "${4 + i % 2}", toughness = "4",
                            oracleText = "Trample.", tags = listOf(CardTag.WIN_CON),
                            edhrecRank = 900 + i, manaCost = "{2}{G}",
                        ),
                    ),
                )
            }
            // 37 lands (WUBG identity → use a green basic for the fixture)
            addAll(lands(37, "G", "tuned"))
        }
        return GoldenDeck(
            name = "Tuned Commander",
            format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.G),
            mainboard = mainboard,
        )
    }

    // ── 2. A cEDH-ish list: low curve, dense fast mana + interaction + tutors ──
    fun cedhCommander(): GoldenDeck {
        val s = GoldenStaples
        val mainboard = buildList {
            // Fast mana / ramp (cheap)
            add(entry(s.solRing()))
            add(entry(s.arcaneSignet()))
            add(entry(s.llanowarElves()))
            repeat(8) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "fast-mana-$i", name = "Fast Mana $i", typeLine = "Artifact",
                            cmc = 1.0, colors = emptyList(), colorIdentity = emptyList(),
                            oracleText = "{T}, Sacrifice this artifact: Add two mana of any one color.",
                            tags = listOf(CardTag.MANA_ROCK), edhrecRank = 20 + i,
                            manaCost = "{1}",
                        ),
                    ),
                )
            }
            // Tutors (dense — cEDH consistency)
            add(entry(s.demonicTutor()))
            repeat(6) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "cedh-tutor-$i", name = "Tutor $i", typeLine = "Instant",
                            cmc = 1.0, colors = listOf("B"), colorIdentity = listOf("B"),
                            oracleText = "Search your library for a card and put it into your hand.",
                            tags = listOf(CardTag.TUTOR), edhrecRank = 60 + i,
                            manaCost = "{B}",
                        ),
                    ),
                )
            }
            // Interaction (counters / cheap removal)
            add(entry(s.counterspell()))
            add(entry(s.swordsToPlowshares()))
            repeat(8) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "cedh-counter-$i", name = "Free Counter $i", typeLine = "Instant",
                            cmc = 1.0, colors = listOf("U"), colorIdentity = listOf("U"),
                            oracleText = "Counter target spell.",
                            tags = listOf(CardTag.COUNTERSPELL), edhrecRank = 90 + i,
                            manaCost = "{U}",
                        ),
                    ),
                )
            }
            // Card advantage
            add(entry(s.rhysticStudy()))
            repeat(5) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "cedh-draw-$i", name = "Draw $i", typeLine = "Instant",
                            cmc = 1.0, colors = listOf("U"), colorIdentity = listOf("U"),
                            oracleText = "Draw two cards.",
                            tags = listOf(CardTag.DRAW_ENGINE), edhrecRank = 110 + i,
                            manaCost = "{U}",
                        ),
                    ),
                )
            }
            // A compact combo core / win pieces
            add(
                entry(
                    goldenCard(
                        id = "combo-a", name = "Combo Piece A", typeLine = "Creature — Wizard",
                        cmc = 2.0, colors = listOf("U"), colorIdentity = listOf("U"),
                        power = "1", toughness = "1",
                        oracleText = "Infinite mana combo enabler.",
                        tags = listOf(CardTag.INFINITE, CardTag.WIN_CON), edhrecRank = 150,
                        manaCost = "{1}{U}",
                    ),
                ),
            )
            repeat(4) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "cedh-payoff-$i", name = "Payoff $i", typeLine = "Sorcery",
                            cmc = 2.0, colors = listOf("B"), colorIdentity = listOf("B"),
                            oracleText = "Each opponent loses life. You win the game if able.",
                            tags = listOf(CardTag.WIN_CON), edhrecRank = 160 + i,
                            manaCost = "{1}{B}",
                        ),
                    ),
                )
            }
            // Lean land base (cEDH runs fewer lands)
            addAll(lands(31, "U", "cedh"))
        }
        return GoldenDeck(
            name = "cEDH",
            format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.U, ManaColor.B, ManaColor.G, ManaColor.W),
            mainboard = mainboard,
        )
    }

    // ── 3. Elf tribal deck (Commander) — dense Elf creatures + tribal payoffs ──
    fun elfTribalCommander(): GoldenDeck {
        val elf = tribeTag("elf")
        val mainboard = buildList {
            // Many Elf creatures
            repeat(28) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "elf-$i", name = "Elf $i", typeLine = "Creature — Elf Warrior",
                            cmc = (1 + i % 3).toDouble(), colors = listOf("G"),
                            colorIdentity = listOf("G"), power = "${1 + i % 3}", toughness = "2",
                            oracleText = if (i % 5 == 0) {
                                "{T}: Add {G}."
                            } else {
                                "Other Elves you control get +1/+1."
                            },
                            tags = if (i % 5 == 0) {
                                listOf(CardTag.MANA_DORK, elf, CardTag.TRIBAL)
                            } else {
                                listOf(elf, CardTag.TRIBAL)
                            },
                            edhrecRank = 1000 + i, manaCost = "{G}",
                        ),
                    ),
                )
            }
            // Elf tribal payoffs / lords
            repeat(6) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "elf-lord-$i", name = "Elvish Lord $i",
                            typeLine = "Creature — Elf Cleric",
                            cmc = 3.0, colors = listOf("G"), colorIdentity = listOf("G"),
                            power = "2", toughness = "2",
                            oracleText = "Elves you control get +1/+1. Whenever an Elf you " +
                                "control enters, draw a card.",
                            tags = listOf(elf, CardTag.TRIBAL, CardTag.WIN_CON),
                            edhrecRank = 1100 + i, manaCost = "{1}{G}{G}",
                        ),
                    ),
                )
            }
            // A little support
            add(entry(GoldenStaples.cultivate()))
            add(entry(GoldenStaples.beastWithin()))
            add(entry(GoldenStaples.craterhoofBehemoth()))
            // Lands
            addAll(lands(37, "G", "elf"))
        }
        return GoldenDeck(
            name = "Elf Tribal",
            format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.G),
            mainboard = mainboard,
            seedTags = listOf(elf, CardTag.TRIBAL),
        )
    }

    // ── 4. Standard aggro (60-card) — low curve, many small threats ───────────
    fun standardAggro(): GoldenDeck {
        val mainboard = buildList {
            // 24 small aggressive creatures (power 2-3, cmc 1-2)
            repeat(6) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "aggro-1drop-$i", name = "One-Drop $i",
                            typeLine = "Creature — Human Soldier",
                            cmc = 1.0, colors = listOf("R"), colorIdentity = listOf("R"),
                            power = "2", toughness = "1", oracleText = "Haste.",
                            tags = listOf(CardTag.AGGRO), edhrecRank = null, manaCost = "{R}",
                        ),
                        quantity = 4,
                    ),
                )
            }
            // Burn / reach
            repeat(3) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "burn-$i", name = "Burn $i", typeLine = "Instant",
                            cmc = 1.0, colors = listOf("R"), colorIdentity = listOf("R"),
                            oracleText = "Lightning bolt deals 3 damage to any target.",
                            tags = listOf(CardTag.BURN, CardTag.REMOVAL),
                            edhrecRank = null, manaCost = "{R}",
                        ),
                        quantity = 4,
                    ),
                )
            }
            // 24 lands
            addAll(lands(24, "R", "aggro"))
        }
        return GoldenDeck(
            name = "Standard Aggro",
            format = DeckFormat.CASUAL,
            colorIdentity = setOf(ManaColor.R),
            mainboard = mainboard,
        )
    }

    // ── 5. Control deck (Standard) — removal + wipes + draw + few finishers ───
    fun standardControl(): GoldenDeck {
        val mainboard = buildList {
            // Spot removal
            repeat(2) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "ctrl-removal-$i", name = "Removal $i", typeLine = "Instant",
                            cmc = 2.0, colors = listOf("W"), colorIdentity = listOf("W"),
                            oracleText = "Destroy target creature.",
                            tags = listOf(CardTag.REMOVAL), manaCost = "{1}{W}",
                        ),
                        quantity = 4,
                    ),
                )
            }
            // Board wipes
            add(entry(GoldenStaples.wrathOfGod(), quantity = 3))
            // Card advantage
            add(entry(GoldenStaples.divination(), quantity = 4))
            // Counters
            add(entry(GoldenStaples.counterspell(), quantity = 4))
            // A couple of finishers
            add(
                entry(
                    goldenCard(
                        id = "ctrl-finisher", name = "Finisher",
                        typeLine = "Creature — Sphinx",
                        cmc = 6.0, colors = listOf("U"), colorIdentity = listOf("U"),
                        power = "5", toughness = "5", oracleText = "Flying. When this enters, draw two cards.",
                        tags = listOf(CardTag.WIN_CON), manaCost = "{4}{U}{U}",
                    ),
                    quantity = 3,
                ),
            )
            // 26 lands
            addAll(lands(26, "U", "ctrl"))
        }
        return GoldenDeck(
            name = "Standard Control",
            format = DeckFormat.CASUAL,
            colorIdentity = setOf(ManaColor.W, ManaColor.U),
            mainboard = mainboard,
        )
    }

    // ── 6a. Broken: NO removal, NO ramp, NO interaction (Commander) ───────────
    fun brokenNoInteraction(): GoldenDeck {
        val mainboard = buildList {
            // 62 vanilla, off-strategy filler creatures with no functional role
            repeat(62) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "vanilla-$i", name = "Vanilla $i",
                            typeLine = "Creature — Bear",
                            cmc = (2 + i % 4).toDouble(), colors = listOf("G"),
                            colorIdentity = listOf("G"), power = "2", toughness = "2",
                            oracleText = "Vigilance.", tags = emptyList(),
                            edhrecRank = 20000 + i, manaCost = "{2}{G}",
                        ),
                    ),
                )
            }
            addAll(lands(37, "G", "broken-noi"))
        }
        return GoldenDeck(
            name = "Broken (no interaction)",
            format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.G),
            mainboard = mainboard,
        )
    }

    // ── 6b. Broken: 20 board wipes (over-coverage) ─────────────────────────────
    fun brokenTwentyWipes(): GoldenDeck {
        val mainboard = buildList {
            add(
                entry(
                    goldenCard(
                        id = "wipe-spam", name = "Generic Wrath", typeLine = "Sorcery",
                        cmc = 4.0, colors = listOf("W"), colorIdentity = listOf("W"),
                        oracleText = "Destroy all creatures.",
                        tags = listOf(CardTag.WRATH), edhrecRank = 2000, manaCost = "{2}{W}{W}",
                    ),
                    quantity = 20,
                ),
            )
            // A handful of other cards so the deck isn't only wipes
            repeat(12) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "wipe-deck-filler-$i", name = "Filler $i",
                            typeLine = "Enchantment",
                            cmc = 3.0, colors = listOf("W"), colorIdentity = listOf("W"),
                            oracleText = "You gain 1 life at the beginning of your upkeep.",
                            tags = emptyList(), edhrecRank = 21000 + i, manaCost = "{2}{W}",
                        ),
                    ),
                )
            }
            addAll(lands(37, "W", "broken-wipes"))
        }
        return GoldenDeck(
            name = "Broken (20 wipes)",
            format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.W),
            mainboard = mainboard,
        )
    }

    // ── 6c. Broken: 25 lands in a Commander deck (mana-screw / too few lands) ──
    fun brokenTwentyFiveLands(): GoldenDeck {
        val s = GoldenStaples
        val mainboard = buildList {
            // Reuse the tuned spell suite so ONLY the land count is wrong.
            add(entry(s.solRing()))
            add(entry(s.cultivate()))
            add(entry(s.rhysticStudy()))
            add(entry(s.swordsToPlowshares()))
            add(entry(s.wrathOfGod()))
            add(entry(s.counterspell()))
            add(entry(s.demonicTutor()))
            repeat(67) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "few-land-spell-$i", name = "Spell $i", typeLine = "Creature — Spirit",
                            cmc = (3 + i % 4).toDouble(), colors = listOf("G"),
                            colorIdentity = listOf("G"), power = "3", toughness = "3",
                            oracleText = "Flying.", tags = listOf(CardTag.WIN_CON),
                            edhrecRank = 5000 + i, manaCost = "{2}{G}",
                        ),
                    ),
                )
            }
            // Only 25 lands → far below the 35 minimum.
            addAll(lands(25, "G", "broken-lands"))
        }
        return GoldenDeck(
            name = "Broken (25 lands)",
            format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.G),
            mainboard = mainboard,
        )
    }

    // ── 7. Empty / tiny deck ───────────────────────────────────────────────────
    fun emptyDeck(): GoldenDeck = GoldenDeck(
        name = "Empty",
        format = DeckFormat.COMMANDER,
        colorIdentity = setOf(ManaColor.G),
        mainboard = emptyList(),
    )

    fun tinyDeck(): GoldenDeck = GoldenDeck(
        name = "Tiny",
        format = DeckFormat.COMMANDER,
        colorIdentity = setOf(ManaColor.G),
        mainboard = listOf(
            entry(GoldenStaples.solRing()),
            entry(GoldenStaples.llanowarElves()),
            entry(landCard(id = "tiny-land", colors = listOf("G"), colorIdentity = listOf("G")), quantity = 3),
        ),
    )

    // ── 8. Pauper deck (60-card, commons-only) ────────────────────────────────
    //  Every spell is legal:pauper ONLY (not_legal everywhere else) so a legality
    //  filter keyed on the Pauper field accepts them and a Standard/Modern filter
    //  would reject them. Used to assert that a Pauper deck's adds are legal:pauper
    //  and that the format wiring picks the Pauper legality field (plan D1/D2).
    fun pauperDeck(): GoldenDeck {
        val mainboard = buildList {
            // 16 one/two-drop common creatures
            repeat(4) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "pauper-creature-$i", name = "Common Creature $i",
                            typeLine = "Creature — Human Soldier",
                            cmc = (1 + i % 2).toDouble(), colors = listOf("W"),
                            colorIdentity = listOf("W"), power = "2", toughness = "2",
                            oracleText = "Vigilance.", tags = listOf(CardTag.AGGRO),
                            // Common-only: legal in Pauper, not legal in the rotating/eternal formats.
                            legalityStandard = "not_legal", legalityCommander = "legal",
                            legalityPioneer = "not_legal", legalityModern = "not_legal",
                            legalityLegacy = "not_legal", legalityVintage = "not_legal",
                            legalityPauper = "legal",
                            manaCost = "{W}",
                        ),
                        quantity = 4,
                    ),
                )
            }
            // 8 common removal + draw
            repeat(2) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "pauper-removal-$i", name = "Common Removal $i",
                            typeLine = "Instant",
                            cmc = 2.0, colors = listOf("W"), colorIdentity = listOf("W"),
                            oracleText = "Destroy target creature.", tags = listOf(CardTag.REMOVAL),
                            legalityStandard = "not_legal", legalityCommander = "legal",
                            legalityPioneer = "not_legal", legalityModern = "not_legal",
                            legalityLegacy = "not_legal", legalityVintage = "not_legal",
                            legalityPauper = "legal",
                            manaCost = "{1}{W}",
                        ),
                        quantity = 4,
                    ),
                )
            }
            // 12 more cheap commons (draw / fillers)
            repeat(3) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "pauper-draw-$i", name = "Common Cantrip $i",
                            typeLine = "Sorcery",
                            cmc = 2.0, colors = listOf("U"), colorIdentity = listOf("U"),
                            oracleText = "Draw two cards.", tags = listOf(CardTag.DRAW_ENGINE),
                            legalityStandard = "not_legal", legalityCommander = "legal",
                            legalityPioneer = "not_legal", legalityModern = "not_legal",
                            legalityLegacy = "not_legal", legalityVintage = "not_legal",
                            legalityPauper = "legal",
                            manaCost = "{1}{U}",
                        ),
                        quantity = 4,
                    ),
                )
            }
            // 24 lands
            addAll(lands(24, "W", "pauper"))
        }
        // NOTE: The Pauper format is hidden for release (its DeckFormat entry is commented
        // out in production), so this fixture is built as CASUAL to keep the test source set
        // compiling. The Pauper-specific harness assertions are @Ignore'd accordingly; the
        // fixture still exercises the generic "evaluates within bounds" invariant under CASUAL.
        return GoldenDeck(
            name = "Pauper",
            format = DeckFormat.CASUAL,
            colorIdentity = setOf(ManaColor.W, ManaColor.U),
            mainboard = mainboard,
        )
    }

    // ── 9. Standard deck breaking the 4-copy rule (5-of a non-basic) ──────────
    //  Otherwise a normal aggro shell, but one non-basic creature appears 5 times.
    //  Used to assert evaluate() flags TooManyCopies (plan C5) and that no add can
    //  ever suggest a 5th copy.
    fun standardFiveCopyViolation(): GoldenDeck {
        val base = standardAggro()
        val offender = entry(
            goldenCard(
                id = "five-of", name = "Overplayed One-Drop",
                typeLine = "Creature — Human Soldier",
                cmc = 1.0, colors = listOf("R"), colorIdentity = listOf("R"),
                power = "2", toughness = "1", oracleText = "Haste.",
                tags = listOf(CardTag.AGGRO), manaCost = "{R}",
            ),
            quantity = 5, // illegal: 5 copies of a non-basic
        )
        return base.copy(
            name = "Standard (5-copy violation)",
            mainboard = base.mainboard + offender,
        )
    }

    // ── 10. Commander deck breaking the singleton rule (a non-basic twice) ────
    //  A small Commander shell with one non-basic duplicated. Used to assert
    //  evaluate() flags SingletonViolation (plan C5).
    fun commanderSingletonViolation(): GoldenDeck {
        val s = GoldenStaples
        val mainboard = buildList {
            add(entry(s.solRing(), quantity = 2)) // singleton violation: Sol Ring x2
            add(entry(s.cultivate()))
            add(entry(s.rhysticStudy()))
            add(entry(s.swordsToPlowshares()))
            add(entry(s.wrathOfGod()))
            add(entry(s.counterspell()))
            add(entry(s.demonicTutor()))
            repeat(60) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "single-spell-$i", name = "Spell $i", typeLine = "Creature — Spirit",
                            cmc = (2 + i % 4).toDouble(), colors = listOf("G"),
                            colorIdentity = listOf("G"), power = "2", toughness = "2",
                            oracleText = "Flying.", tags = listOf(CardTag.WIN_CON),
                            edhrecRank = 5000 + i, manaCost = "{2}{G}",
                        ),
                    ),
                )
            }
            addAll(lands(37, "G", "singleton"))
        }
        return GoldenDeck(
            name = "Commander (singleton violation)",
            format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.G),
            mainboard = mainboard,
        )
    }

    // ── 11. Four-colour deck with NO fixing (Phase 5 / C3) ────────────────────
    //  A Commander deck whose spells demand four colours (WUBR) but whose land base
    //  is 37 mono-WHITE Plains. Every non-white colour is therefore an unfixed splash:
    //  the deck literally cannot cast its U/B/R spells. Used to assert the mana-base
    //  analyzer flags ColorSourceShortage / UnfixedSplash.
    fun fourColorNoFixing(): GoldenDeck {
        val mainboard = buildList {
            // White spells (well-supported by the all-Plains base).
            repeat(15) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "wubr-white-$i", name = "White Spell $i", typeLine = "Instant",
                            cmc = 2.0, colors = listOf("W"), colorIdentity = listOf("W"),
                            oracleText = "Destroy target creature.", tags = listOf(CardTag.REMOVAL),
                            edhrecRank = 1000 + i, manaCost = "{1}{W}",
                        ),
                    ),
                )
            }
            // Blue, Black and Red double-pip spells — demanded but UNFIXED (no U/B/R lands).
            listOf("U" to "Counter target spell.", "B" to "Each opponent discards two cards.",
                "R" to "Deal 4 damage to any target.").forEach { (color, text) ->
                repeat(16) { i ->
                    add(
                        entry(
                            goldenCard(
                                id = "wubr-$color-$i", name = "$color Spell $i", typeLine = "Instant",
                                cmc = 2.0, colors = listOf(color), colorIdentity = listOf(color),
                                oracleText = text, tags = listOf(CardTag.REMOVAL),
                                edhrecRank = 1000 + i, manaCost = "{$color}{$color}",
                            ),
                        ),
                    )
                }
            }
            // 37 lands — ALL Plains (white only). No fixing for U/B/R.
            addAll(lands(37, "W", "wubr"))
        }
        return GoldenDeck(
            name = "Four-color no fixing",
            format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.R),
            mainboard = mainboard,
        )
    }

    // ── 12. Clean mono-colour deck with a correct mana base (Phase 5 / C3) ────
    //  Mono-green: every spell demands only green and the 37 lands are all Forests.
    //  The mana-base analyzer must flag NO shortage for this deck.
    fun cleanMonoColor(): GoldenDeck {
        val mainboard = buildList {
            repeat(30) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "mono-green-$i", name = "Green Spell $i",
                            typeLine = "Creature — Beast",
                            cmc = (2 + i % 4).toDouble(), colors = listOf("G"),
                            colorIdentity = listOf("G"), power = "3", toughness = "3",
                            oracleText = "Trample.", tags = listOf(CardTag.WIN_CON),
                            edhrecRank = 1000 + i, manaCost = "{1}{G}{G}",
                        ),
                    ),
                )
            }
            addAll(lands(37, "G", "mono"))
        }
        return GoldenDeck(
            name = "Clean mono-green",
            format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.G),
            mainboard = mainboard,
        )
    }

    // ── 13. Healthy two-colour constructed deck with adequate fixing (Phase 5 / C1) ──
    //  A realistic ~60-card Azorius (WU) constructed deck. Both colours are adequately
    //  supported: 24 lands = 8 Plains + 8 Island + 8 WU duals → 16 white sources and 16
    //  blue sources. Every spell is SINGLE-pip ({1}{W}, {2}{U}, …) so the 60-card single-
    //  pip Karsten need is 14 — comfortably met by 16 sources of each colour. This deck
    //  must emit NO ColorSourceShortage and NO UnfixedSplash: it is the regression guard
    //  for the C1 bug where the deck-wide pip SUM (not the per-card intensity) was fed to
    //  the Karsten table and falsely flagged nearly every 2+ colour deck.
    fun healthyTwoColorConstructed(): GoldenDeck {
        val mainboard = buildList {
            // White single-pip spells (12).
            repeat(3) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "wu-white-$i", name = "White Spell $i", typeLine = "Instant",
                            cmc = 2.0, colors = listOf("W"), colorIdentity = listOf("W"),
                            oracleText = "Destroy target creature.", tags = listOf(CardTag.REMOVAL),
                            manaCost = "{1}{W}",
                        ),
                        quantity = 4,
                    ),
                )
            }
            // Blue single-pip spells (12).
            repeat(3) { i ->
                add(
                    entry(
                        goldenCard(
                            id = "wu-blue-$i", name = "Blue Spell $i", typeLine = "Sorcery",
                            cmc = 3.0, colors = listOf("U"), colorIdentity = listOf("U"),
                            oracleText = "Draw two cards.", tags = listOf(CardTag.DRAW_ENGINE),
                            manaCost = "{2}{U}",
                        ),
                        quantity = 4,
                    ),
                )
            }
            // A couple of single-pip finishers (4) → 60-card with the 24 lands below.
            add(
                entry(
                    goldenCard(
                        id = "wu-finisher", name = "Azorius Finisher",
                        typeLine = "Creature — Sphinx",
                        cmc = 5.0, colors = listOf("U"), colorIdentity = listOf("U"),
                        power = "4", toughness = "4", oracleText = "Flying.",
                        tags = listOf(CardTag.WIN_CON), manaCost = "{4}{U}",
                    ),
                    quantity = 4,
                ),
            )
            // 24 lands: 8 Plains + 8 Island + 8 WU duals → 16 W sources, 16 U sources.
            addAll(lands(8, "W", "wu-plains"))
            addAll(lands(8, "U", "wu-island"))
            add(
                entry(
                    card(
                        id = "wu-dual", name = "Azorius Dual", typeLine = "Land",
                        cmc = 0.0, colors = emptyList(), colorIdentity = listOf("W", "U"),
                        oracleText = "{T}: Add {W} or {U}.",
                    ),
                    quantity = 8,
                ),
            )
        }
        return GoldenDeck(
            name = "Healthy two-color constructed",
            format = DeckFormat.CASUAL,
            colorIdentity = setOf(ManaColor.W, ManaColor.U),
            mainboard = mainboard,
        )
    }

    /** Every golden deck, for table-driven tests. */
    fun all(): List<GoldenDeck> = listOf(
        tunedCommander(),
        cedhCommander(),
        elfTribalCommander(),
        standardAggro(),
        standardControl(),
        brokenNoInteraction(),
        brokenTwentyWipes(),
        brokenTwentyFiveLands(),
        emptyDeck(),
        tinyDeck(),
        pauperDeck(),
        standardFiveCopyViolation(),
        commanderSingletonViolation(),
        fourColorNoFixing(),
        cleanMonoColor(),
        healthyTwoColorConstructed(),
    )
}
