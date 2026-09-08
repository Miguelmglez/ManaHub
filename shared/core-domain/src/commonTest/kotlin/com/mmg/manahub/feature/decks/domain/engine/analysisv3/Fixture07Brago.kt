package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #7 (spec §9) — Brago, King Eternal, Blink/ETB. Commander, Azorius (WU).
 * Expected: macro MIDRANGE, no posture, themes BLINK + ETB.
 */
fun fixture07Brago(): AnalysisV3Fixture {
    val commander = card(
        id = "cmd-brago", name = "Brago, King Eternal", typeLine = "Legendary Creature — Bird Spirit",
        cmc = 4.0, colorIdentity = listOf("W", "U"), power = "2", toughness = "4",
        oracleText = "Flying. Whenever Brago, King Eternal deals combat damage to a player, exile up to two other target nonland permanents you control, then return those cards to the battlefield under their owner's control.",
    )
    val nonland = listOf(
        entry(commander),
        entry(card(id = "mulldrifter", name = "Mulldrifter", typeLine = "Creature — Elemental", cmc = 5.0, colorIdentity = listOf("U"), power = "2", toughness = "2", tags = listOf(roleTag("etb_payoff"), CardTag.DRAW_ENGINE))),
        entry(card(id = "solemn-simulacrum-br", name = "Solemn Simulacrum", typeLine = "Artifact Creature — Golem", cmc = 4.0, colorIdentity = emptyList(), power = "2", toughness = "2", tags = listOf(roleTag("etb_payoff")))),
        entry(card(id = "reveillark", name = "Reveillark", typeLine = "Creature — Elemental", cmc = 5.0, colorIdentity = listOf("W"), power = "3", toughness = "3", tags = listOf(roleTag("etb_payoff")))),
        entry(card(id = "flickerwisp", name = "Flickerwisp", typeLine = "Creature — Elemental", cmc = 3.0, colorIdentity = listOf("W"), power = "3", toughness = "1", tags = listOf(roleTag("blink_effect")))),
        entry(card(id = "ephemerate", name = "Ephemerate", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(roleTag("blink_effect")))),
        entry(card(id = "restoration-angel", name = "Restoration Angel", typeLine = "Creature — Angel", cmc = 4.0, colorIdentity = listOf("W"), power = "3", toughness = "4", tags = listOf(roleTag("blink_effect")))),
        entry(card(id = "ghostly-flicker", name = "Ghostly Flicker", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("U"), tags = listOf(roleTag("blink_effect")))),
        entry(card(id = "eldrazi-displacer", name = "Eldrazi Displacer", typeLine = "Creature — Eldrazi", cmc = 3.0, colorIdentity = emptyList(), power = "2", toughness = "2", tags = listOf(roleTag("blink_effect")))),
        entry(card(id = "venser-shaper", name = "Venser, Shaper Savant", typeLine = "Legendary Creature — Human Wizard", cmc = 4.0, colorIdentity = listOf("U"), power = "3", toughness = "3", tags = listOf(roleTag("etb_payoff")))),
        entry(card(id = "wall-of-omens", name = "Wall of Omens", typeLine = "Creature — Wall", cmc = 2.0, colorIdentity = listOf("W"), power = "0", toughness = "4", tags = listOf(roleTag("etb_payoff")))),
        entry(card(id = "counterspell-br", name = "Counterspell", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Counter target spell.")),
        entry(card(id = "swords-br", name = "Swords to Plowshares", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "wrath-br", name = "Wrath of God", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("W"), tags = listOf(CardTag.WRATH))),
        entry(card(id = "fact-fiction-br", name = "Fact or Fiction", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "rhystic-study-br", name = "Rhystic Study", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "sol-ring-br", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "arcane-signet-br", name = "Arcane Signet", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList())),
        entry(card(id = "farewell-br", name = "Farewell", typeLine = "Sorcery", cmc = 5.0, colorIdentity = listOf("W"), tags = listOf(CardTag.WRATH))),
        entry(card(id = "duplicant", name = "Duplicant", typeLine = "Artifact Creature — Shapeshifter", cmc = 5.0, colorIdentity = emptyList(), power = "2", toughness = "2", tags = listOf(CardTag.REMOVAL, roleTag("etb_payoff")))),
        entry(card(id = "conjurers-closet", name = "Conjurer's Closet", typeLine = "Artifact", cmc = 4.0, colorIdentity = emptyList(), tags = listOf(roleTag("blink_effect")))),
        entry(card(id = "deadeye-navigator", name = "Deadeye Navigator", typeLine = "Creature — Human Pirate", cmc = 4.0, colorIdentity = listOf("U"), power = "2", toughness = "4", tags = listOf(roleTag("blink_effect")))),
        entry(card(id = "elspeth-br", name = "Elspeth, Sun's Champion", typeLine = "Legendary Planeswalker — Elspeth", cmc = 6.0, colorIdentity = listOf("W"))),
        entry(card(id = "batterskull-br", name = "Batterskull", typeLine = "Legendary Artifact — Equipment", cmc = 5.0, colorIdentity = emptyList())),
        // Phase 3a-FIX batch A: density expansion (23 -> 63 non-land, spec §9 realistic-deck fix).
        // Real Azorius Blink/ETB staples -- roughly doubles both etb_payoff (producer) and
        // blink_effect (consumer -- Brago's own oracle text is the flicker source, so more targets
        // AND more flicker enablers both matter here) plus a realistic WU control shell.
        entry(card(id = "fiend-hunter", name = "Fiend Hunter", typeLine = "Creature — Human Cleric", cmc = 3.0, colorIdentity = listOf("W"), power = "1", toughness = "3", oracleText = "When Fiend Hunter enters the battlefield, exile another target creature.", tags = listOf(roleTag("etb_payoff")))),
        entry(card(id = "banisher-priest", name = "Banisher Priest", typeLine = "Creature — Human Cleric", cmc = 3.0, colorIdentity = listOf("W"), power = "2", toughness = "2", oracleText = "When Banisher Priest enters the battlefield, exile target creature an opponent controls until Banisher Priest leaves the battlefield.", tags = listOf(roleTag("etb_payoff")))),
        entry(card(id = "angel-of-invention", name = "Angel of Invention", typeLine = "Creature — Angel", cmc = 4.0, colorIdentity = listOf("W"), power = "2", toughness = "2", oracleText = "Flying. As Angel of Invention enters the battlefield, choose fabricate 2 or +1/+1 counters mode. When Angel of Invention enters the battlefield, other creatures you control get +1/+1 until end of turn.", tags = listOf(roleTag("etb_payoff")))),
        entry(card(id = "whitemane-lion", name = "Whitemane Lion", typeLine = "Creature — Cat", cmc = 2.0, colorIdentity = listOf("W"), power = "2", toughness = "2", oracleText = "When Whitemane Lion enters the battlefield, return a creature you control to its owner's hand.", tags = listOf(roleTag("etb_payoff")))),
        entry(card(id = "cloudblazer", name = "Cloudblazer", typeLine = "Creature — Bird", cmc = 4.0, colorIdentity = listOf("W", "U"), power = "3", toughness = "3", oracleText = "Flying. When Cloudblazer enters the battlefield, you gain 2 life and draw a card.", tags = listOf(roleTag("etb_payoff")))),
        entry(card(id = "blade-splicer", name = "Blade Splicer", typeLine = "Creature — Human Artificer", cmc = 3.0, colorIdentity = listOf("W"), power = "1", toughness = "1", oracleText = "When Blade Splicer enters the battlefield, create a 3/3 colorless Golem artifact creature token.", tags = listOf(roleTag("etb_payoff")))),
        entry(card(id = "angel-of-serenity", name = "Angel of Serenity", typeLine = "Creature — Angel", cmc = 6.0, colorIdentity = listOf("W"), power = "5", toughness = "6", oracleText = "Flying. When Angel of Serenity enters the battlefield, exile up to three target creatures from the battlefield and/or from graveyards.", tags = listOf(roleTag("etb_payoff")))),
        entry(card(id = "sun-titan-br", name = "Sun Titan", typeLine = "Creature — Giant", cmc = 6.0, colorIdentity = listOf("W"), power = "6", toughness = "6", oracleText = "Vigilance. Whenever Sun Titan enters the battlefield or attacks, you may return target permanent card with mana value 3 or less from your graveyard to the battlefield.", tags = listOf(roleTag("etb_payoff")))),
        entry(card(id = "karmic-guide-br", name = "Karmic Guide", typeLine = "Creature — Cleric", cmc = 5.0, colorIdentity = listOf("W"), power = "2", toughness = "2", oracleText = "Flying. When Karmic Guide enters the battlefield, return target creature card from your graveyard to the battlefield.", tags = listOf(roleTag("etb_payoff")))),
        entry(card(id = "palace-jailer-br", name = "Palace Jailer", typeLine = "Creature — Human Soldier", cmc = 3.0, colorIdentity = listOf("W"), power = "2", toughness = "3", oracleText = "When Palace Jailer enters the battlefield, exile target creature an opponent controls until you cease to be the monarch.", tags = listOf(roleTag("etb_payoff"), CardTag.REMOVAL))),
        entry(card(id = "skyclave-apparition-br", name = "Skyclave Apparition", typeLine = "Creature — Spirit", cmc = 2.0, colorIdentity = listOf("W"), power = "3", toughness = "2", oracleText = "Flying. When Skyclave Apparition enters the battlefield, exile up to one target nonland permanent an opponent controls with mana value 4 or less.", tags = listOf(roleTag("etb_payoff"), CardTag.REMOVAL))),
        entry(card(id = "felidar-guardian", name = "Felidar Guardian", typeLine = "Creature — Cat Beast", cmc = 4.0, colorIdentity = listOf("W"), power = "3", toughness = "4", oracleText = "When Felidar Guardian enters the battlefield, you may exile another target permanent you control, then return that card to the battlefield under its owner's control.", tags = listOf(roleTag("blink_effect")))),
        entry(card(id = "cloudshift", name = "Cloudshift", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), oracleText = "Exile target creature you control, then return that card to the battlefield under its owner's control.", tags = listOf(roleTag("blink_effect")))),
        entry(card(id = "momentary-blink", name = "Momentary Blink", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("W"), oracleText = "Exile up to one target creature you control, then return that card to the battlefield under its owner's control. Flashback {2}{W}.", tags = listOf(roleTag("blink_effect")))),
        entry(card(id = "essence-flux", name = "Essence Flux", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Exile target creature you control, then return that card to the battlefield under its owner's control.", tags = listOf(roleTag("blink_effect")))),
        entry(card(id = "chart-a-course-br", name = "Chart a Course", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "mystic-confluence-br", name = "Mystic Confluence", typeLine = "Instant", cmc = 5.0, colorIdentity = listOf("U"), oracleText = "Choose two — Counter target spell unless its controller pays {3}; return target permanent to its owner's hand; draw a card.")),
        entry(card(id = "cryptic-command-br", name = "Cryptic Command", typeLine = "Instant", cmc = 4.0, colorIdentity = listOf("U"), oracleText = "Choose two — Counter target spell; return target permanent to its owner's hand; draw a card; tap all creatures your opponents control.")),
        entry(card(id = "prison-realm", name = "Prison Realm", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "path-to-exile-br", name = "Path to Exile", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "councils-judgment-br", name = "Council's Judgment", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "mind-stone-br", name = "Mind Stone", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "thought-vessel-br", name = "Thought Vessel", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "aven-riftwatcher", name = "Aven Riftwatcher", typeLine = "Creature — Bird Soldier", cmc = 3.0, colorIdentity = listOf("W"), power = "2", toughness = "1", oracleText = "Flying. When Aven Riftwatcher enters the battlefield, you gain 2 life. When Aven Riftwatcher leaves the battlefield, you lose 2 life.", tags = listOf(roleTag("etb_payoff")))),
        entry(card(id = "fblthp-br", name = "Fblthp, the Lost", typeLine = "Legendary Creature — Homunculus", cmc = 2.0, colorIdentity = listOf("U"), power = "1", toughness = "3", oracleText = "When Fblthp, the Lost enters the battlefield, draw a card.", tags = listOf(roleTag("etb_payoff"), CardTag.DRAW_ENGINE))),
        entry(card(id = "sea-gate-oracle", name = "Sea Gate Oracle", typeLine = "Creature — Human Wizard", cmc = 3.0, colorIdentity = listOf("U"), power = "1", toughness = "2", oracleText = "When Sea Gate Oracle enters the battlefield, look at the top two cards of your library. Put one of them into your hand and the other into your graveyard.", tags = listOf(roleTag("etb_payoff")))),
        entry(card(id = "kor-skyfisher", name = "Kor Skyfisher", typeLine = "Creature — Kor Soldier", cmc = 2.0, colorIdentity = listOf("W"), power = "2", toughness = "3", oracleText = "Flying. When Kor Skyfisher enters the battlefield, return a permanent you control to its owner's hand.", tags = listOf(roleTag("etb_payoff")))),
        entry(card(id = "man-o-war", name = "Man-o'-War", typeLine = "Creature — Jellyfish", cmc = 3.0, colorIdentity = listOf("U"), power = "2", toughness = "2", oracleText = "When Man-o'-War enters the battlefield, return target creature to its owner's hand.", tags = listOf(roleTag("etb_payoff")))),
        entry(card(id = "frost-titan-br", name = "Frost Titan", typeLine = "Creature — Giant", cmc = 6.0, colorIdentity = listOf("U"), power = "6", toughness = "6")),
        entry(card(id = "consecrated-sphinx-br", name = "Consecrated Sphinx", typeLine = "Creature — Sphinx", cmc = 6.0, colorIdentity = listOf("U"), power = "4", toughness = "6", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "mnemonic-wall", name = "Mnemonic Wall", typeLine = "Creature — Wall", cmc = 4.0, colorIdentity = listOf("U"), power = "0", toughness = "5", oracleText = "When Mnemonic Wall enters the battlefield, return target instant or sorcery card from your graveyard to your hand.", tags = listOf(roleTag("etb_payoff"), roleTag("recursion")))),
        entry(card(id = "detention-sphere", name = "Detention Sphere", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("W", "U"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "oblivion-ring", name = "Oblivion Ring", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "banishing-light", name = "Banishing Light", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "ghostway", name = "Ghostway", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("W"), oracleText = "Exile each creature you control, then return those cards to the battlefield under their owner's control.", tags = listOf(roleTag("blink_effect")))),
        entry(card(id = "eerie-interlude", name = "Eerie Interlude", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("W"), oracleText = "Exile up to two target creatures you control, then return those cards to the battlefield under their owner's control.", tags = listOf(roleTag("blink_effect")))),
        entry(card(id = "venser-sojourner", name = "Venser, the Sojourner", typeLine = "Legendary Planeswalker — Venser", cmc = 4.0, colorIdentity = listOf("U"), tags = listOf(roleTag("blink_effect")))),
        entry(card(id = "azorius-signet", name = "Azorius Signet", typeLine = "Artifact", cmc = 2.0, colorIdentity = listOf("W", "U"), tags = listOf(CardTag.RAMP))),
        entry(card(id = "coalition-relic", name = "Coalition Relic", typeLine = "Artifact", cmc = 3.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "prophetic-prism-br", name = "Prophetic Prism", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), tags = listOf(CardTag.DRAW_ENGINE))),
    )
    val mainboard = withBasicsCommander(nonland, "Island", "U")
    return AnalysisV3Fixture(
        id = 7, name = "Brago, King Eternal (Blink/ETB)", anchor = "Brago, King Eternal",
        format = DeckFormat.COMMANDER, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.W, ManaColor.U),
        // Commander prior workstream: DELIBERATELY no macro tag -- Brago's own real-world deck
        // building convention genuinely splits between a control-adjacent value shell and a more
        // aggressive combat-damage-triggered blink shell (the trigger itself is combat damage, not
        // a passive ETB), so a confident single-macro tag here would not be an honest real-world
        // read (see Task 1's "commander suggests, deck decides" constraint). The blink/flicker
        // identity itself IS unambiguous (the whole card is built around it), so only the theme
        // prior fires.
        commanderTags = listOf(CardTag.BLINK),
        expectedMacro = "MIDRANGE", expectedPosture = NO_POSTURE, expectedThemes = "BLINK, ETB",
    )
}
