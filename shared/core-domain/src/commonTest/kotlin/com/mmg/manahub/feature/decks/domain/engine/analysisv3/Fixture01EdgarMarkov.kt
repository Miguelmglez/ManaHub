package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #1 (spec §9) — Edgar Markov, Vampire tribal Aggro. Commander, Mardu (RWB).
 * Expected: macro AGGRO, no posture, theme TRIBAL:vampire.
 */
fun fixture01EdgarMarkov(): AnalysisV3Fixture {
    val commander = card(
        id = "cmd-edgar-markov", name = "Edgar Markov",
        typeLine = "Legendary Creature — Vampire Knight", cmc = 6.0,
        colorIdentity = listOf("R", "W", "B"), power = "4", toughness = "4",
        oracleText = "Eminence — Whenever you cast a Vampire spell, if Edgar Markov is on the battlefield or in the command zone, create a 1/1 black Vampire creature token with lifelink. Other Vampires you control get +1/+1. Whenever Edgar Markov attacks, create X 1/1 black Vampire creature tokens with lifelink, where X is the number of Vampires you control.",
    )
    val nonland = listOf(
        entry(commander),
        entry(card(id = "vampire-nighthawk", name = "Vampire Nighthawk", typeLine = "Creature — Vampire Shaman", cmc = 3.0, colorIdentity = listOf("B"), power = "2", toughness = "3")),
        entry(card(id = "bloodline-keeper", name = "Bloodline Keeper", typeLine = "Creature — Vampire", cmc = 4.0, colorIdentity = listOf("B"), power = "3", toughness = "3", tags = listOf(roleTag("token_generator")))),
        entry(card(id = "captivating-vampire", name = "Captivating Vampire", typeLine = "Creature — Vampire Noble", cmc = 3.0, colorIdentity = listOf("B"), power = "2", toughness = "2", oracleText = "Other Vampires you control get +1/+1.")),
        entry(card(id = "stromkirk-noble", name = "Stromkirk Noble", typeLine = "Creature — Vampire Warrior", cmc = 1.0, colorIdentity = listOf("R"), power = "2", toughness = "1")),
        entry(card(id = "falkenrath-gorger-em", name = "Falkenrath Gorger", typeLine = "Creature — Vampire", cmc = 1.0, colorIdentity = listOf("R"), power = "3", toughness = "1")),
        entry(card(id = "legion-lieutenant", name = "Legion Lieutenant", typeLine = "Creature — Vampire Soldier", cmc = 2.0, colorIdentity = listOf("W"), power = "2", toughness = "1", oracleText = "Other Vampires you control get +1/+1.")),
        entry(card(id = "vampire-cutthroat", name = "Vampire Cutthroat", typeLine = "Creature — Vampire Pirate", cmc = 4.0, colorIdentity = listOf("B"), power = "3", toughness = "3")),
        entry(card(id = "sanguine-bond-em", name = "Sanguine Bond", typeLine = "Enchantment", cmc = 4.0, colorIdentity = listOf("B"), tags = listOf(roleTag("lifegain_payoff")))),
        entry(card(id = "kalitas", name = "Kalitas, Traitor of Ghet", typeLine = "Legendary Creature — Vampire Warrior", cmc = 4.0, colorIdentity = listOf("B"), power = "3", toughness = "4", tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "olivia-voldaren", name = "Olivia Voldaren", typeLine = "Legendary Creature — Vampire", cmc = 4.0, colorIdentity = listOf("B", "R"), power = "3", toughness = "3")),
        entry(card(id = "anowon", name = "Anowon, the Ruin Sage", typeLine = "Legendary Creature — Vampire Wizard", cmc = 4.0, colorIdentity = listOf("B"), power = "3", toughness = "3")),
        entry(card(id = "vona-butcher", name = "Vona, Butcher of Magan", typeLine = "Legendary Creature — Vampire Warrior", cmc = 5.0, colorIdentity = listOf("R", "W", "B"), power = "5", toughness = "5")),
        entry(card(id = "bolt-em", name = "Lightning Bolt", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "swords-em", name = "Swords to Plowshares", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "anguished-unmaking", name = "Anguished Unmaking", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("W", "B"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "wingmate-roc", name = "Wingmate Roc", typeLine = "Creature — Bird", cmc = 4.0, colorIdentity = listOf("W"), power = "3", toughness = "4")),
        entry(card(id = "skullclamp-em", name = "Skullclamp", typeLine = "Artifact — Equipment", cmc = 1.0, colorIdentity = emptyList())),
        entry(card(id = "bloodforged-battle-axe", name = "Bloodforged Battle-Axe", typeLine = "Artifact — Equipment", cmc = 2.0, colorIdentity = emptyList())),
        entry(card(id = "sol-ring-em", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "arcane-signet-em", name = "Arcane Signet", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList())),
        entry(card(id = "indulgent-aristocrat", name = "Indulgent Aristocrat", typeLine = "Creature — Vampire Noble", cmc = 2.0, colorIdentity = listOf("B", "R"), power = "2", toughness = "2", oracleText = "Other Vampires you control get +1/+1.")),
        entry(card(id = "vanquisher-banner", name = "Vanquisher's Banner", typeLine = "Legendary Artifact", cmc = 5.0, colorIdentity = emptyList(), oracleText = "Whenever you cast a creature spell of the chosen type, draw a card.")),
        entry(card(id = "patriarchs-bidding", name = "Patriarch's Bidding", typeLine = "Sorcery", cmc = 5.0, colorIdentity = listOf("B"), tags = listOf(roleTag("token_generator")))),
        entry(card(id = "urge-to-feed", name = "Urge to Feed", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("B"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "path-to-exile", name = "Path to Exile", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        // Phase 3a-FIX batch A: density expansion (25 -> 63 non-land, spec §9 realistic-deck fix).
        // Real Mardu Vampire tribal / aggro staples, strengthening TRIBE:vampire (more vampire
        // bodies + more real "Other Vampires you control get +1/+1"-shaped lord text for
        // TribeDeriver) plus a realistic ramp/removal/draw package for the AGGRO macro.
        entry(card(id = "drana-liberator", name = "Drana, Liberator of Malakir", typeLine = "Legendary Creature — Vampire Warrior", cmc = 4.0, colorIdentity = listOf("R", "B"), power = "3", toughness = "3", oracleText = "Flying, first strike, haste. Whenever Drana, Liberator of Malakir attacks, each other attacking creature gets +1/+1 until end of turn.")),
        entry(card(id = "sanctum-seeker", name = "Sanctum Seeker", typeLine = "Creature — Vampire Warlock", cmc = 5.0, colorIdentity = listOf("R", "B"), power = "4", toughness = "4", oracleText = "Flying. Whenever you gain life, Sanctum Seeker deals that much damage to target opponent or planeswalker an opponent controls.", tags = listOf(roleTag("lifegain_payoff")))),
        entry(card(id = "champion-of-dusk", name = "Champion of Dusk", typeLine = "Creature — Vampire Cleric", cmc = 5.0, colorIdentity = listOf("B"), power = "3", toughness = "4", oracleText = "When Champion of Dusk enters the battlefield, you gain 1 life and draw a card for each Vampire you control.", tags = listOf(roleTag("etb_payoff"), CardTag.DRAW_ENGINE))),
        entry(card(id = "gifted-aetherborn", name = "Gifted Aetherborn", typeLine = "Creature — Vampire", cmc = 2.0, colorIdentity = listOf("B"), power = "2", toughness = "3", oracleText = "Deathtouch, lifelink.")),
        entry(card(id = "malakir-bloodwitch", name = "Malakir Bloodwitch", typeLine = "Creature — Vampire Shaman", cmc = 5.0, colorIdentity = listOf("W", "B"), power = "4", toughness = "4", oracleText = "Flying. When Malakir Bloodwitch enters the battlefield, each opponent loses 2 life and you gain life equal to the life lost this way.", tags = listOf(roleTag("lifegain_source"), roleTag("etb_payoff")))),
        entry(card(id = "elenda-dusk-rose", name = "Elenda, the Dusk Rose", typeLine = "Legendary Creature — Vampire Cleric", cmc = 5.0, colorIdentity = listOf("B"), power = "0", toughness = "0", oracleText = "Elenda, the Dusk Rose gets +1/+1 for each other creature that died this turn. When Elenda dies, create X 1/1 white Vampire creature tokens with lifelink, where X is Elenda's power.", tags = listOf(roleTag("death_payoff"), roleTag("token_generator")))),
        entry(card(id = "necropolis-regent", name = "Necropolis Regent", typeLine = "Creature — Dragon Vampire", cmc = 6.0, colorIdentity = listOf("R", "B"), power = "4", toughness = "4", oracleText = "Flying. Whenever one or more creatures you control deal combat damage to a player, put that many +1/+1 counters on Necropolis Regent and you gain that much life.", tags = listOf(roleTag("lifegain_source"), roleTag("counters_source")))),
        entry(card(id = "vampiric-rites", name = "Vampiric Rites", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("B"), oracleText = "Sacrifice a creature. If you do, draw a card and you gain 1 life.", tags = listOf(roleTag("sac_outlet"), CardTag.DRAW_ENGINE))),
        entry(card(id = "falkenrath-noble", name = "Falkenrath Noble", typeLine = "Creature — Vampire Shaman", cmc = 3.0, colorIdentity = listOf("B"), power = "2", toughness = "2", oracleText = "Flying. Whenever Falkenrath Noble or another creature dies, target player loses 1 life and you gain 1 life.", tags = listOf(roleTag("death_payoff"), roleTag("lifegain_source")))),
        entry(card(id = "vampire-nocturnus", name = "Vampire Nocturnus", typeLine = "Creature — Vampire", cmc = 4.0, colorIdentity = listOf("B"), power = "3", toughness = "3", oracleText = "During your turn, Vampire Nocturnus has flying and other Vampire creatures you control get +1/+1 and have flying.")),
        entry(card(id = "stromkirk-captain", name = "Stromkirk Captain", typeLine = "Creature — Vampire Pirate", cmc = 2.0, colorIdentity = listOf("R"), power = "2", toughness = "2", oracleText = "Other attacking Vampire creatures you control get +1/+1. Whenever a Vampire creature you control deals combat damage to a player, you may put a +1/+1 counter on that creature.")),
        entry(card(id = "bloodcrazed-paladin", name = "Bloodcrazed Paladin", typeLine = "Creature — Human Knight", cmc = 1.0, colorIdentity = listOf("R"), power = "2", toughness = "1", oracleText = "Whenever Bloodcrazed Paladin deals combat damage to a player, it gets +2/+0 until end of turn and you may have it fight target creature that player controls.")),
        entry(card(id = "hero-of-bladehold", name = "Hero of Bladehold", typeLine = "Creature — Human Soldier", cmc = 4.0, colorIdentity = listOf("W"), power = "3", toughness = "4", oracleText = "Battle cry. Whenever Hero of Bladehold attacks, create two 1/1 white Soldier creature tokens that are tapped and attacking.", tags = listOf(roleTag("token_generator")))),
        entry(card(id = "combat-celebrant", name = "Combat Celebrant", typeLine = "Creature — Human Warrior", cmc = 4.0, colorIdentity = listOf("R"), power = "3", toughness = "1")),
        entry(card(id = "aurelia-warleader", name = "Aurelia, the Warleader", typeLine = "Legendary Creature — Angel", cmc = 5.0, colorIdentity = listOf("R", "W"), power = "3", toughness = "4", oracleText = "Flying, haste. Whenever Aurelia, the Warleader attacks, untap all creatures you control that are attacking. After this combat phase, there is an additional combat phase.")),
        entry(card(id = "basilisk-collar", name = "Basilisk Collar", typeLine = "Artifact — Equipment", cmc = 2.0, colorIdentity = emptyList(), oracleText = "Equipped creature has deathtouch and lifelink. Equip {2}.")),
        entry(card(id = "shadowspear", name = "Shadowspear", typeLine = "Legendary Artifact — Equipment", cmc = 1.0, colorIdentity = emptyList())),
        entry(card(id = "anointed-procession", name = "Anointed Procession", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("W"), oracleText = "If an effect would create one or more tokens under your control, it creates twice that many of those tokens instead.", tags = listOf(roleTag("token_generator")))),
        entry(card(id = "skirsdag-high-priest", name = "Skirsdag High Priest", typeLine = "Creature — Human Cleric", cmc = 3.0, colorIdentity = listOf("R"), power = "2", toughness = "2", oracleText = "Whenever Skirsdag High Priest or another Cleric enters the battlefield under your control, create a 5/5 red Demon creature token. When you sacrifice a Cleric, target creature an opponent controls gets -2/-2 until end of turn.", tags = listOf(roleTag("token_generator"), roleTag("sac_outlet")))),
        entry(card(id = "bastion-of-remembrance", name = "Bastion of Remembrance", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("W", "B"), oracleText = "Whenever a creature you control dies, each opponent loses 1 life and you gain 1 life.", tags = listOf(roleTag("death_payoff")))),
        entry(card(id = "vindicate-em", name = "Vindicate", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("W", "B"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "utter-end-em", name = "Utter End", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("W", "B"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "chaos-warp-em", name = "Chaos Warp", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "blasphemous-act-em", name = "Blasphemous Act", typeLine = "Sorcery", cmc = 8.0, colorIdentity = listOf("R"), tags = listOf(CardTag.WRATH))),
        entry(card(id = "nights-whisper-em", name = "Night's Whisper", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "read-the-bones-em", name = "Read the Bones", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("B"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "chandra-torch-em", name = "Chandra, Torch of Defiance", typeLine = "Legendary Planeswalker — Chandra", cmc = 4.0, colorIdentity = listOf("R"))),
        entry(card(id = "mind-stone-em", name = "Mind Stone", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "fellwar-stone-em", name = "Fellwar Stone", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "talisman-indulgence-em", name = "Talisman of Indulgence", typeLine = "Artifact", cmc = 2.0, colorIdentity = listOf("B", "R"), tags = listOf(CardTag.RAMP))),
        entry(card(id = "coat-of-arms-em", name = "Coat of Arms", typeLine = "Legendary Artifact", cmc = 5.0, colorIdentity = emptyList(), oracleText = "Each creature gets +1/+1 for each other creature on the battlefield that shares at least one creature type with it.")),
        entry(card(id = "door-of-destinies-em", name = "Door of Destinies", typeLine = "Legendary Artifact", cmc = 3.0, colorIdentity = emptyList(), oracleText = "As Door of Destinies enters the battlefield, choose a creature type. Whenever you cast a spell of the chosen type, put a charge counter on Door of Destinies. Creatures you control of the chosen type get +1/+1 for each charge counter on Door of Destinies.")),
        entry(card(id = "metallic-mimic-em", name = "Metallic Mimic", typeLine = "Artifact Creature — Shapeshifter", cmc = 2.0, colorIdentity = emptyList(), power = "2", toughness = "2", oracleText = "As Metallic Mimic enters the battlefield, choose a creature type. Metallic Mimic is the chosen type in addition to its other types. Other creatures you control of the chosen type enter the battlefield with an additional +1/+1 counter on them.")),
        entry(card(id = "cordial-vampire", name = "Cordial Vampire", typeLine = "Creature — Vampire", cmc = 1.0, colorIdentity = listOf("B"), power = "1", toughness = "1", oracleText = "Whenever another creature you control dies, put a +1/+1 counter on Cordial Vampire. Sacrifice Cordial Vampire: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle. Activate only if a creature died this turn.", tags = listOf(roleTag("death_payoff"), roleTag("counters_source")))),
        entry(card(id = "bloodthirsty-aerialist", name = "Bloodthirsty Aerialist", typeLine = "Creature — Vampire Warrior", cmc = 3.0, colorIdentity = listOf("R"), power = "3", toughness = "2", oracleText = "Flying. Whenever Bloodthirsty Aerialist deals combat damage to a player, put a +1/+1 counter on it.", tags = listOf(roleTag("counters_source")))),
        entry(card(id = "kindred-dominance", name = "Kindred Dominance", typeLine = "Sorcery", cmc = 5.0, colorIdentity = listOf("W", "B"), oracleText = "Destroy all creatures except creatures of the creature type of your choice. Damage can't be prevented this turn.", tags = listOf(CardTag.WRATH))),
        entry(card(id = "legions-landing", name = "Legion's Landing", typeLine = "Legendary Enchantment", cmc = 1.0, colorIdentity = listOf("W"), oracleText = "When Legion's Landing enters the battlefield, create a 1/1 white Vampire creature token with lifelink. Then if you control three or more creatures, transform Legion's Landing.", tags = listOf(roleTag("token_generator")))),
        entry(card(id = "bloodhall-priest", name = "Bloodhall Priest", typeLine = "Creature — Vampire Shaman", cmc = 5.0, colorIdentity = listOf("R", "B"), power = "3", toughness = "3", oracleText = "Lifelink. Whenever Bloodhall Priest deals damage, each opponent loses that much life.", tags = listOf(roleTag("lifegain_source")))),
    )
    val mainboard = withBasicsCommander(nonland, "Mountain", "R")
    return AnalysisV3Fixture(
        id = 1, name = "Edgar Markov (Vampire Tribal Aggro)", anchor = "Edgar Markov",
        format = DeckFormat.COMMANDER, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.R, ManaColor.W, ManaColor.B),
        // Commander prior workstream (2026-08-26): Edgar Markov (Eminence go-wide vampire lord,
        // "Other Vampires you control get +1/+1") is an unambiguous, well-established real-world
        // AGGRO + vampire-tribal commander -- honest, independent of this fixture's own decklist.
        commanderTags = listOf(CardTag.AGGRO, CardTag.TRIBAL),
        expectedMacro = "AGGRO", expectedPosture = NO_POSTURE, expectedThemes = "TRIBAL:vampire",
    )
}
