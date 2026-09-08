package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #6 (spec §9) — Veyran, Voice of Duality, Spellslinger. Commander, Izzet (UR).
 * Expected: macro MIDRANGE, no posture, theme SPELLSLINGER.
 */
fun fixture06Veyran(): AnalysisV3Fixture {
    val commander = card(
        id = "cmd-veyran", name = "Veyran, Voice of Duality", typeLine = "Legendary Creature — Djinn Wizard",
        cmc = 3.0, colorIdentity = listOf("U", "R"), power = "2", toughness = "2",
        oracleText = "Whenever you cast an instant or sorcery spell that targets only a single permanent or player and/or has a single mode, copy that spell.",
    )
    val nonland = listOf(
        entry(commander),
        entry(card(id = "young-pyromancer", name = "Young Pyromancer", typeLine = "Creature — Human Shaman", cmc = 2.0, colorIdentity = listOf("R"), power = "2", toughness = "1", tags = listOf(roleTag("spell_payoff")))),
        entry(card(id = "guttersnipe", name = "Guttersnipe", typeLine = "Creature — Goblin Shaman", cmc = 2.0, colorIdentity = listOf("R"), power = "2", toughness = "2", tags = listOf(roleTag("spell_payoff")))),
        entry(card(id = "electromancer", name = "Electrodominance", typeLine = "Instant", cmc = 4.0, colorIdentity = listOf("U", "R"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "kessig-flamebreather", name = "Kessig Flamebreather", typeLine = "Creature — Human Shaman", cmc = 2.0, colorIdentity = listOf("R"), power = "1", toughness = "2", tags = listOf(roleTag("spell_payoff")))),
        entry(card(id = "storm-surge", name = "Talrand, Sky Summoner", typeLine = "Legendary Creature — Merfolk Wizard", cmc = 3.0, colorIdentity = listOf("U"), power = "2", toughness = "3", tags = listOf(roleTag("spell_payoff")))),
        entry(card(id = "counterspell-vy", name = "Counterspell", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Counter target spell.")),
        entry(card(id = "mana-drain-vy", name = "Mana Drain", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Counter target spell.")),
        entry(card(id = "cyclonic-rift-vy", name = "Cyclonic Rift", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"))),
        entry(card(id = "bolt-vy", name = "Lightning Bolt", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "abrade-vy", name = "Abrade", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "brainstorm-vy", name = "Brainstorm", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "ponder-vy", name = "Ponder", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "faithless-looting-vy", name = "Faithless Looting", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "wheel-vy", name = "Wheel of Fortune", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "sol-ring-vy", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "arcane-signet-vy", name = "Arcane Signet", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList())),
        entry(card(id = "izzet-signet", name = "Izzet Signet", typeLine = "Artifact", cmc = 2.0, colorIdentity = listOf("U", "R"), tags = listOf(CardTag.RAMP))),
        entry(card(id = "electrolyze", name = "Electrolyze", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("U", "R"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "cathartic-reunion", name = "Cathartic Reunion", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "expansion-explosion", name = "Expansion // Explosion", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U", "R"))),
        entry(card(id = "fiery-confluence", name = "Fiery Confluence", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "swan-song-vy", name = "Swan Song", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), oracleText = "Counter target enchantment, artifact, or spell.")),
        entry(card(id = "reiterate", name = "Reiterate", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("R"))),
        // Phase 3a-FIX batch A: density expansion (23 -> 63 non-land, spec §9 realistic-deck fix).
        // Real Izzet spellslinger staples. SPELLS axis producer density is already structural
        // (instant/sorcery typeLine, per phase 2's memory); this batch mostly strengthens the
        // spell_payoff consumer half plus a realistic draw/removal/ramp shell for the MIDRANGE macro.
        entry(card(id = "increasing-vengeance", name = "Increasing Vengeance", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("R"), oracleText = "Choose target instant or sorcery spell you control. Copy that spell twice if you cast this spell using its escalate cost. Copy that spell if you don't.", tags = listOf(roleTag("spell_copy")))),
        entry(card(id = "fork-vy", name = "Fork", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("R"), oracleText = "Copy target instant or sorcery spell.", tags = listOf(roleTag("spell_copy")))),
        entry(card(id = "reverberate-vy", name = "Reverberate", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("R"), oracleText = "Copy target instant or sorcery spell.", tags = listOf(roleTag("spell_copy")))),
        entry(card(id = "melek-izzet-paragon", name = "Melek, Izzet Paragon", typeLine = "Legendary Creature — Human Wizard", cmc = 4.0, colorIdentity = listOf("U", "R"), power = "2", toughness = "4", oracleText = "Whenever you cast an instant or sorcery spell, you may pay {1}. If you do, exile the top card of your library. You may play that card this turn.", tags = listOf(roleTag("spell_payoff")))),
        entry(card(id = "naru-meha", name = "Naru Meha, Master Wizard", typeLine = "Legendary Creature — Human Wizard", cmc = 4.0, colorIdentity = listOf("U", "R"), power = "2", toughness = "2", oracleText = "Whenever you cast a noncreature spell that targets only a single Wizard you control, create a token that's a copy of that Wizard.", tags = listOf(roleTag("spell_payoff")))),
        entry(card(id = "adeliz-cinder-wind", name = "Adeliz, the Cinder Wind", typeLine = "Legendary Creature — Human Wizard", cmc = 3.0, colorIdentity = listOf("U", "R"), power = "2", toughness = "2", oracleText = "Flying, haste. Whenever you cast a noncreature spell, put a +1/+1 counter on Adeliz and other Wizards you control get +1/+0 until end of turn.", tags = listOf(roleTag("spell_payoff")))),
        entry(card(id = "crackling-drake", name = "Crackling Drake", typeLine = "Creature — Drake", cmc = 4.0, colorIdentity = listOf("U", "R"), power = "0", toughness = "0", oracleText = "Flying. Crackling Drake's power and toughness are each equal to the number of instant and sorcery cards you own in exile and in your graveyard. When Crackling Drake enters the battlefield, draw a card.", tags = listOf(roleTag("spell_payoff"), CardTag.DRAW_ENGINE))),
        entry(card(id = "enigma-drake", name = "Enigma Drake", typeLine = "Creature — Drake", cmc = 3.0, colorIdentity = listOf("U", "R"), power = "0", toughness = "0", oracleText = "Flying. Enigma Drake gets +1/+1 for each instant and sorcery card in your graveyard.", tags = listOf(roleTag("spell_payoff")))),
        entry(card(id = "stormwing-entity", name = "Stormwing Entity", typeLine = "Creature — Elemental", cmc = 3.0, colorIdentity = listOf("U", "R"), power = "3", toughness = "2", oracleText = "As long as it's not your turn, Stormwing Entity has flying. Whenever you cast a noncreature spell, Stormwing Entity gets +1/+0 and gains flying until end of turn.", tags = listOf(roleTag("spell_payoff")))),
        entry(card(id = "murmuring-mystic", name = "Murmuring Mystic", typeLine = "Creature — Human Wizard", cmc = 4.0, colorIdentity = listOf("U"), power = "2", toughness = "4", oracleText = "Whenever you cast an instant or sorcery spell, create a 1/1 blue Bird creature token with flying.", tags = listOf(roleTag("spell_payoff"), roleTag("token_generator")))),
        entry(card(id = "archmage-emeritus", name = "Archmage Emeritus", typeLine = "Creature — Human Wizard", cmc = 3.0, colorIdentity = listOf("U"), power = "1", toughness = "4", oracleText = "The first noncreature spell you cast each turn costs {1} less to cast. Whenever you cast your first noncreature spell each turn, draw a card.", tags = listOf(roleTag("spell_payoff"), CardTag.DRAW_ENGINE))),
        entry(card(id = "niv-mizzet-parun", name = "Niv-Mizzet, Parun", typeLine = "Legendary Creature — Dragon", cmc = 5.0, colorIdentity = listOf("U", "R"), power = "5", toughness = "5", oracleText = "Flying. Whenever you draw a card, Niv-Mizzet, Parun deals 1 damage to any target. Whenever you cast an instant or sorcery spell, draw a card.", tags = listOf(roleTag("spell_payoff")))),
        entry(card(id = "aria-of-flame", name = "Aria of Flame", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("R"), oracleText = "Whenever you cast an instant or sorcery spell, Aria of Flame deals 1 damage to each opponent. Whenever a permanent you control is dealt damage, put that many +1/+1 counters on Aria of Flame.", tags = listOf(roleTag("spell_payoff")))),
        entry(card(id = "opt-vy", name = "Opt", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "consider-vy", name = "Consider", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "chart-a-course-vy", name = "Chart a Course", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "tormenting-voice-vy", name = "Tormenting Voice", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "light-up-the-stage-vy", name = "Light Up the Stage", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "flame-slash-vy", name = "Flame Slash", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "rolling-thunder-vy", name = "Rolling Thunder", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "comet-storm-vy", name = "Comet Storm", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "mind-stone-vy", name = "Mind Stone", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "fellwar-stone-vy", name = "Fellwar Stone", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "thran-dynamo-vy", name = "Thran Dynamo", typeLine = "Artifact", cmc = 4.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "remand-vy", name = "Remand", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Counter target spell. Its controller draws a card.")),
        entry(card(id = "mystic-confluence-vy", name = "Mystic Confluence", typeLine = "Instant", cmc = 5.0, colorIdentity = listOf("U"), oracleText = "Choose two — Counter target spell unless its controller pays {3}; return target permanent to its owner's hand; draw a card.")),
        entry(card(id = "negate-vy", name = "Negate", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Counter target noncreature spell.")),
        entry(card(id = "chain-lightning-vy", name = "Chain Lightning", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "pyroclasm-vy", name = "Pyroclasm", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(CardTag.WRATH))),
        entry(card(id = "blasphemous-act-vy", name = "Blasphemous Act", typeLine = "Sorcery", cmc = 8.0, colorIdentity = listOf("R"), tags = listOf(CardTag.WRATH))),
        entry(card(id = "chandra-torch-vy", name = "Chandra, Torch of Defiance", typeLine = "Legendary Planeswalker — Chandra", cmc = 4.0, colorIdentity = listOf("R"))),
        entry(card(id = "fblthp-vy", name = "Fblthp, the Lost", typeLine = "Legendary Creature — Homunculus", cmc = 2.0, colorIdentity = listOf("U"), power = "1", toughness = "3", oracleText = "When Fblthp, the Lost enters the battlefield, draw a card.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "baral-vy", name = "Baral, Chief of Compliance", typeLine = "Legendary Creature — Human Wizard", cmc = 2.0, colorIdentity = listOf("U"), power = "1", toughness = "3", oracleText = "Instant and sorcery spells you cast cost {1} less to cast. Whenever you draw your second card each turn, return Baral, Chief of Compliance to its owner's hand.", tags = listOf(roleTag("cost_reducer")))),
        entry(card(id = "goblin-electromancer-vy", name = "Goblin Electromancer", typeLine = "Creature — Goblin Wizard", cmc = 2.0, colorIdentity = listOf("U"), power = "2", toughness = "2", oracleText = "Instant and sorcery spells you cast cost {1} less to cast.", tags = listOf(roleTag("cost_reducer")))),
        entry(card(id = "zada-hedron-grinder", name = "Zada, Hedron Grinder", typeLine = "Legendary Creature — Goblin Shaman", cmc = 3.0, colorIdentity = listOf("R"), power = "2", toughness = "2", oracleText = "If Zada, Hedron Grinder is targeted by a spell you control, that spell also targets each other creature you control.", tags = listOf(roleTag("spell_copy")))),
        entry(card(id = "pyromancers-goggles", name = "Pyromancer's Goggles", typeLine = "Legendary Artifact", cmc = 4.0, colorIdentity = emptyList(), oracleText = "The first instant or sorcery spell you cast each turn costs {1} less to cast. Whenever you cast an instant or sorcery spell, exile it instead of putting it into your graveyard. If you do, copy it and you may cast the copy.", tags = listOf(roleTag("spell_copy"), roleTag("cost_reducer")))),
        entry(card(id = "wee-dragonauts", name = "Wee Dragonauts", typeLine = "Creature — Faerie Dragon", cmc = 1.0, colorIdentity = listOf("U"), power = "0", toughness = "2", oracleText = "Whenever you cast an instant or sorcery spell, Wee Dragonauts gets +2/+0 until end of turn.", tags = listOf(roleTag("spell_payoff")))),
        entry(card(id = "rielle-everwise", name = "Rielle, the Everwise", typeLine = "Legendary Creature — Human Wizard", cmc = 4.0, colorIdentity = listOf("U", "R"), power = "3", toughness = "3", oracleText = "Whenever you cast an instant or sorcery spell, exile the top card of your library. You may play it this turn. If you don't, draw a card at the beginning of the next end step.", tags = listOf(roleTag("spell_payoff"), CardTag.DRAW_ENGINE))),
        entry(card(id = "past-in-flames-vy", name = "Past in Flames", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("R"))),
        entry(card(id = "alhammarrets-archive", name = "Alhammarret's Archive", typeLine = "Legendary Artifact", cmc = 4.0, colorIdentity = emptyList())),
    )
    val mainboard = withBasicsCommander(nonland, "Island", "U")
    return AnalysisV3Fixture(
        id = 6, name = "Veyran, Voice of Duality (Spellslinger)", anchor = "Veyran, Voice of Duality",
        format = DeckFormat.COMMANDER, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.U, ManaColor.R),
        // Commander prior workstream: DELIBERATELY no macro tag -- Veyran genuinely bifurcates in
        // real-world deck building between a storm/combo payoff (spell-copy doubling enables
        // infinite loops) and a grindy spellslinger value shell, and this fixture's own decklist
        // represents the latter (expectedMacro MIDRANGE, not COMBO) -- an honest COMBO tag here
        // would contradict this fixture's own composition, exactly the "commander suggests, deck
        // decides" scenario Task 1 asks to handle by NOT forcing a signal. The spellslinger
        // identity itself IS unambiguous (spell-copy is the card's whole gimmick), so only the
        // theme prior fires.
        commanderTags = listOf(CardTag("spellslinger", TagCategory.STRATEGY)),
        expectedMacro = "MIDRANGE", expectedPosture = NO_POSTURE, expectedThemes = "SPELLSLINGER",
    )
}
