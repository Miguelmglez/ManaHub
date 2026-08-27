package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #10 (spec §9) — Grand Arbiter Augustin IV, Stax/Prison. Commander, Azorius (WU).
 * Expected: macro PRISON, no posture, no themes.
 *
 * PRISON is a spec-v3-only macro (does not exist as an [ArchetypeId] entry yet — only added in
 * Phase 3/§2). The CURRENT engine has no PRISON candidate at all, so this fixture is exactly the
 * one the current 5-macro (AGGRO/CONTROL/COMBO/RAMP/TEMPO, MIDRANGE-fallback) inference is
 * structurally unable to classify correctly — expected per the plan's own framing (deck-plan-
 * selection-mechanics.md).
 */
fun fixture10GrandArbiter(): AnalysisV3Fixture {
    val commander = card(
        id = "cmd-gaa4", name = "Grand Arbiter Augustin IV", typeLine = "Legendary Creature — Human Wizard",
        cmc = 4.0, colorIdentity = listOf("W", "U"), power = "2", toughness = "2",
        oracleText = "Each opponent plays with their hand revealed. Spells your opponents cast cost {1} more to cast. Spells you cast cost {1} less to cast.",
    )
    val nonland = listOf(
        entry(commander),
        entry(card(id = "winter-orb", name = "Winter Orb", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), tags = listOf(roleTag("stax_piece")))),
        entry(card(id = "static-orb", name = "Static Orb", typeLine = "Artifact", cmc = 3.0, colorIdentity = emptyList(), tags = listOf(roleTag("stax_piece")))),
        entry(card(id = "smokestack", name = "Smokestack", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), tags = listOf(roleTag("stax_piece")))),
        entry(card(id = "thalia-guardian", name = "Thalia, Guardian of Thraben", typeLine = "Legendary Creature — Human Soldier", cmc = 2.0, colorIdentity = listOf("W"), power = "2", toughness = "1", tags = listOf(roleTag("stax_piece")))),
        entry(card(id = "rule-of-law", name = "Rule of Law", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("W"), tags = listOf(roleTag("stax_piece")))),
        entry(card(id = "sphere-of-resistance", name = "Sphere of Resistance", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(roleTag("stax_piece")))),
        entry(card(id = "trinisphere", name = "Trinisphere", typeLine = "Artifact", cmc = 3.0, colorIdentity = emptyList(), tags = listOf(roleTag("stax_piece")))),
        entry(card(id = "lavinia", name = "Lavinia, Azorius Renegade", typeLine = "Legendary Creature — Human Wizard", cmc = 3.0, colorIdentity = listOf("W", "U"), power = "2", toughness = "3", tags = listOf(roleTag("stax_piece")))),
        entry(card(id = "aven-mindcensor", name = "Aven Mindcensor", typeLine = "Creature — Bird Cleric", cmc = 2.0, colorIdentity = listOf("W"), power = "2", toughness = "2", tags = listOf(roleTag("stax_piece")))),
        entry(card(id = "counterspell-ga", name = "Counterspell", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Counter target spell.")),
        entry(card(id = "swords-ga", name = "Swords to Plowshares", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "path-ga", name = "Path to Exile", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "wrath-ga", name = "Wrath of God", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("W"), tags = listOf(CardTag.WRATH))),
        entry(card(id = "supreme-verdict-ga", name = "Supreme Verdict", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("U", "W"), tags = listOf(CardTag.WRATH))),
        entry(card(id = "fact-fiction-ga", name = "Fact or Fiction", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "rhystic-study-ga", name = "Rhystic Study", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "kismet-ga", name = "Kismet", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("W"), tags = listOf(roleTag("stax_piece")))),
        entry(card(id = "enlightened-tutor", name = "Enlightened Tutor", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.TUTOR))),
        entry(card(id = "sol-ring-ga", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "arcane-signet-ga", name = "Arcane Signet", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList())),
        entry(card(id = "jace-tms-ga", name = "Jace, the Mind Sculptor", typeLine = "Legendary Planeswalker — Jace", cmc = 4.0, colorIdentity = listOf("U"), tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "elspeth-ga", name = "Elspeth, Sun's Champion", typeLine = "Legendary Planeswalker — Elspeth", cmc = 6.0, colorIdentity = listOf("W"))),
        entry(card(id = "esper-sentinel-ga", name = "Esper Sentinel", typeLine = "Artifact Creature — Human Soldier", cmc = 1.0, colorIdentity = listOf("W"), power = "1", toughness = "1")),
        entry(card(id = "batterskull-ga", name = "Batterskull", typeLine = "Legendary Artifact — Equipment", cmc = 5.0, colorIdentity = emptyList())),
        // Batch B expansion — additional stax pieces (a genuine stax skeleton, not overlay-thin).
        entry(card(id = "null-rod-ga", name = "Null Rod", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), oracleText = "Activated abilities of artifacts can't be activated unless they're mana abilities.", tags = listOf(roleTag("stax_piece")))),
        entry(card(id = "drannith-magistrate-ga", name = "Drannith Magistrate", typeLine = "Creature — Human Soldier", cmc = 2.0, colorIdentity = listOf("W"), power = "2", toughness = "2", oracleText = "Each opponent can't cast spells from anywhere other than their hand. {2}{W}, {T}: Exile target creature card from a graveyard.", tags = listOf(roleTag("stax_piece")))),
        entry(card(id = "ethersworn-canonist-ga", name = "Ethersworn Canonist", typeLine = "Artifact Creature — Construct", cmc = 3.0, colorIdentity = emptyList(), power = "2", toughness = "3", oracleText = "Each player can't cast more than one spell each turn.", tags = listOf(roleTag("stax_piece")))),
        entry(card(id = "grand-abolisher-ga", name = "Grand Abolisher", typeLine = "Creature — Human Cleric", cmc = 2.0, colorIdentity = listOf("W"), power = "2", toughness = "2", oracleText = "During your turn, spells you control can't be countered by spells or abilities. During your turn, your opponents can't cast spells or activate abilities of artifacts, creatures, or lands.", tags = listOf(roleTag("stax_piece")))),
        // Batch B expansion — spot removal.
        entry(card(id = "banisher-priest-ga", name = "Banisher Priest", typeLine = "Creature — Human Cleric", cmc = 3.0, colorIdentity = listOf("W"), power = "2", toughness = "3", oracleText = "When Banisher Priest enters the battlefield, exile target creature an opponent controls until Banisher Priest leaves the battlefield.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "fateful-absence-ga", name = "Fateful Absence", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("W"), oracleText = "Destroy target creature or planeswalker. Its controller may draw a card.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "prison-realm-ga", name = "Prison Realm", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("W"), oracleText = "When Prison Realm enters the battlefield, exile target creature or planeswalker an opponent controls until Prison Realm leaves the battlefield. You gain 2 life.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "oblivion-ring-ga", name = "Oblivion Ring", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("W"), oracleText = "When Oblivion Ring enters the battlefield, exile another target nonland permanent.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "pongify-ga", name = "Pongify", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), oracleText = "Destroy target creature. Its controller creates a 3/3 green Ape creature token.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "rapid-hybridization-ga", name = "Rapid Hybridization", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), oracleText = "Destroy target creature. Its controller creates a 3/3 green Frog Lizard creature token.", tags = listOf(CardTag.REMOVAL))),
        // Batch B expansion — mass removal.
        entry(card(id = "fumigate-ga", name = "Fumigate", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("W"), oracleText = "Destroy all creatures. You gain 1 life for each creature destroyed this way.", tags = listOf(CardTag.WRATH))),
        entry(card(id = "terminus-ga", name = "Terminus", typeLine = "Sorcery", cmc = 6.0, colorIdentity = listOf("W"), oracleText = "Put all creatures on the bottom of their owners' libraries. Miracle {W}.", tags = listOf(CardTag.WRATH))),
        // Batch B expansion — card draw.
        entry(card(id = "mystic-remora-ga", name = "Mystic Remora", typeLine = "Enchantment", cmc = 1.0, colorIdentity = listOf("U"), oracleText = "Whenever an opponent casts a noncreature spell, you may pay {1}. If you don't, sacrifice Mystic Remora. At the beginning of your upkeep, if Mystic Remora is on the battlefield, draw a card.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "mulldrifter-ga", name = "Mulldrifter", typeLine = "Creature — Elemental", cmc = 5.0, colorIdentity = listOf("U"), power = "2", toughness = "2", oracleText = "Flying. When Mulldrifter enters the battlefield, draw two cards. Evoke {2}{U}.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "consecrated-sphinx-ga", name = "Consecrated Sphinx", typeLine = "Creature — Sphinx", cmc = 6.0, colorIdentity = listOf("U"), power = "4", toughness = "6", oracleText = "Flying. Whenever an opponent draws a card, you may draw two cards.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "windfall-ga", name = "Windfall", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Each player discards their hand, then draws cards equal to the greatest number of cards a player discarded this way.", tags = listOf(CardTag.DRAW_ENGINE))),
        // Batch B expansion — tutors.
        entry(card(id = "idyllic-tutor-ga", name = "Idyllic Tutor", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("W"), oracleText = "Search your library for an enchantment card, reveal it, put it into your hand, then shuffle.", tags = listOf(CardTag.TUTOR))),
        entry(card(id = "merchant-scroll-ga", name = "Merchant Scroll", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), oracleText = "Search your library for a blue instant or sorcery card, reveal it, put it into your hand, then shuffle.", tags = listOf(CardTag.TUTOR))),
        entry(card(id = "mystical-tutor-ga", name = "Mystical Tutor", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), oracleText = "Search your library for an instant or sorcery card, reveal it, then shuffle and put that card on top.", tags = listOf(CardTag.TUTOR))),
        // Batch B expansion — mana rocks (mono-artifact ramp, the only ramp shape a WU deck has).
        entry(card(id = "mind-stone-ga", name = "Mind Stone", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), oracleText = "{T}: Add {C}. {1}, {T}, Sacrifice Mind Stone: Draw a card.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "fellwar-stone-ga", name = "Fellwar Stone", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), oracleText = "{T}: Add one mana of any color that a land an opponent controls could produce.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "coldsteel-heart-ga", name = "Coldsteel Heart", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), oracleText = "Coldsteel Heart enters the battlefield tapped. As it enters, choose a color. {T}: Add one mana of the chosen color.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "thran-dynamo-ga", name = "Thran Dynamo", typeLine = "Artifact", cmc = 4.0, colorIdentity = emptyList(), oracleText = "{T}: Add {C}{C}{C}.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "worn-powerstone-ga", name = "Worn Powerstone", typeLine = "Artifact", cmc = 3.0, colorIdentity = emptyList(), oracleText = "Worn Powerstone enters the battlefield tapped. {T}: Add {C}{C}.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "prismatic-lens-ga", name = "Prismatic Lens", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), oracleText = "{T}: Add {C}. {1}, {T}: Add one mana of any color.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "everflowing-chalice-ga", name = "Everflowing Chalice", typeLine = "Artifact", cmc = 0.0, colorIdentity = emptyList(), oracleText = "Everflowing Chalice enters the battlefield with X charge counters on it. {T}: Add an amount of {C} equal to the number of charge counters on Everflowing Chalice.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "gilded-lotus-ga", name = "Gilded Lotus", typeLine = "Artifact", cmc = 5.0, colorIdentity = emptyList(), oracleText = "{T}: Add three mana of any one color.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "solemn-simulacrum-ga", name = "Solemn Simulacrum", typeLine = "Artifact Creature — Golem", cmc = 4.0, colorIdentity = emptyList(), power = "2", toughness = "2", oracleText = "When Solemn Simulacrum enters the battlefield, search your library for a basic land card, put it onto the battlefield tapped, then shuffle. When Solemn Simulacrum dies, draw a card.", tags = listOf(CardTag.RAMP))),
        // Batch B expansion — finishers.
        entry(card(id = "approach-second-sun-ga", name = "Approach of the Second Sun", typeLine = "Sorcery", cmc = 7.0, colorIdentity = listOf("W"), oracleText = "If this spell was cast from your hand and you've cast another spell named Approach of the Second Sun this game, you win the game. Otherwise, put it into your library seventh from the top and you gain 7 life.", tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "torrential-gearhulk-ga", name = "Torrential Gearhulk", typeLine = "Artifact Creature — Construct", cmc = 5.0, colorIdentity = listOf("U"), power = "5", toughness = "6", oracleText = "When Torrential Gearhulk enters the battlefield, you may cast target instant card from your graveyard without paying its mana cost.")),
        entry(card(id = "aetherling-ga", name = "Aetherling", typeLine = "Creature — Shapeshifter", cmc = 5.0, colorIdentity = listOf("U"), power = "4", toughness = "4", oracleText = "{U}: Aetherling gains indestructible until end of turn. {3}{U}: Return Aetherling to its owner's hand at the beginning of the next end step. {X}{X}{U}{U}: Put X +1/+1 counters on Aetherling.")),
        entry(card(id = "void-winnower-ga", name = "Void Winnower", typeLine = "Creature — Eldrazi", cmc = 9.0, colorIdentity = emptyList(), power = "11", toughness = "9", oracleText = "Your opponents can't cast spells with an odd mana value. Your opponents can't activate abilities with an odd activation cost unless they're mana abilities.")),
        // Batch B expansion — protection.
        entry(card(id = "swiftfoot-boots-ga", name = "Swiftfoot Boots", typeLine = "Artifact — Equipment", cmc = 2.0, colorIdentity = emptyList(), oracleText = "Equipped creature has hexproof and haste. Equip {1}.", tags = listOf(CardTag.PROTECTION))),
        entry(card(id = "lightning-greaves-ga", name = "Lightning Greaves", typeLine = "Artifact — Equipment", cmc = 2.0, colorIdentity = emptyList(), oracleText = "Equipped creature has haste and shroud. Equip {0}.", tags = listOf(CardTag.PROTECTION))),
        entry(card(id = "darksteel-plate-ga", name = "Darksteel Plate", typeLine = "Artifact — Equipment", cmc = 5.0, colorIdentity = emptyList(), oracleText = "Indestructible. Equipped creature has indestructible. Equip {3}.", tags = listOf(CardTag.PROTECTION))),
        entry(card(id = "dawn-charm-ga", name = "Dawn Charm", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Choose one — Prevent all damage that a source of your choice would deal this turn; or counter target activated ability; or target permanent you control gains protection from a color of your choice until end of turn.", tags = listOf(CardTag.PROTECTION))),
        entry(card(id = "rebuff-wicked-ga", name = "Rebuff the Wicked", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), oracleText = "Counter target activated or triggered ability that targets a permanent you control.", tags = listOf(CardTag.PROTECTION))),
        // Batch B expansion — generic control staples (no dedicated axis role, real Prison-adjacent value).
        entry(card(id = "cyclonic-rift-ga", name = "Cyclonic Rift", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Return target permanent you don't control to its owner's hand. Overload {6}{U}.")),
        entry(card(id = "mystic-confluence-ga", name = "Mystic Confluence", typeLine = "Instant", cmc = 4.0, colorIdentity = listOf("U"), oracleText = "Choose three. You may choose the same mode more than once — Counter target spell; or return target creature to its owner's hand; or draw a card.")),
    )
    val mainboard = withBasicsCommander(nonland, "Island", "U")
    return AnalysisV3Fixture(
        id = 10, name = "Grand Arbiter Augustin IV (Stax/Prison)", anchor = "Grand Arbiter Augustin IV",
        format = DeckFormat.COMMANDER, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.W, ManaColor.U),
        // Commander prior workstream: Grand Arbiter Augustin IV (tax your opponents' spells,
        // discount your own) is one of THE textbook stax/tax commanders in cEDH and casual stax
        // alike -- honest, well-established real-world PRISON read (CardTag.STAX reverse-maps to
        // ArchetypeId.PRISON via DeckIdentitySeedTags, mirroring PRISON's own absorption of the old
        // STAX theme, spec §4.1). See the gate report for why this prior CANNOT flip this fixture's
        // macro despite matching expectedMacro exactly -- PRISON's own linearity prototype (0.60)
        // is structurally unreachable here (measured 0.000, stax_piece forms no producer/payoff
        // edge), a bounded prior correctly refuses to paper over that.
        commanderTags = listOf(CardTag.STAX),
        expectedMacro = "PRISON", expectedPosture = NO_POSTURE, expectedThemes = NO_THEMES,
    )
}
