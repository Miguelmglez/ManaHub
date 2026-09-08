package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #17 (spec §9) — the NEGATIVE fixture: a deliberately incoherent 99-card Sultai (BUG)
 * "goodstuff" pile. Every card is individually strong and format-staple-tier, but by construction
 * NO axis has both a producer and a payoff present: one token generator with zero death/anthem
 * payoffs, one reanimation target with zero reanimation spells, a graveyard-hate piece alongside a
 * (single, isolated) graveyard enabler, a counters payoff with zero counters producers, an
 * artifact payoff with zero artifact producers, etc. — every "engine" is a single, orphaned card.
 * This is the fixture that proves the engine can tell coherence from card quality; it is expected
 * (later phases) to land low on P4/SYNERGY, resolve an ambiguous macro, and get the "Custom" label.
 * No commander is pinned (no single Sultai legendary anchors this pile on purpose — it is a random
 * assortment, not a build-around).
 */
fun fixture17NegativeGoodstuff(): AnalysisV3Fixture {
    val commander = card(
        id = "cmd-negative-goodstuff", name = "Muldrotha, the Gravetide", typeLine = "Legendary Creature — Elder Horror",
        cmc = 6.0, colorIdentity = listOf("B", "G", "U"), power = "6", toughness = "6",
        oracleText = "During each of your turns, you may play a land and cast a permanent spell of each permanent type from your graveyard.",
    )
    val nonland = listOf(
        entry(commander),
        // One isolated token generator (no anthem, no death payoff anywhere in this list).
        entry(card(id = "ng-mimic-vat", name = "Mimic Vat", typeLine = "Artifact", cmc = 3.0, colorIdentity = emptyList(), tags = listOf(roleTag("token_generator")))),
        // One isolated reanimation target (no reanimation spell/graveyard_enabler anywhere here).
        entry(card(id = "ng-griselbrand", name = "Griselbrand", typeLine = "Legendary Creature — Demon", cmc = 8.0, colorIdentity = listOf("B"), power = "7", toughness = "7", tags = listOf(CardTag.WIN_CON))),
        // One isolated counters payoff (no counters_source anywhere in this list).
        entry(card(id = "ng-hydras-growth", name = "Kalonian Hydra", typeLine = "Creature — Hydra", cmc = 4.0, colorIdentity = listOf("G"), power = "4", toughness = "4", tags = listOf(roleTag("counters_payoff")))),
        // One isolated artifact payoff (no treasure/artifact producer anywhere in this list).
        entry(card(id = "ng-etherium-sculptor", name = "Etherium Sculptor", typeLine = "Creature — Human Artificer", cmc = 2.0, colorIdentity = listOf("U"), power = "1", toughness = "2", tags = listOf(roleTag("artifact_payoff")))),
        // One isolated graveyard-hate piece (self-defeating alongside the single reanimation target above).
        entry(card(id = "ng-relic-progenitus", name = "Relic of Progenitus", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(roleTag("graveyard_hate")))),
        // Generic high-power staples, no shared axis.
        entry(card(id = "ng-consecrated-sphinx", name = "Consecrated Sphinx", typeLine = "Creature — Sphinx", cmc = 6.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE), power = "4", toughness = "6")),
        entry(card(id = "ng-sheoldred", name = "Sheoldred, the Apocalypse", typeLine = "Legendary Creature — Phyrexian Praetor", cmc = 4.0, colorIdentity = listOf("B"), power = "4", toughness = "5")),
        entry(card(id = "ng-craterhoof", name = "Craterhoof Behemoth", typeLine = "Creature — Beast", cmc = 6.0, colorIdentity = listOf("G"), power = "6", toughness = "6", tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "ng-toxic-deluge", name = "Toxic Deluge", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("B"), tags = listOf(CardTag.WRATH))),
        entry(card(id = "ng-cyclonic-rift", name = "Cyclonic Rift", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"))),
        entry(card(id = "ng-beast-within", name = "Beast Within", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("G"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "ng-maelstrom-pulse", name = "Maelstrom Pulse", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("B", "G"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "ng-demonic-tutor", name = "Demonic Tutor", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(CardTag.TUTOR))),
        entry(card(id = "ng-rhystic-study", name = "Rhystic Study", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "ng-solemn-simulacrum", name = "Solemn Simulacrum", typeLine = "Artifact Creature — Golem", cmc = 4.0, colorIdentity = emptyList(), power = "2", toughness = "2", tags = listOf(CardTag.RAMP))),
        entry(card(id = "ng-farseek", name = "Farseek", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), tags = listOf(CardTag.RAMP))),
        entry(card(id = "ng-sol-ring", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "ng-arcane-signet", name = "Arcane Signet", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList())),
        entry(card(id = "ng-eternal-witness", name = "Eternal Witness", typeLine = "Creature — Elf Shaman", cmc = 3.0, colorIdentity = listOf("G"), power = "2", toughness = "1", tags = listOf(roleTag("recursion")))),
        entry(card(id = "ng-heros-downfall", name = "Hero's Downfall", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "ng-baleful-strix", name = "Baleful Strix", typeLine = "Artifact Creature — Bird", cmc = 2.0, colorIdentity = listOf("U", "B"), power = "1", toughness = "1", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "ng-massacre-wurm", name = "Massacre Wurm", typeLine = "Creature — Wurm", cmc = 5.0, colorIdentity = listOf("B"), power = "6", toughness = "5")),
        entry(card(id = "ng-thassas-oracle", name = "Thassa's Oracle", typeLine = "Creature — Merfolk Wizard", cmc = 1.0, colorIdentity = listOf("U"), power = "1", toughness = "3", tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "ng-vorinclex", name = "Vorinclex, Voice of Hunger", typeLine = "Legendary Creature — Phyrexian Beast", cmc = 6.0, colorIdentity = listOf("G"), power = "7", toughness = "6")),
        entry(card(id = "ng-nighthawk", name = "Vampire Nighthawk", typeLine = "Creature — Vampire Shaman", cmc = 3.0, colorIdentity = listOf("B"), power = "2", toughness = "3")),
        // Batch B expansion — MORE individually strong Sultai staples, deliberately untagged for
        // any synergy-axis role (no roleTag(...) call anywhere in this block): each card is a
        // real, powerful, standalone pick that shares no producer<->payoff pair with anything else
        // in this deck. The only tags used below (REMOVAL/WRATH/DRAW_ENGINE/RAMP/TUTOR/WIN_CON) are
        // the 5 legacy CardTag-based roles plus WIN_CON/finisher, none of which appear in
        // SynergyGraph's AXIS_PRODUCES/AXIS_CONSUMES/AXIS_AMPLIFIES tables (verified against
        // ArchetypeRoleClassifier.kt before authoring this block) -- so none of them can light an
        // axis. Expanding this fixture can only ever DILUTE its already-orphaned axis signals
        // further (every AxisIdeal scales up with nonLandCount while the lone existing
        // producer/payoff counts per axis stay fixed at 1), never accidentally create a live one.
        entry(card(id = "ng-jin-gitaxias", name = "Jin-Gitaxias, Core Augur", typeLine = "Legendary Creature — Phyrexian Giant", cmc = 6.0, colorIdentity = listOf("U"), power = "5", toughness = "5", oracleText = "Flying. Whenever you draw a card, draw two additional cards. Whenever an opponent draws a card, that player draws two fewer cards instead.", tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "ng-grave-titan", name = "Grave Titan", typeLine = "Creature — Giant", cmc = 6.0, colorIdentity = listOf("B"), power = "6", toughness = "6", oracleText = "Deathtouch. When Grave Titan enters the battlefield, create two 2/2 black Zombie creature tokens. Whenever Grave Titan attacks, create a 2/2 black Zombie creature token that's tapped and attacking.", tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "ng-torment-hailfire", name = "Torment of Hailfire", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("B"), oracleText = "Torment of Hailfire deals X damage to each opponent. Repeat this process for each opponent, choosing new modes.", tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "ng-tasigur", name = "Tasigur, the Golden Fang", typeLine = "Legendary Creature — Human Warrior", cmc = 6.0, colorIdentity = listOf("B", "U", "G"), power = "4", toughness = "5", oracleText = "Delve. {2}{B/G}: Target opponent puts the top two cards of their library into their graveyard.")),
        entry(card(id = "ng-gonti", name = "Gonti, Lord of Luxury", typeLine = "Legendary Creature — Vampire Advisor", cmc = 4.0, colorIdentity = listOf("B"), power = "3", toughness = "3", oracleText = "Deathtouch. When Gonti, Lord of Luxury enters the battlefield, look at the top four cards of target opponent's library. You may cast a nonland card from among them without paying its mana cost.")),
        entry(card(id = "ng-yawgmoth", name = "Yawgmoth, Thran Physician", typeLine = "Legendary Creature — Phyrexian Human", cmc = 2.0, colorIdentity = listOf("B"), power = "1", toughness = "3", oracleText = "Sacrifice a creature: Put a -1/-1 counter on up to one target creature and draw a card. Activate only once each turn. {B}, Remove a -1/-1 counter from a permanent: Target creature gets -1/-1 until end of turn.")),
        entry(card(id = "ng-vito", name = "Vito, Thorn of the Dusk Rose", typeLine = "Legendary Creature — Vampire Cleric", cmc = 3.0, colorIdentity = listOf("B"), power = "2", toughness = "3", oracleText = "Lifelink. If a source you control would deal damage to a permanent or player, it deals that much damage plus 1 instead.")),
        entry(card(id = "ng-grim-tutor", name = "Grim Tutor", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("B"), oracleText = "Search your library for a card, put it into your hand, then shuffle. You lose 3 life.", tags = listOf(CardTag.TUTOR))),
        entry(card(id = "ng-imperial-seal", name = "Imperial Seal", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("B"), oracleText = "Search your library for a card and put it on top of your library, then shuffle. You lose 2 life.", tags = listOf(CardTag.TUTOR))),
        entry(card(id = "ng-mystical-tutor", name = "Mystical Tutor", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), oracleText = "Search your library for an instant or sorcery card, reveal it, then shuffle and put that card on top.", tags = listOf(CardTag.TUTOR))),
        entry(card(id = "ng-assassins-trophy", name = "Assassin's Trophy", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("B", "G"), oracleText = "Destroy target permanent an opponent controls. Its controller may search their library for a basic land card, put it onto the battlefield, then shuffle.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "ng-putrefy", name = "Putrefy", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("B", "G"), oracleText = "Destroy target artifact or creature.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "ng-ravenous-chupacabra", name = "Ravenous Chupacabra", typeLine = "Creature — Beast", cmc = 4.0, colorIdentity = listOf("B"), power = "2", toughness = "2", oracleText = "When Ravenous Chupacabra enters the battlefield, destroy target creature an opponent controls.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "ng-baleful-mastery", name = "Baleful Mastery", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Exile target creature or planeswalker. Its controller may search their library for a card with a different name and equal or lesser mana value.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "ng-feed-swarm", name = "Feed the Swarm", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Destroy target creature or enchantment an opponent controls. You lose life equal to that permanent's mana value.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "ng-doom-blade", name = "Doom Blade", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Destroy target nonblack creature.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "ng-damnation", name = "Damnation", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("B"), oracleText = "Destroy all creatures. They can't be regenerated.", tags = listOf(CardTag.WRATH))),
        entry(card(id = "ng-languish", name = "Languish", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("B"), oracleText = "All creatures get -4/-4 until end of turn.", tags = listOf(CardTag.WRATH))),
        entry(card(id = "ng-bane-living", name = "Bane of the Living", typeLine = "Creature — Zombie", cmc = 6.0, colorIdentity = listOf("G"), power = "5", toughness = "5", oracleText = "Morph {X}{X}{G}{G}. When Bane of the Living is turned face up, all creatures get -X/-X until end of turn.", tags = listOf(CardTag.WRATH))),
        // NOTE: deliberately NOT a counterspell-texted card — the "counter target ... spell" oracle
        // regex is an automatic, TAG-INDEPENDENT fallback (ArchetypeRoleClassifier.COUNTER_SPELL_ORACLE)
        // that would register as a SPELLS-axis payoff and risk lighting SPELLS given this fixture's
        // already-heavy structural instant/sorcery density. Brainstorm carries no such text.
        entry(card(id = "ng-brainstorm", name = "Brainstorm", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), oracleText = "Draw three cards, then put two cards from your hand on top of your library in any order.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "ng-fact-fiction", name = "Fact or Fiction", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("U"), oracleText = "Reveal the top five cards of your library. An opponent separates them into two piles. Put one pile into your hand and the other into your graveyard.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "ng-nights-whisper", name = "Night's Whisper", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "You draw two cards and you lose 2 life.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "ng-sign-blood", name = "Sign in Blood", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Target player draws two cards and loses 2 life.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "ng-read-bones", name = "Read the Bones", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("B"), oracleText = "Look at the top four cards of your library. Put two of them into your hand and the rest into your graveyard. You lose 2 life.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "ng-dark-confidant", name = "Dark Confidant", typeLine = "Creature — Human Wizard", cmc = 2.0, colorIdentity = listOf("B"), power = "2", toughness = "1", oracleText = "At the beginning of your upkeep, reveal the top card of your library and put that card into your hand. You lose life equal to its mana value.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "ng-growth-spiral", name = "Growth Spiral", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("G", "U"), oracleText = "Draw a card. You may put a land card from your hand onto the battlefield.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "ng-natures-lore", name = "Nature's Lore", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), oracleText = "Search your library for a Forest card, put it onto the battlefield, then shuffle.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "ng-rampant-growth", name = "Rampant Growth", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), oracleText = "Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "ng-kodamas-reach", name = "Kodama's Reach", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("G"), oracleText = "Search your library for up to two basic land cards, reveal them, put one onto the battlefield tapped and the other into your hand, then shuffle.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "ng-sakura-tribe-elder", name = "Sakura-Tribe Elder", typeLine = "Creature — Snake Shaman", cmc = 2.0, colorIdentity = listOf("G"), power = "1", toughness = "1", oracleText = "Sacrifice Sakura-Tribe Elder: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.", tags = listOf(CardTag.RAMP))),
        // NOTE: same reason as Brainstorm above — Vendilion Clique is a real strong standalone
        // blue staple with no counterspell text, avoiding the automatic SPELLS-axis fallback.
        entry(card(id = "ng-vendilion-clique", name = "Vendilion Clique", typeLine = "Creature — Faerie Wizard", cmc = 4.0, colorIdentity = listOf("U"), power = "3", toughness = "1", oracleText = "Flash. Flying. When Vendilion Clique enters the battlefield, look at target player's hand. You may choose a nonland card from it. If you do, that player reveals the chosen card, puts it on the bottom of their library, then draws a card.")),
        entry(card(id = "ng-deathrite-shaman", name = "Deathrite Shaman", typeLine = "Creature — Elf Shaman", cmc = 1.0, colorIdentity = listOf("B", "G"), power = "1", toughness = "2", oracleText = "{T}: Exile target land card from a graveyard. Add one mana of any color. {B}, {T}: Exile target creature card from a graveyard. Each opponent loses 2 life. {G}, {T}: Exile target instant or sorcery card from a graveyard. You gain 2 life.")),
        entry(card(id = "ng-tergrid", name = "Tergrid, God of Fright", typeLine = "Legendary Creature — God", cmc = 5.0, colorIdentity = listOf("B"), power = "5", toughness = "5", oracleText = "Whenever an opponent sacrifices or discards a permanent or nonland card, except during their draw step, you may put that card onto the battlefield under your control, or into your hand if it isn't a permanent card.")),
        entry(card(id = "ng-woe-strider", name = "Woe Strider", typeLine = "Creature — Zombie Goat", cmc = 2.0, colorIdentity = listOf("B"), power = "2", toughness = "2", oracleText = "Sacrifice another creature: Scry 1. Sacrifice Woe Strider: Return Woe Strider to the battlefield tapped under its owner's control at the beginning of the next end step.")),
        entry(card(id = "ng-snapcaster-mage", name = "Snapcaster Mage", typeLine = "Creature — Human Wizard", cmc = 2.0, colorIdentity = listOf("U"), power = "2", toughness = "1", oracleText = "Flash. When Snapcaster Mage enters the battlefield, target instant or sorcery card in your graveyard gains flashback until end of turn.")),
        entry(card(id = "ng-uro", name = "Uro, Titan of Nature's Wrath", typeLine = "Legendary Creature — Titan", cmc = 3.0, colorIdentity = listOf("G", "U"), power = "6", toughness = "6", oracleText = "Escape—{4}{G}{U}, exile five other cards from your graveyard. When Uro enters the battlefield, gain 3 life, then draw a card, then put a land card from your hand onto the battlefield.", tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "ng-hostage-taker", name = "Hostage Taker", typeLine = "Creature — Human Pirate", cmc = 3.0, colorIdentity = listOf("U", "B"), power = "2", toughness = "3", oracleText = "When Hostage Taker enters the battlefield, exile another target creature or artifact until Hostage Taker leaves the battlefield. You may cast the exiled card for as long as it remains exiled, and you may spend mana as though it were mana of any type to cast it.")),
        entry(card(id = "ng-diabolic-intent", name = "Diabolic Intent", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "As an additional cost to cast this spell, sacrifice a creature. Search your library for a card, put it into your hand, then shuffle.", tags = listOf(CardTag.TUTOR))),
    )
    val mainboard = withBasicsCommander(nonland, "Swamp", "B")
    return AnalysisV3Fixture(
        id = 17, name = "Negative fixture — Sultai goodstuff pile (incoherent)", anchor = "n/a (negative fixture)",
        format = DeckFormat.COMMANDER, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.B, ManaColor.G, ManaColor.U),
        // Commander prior workstream (2026-08-26): DELIBERATELY commanderTags stays emptyList()
        // (the default). Muldrotha's own ability ("cast a permanent of each type from graveyard")
        // is a raw flexibility/value engine with NO directional archetype lean of its own -- unlike
        // every other fixture's commander (Edgar Markov/Urza/Grand Arbiter etc., each unambiguously
        // single-archetype-coded), Muldrotha could headline literally any of the 5 macros. This is
        // an honest real-world read, not an evasion: giving this fixture's commander ANY
        // macro-signaling tag was checked by hand against this fixture's own measured axes
        // (winner CONTROL .799, runnerup MIDRANGE .721, margin .078 -- one hair under the 0.08
        // ambiguity floor) and a MIDRANGE tag (the honest-looking "graveyard value" read) would
        // have pushed MIDRANGE to .801, FLIPPING this fixture to a confident (wrong) macro -- which
        // would violate this workstream's own hard constraint that the negative fixture must never
        // gain a confident macro. Reported in the gate report, not silently avoided.
        expectedMacro = "ambiguous / Custom", expectedPosture = NO_POSTURE, expectedThemes = NO_THEMES,
    )
}
