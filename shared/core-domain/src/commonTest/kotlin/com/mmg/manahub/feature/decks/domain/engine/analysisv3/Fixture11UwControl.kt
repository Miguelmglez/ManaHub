package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #11 (spec §9) — "the existing UW Control fixture" (the W13/synergy-pillar-mechanics.md
 * documented case: a well-built UW Control deck that currently scores SYNERGY 27/100). Commander,
 * Azorius (WU). Expected: macro CONTROL, no posture, no themes.
 */
fun fixture11UwControl(): AnalysisV3Fixture {
    val commander = card(
        id = "cmd-aminatou", name = "Aminatou, the Fateshifter", typeLine = "Legendary Planeswalker — Aminatou",
        cmc = 4.0, colorIdentity = listOf("W", "U"),
        oracleText = "Each opponent votes for reveal or conceal. Then look at the top four cards of your library.",
    )
    val nonland = listOf(
        entry(commander),
        entry(card(id = "counterspell-uw", name = "Counterspell", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Counter target spell.")),
        entry(card(id = "mana-drain-uw", name = "Mana Drain", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Counter target spell.")),
        entry(card(id = "cryptic-uw", name = "Cryptic Command", typeLine = "Instant", cmc = 4.0, colorIdentity = listOf("U"), oracleText = "Choose two — Counter target spell.")),
        entry(card(id = "swan-song-uw", name = "Swan Song", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), oracleText = "Counter target enchantment, artifact, or spell.")),
        entry(card(id = "arcane-denial", name = "Arcane Denial", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Counter target spell.")),
        entry(card(id = "wrath-uw", name = "Wrath of God", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("W"), tags = listOf(CardTag.WRATH))),
        entry(card(id = "supreme-verdict-uw", name = "Supreme Verdict", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("U", "W"), tags = listOf(CardTag.WRATH))),
        entry(card(id = "farewell-uw", name = "Farewell", typeLine = "Sorcery", cmc = 5.0, colorIdentity = listOf("W"), tags = listOf(CardTag.WRATH))),
        entry(card(id = "swords-uw", name = "Swords to Plowshares", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "path-uw", name = "Path to Exile", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "fact-fiction-uw", name = "Fact or Fiction", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "rhystic-study-uw", name = "Rhystic Study", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "consecrated-sphinx-uw", name = "Consecrated Sphinx", typeLine = "Creature — Sphinx", cmc = 6.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE), power = "4", toughness = "6")),
        entry(card(id = "mystic-remora", name = "Mystic Remora", typeLine = "Enchantment", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "jace-tms-uw", name = "Jace, the Mind Sculptor", typeLine = "Legendary Planeswalker — Jace", cmc = 4.0, colorIdentity = listOf("U"), tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "elspeth-uw", name = "Elspeth, Sun's Champion", typeLine = "Legendary Planeswalker — Elspeth", cmc = 6.0, colorIdentity = listOf("W"))),
        entry(card(id = "esper-sentinel-uw", name = "Esper Sentinel", typeLine = "Artifact Creature — Human Soldier", cmc = 1.0, colorIdentity = listOf("W"), power = "1", toughness = "1")),
        entry(card(id = "batterskull-uw", name = "Batterskull", typeLine = "Legendary Artifact — Equipment", cmc = 5.0, colorIdentity = emptyList())),
        entry(card(id = "gilded-lotus-uw", name = "Gilded Lotus", typeLine = "Artifact", cmc = 5.0, colorIdentity = emptyList(), oracleText = "{T}: Add three mana of any one color.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "sol-ring-uw", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "arcane-signet-uw", name = "Arcane Signet", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList())),
        entry(card(id = "cyclonic-rift-uw", name = "Cyclonic Rift", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"))),
        entry(card(id = "solitude", name = "Solitude", typeLine = "Creature — Elemental Incarnation", cmc = 4.0, colorIdentity = listOf("W"), power = "3", toughness = "2", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "toxic-deluge-uw", name = "Toxic Deluge", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("B"), tags = listOf(CardTag.WRATH))),
        // Batch B expansion — additional counterspells (genuine draw-go control shell).
        entry(card(id = "mana-leak-uw", name = "Mana Leak", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Counter target spell unless its controller pays {3}.")),
        entry(card(id = "negate-uw", name = "Negate", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Counter target noncreature spell.")),
        entry(card(id = "dovins-veto-uw", name = "Dovin's Veto", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("W", "U"), oracleText = "Counter target spell. It can't be countered by spells or abilities.")),
        entry(card(id = "absorb-uw", name = "Absorb", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("W", "U"), oracleText = "Counter target spell. You gain 3 life.")),
        entry(card(id = "mystic-confluence-uw", name = "Mystic Confluence", typeLine = "Instant", cmc = 4.0, colorIdentity = listOf("U"), oracleText = "Choose three. You may choose the same mode more than once — Counter target spell; or return target creature to its owner's hand; or draw a card.")),
        // Batch B expansion — spot removal.
        entry(card(id = "councils-judgment-uw", name = "Council's Judgment", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("W"), oracleText = "Starting with you, each player votes for a nonland permanent you don't control. Exile each permanent with the most votes or tied for most votes.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "prison-realm-uw", name = "Prison Realm", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("W"), oracleText = "When Prison Realm enters the battlefield, exile target creature or planeswalker an opponent controls until Prison Realm leaves the battlefield. You gain 2 life.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "oblivion-ring-uw", name = "Oblivion Ring", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("W"), oracleText = "When Oblivion Ring enters the battlefield, exile another target nonland permanent.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "pongify-uw", name = "Pongify", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), oracleText = "Destroy target creature. Its controller creates a 3/3 green Ape creature token.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "rapid-hybridization-uw", name = "Rapid Hybridization", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), oracleText = "Destroy target creature. Its controller creates a 3/3 green Frog Lizard creature token.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "fateful-absence-uw", name = "Fateful Absence", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("W"), oracleText = "Destroy target creature or planeswalker. Its controller may draw a card.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "banisher-priest-uw", name = "Banisher Priest", typeLine = "Creature — Human Cleric", cmc = 3.0, colorIdentity = listOf("W"), power = "2", toughness = "3", oracleText = "When Banisher Priest enters the battlefield, exile target creature an opponent controls until Banisher Priest leaves the battlefield.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "reality-shift-uw", name = "Reality Shift", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Exile target creature. Its controller manifests the top card of their library.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "grasp-of-fate-uw", name = "Grasp of Fate", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("W"), oracleText = "When Grasp of Fate enters the battlefield, for each opponent, exile up to one target nonland permanent that player controls until Grasp of Fate leaves the battlefield.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "descend-sinful-uw", name = "Descend upon the Sinful", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("W"), oracleText = "Destroy target creature. If it had flying, exile it instead. Flashback—Sacrifice three untapped creatures.", tags = listOf(CardTag.REMOVAL))),
        // Batch B expansion — mass removal.
        entry(card(id = "rout-uw", name = "Rout", typeLine = "Sorcery", cmc = 6.0, colorIdentity = listOf("W"), oracleText = "Flash. Destroy all creatures.", tags = listOf(CardTag.WRATH))),
        entry(card(id = "winds-of-abandon-uw", name = "Winds of Abandon", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("W"), oracleText = "Exile target creature you don't control. Its controller searches their library for a basic land card, puts it onto the battlefield, then shuffles. Overload {4}{W}{W}.", tags = listOf(CardTag.WRATH))),
        entry(card(id = "day-of-judgment-uw", name = "Day of Judgment", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("W"), oracleText = "Destroy all creatures.", tags = listOf(CardTag.WRATH))),
        // Batch B expansion — card draw.
        entry(card(id = "windfall-uw", name = "Windfall", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Each player discards their hand, then draws cards equal to the greatest number of cards a player discarded this way.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "mulldrifter-uw", name = "Mulldrifter", typeLine = "Creature — Elemental", cmc = 5.0, colorIdentity = listOf("U"), power = "2", toughness = "2", oracleText = "Flying. When Mulldrifter enters the battlefield, draw two cards. Evoke {2}{U}.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "blue-suns-zenith-uw", name = "Blue Sun's Zenith", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Draw X cards, then put Blue Sun's Zenith into its owner's library X cards from the top.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "dig-through-time-uw", name = "Dig Through Time", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Delve. Look at the top seven cards of your library. Put two of them into your hand and the rest on the bottom in a random order.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "compulsive-research-uw", name = "Compulsive Research", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Draw three cards. Then discard a card unless you discard an artifact or land card.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "preordain-uw", name = "Preordain", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("U"), oracleText = "Scry 2, then draw a card.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "frantic-search-uw", name = "Frantic Search", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Draw two cards, then discard two cards. You may return an Island you control to its owner's hand.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "anticipate-uw", name = "Anticipate", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Look at the top three cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order.", tags = listOf(CardTag.DRAW_ENGINE))),
        // Batch B expansion — finishers.
        entry(card(id = "torrential-gearhulk-uw", name = "Torrential Gearhulk", typeLine = "Artifact Creature — Construct", cmc = 5.0, colorIdentity = listOf("U"), power = "5", toughness = "6", oracleText = "When Torrential Gearhulk enters the battlefield, you may cast target instant card from your graveyard without paying its mana cost.")),
        entry(card(id = "aetherling-uw", name = "Aetherling", typeLine = "Creature — Shapeshifter", cmc = 5.0, colorIdentity = listOf("U"), power = "4", toughness = "4", oracleText = "{U}: Aetherling gains indestructible until end of turn. {3}{U}: Return Aetherling to its owner's hand at the beginning of the next end step. {X}{X}{U}{U}: Put X +1/+1 counters on Aetherling.")),
        entry(card(id = "void-winnower-uw", name = "Void Winnower", typeLine = "Creature — Eldrazi", cmc = 9.0, colorIdentity = emptyList(), power = "11", toughness = "9", oracleText = "Your opponents can't cast spells with an odd mana value. Your opponents can't activate abilities with an odd activation cost unless they're mana abilities.")),
        entry(card(id = "approach-second-sun-uw", name = "Approach of the Second Sun", typeLine = "Sorcery", cmc = 7.0, colorIdentity = listOf("W"), oracleText = "If this spell was cast from your hand and you've cast another spell named Approach of the Second Sun this game, you win the game. Otherwise, put it into your library seventh from the top and you gain 7 life.", tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "inkwell-leviathan-uw", name = "Inkwell Leviathan", typeLine = "Creature — Leviathan", cmc = 7.0, colorIdentity = listOf("U"), power = "7", toughness = "7", oracleText = "This spell can't be countered. Trample, islandwalk.")),
        // Batch B expansion — mana rocks (ramp, mirrors the deck's control-shell fixing needs).
        entry(card(id = "mind-stone-uw", name = "Mind Stone", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), oracleText = "{T}: Add {C}. {1}, {T}, Sacrifice Mind Stone: Draw a card.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "fellwar-stone-uw", name = "Fellwar Stone", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), oracleText = "{T}: Add one mana of any color that a land an opponent controls could produce.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "coldsteel-heart-uw", name = "Coldsteel Heart", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), oracleText = "Coldsteel Heart enters the battlefield tapped. As it enters, choose a color. {T}: Add one mana of the chosen color.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "thran-dynamo-uw", name = "Thran Dynamo", typeLine = "Artifact", cmc = 4.0, colorIdentity = emptyList(), oracleText = "{T}: Add {C}{C}{C}.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "worn-powerstone-uw", name = "Worn Powerstone", typeLine = "Artifact", cmc = 3.0, colorIdentity = emptyList(), oracleText = "Worn Powerstone enters the battlefield tapped. {T}: Add {C}{C}.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "prismatic-lens-uw", name = "Prismatic Lens", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), oracleText = "{T}: Add {C}. {1}, {T}: Add one mana of any color.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "everflowing-chalice-uw", name = "Everflowing Chalice", typeLine = "Artifact", cmc = 0.0, colorIdentity = emptyList(), oracleText = "Everflowing Chalice enters the battlefield with X charge counters on it. {T}: Add an amount of {C} equal to the number of charge counters on Everflowing Chalice.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "solemn-simulacrum-uw", name = "Solemn Simulacrum", typeLine = "Artifact Creature — Golem", cmc = 4.0, colorIdentity = emptyList(), power = "2", toughness = "2", oracleText = "When Solemn Simulacrum enters the battlefield, search your library for a basic land card, put it onto the battlefield tapped, then shuffle. When Solemn Simulacrum dies, draw a card.", tags = listOf(CardTag.RAMP))),
    )
    val mainboard = withBasicsCommander(nonland, "Island", "U")
    return AnalysisV3Fixture(
        id = 11, name = "UW Control (W13 fixture)", anchor = "existing UW Control fixture",
        format = DeckFormat.COMMANDER, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.W, ManaColor.U, ManaColor.B),
        // Commander prior workstream: Aminatou's own ability (opponent vote + library manipulation,
        // no board impact) is a defensive utility piece with no strong secondary theme lean in this
        // fixture's own build -- CONTROL only, no theme prior (matches expectedThemes = NO_THEMES).
        commanderTags = listOf(CardTag.CONTROL),
        expectedMacro = "CONTROL", expectedPosture = NO_POSTURE, expectedThemes = NO_THEMES,
    )
}
