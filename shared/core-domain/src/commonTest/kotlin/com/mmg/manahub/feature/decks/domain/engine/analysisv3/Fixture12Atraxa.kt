package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #12 (spec §9) — Atraxa, Praetors' Voice, Superfriends + +1/+1 Counters. Commander,
 * Abzan+Sultai (WUBG / four-color).
 * Expected: macro MIDRANGE, no posture, themes SUPERFRIENDS + PLUS1_COUNTERS.
 */
fun fixture12Atraxa(): AnalysisV3Fixture {
    // Phase 2b (deck-analysis-engine-v3-spec.md §5.2 PLANESWALKERS/COUNTERS fix): Atraxa's own real
    // oracle text already proliferates every end step -- the fixture never tagged her
    // `counters_source`, so both axes' payoff/producer half (respectively -- counters_source
    // PRODUCES COUNTERS and CONSUMES PLANESWALKERS per the axis table) was structurally empty.
    val commander = card(
        id = "cmd-atraxa", name = "Atraxa, Praetors' Voice", typeLine = "Legendary Creature — Phyrexian Angel",
        cmc = 4.0, colorIdentity = listOf("W", "U", "B", "G"), power = "4", toughness = "4",
        oracleText = "Flying, vigilance, deathtouch, lifelink. At the beginning of your end step, proliferate.",
        tags = listOf(roleTag("counters_source")),
    )
    val nonland = listOf(
        entry(commander),
        entry(card(id = "doubling-season-at", name = "Doubling Season", typeLine = "Enchantment", cmc = 4.0, colorIdentity = listOf("G"))),
        entry(card(id = "hardened-scales-at", name = "Hardened Scales", typeLine = "Enchantment", cmc = 1.0, colorIdentity = listOf("G"))),
        entry(card(id = "conclave-mentor", name = "Conclave Mentor", typeLine = "Creature — Centaur Cleric", cmc = 3.0, colorIdentity = listOf("W"), power = "2", toughness = "4", tags = listOf(roleTag("counters_payoff")))),
        entry(card(id = "hangarback-walker", name = "Hangarback Walker", typeLine = "Artifact Creature — Construct", cmc = 2.0, colorIdentity = emptyList(), power = "0", toughness = "0", tags = listOf(roleTag("counters_payoff")))),
        entry(card(id = "kalonian-hydra", name = "Kalonian Hydra", typeLine = "Creature — Hydra", cmc = 4.0, colorIdentity = listOf("G"), power = "4", toughness = "4", tags = listOf(roleTag("counters_payoff")))),
        entry(card(id = "vorel-of-the-hull-clade", name = "Vorel of the Hull Clade", typeLine = "Legendary Creature — Merfolk Wizard", cmc = 4.0, colorIdentity = listOf("U", "G"), power = "2", toughness = "2", tags = listOf(roleTag("counters_payoff")))),
        entry(card(id = "tezzeret-agent", name = "Tezzeret the Seeker", typeLine = "Legendary Planeswalker — Tezzeret", cmc = 5.0, colorIdentity = listOf("U"), tags = listOf(roleTag("planeswalker")))),
        entry(card(id = "vraska-golgari", name = "Vraska, Golgari Queen", typeLine = "Legendary Planeswalker — Vraska", cmc = 3.0, colorIdentity = listOf("B", "G"), tags = listOf(roleTag("planeswalker")))),
        entry(card(id = "kiora-behemoth", name = "Kiora, Behemoth Beckoner", typeLine = "Legendary Planeswalker — Kiora", cmc = 2.0, colorIdentity = listOf("G", "U"), tags = listOf(roleTag("planeswalker")))),
        entry(card(id = "ugin-spirit-dragon", name = "Ugin, the Spirit Dragon", typeLine = "Legendary Planeswalker — Ugin", cmc = 8.0, colorIdentity = emptyList(), tags = listOf(roleTag("planeswalker")))),
        entry(card(id = "teferi-hero", name = "Teferi, Hero of Dominaria", typeLine = "Legendary Planeswalker — Teferi", cmc = 5.0, colorIdentity = listOf("W", "U"), tags = listOf(roleTag("planeswalker")))),
        entry(card(id = "deepglow-skate", name = "Deepglow Skate", typeLine = "Creature — Fish Beast", cmc = 4.0, colorIdentity = listOf("U", "G"), power = "3", toughness = "5", oracleText = "Flash. When Deepglow Skate enters the battlefield, double the number of each kind of counter on up to two target permanents.", tags = listOf(roleTag("counters_source")))),
        entry(card(id = "swords-at", name = "Swords to Plowshares", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "beast-within-at", name = "Beast Within", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("G"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "wrath-at", name = "Wrath of God", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("W"), tags = listOf(CardTag.WRATH))),
        entry(card(id = "cyclonic-rift-at", name = "Cyclonic Rift", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"))),
        entry(card(id = "sol-ring-at", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "arcane-signet-at", name = "Arcane Signet", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList())),
        entry(card(id = "farseek-at", name = "Farseek", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), tags = listOf(CardTag.RAMP))),
        entry(card(id = "demonic-tutor-at", name = "Demonic Tutor", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(CardTag.TUTOR))),
        entry(card(id = "fact-fiction-at", name = "Fact or Fiction", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "rhystic-study-at", name = "Rhystic Study", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "eternal-witness-at", name = "Eternal Witness", typeLine = "Creature — Elf Shaman", cmc = 3.0, colorIdentity = listOf("G"), power = "2", toughness = "1", tags = listOf(roleTag("recursion")))),
        entry(card(id = "solemn-simulacrum-at", name = "Solemn Simulacrum", typeLine = "Artifact Creature — Golem", cmc = 4.0, colorIdentity = emptyList(), power = "2", toughness = "2", tags = listOf(CardTag.RAMP))),
        // Phase 2b: real proliferate/counters-doubler staples, all legal within the deck's WUBG
        // identity -- gives COUNTERS and PLANESWALKERS a producer/payoff half beyond Atraxa alone.
        entry(card(id = "contagion-engine-at", name = "Contagion Engine", typeLine = "Artifact", cmc = 4.0, colorIdentity = emptyList(), oracleText = "{4}, {T}: Proliferate twice.", tags = listOf(roleTag("counters_source")))),
        entry(card(id = "inexorable-tide-at", name = "Inexorable Tide", typeLine = "Enchantment", cmc = 4.0, colorIdentity = listOf("U"), oracleText = "Whenever you cast an instant or sorcery spell, proliferate.", tags = listOf(roleTag("counters_source")))),
        entry(card(id = "evolution-sage-at", name = "Evolution Sage", typeLine = "Creature — Human Shaman", cmc = 3.0, colorIdentity = listOf("G"), power = "1", toughness = "1", oracleText = "Whenever a land you control enters the battlefield, proliferate.", tags = listOf(roleTag("counters_source")))),
        entry(card(id = "winding-constrictor-at", name = "Winding Constrictor", typeLine = "Creature — Snake", cmc = 3.0, colorIdentity = listOf("B", "G"), power = "2", toughness = "2", oracleText = "If one or more +1/+1 counters would be put on a creature you control, that many plus one +1/+1 counters are put on that creature instead.", tags = listOf(roleTag("counters_payoff")))),
        // Batch B expansion — additional planeswalkers (Superfriends' own producer axis).
        entry(card(id = "elspeth-sun-champion-at", name = "Elspeth, Sun's Champion", typeLine = "Legendary Planeswalker — Elspeth", cmc = 6.0, colorIdentity = listOf("W"), oracleText = "+1: Create two 1/1 white Soldier creature tokens. -3: Destroy all creatures with power 4 or greater. -7: You get an emblem with 'Artifacts, creatures, enchantments, and lands you control have indestructible.'", tags = listOf(roleTag("planeswalker"), roleTag("token_generator")))),
        entry(card(id = "jace-mind-sculptor-at", name = "Jace, the Mind Sculptor", typeLine = "Legendary Planeswalker — Jace", cmc = 4.0, colorIdentity = listOf("U"), oracleText = "+2: Look at the top card of target player's library. +0: Draw three cards, then put two cards from your hand on top of your library. -1: Return target creature to its owner's hand. -12: Exile all cards from target player's library.", tags = listOf(roleTag("planeswalker")))),
        entry(card(id = "liliana-deaths-majesty-at", name = "Liliana, Death's Majesty", typeLine = "Legendary Planeswalker — Liliana", cmc = 5.0, colorIdentity = listOf("B"), oracleText = "+1: Create a 2/2 black Zombie creature token. -3: Return target creature card from your graveyard to the battlefield. It's a Zombie in addition to its other types. -7: Each opponent chooses a permanent they control of each permanent type and sacrifices the rest.", tags = listOf(roleTag("planeswalker")))),
        entry(card(id = "nissa-shakes-world-at", name = "Nissa, Who Shakes the World", typeLine = "Legendary Planeswalker — Nissa", cmc = 5.0, colorIdentity = listOf("G"), oracleText = "Compleated. Static — Whenever you tap a Forest for mana, add an additional {G}. +1: Create a 0/0 green Elemental creature token, then put three +1/+1 counters on it. -8: You get an emblem with 'Lands you control have indestructible.'", tags = listOf(roleTag("planeswalker")))),
        entry(card(id = "gideon-ally-zendikar-at", name = "Gideon, Ally of Zendikar", typeLine = "Legendary Planeswalker — Gideon", cmc = 4.0, colorIdentity = listOf("W"), oracleText = "+1: Until end of turn, Gideon becomes a 5/5 Human Soldier Ally creature that's indestructible. +1: Create a 2/2 white Knight Ally creature token. -4: You get an emblem with 'Creatures you control get +1/+1.'", tags = listOf(roleTag("planeswalker")))),
        entry(card(id = "karn-scion-urza-at", name = "Karn, Scion of Urza", typeLine = "Legendary Planeswalker — Karn", cmc = 4.0, colorIdentity = emptyList(), oracleText = "-1: Create a token that's a copy of target artifact you control, except it's not legendary. -2: Reveal the top two cards of your library. An opponent chooses one. Put that card into your hand and exile the other.", tags = listOf(roleTag("planeswalker")))),
        entry(card(id = "ob-nixilis-reignited-at", name = "Ob Nixilis Reignited", typeLine = "Legendary Planeswalker — Ob Nixilis", cmc = 6.0, colorIdentity = listOf("B"), oracleText = "+1: Target player draws a card and loses 1 life. -3: Destroy target creature. Draw a card. -8: Search your library for any number of basic land cards, put them onto the battlefield tapped, then shuffle.", tags = listOf(roleTag("planeswalker")))),
        entry(card(id = "vivien-reid-at", name = "Vivien Reid", typeLine = "Legendary Planeswalker — Vivien", cmc = 5.0, colorIdentity = listOf("G"), oracleText = "+1: Draw a card. Then discard a card unless it's a creature card. -3: Destroy target creature or artifact. -6: You may cast creature spells this turn as though they had flash.", tags = listOf(roleTag("planeswalker")))),
        entry(card(id = "dovin-baan-at", name = "Dovin Baan", typeLine = "Legendary Planeswalker — Dovin", cmc = 4.0, colorIdentity = listOf("W", "U"), oracleText = "+1: You gain 2 life and scry 2. -2: Tap two target artifacts, creatures, and/or lands. Untap two target artifacts and/or creatures you control. -6: You get an emblem with 'Whenever an opponent casts a spell, counter that spell unless its controller pays {4}.'", tags = listOf(roleTag("planeswalker")))),
        // Batch B expansion — additional counters payoffs.
        entry(card(id = "fathom-mage-at", name = "Fathom Mage", typeLine = "Creature — Elf Druid", cmc = 3.0, colorIdentity = listOf("G"), power = "1", toughness = "1", oracleText = "Whenever one or more +1/+1 counters are put on Fathom Mage, if it has three or more +1/+1 counters on it, draw a card.", tags = listOf(roleTag("counters_payoff")))),
        entry(card(id = "fynn-fangbearer-at", name = "Fynn, the Fangbearer", typeLine = "Legendary Creature — Elf Shaman", cmc = 3.0, colorIdentity = listOf("G", "W"), power = "1", toughness = "3", oracleText = "Whenever a creature you control with a +1/+1 counter on it deals combat damage to a player, that player gets a poison counter.", tags = listOf(roleTag("counters_payoff")))),
        entry(card(id = "simic-ascendancy-at", name = "Simic Ascendancy", typeLine = "Enchantment", cmc = 4.0, colorIdentity = listOf("G", "U"), oracleText = "If a permanent you control would be dealt damage, prevent that damage and put that many +1/+1 counters on it instead. You win the game if you control a permanent with fifteen or more +1/+1 counters on it.", tags = listOf(roleTag("counters_payoff")))),
        entry(card(id = "ozolith-at", name = "The Ozolith", typeLine = "Legendary Artifact", cmc = 2.0, colorIdentity = emptyList(), oracleText = "Whenever a creature you control with a +1/+1 counter on it leaves the battlefield, move all +1/+1 counters from it onto The Ozolith. {2}: Put a +1/+1 counter on target creature for each +1/+1 counter on The Ozolith, then remove all +1/+1 counters from The Ozolith.", tags = listOf(roleTag("counters_payoff")))),
        entry(card(id = "vorapede-at", name = "Vorapede", typeLine = "Creature — Insect", cmc = 6.0, colorIdentity = listOf("G"), power = "6", toughness = "6", oracleText = "Trample. Whenever a creature dealt damage by Vorapede this turn dies, put a +1/+1 counter on Vorapede. Whenever one or more +1/+1 counters are put on Vorapede, you gain that much life.", tags = listOf(roleTag("counters_payoff")))),
        entry(card(id = "renegade-krasis-at", name = "Renegade Krasis", typeLine = "Creature — Human Warrior", cmc = 3.0, colorIdentity = listOf("G", "U"), power = "1", toughness = "1", oracleText = "Evolve. Whenever Renegade Krasis evolves, put an additional +1/+1 counter on it.", tags = listOf(roleTag("counters_payoff")))),
        // Batch B expansion — protection (Superfriends' own defensive axis).
        entry(card(id = "swiftfoot-boots-at", name = "Swiftfoot Boots", typeLine = "Artifact — Equipment", cmc = 2.0, colorIdentity = emptyList(), oracleText = "Equipped creature has hexproof and haste. Equip {1}.", tags = listOf(CardTag.PROTECTION))),
        entry(card(id = "lightning-greaves-at", name = "Lightning Greaves", typeLine = "Artifact — Equipment", cmc = 2.0, colorIdentity = emptyList(), oracleText = "Equipped creature has haste and shroud. Equip {0}.", tags = listOf(CardTag.PROTECTION))),
        entry(card(id = "heroic-intervention-at", name = "Heroic Intervention", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("G"), oracleText = "Permanents you control gain hexproof and indestructible until end of turn.", tags = listOf(CardTag.PROTECTION))),
        entry(card(id = "selfless-spirit-at", name = "Selfless Spirit", typeLine = "Creature — Spirit", cmc = 2.0, colorIdentity = listOf("W"), power = "2", toughness = "1", oracleText = "Flying. Sacrifice Selfless Spirit: Creatures you control gain indestructible until end of turn.", tags = listOf(CardTag.PROTECTION))),
        entry(card(id = "shalai-voice-plenty-at", name = "Shalai, Voice of Plenty", typeLine = "Legendary Creature — Angel", cmc = 4.0, colorIdentity = listOf("G"), power = "3", toughness = "4", oracleText = "Flying. You, planeswalkers you control, and other creatures you control have hexproof. {5}{G}{G}: Put a +1/+1 counter on each creature you control.", tags = listOf(CardTag.PROTECTION))),
        entry(card(id = "dawn-charm-at", name = "Dawn Charm", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Choose one — Prevent all damage that a source of your choice would deal this turn; or counter target activated ability; or target permanent you control gains protection from a color of your choice until end of turn.", tags = listOf(CardTag.PROTECTION))),
        entry(card(id = "darksteel-plate-at", name = "Darksteel Plate", typeLine = "Artifact — Equipment", cmc = 5.0, colorIdentity = emptyList(), oracleText = "Indestructible. Equipped creature has indestructible. Equip {3}.", tags = listOf(CardTag.PROTECTION))),
        // Batch B expansion — dedicated token producers (loyalty fodder / board presence).
        entry(card(id = "sigil-empty-throne-at", name = "Sigil of the Empty Throne", typeLine = "Enchantment", cmc = 4.0, colorIdentity = listOf("W"), oracleText = "At the beginning of combat on your turn, if you control six or more enchantments, create a 4/4 white Avatar creature token with flying.", tags = listOf(roleTag("token_generator")))),
        entry(card(id = "march-multitudes-at", name = "March of the Multitudes", typeLine = "Sorcery", cmc = 6.0, colorIdentity = listOf("W", "G"), oracleText = "Convoke. Create X 1/1 white Elf Warrior creature tokens.", tags = listOf(roleTag("token_generator")))),
        entry(card(id = "growing-ranks-at", name = "Growing Ranks", typeLine = "Sorcery", cmc = 5.0, colorIdentity = listOf("W"), oracleText = "Convoke. Create a 1/1 white Elf Warrior creature token for each Elf you control.", tags = listOf(roleTag("token_generator")))),
        entry(card(id = "trostani-discordant-at", name = "Trostani Discordant", typeLine = "Legendary Creature — Dryad", cmc = 4.0, colorIdentity = listOf("G", "W"), power = "4", toughness = "4", oracleText = "When Trostani Discordant enters the battlefield, create two 1/1 white Soldier creature tokens with lifelink. Whenever another nontoken creature you control enters the battlefield, you gain 1 life.", tags = listOf(roleTag("token_generator")))),
        // Batch B expansion — mass removal.
        entry(card(id = "toxic-deluge-at", name = "Toxic Deluge", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("B"), oracleText = "As an additional cost to cast this spell, pay X life. All creatures get -X/-X until end of turn.", tags = listOf(CardTag.WRATH))),
        entry(card(id = "damnation-at", name = "Damnation", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("B"), oracleText = "Destroy all creatures. They can't be regenerated.", tags = listOf(CardTag.WRATH))),
        entry(card(id = "winds-abandon-at", name = "Winds of Abandon", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("W"), oracleText = "Exile target creature you don't control. Its controller searches their library for a basic land card, puts it onto the battlefield, then shuffles. Overload {4}{W}{W}.", tags = listOf(CardTag.WRATH))),
        entry(card(id = "crux-fate-at", name = "Crux of Fate", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("B"), oracleText = "Choose one — Destroy all Dragon creatures; or destroy all non-Dragon creatures.", tags = listOf(CardTag.WRATH))),
        // Batch B expansion — card draw, spot removal, tutor, ramp (round out the support shell).
        entry(card(id = "mystic-remora-at", name = "Mystic Remora", typeLine = "Enchantment", cmc = 1.0, colorIdentity = listOf("U"), oracleText = "Whenever an opponent casts a noncreature spell, you may pay {1}. If you don't, sacrifice Mystic Remora. At the beginning of your upkeep, if Mystic Remora is on the battlefield, draw a card.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "consecrated-sphinx-at", name = "Consecrated Sphinx", typeLine = "Creature — Sphinx", cmc = 6.0, colorIdentity = listOf("U"), power = "4", toughness = "6", oracleText = "Flying. Whenever an opponent draws a card, you may draw two cards.", tags = listOf(CardTag.DRAW_ENGINE))),
        // Swapped in over 3 originally-planned generic staples (Council's Judgment/Vampiric Tutor/
        // Cultivate, redundant with removal_spot/tutor/ramp already well covered elsewhere in this
        // list) to strengthen COUNTERS' own producer side — the live axis-graph evidence run showed
        // PLANESWALKERS going live (0.827) but COUNTERS staying short (0.254, producers=4 against a
        // scaled ideal of ~7.75) purely on a producer-count shortfall, not a payoff one.
        entry(card(id = "tezzerets-gambit-at", name = "Tezzeret's Gambit", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("U"), oracleText = "Proliferate. Draw a card.", tags = listOf(roleTag("counters_source")))),
        entry(card(id = "contagion-clasp-at", name = "Contagion Clasp", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), oracleText = "{2}, {T}: Put a -1/-1 counter on target creature or land. {4}, {T}: Proliferate.", tags = listOf(roleTag("counters_source")))),
        entry(card(id = "steady-progress-at", name = "Steady Progress", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("U"), oracleText = "Proliferate. Draw a card.", tags = listOf(roleTag("counters_source")))),
    )
    val mainboard = withBasicsCommander(nonland, "Forest", "G")
    return AnalysisV3Fixture(
        id = 12, name = "Atraxa, Praetors' Voice (Superfriends + Counters)", anchor = "Atraxa, Praetors' Voice",
        format = DeckFormat.COMMANDER, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.G),
        // Commander prior workstream: Atraxa's own ability is LITERALLY "proliferate" -- the
        // quintessential, entirely unambiguous superfriends+counters commander in EDH. CardTag
        // .PROLIFERATE reverse-maps to ThemeId.PLUS1_COUNTERS (first-declared wins in
        // DeckIdentitySeedTags.THEME_TAGS's shared PROLIFERATE entry); the inline "doublers" tag is
        // SUPERFRIENDS' own second, non-shared tag, giving both of this fixture's expected themes a
        // real prior.
        commanderTags = listOf(CardTag.MIDRANGE, CardTag.PROLIFERATE, CardTag("doublers", TagCategory.STRATEGY)),
        expectedMacro = "MIDRANGE", expectedPosture = NO_POSTURE, expectedThemes = "SUPERFRIENDS, PLUS1_COUNTERS",
    )
}
