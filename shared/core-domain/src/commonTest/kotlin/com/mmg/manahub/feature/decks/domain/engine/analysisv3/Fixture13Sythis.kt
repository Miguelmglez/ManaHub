package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #13 (spec §9) — Sythis, Harvest's Hand, Enchantress. Commander, Selesnya (GW).
 * Expected: macro MIDRANGE, no posture, theme ENCHANTRESS.
 */
fun fixture13Sythis(): AnalysisV3Fixture {
    val commander = card(
        id = "cmd-sythis", name = "Sythis, Harvest's Hand", typeLine = "Legendary Creature — Human Cleric",
        cmc = 3.0, colorIdentity = listOf("G", "W"), power = "2", toughness = "3",
        oracleText = "Whenever you cast an enchantment spell, draw a card.",
    )
    val nonland = listOf(
        entry(commander),
        entry(card(id = "argothian-enchantress", name = "Argothian Enchantress", typeLine = "Creature — Human Druid", cmc = 1.0, colorIdentity = listOf("G"), power = "0", toughness = "1", tags = listOf(roleTag("enchantment_payoff")))),
        entry(card(id = "enchantress-presence", name = "Enchantress's Presence", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("G"), tags = listOf(roleTag("enchantment_payoff")))),
        entry(card(id = "eidolon-blossoms", name = "Eidolon of Blossoms", typeLine = "Enchantment Creature — Nymph", cmc = 3.0, colorIdentity = listOf("G"), power = "2", toughness = "2", tags = listOf(roleTag("enchantment_payoff")))),
        entry(card(id = "setessan-champion", name = "Setessan Champion", typeLine = "Creature — Human Warrior", cmc = 4.0, colorIdentity = listOf("G"), power = "4", toughness = "4", tags = listOf(roleTag("enchantment_payoff")))),
        entry(card(id = "sythis-court", name = "Verduran Enchantress", typeLine = "Creature — Human Druid", cmc = 2.0, colorIdentity = listOf("G"), power = "1", toughness = "1", tags = listOf(roleTag("enchantment_payoff")))),
        entry(card(id = "aura-shards", name = "Aura Shards", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("G"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "sterling-grove", name = "Sterling Grove", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("G"), tags = listOf(CardTag.TUTOR))),
        entry(card(id = "estrid-masked", name = "Estrid, the Masked", typeLine = "Legendary Planeswalker — Estrid", cmc = 3.0, colorIdentity = listOf("G", "W"))),
        entry(card(id = "starfield-of-nyx", name = "Starfield of Nyx", typeLine = "Enchantment", cmc = 4.0, colorIdentity = listOf("W"))),
        entry(card(id = "sigil-of-the-empty-throne", name = "Sigil of the Empty Throne", typeLine = "Enchantment", cmc = 4.0, colorIdentity = listOf("W"), tags = listOf(roleTag("token_generator")))),
        entry(card(id = "swords-sy", name = "Swords to Plowshares", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "path-sy", name = "Path to Exile", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "beast-within-sy", name = "Beast Within", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("G"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "wrath-sy", name = "Wrath of God", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("W"), tags = listOf(CardTag.WRATH))),
        entry(card(id = "sol-ring-sy", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "arcane-signet-sy", name = "Arcane Signet", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList())),
        entry(card(id = "llanowar-elves-sy", name = "Llanowar Elves", typeLine = "Creature — Elf Druid", cmc = 1.0, colorIdentity = listOf("G"), power = "1", toughness = "1", tags = listOf(CardTag.RAMP))),
        entry(card(id = "farseek-sy", name = "Farseek", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), tags = listOf(CardTag.RAMP))),
        entry(card(id = "greater-auramancy", name = "Greater Auramancy", typeLine = "Enchantment", cmc = 4.0, colorIdentity = listOf("W"), tags = listOf(CardTag.PROTECTION))),
        entry(card(id = "wild-growth", name = "Wild Growth", typeLine = "Enchantment", cmc = 1.0, colorIdentity = listOf("G"), tags = listOf(CardTag.RAMP))),
        entry(card(id = "utopia-sprawl", name = "Utopia Sprawl", typeLine = "Enchantment", cmc = 1.0, colorIdentity = listOf("G"), tags = listOf(CardTag.RAMP))),
        entry(card(id = "estrid-elven-chorus", name = "Elvish Vanguard", typeLine = "Creature — Elf Warrior", cmc = 2.0, colorIdentity = listOf("G"), power = "1", toughness = "1")),
        entry(card(id = "smothering-tithe", name = "Smothering Tithe", typeLine = "Enchantment", cmc = 4.0, colorIdentity = listOf("W"), tags = listOf(CardTag.RAMP))),
        entry(card(id = "solemn-simulacrum-sy", name = "Solemn Simulacrum", typeLine = "Artifact Creature — Golem", cmc = 4.0, colorIdentity = emptyList(), power = "2", toughness = "2", tags = listOf(CardTag.RAMP))),
        // Batch B expansion — additional enchantment payoffs (ENCHANTRESS's own consumer side).
        entry(card(id = "satyr-enchanter-sy", name = "Satyr Enchanter", typeLine = "Creature — Satyr", cmc = 1.0, colorIdentity = listOf("G"), power = "1", toughness = "1", oracleText = "Whenever you cast an enchantment spell, you may draw a card. If you do, discard a card.", tags = listOf(roleTag("enchantment_payoff")))),
        entry(card(id = "kor-spiritdancer-sy", name = "Kor Spiritdancer", typeLine = "Creature — Kor Monk", cmc = 2.0, colorIdentity = listOf("W"), power = "1", toughness = "1", oracleText = "Kor Spiritdancer gets +2/+2 as long as you control an Aura. Whenever an Aura enters the battlefield under your control, draw a card.", tags = listOf(roleTag("enchantment_payoff")))),
        entry(card(id = "archon-suns-grace-sy", name = "Archon of Sun's Grace", typeLine = "Creature — Archon", cmc = 4.0, colorIdentity = listOf("W"), power = "4", toughness = "4", oracleText = "Flying. Whenever an enchantment you control enters, create a 1/1 white Pegasus creature token with flying. As long as it's your turn, you have hexproof.", tags = listOf(roleTag("enchantment_payoff")))),
        entry(card(id = "herald-pantheon-sy", name = "Herald of the Pantheon", typeLine = "Creature — Human Monk", cmc = 2.0, colorIdentity = listOf("G"), power = "1", toughness = "3", oracleText = "Enchantment spells you cast cost {1} less to cast. You may cast enchantment spells as though they had flash.", tags = listOf(roleTag("enchantment_payoff")))),
        entry(card(id = "bruna-fading-light-sy", name = "Bruna, the Fading Light", typeLine = "Legendary Creature — Angel Spirit", cmc = 5.0, colorIdentity = listOf("W"), power = "3", toughness = "4", oracleText = "Flying, vigilance. Bruna, the Fading Light gets +1/+1 for each enchantment you control and has all activated abilities of all enchantment creatures you control.", tags = listOf(roleTag("enchantment_payoff")))),
        entry(card(id = "mesa-enchantress-sy", name = "Mesa Enchantress", typeLine = "Creature — Human Cleric", cmc = 2.0, colorIdentity = listOf("W"), power = "1", toughness = "1", oracleText = "Whenever you cast an enchantment spell, you may draw a card. If you do, discard a card at the beginning of the next end step.", tags = listOf(roleTag("enchantment_payoff")))),
        // Batch B expansion — aura buffs (the aura_buff amplifier side).
        entry(card(id = "ethereal-armor-sy", name = "Ethereal Armor", typeLine = "Enchantment — Aura", cmc = 1.0, colorIdentity = listOf("W"), oracleText = "Enchant creature. Enchanted creature gets +1/+1 for each enchantment you control, and gets +0/+1 and has first strike, or gets +1/+0 and has vigilance.", tags = listOf(roleTag("aura_buff")))),
        entry(card(id = "armadillo-cloak-sy", name = "Armadillo Cloak", typeLine = "Enchantment — Aura", cmc = 3.0, colorIdentity = listOf("G"), oracleText = "Enchant creature. Enchanted creature gets +2/+2 and has trample and lifelink.", tags = listOf(roleTag("aura_buff")))),
        entry(card(id = "rancor-sy", name = "Rancor", typeLine = "Enchantment — Aura", cmc = 1.0, colorIdentity = listOf("G"), oracleText = "Enchant creature. Enchanted creature gets +2/+0 and has trample. When Rancor is put into a graveyard from the battlefield, return Rancor to its owner's hand.", tags = listOf(roleTag("aura_buff")))),
        entry(card(id = "spirit-mantle-sy", name = "Spirit Mantle", typeLine = "Enchantment — Aura", cmc = 2.0, colorIdentity = listOf("W"), oracleText = "Enchant creature. Enchanted creature gets +1/+1 and has protection from creatures.", tags = listOf(roleTag("aura_buff")))),
        entry(card(id = "all-that-glitters-sy", name = "All That Glitters", typeLine = "Enchantment — Aura", cmc = 1.0, colorIdentity = listOf("W"), oracleText = "Enchant creature. Enchanted creature gets +1/+1 for each artifact and/or enchantment you control.", tags = listOf(roleTag("aura_buff")))),
        entry(card(id = "ancestral-mask-sy", name = "Ancestral Mask", typeLine = "Enchantment — Aura", cmc = 2.0, colorIdentity = listOf("G"), oracleText = "Enchant creature. Enchanted creature gets +2/+2 for each other enchantment you control.", tags = listOf(roleTag("aura_buff")))),
        // Batch B expansion — card draw.
        entry(card(id = "return-wildspeaker-sy", name = "Return of the Wildspeaker", typeLine = "Sorcery", cmc = 5.0, colorIdentity = listOf("G"), oracleText = "Ferocious — Draw a card for each creature you control. If you control a creature with power 4 or greater, instead draw two cards for each creature you control.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "shamanic-revelation-sy", name = "Shamanic Revelation", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("G"), oracleText = "You gain 1 life for each creature you control. Ferocious — Then if you control a creature with power 4 or greater, draw a card for each creature you control.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "rishkars-expertise-sy", name = "Rishkar's Expertise", typeLine = "Sorcery", cmc = 5.0, colorIdentity = listOf("G"), oracleText = "Draw cards equal to the greatest power among creatures you control. You may cast a spell with mana value 4 or less from your hand without paying its mana cost.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "well-lost-dreams-sy", name = "Well of Lost Dreams", typeLine = "Artifact", cmc = 3.0, colorIdentity = emptyList(), oracleText = "Whenever you gain life, you may pay that much life. If you do, draw that many cards.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "guardian-project-sy", name = "The Guardian Project", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("G"), oracleText = "Whenever a nontoken creature you control enters the battlefield, if it's not the first creature you controlled this turn, draw a card.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "zendikar-resurgent-sy", name = "Zendikar Resurgent", typeLine = "Enchantment", cmc = 5.0, colorIdentity = listOf("G"), oracleText = "You draw a card whenever a land enters the battlefield under your control. Creature tokens you control get +1/+1. You have no maximum hand size. Whenever you tap a land for mana, add one additional mana of any type that land produced.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "mentor-meek-sy", name = "Mentor of the Meek", typeLine = "Creature — Human Soldier", cmc = 2.0, colorIdentity = listOf("W"), power = "2", toughness = "2", oracleText = "Whenever another creature with power 2 or less enters the battlefield under your control, you may pay {1}. If you do, draw a card.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "sylvan-library-sy", name = "Sylvan Library", typeLine = "Enchantment", cmc = 1.0, colorIdentity = listOf("G"), oracleText = "At the beginning of your draw step, draw three additional cards. If Sylvan Library is untapped, choose two of those cards. For each of those two cards, pay 4 life or put it back.", tags = listOf(CardTag.DRAW_ENGINE))),
        // Batch B expansion — mana ramp (more Enchantment-typed aura ramp, doubling as ENCHANTMENTS density).
        entry(card(id = "overgrowth-sy", name = "Overgrowth", typeLine = "Enchantment — Aura", cmc = 2.0, colorIdentity = listOf("G"), oracleText = "Enchant land. Whenever enchanted land is tapped for mana, its controller adds an additional two mana of any one color.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "fertile-ground-sy", name = "Fertile Ground", typeLine = "Enchantment — Aura", cmc = 2.0, colorIdentity = listOf("G"), oracleText = "Enchant land. Whenever enchanted land is tapped for mana, its controller adds an additional one mana of any color.", tags = listOf(CardTag.RAMP))),
        // Batch B expansion — removal.
        entry(card(id = "councils-judgment-sy", name = "Council's Judgment", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("W"), oracleText = "Starting with you, each player votes for a nonland permanent you don't control. Exile each permanent with the most votes or tied for most votes.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "fell-mighty-sy", name = "Fell the Mighty", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("W"), oracleText = "Destroy target creature with the greatest power among creatures on the battlefield. Create a 3/3 white Elephant creature token.", tags = listOf(CardTag.REMOVAL))),
        // Batch B expansion — tutor.
        entry(card(id = "idyllic-tutor-sy", name = "Idyllic Tutor", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("W"), oracleText = "Search your library for an enchantment card, reveal it, put it into your hand, then shuffle.", tags = listOf(CardTag.TUTOR))),
        entry(card(id = "karametra-god-sy", name = "Karametra, God of Harvests", typeLine = "Legendary Enchantment Creature — God", cmc = 4.0, colorIdentity = listOf("G", "W"), power = "6", toughness = "7", oracleText = "Indestructible. Whenever a land enters the battlefield under your control, you may search your library for a creature card, reveal it, put it into your hand, then shuffle.", tags = listOf(CardTag.TUTOR))),
        entry(card(id = "weathered-wayfarer-sy", name = "Weathered Wayfarer", typeLine = "Creature — Human Nomad", cmc = 1.0, colorIdentity = listOf("W"), power = "1", toughness = "1", oracleText = "{W}, {T}, Discard a card: Search your library for a land card, reveal it, put it into your hand, then shuffle.", tags = listOf(CardTag.TUTOR))),
        // Batch B expansion — protection.
        entry(card(id = "heroic-intervention-sy", name = "Heroic Intervention", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("G"), oracleText = "Permanents you control gain hexproof and indestructible until end of turn.", tags = listOf(CardTag.PROTECTION))),
        entry(card(id = "selfless-spirit-sy", name = "Selfless Spirit", typeLine = "Creature — Spirit", cmc = 2.0, colorIdentity = listOf("W"), power = "2", toughness = "1", oracleText = "Flying. Sacrifice Selfless Spirit: Creatures you control gain indestructible until end of turn.", tags = listOf(CardTag.PROTECTION))),
        // Batch B expansion — finishers.
        entry(card(id = "archangel-thune-sy", name = "Archangel of Thune", typeLine = "Creature — Angel", cmc = 5.0, colorIdentity = listOf("W"), power = "4", toughness = "4", oracleText = "Flying. Whenever you gain life, put a +1/+1 counter on each creature you control.", tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "sigarda-herons-sy", name = "Sigarda, Host of Herons", typeLine = "Legendary Creature — Angel", cmc = 5.0, colorIdentity = listOf("G", "W"), power = "5", toughness = "5", oracleText = "Flying, hexproof. Other female creatures you control get hexproof.")),
        // Batch B expansion — generic Selesnya value staples.
        entry(card(id = "sun-titan-sy", name = "Sun Titan", typeLine = "Creature — Giant Soldier", cmc = 6.0, colorIdentity = listOf("W"), power = "6", toughness = "6", oracleText = "Vigilance. Whenever Sun Titan enters the battlefield or attacks, you may return target permanent card with mana value 3 or less from your graveyard to the battlefield.")),
        entry(card(id = "verdant-confluence-sy", name = "Verdant Confluence", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("G"), oracleText = "Choose three. You may choose the same mode more than once — Search your library for a basic land card, put it onto the battlefield, then shuffle; or you gain 4 life; or target player draws a card, then discards a card.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "trostani-discordant-sy", name = "Trostani Discordant", typeLine = "Legendary Creature — Dryad", cmc = 4.0, colorIdentity = listOf("G", "W"), power = "4", toughness = "4", oracleText = "When Trostani Discordant enters the battlefield, create two 1/1 white Soldier creature tokens with lifelink. Whenever another nontoken creature you control enters the battlefield, you gain 1 life.")),
        entry(card(id = "voice-resurgence-sy", name = "Voice of Resurgence", typeLine = "Creature — Elemental", cmc = 2.0, colorIdentity = listOf("G", "W"), power = "1", toughness = "1", oracleText = "If a spell or ability an opponent controls causes you to discard Voice of Resurgence, put it onto the battlefield instead. When Voice of Resurgence leaves the battlefield, create a green and white Elemental creature token with 'This creature's power and toughness are each equal to the number of creatures you control.'")),
        entry(card(id = "wiltleaf-liege-sy", name = "Wilt-Leaf Liege", typeLine = "Creature — Elemental", cmc = 4.0, colorIdentity = listOf("G", "W"), power = "4", toughness = "4", oracleText = "Protection from black. Other green and/or white creatures you control get +1/+1. Creatures your opponents control that are neither green nor white get -1/-1.")),
        entry(card(id = "emmara-soul-accord-sy", name = "Emmara, Soul of the Accord", typeLine = "Legendary Creature — Elf Warrior", cmc = 2.0, colorIdentity = listOf("W"), power = "1", toughness = "3", oracleText = "Whenever Emmara, Soul of the Accord or another creature enters the battlefield under your control tapped, you gain 1 life.")),
        entry(card(id = "fleecemane-lion-sy", name = "Fleecemane Lion", typeLine = "Creature — Cat", cmc = 2.0, colorIdentity = listOf("G", "W"), power = "3", toughness = "3", oracleText = "Bestow {4}{G}{W}. Hexproof as long as Fleecemane Lion is untapped. Monstrosity 2 — {2}{G}{W}: If Fleecemane Lion is monstrous, it gets +1/+1 and gains hexproof and indestructible for as long as it remains monstrous.")),
        entry(card(id = "loxodon-smiter-sy", name = "Loxodon Smiter", typeLine = "Creature — Elephant Soldier", cmc = 4.0, colorIdentity = listOf("G"), power = "5", toughness = "4", oracleText = "Hexproof as long as your hand has seven or more cards.")),
    )
    val mainboard = withBasicsCommander(nonland, "Forest", "G")
    return AnalysisV3Fixture(
        id = 13, name = "Sythis, Harvest's Hand (Enchantress)", anchor = "Sythis, Harvest's Hand",
        format = DeckFormat.COMMANDER, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.G, ManaColor.W),
        // Commander prior workstream: Sythis's own ability (draw on enchantment cast) is a
        // literal, unambiguous enchantress payoff -- honest real-world read.
        commanderTags = listOf(CardTag.MIDRANGE, CardTag.ENCHANTRESS),
        expectedMacro = "MIDRANGE", expectedPosture = NO_POSTURE, expectedThemes = "ENCHANTRESS",
    )
}
