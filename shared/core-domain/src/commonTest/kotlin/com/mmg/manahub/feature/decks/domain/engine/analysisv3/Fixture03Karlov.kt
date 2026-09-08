package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #3 (spec §9) — Karlov of the Ghost Council, Lifegain. Commander, Orzhov (WB).
 * Expected: macro MIDRANGE, no posture, theme LIFEGAIN.
 */
fun fixture03Karlov(): AnalysisV3Fixture {
    val commander = card(
        id = "cmd-karlov", name = "Karlov of the Ghost Council", typeLine = "Legendary Creature — Vampire Spirit",
        cmc = 3.0, colorIdentity = listOf("W", "B"), power = "2", toughness = "2",
        oracleText = "First strike, lifelink. {1}{W/B}: Karlov of the Ghost Council gets +1/+1 until end of turn.",
    )
    val nonland = listOf(
        entry(commander),
        // Phase 2b (deck-analysis-engine-v3-spec.md §5.2 LIFE axis fix): these three are real
        // lifegain SOURCES (their oracle text triggers on ETB and grants life -- they do not care
        // about life already gained), not payoffs -- the Phase 0 corpus mistagged them as
        // `lifegain_payoff` before the axis vocabulary existed. Retagged to `lifegain_source` with
        // real oracle text so LIFE's producer half is no longer structurally empty.
        entry(card(id = "soul-warden", name = "Soul Warden", typeLine = "Creature — Human Cleric", cmc = 1.0, colorIdentity = listOf("W"), power = "1", toughness = "1", oracleText = "Whenever another creature enters the battlefield under your control, you gain 1 life.", tags = listOf(roleTag("lifegain_source")))),
        entry(card(id = "souls-attendant", name = "Soul's Attendant", typeLine = "Creature — Cleric", cmc = 1.0, colorIdentity = listOf("W"), power = "1", toughness = "1", oracleText = "Whenever another creature enters the battlefield under your control, you gain 1 life.", tags = listOf(roleTag("lifegain_source")))),
        entry(card(id = "suture-priest", name = "Suture Priest", typeLine = "Creature — Human Cleric", cmc = 2.0, colorIdentity = listOf("W"), power = "1", toughness = "2", oracleText = "Whenever a nontoken creature enters the battlefield, its controller gains 1 life. Whenever a token enters the battlefield, its controller loses 1 life.", tags = listOf(roleTag("lifegain_source")))),
        entry(card(id = "vito-thorn", name = "Vito, Thorn of the Dusk Rose", typeLine = "Legendary Creature — Vampire Knight", cmc = 2.0, colorIdentity = listOf("W", "B"), power = "1", toughness = "2", tags = listOf(roleTag("lifegain_payoff")))),
        entry(card(id = "sanguine-bond-kv", name = "Sanguine Bond", typeLine = "Enchantment", cmc = 4.0, colorIdentity = listOf("B"), tags = listOf(roleTag("lifegain_payoff")))),
        entry(card(id = "aetherflux-reservoir", name = "Aetherflux Reservoir", typeLine = "Artifact", cmc = 4.0, colorIdentity = emptyList(), tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "well-of-lost-dreams", name = "Well of Lost Dreams", typeLine = "Artifact", cmc = 3.0, colorIdentity = emptyList(), tags = listOf(roleTag("lifegain_payoff")))),
        // Phase 2b: real lifelink creature already present but untagged -- the LIFE axis's third
        // producer (lifelink alone clears `lifegain_source`'s dedicated rule).
        entry(card(id = "rhox-faithmender", name = "Rhox Faithmender", typeLine = "Creature — Rhino Cleric", cmc = 4.0, colorIdentity = listOf("W"), power = "2", toughness = "4", oracleText = "Lifelink. If you would gain life, you gain twice that much life instead.", tags = listOf(roleTag("lifegain_source")))),
        entry(card(id = "heliods-intervention", name = "Heliod's Intervention", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "trailblazers-boots", name = "Trailblazer's Boots", typeLine = "Artifact — Equipment", cmc = 3.0, colorIdentity = emptyList())),
        entry(card(id = "swords-kv", name = "Swords to Plowshares", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "path-kv", name = "Path to Exile", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "vindicate-kv", name = "Vindicate", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("W", "B"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "wrath-kv", name = "Wrath of God", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("W"), tags = listOf(CardTag.WRATH))),
        entry(card(id = "toxic-deluge-kv", name = "Toxic Deluge", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("B"), tags = listOf(CardTag.WRATH))),
        entry(card(id = "demonic-tutor-kv", name = "Demonic Tutor", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(CardTag.TUTOR))),
        entry(card(id = "sol-ring-kv", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "arcane-signet-kv", name = "Arcane Signet", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList())),
        entry(card(id = "phyrexian-arena-kv", name = "Phyrexian Arena", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "authority-of-consuls", name = "Authority of the Consuls", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("W"), tags = listOf(roleTag("lifegain_payoff")))),
        entry(card(id = "exquisite-blood", name = "Exquisite Blood", typeLine = "Enchantment", cmc = 4.0, colorIdentity = listOf("B"), tags = listOf(roleTag("lifegain_payoff")))),
        entry(card(id = "revenge-of-ravens", name = "Revenge of Ravens", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("W"))),
        entry(card(id = "batterskull-kv", name = "Batterskull", typeLine = "Legendary Artifact — Equipment", cmc = 5.0, colorIdentity = emptyList())),
        // Phase 3a-FIX batch A: density expansion (25 -> 63 non-land, spec §9 realistic-deck fix).
        // Real Orzhov Lifegain staples -- strengthens the LIFE axis's producer half (more
        // lifegain_source, honest oracle text) and payoff half (more lifegain_payoff) well beyond
        // the Phase 2b fix's initial 4/5 split.
        entry(card(id = "serra-ascendant", name = "Serra Ascendant", typeLine = "Creature — Human Monk", cmc = 2.0, colorIdentity = listOf("W"), power = "1", toughness = "1", oracleText = "Lifelink. As long as you have 30 or more life, Serra Ascendant gets +5/+5 and has flying.", tags = listOf(roleTag("lifegain_source")))),
        entry(card(id = "angel-of-vitality", name = "Angel of Vitality", typeLine = "Creature — Angel", cmc = 3.0, colorIdentity = listOf("W"), power = "2", toughness = "3", oracleText = "Flying, lifelink. If you would gain life, you gain twice that much life instead.", tags = listOf(roleTag("lifegain_source")))),
        entry(card(id = "heliod-sun-crowned", name = "Heliod, Sun-Crowned", typeLine = "Legendary Enchantment Creature — God", cmc = 3.0, colorIdentity = listOf("W"), power = "5", toughness = "5", oracleText = "Indestructible. Whenever you gain life, put a +1/+1 counter on target creature or enchantment you control.", tags = listOf(roleTag("lifegain_payoff")))),
        entry(card(id = "cliffhaven-vampire", name = "Cliffhaven Vampire", typeLine = "Creature — Vampire Cleric", cmc = 3.0, colorIdentity = listOf("W"), power = "2", toughness = "3", oracleText = "Whenever you gain life, exile the top card of your library. You may play that card this turn.", tags = listOf(roleTag("lifegain_payoff")))),
        entry(card(id = "debt-to-the-deathless", name = "Debt to the Deathless", typeLine = "Sorcery", cmc = 6.0, colorIdentity = listOf("W", "B"), oracleText = "Each opponent loses X life and you gain life equal to the life lost this way, where X is 2 plus the number of creature cards in your graveyard.", tags = listOf(roleTag("lifegain_source"), CardTag.WIN_CON))),
        entry(card(id = "bishop-of-wings", name = "Bishop of Wings", typeLine = "Creature — Human Cleric", cmc = 4.0, colorIdentity = listOf("W"), power = "2", toughness = "4", oracleText = "Whenever an Angel enters the battlefield under your control, you gain 4 life. Whenever you gain life for the first time in a turn, draw a card.", tags = listOf(roleTag("lifegain_source"), roleTag("lifegain_payoff")))),
        entry(card(id = "archangel-of-thune", name = "Archangel of Thune", typeLine = "Creature — Angel", cmc = 5.0, colorIdentity = listOf("W"), power = "4", toughness = "4", oracleText = "Flying, lifelink. At the beginning of your upkeep, if you have more life than your starting life total, put a +1/+1 counter on each creature you control.", tags = listOf(roleTag("lifegain_payoff")))),
        entry(card(id = "fumigate-kv", name = "Fumigate", typeLine = "Sorcery", cmc = 5.0, colorIdentity = listOf("W"), oracleText = "Destroy all creatures. You gain 1 life for each creature destroyed this way.", tags = listOf(CardTag.WRATH, roleTag("lifegain_source")))),
        entry(card(id = "sun-titan-kv", name = "Sun Titan", typeLine = "Creature — Giant", cmc = 6.0, colorIdentity = listOf("W"), power = "6", toughness = "6", oracleText = "Vigilance. Whenever Sun Titan enters the battlefield or attacks, you may return target permanent card with mana value 3 or less from your graveyard to the battlefield.", tags = listOf(roleTag("recursion")))),
        entry(card(id = "righteous-valkyrie", name = "Righteous Valkyrie", typeLine = "Creature — Angel", cmc = 4.0, colorIdentity = listOf("W"), power = "3", toughness = "4", oracleText = "Flying. Whenever another creature you control with power 4 or greater enters the battlefield, you gain 4 life. As long as you have more life than your starting life total, creatures you control get +1/+1 and have lifelink.", tags = listOf(roleTag("lifegain_source")))),
        entry(card(id = "mortify-kv", name = "Mortify", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("W", "B"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "bloodchiefs-thirst", name = "Bloodchief's Thirst", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("B"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "councils-judgment", name = "Council's Judgment", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "anguished-unmaking-kv", name = "Anguished Unmaking", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("W", "B"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "utter-end-kv", name = "Utter End", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("W", "B"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "sign-in-blood-kv", name = "Sign in Blood", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "read-the-bones-kv", name = "Read the Bones", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("B"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "bygone-bishop", name = "Bygone Bishop", typeLine = "Creature — Human Cleric", cmc = 3.0, colorIdentity = listOf("W", "B"), power = "2", toughness = "3", oracleText = "Whenever Bygone Bishop or another Cleric or Spirit you control deals combat damage to a player, draw a card, then discard a card.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "orzhov-signet", name = "Orzhov Signet", typeLine = "Artifact", cmc = 2.0, colorIdentity = listOf("W", "B"), tags = listOf(CardTag.RAMP))),
        entry(card(id = "thought-vessel", name = "Thought Vessel", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "solemn-simulacrum-kv", name = "Solemn Simulacrum", typeLine = "Artifact Creature — Golem", cmc = 4.0, colorIdentity = emptyList(), power = "2", toughness = "2", tags = listOf(CardTag.RAMP))),
        entry(card(id = "karmic-guide", name = "Karmic Guide", typeLine = "Creature — Cleric", cmc = 5.0, colorIdentity = listOf("W"), power = "2", toughness = "2", oracleText = "Flying. When Karmic Guide enters the battlefield, return target creature card from your graveyard to the battlefield.", tags = listOf(roleTag("recursion")))),
        entry(card(id = "lyra-dawnbringer", name = "Lyra Dawnbringer", typeLine = "Creature — Angel", cmc = 5.0, colorIdentity = listOf("W"), power = "5", toughness = "5", oracleText = "Flying, first strike, lifelink. Other Angels you control get +1/+1 and have lifelink.", tags = listOf(roleTag("lifegain_source")))),
        entry(card(id = "angelic-chorus", name = "Angelic Chorus", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("W"), oracleText = "Whenever a creature enters the battlefield under your control, you gain life equal to its toughness.", tags = listOf(roleTag("lifegain_source")))),
        entry(card(id = "palace-jailer", name = "Palace Jailer", typeLine = "Creature — Human Soldier", cmc = 3.0, colorIdentity = listOf("W"), power = "2", toughness = "3", oracleText = "When Palace Jailer enters the battlefield, exile target creature an opponent controls until you cease to be the monarch.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "skyclave-apparition", name = "Skyclave Apparition", typeLine = "Creature — Spirit", cmc = 2.0, colorIdentity = listOf("W"), power = "3", toughness = "2", oracleText = "Flying. When Skyclave Apparition enters the battlefield, exile up to one target nonland permanent an opponent controls with mana value 4 or less.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "kambal-consul", name = "Kambal, Consul of Allocation", typeLine = "Legendary Creature — Human Advisor", cmc = 3.0, colorIdentity = listOf("W", "B"), power = "2", toughness = "3", oracleText = "Whenever an opponent casts a spell that targets Kambal or a permanent you control, you gain 2 life. Whenever a permanent an opponent controls is dealt damage, you gain 1 life.", tags = listOf(roleTag("lifegain_source")))),
        entry(card(id = "lunarch-veteran", name = "Lunarch Veteran", typeLine = "Creature — Human Cleric", cmc = 1.0, colorIdentity = listOf("W"), power = "1", toughness = "1", oracleText = "When Lunarch Veteran enters the battlefield, you gain 1 life for each creature you control.", tags = listOf(roleTag("lifegain_source")))),
        entry(card(id = "griffin-aerie", name = "Griffin Aerie", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("W"), oracleText = "{1}{W}: You gain 1 life. Activate only if you control a creature with flying. At the beginning of your upkeep, if you gained life this turn, create a 2/2 white Griffin creature token with flying.", tags = listOf(roleTag("lifegain_source"), roleTag("lifegain_payoff"), roleTag("token_generator")))),
        entry(card(id = "marauding-blight-priest", name = "Marauding Blight-Priest", typeLine = "Creature — Human Cleric", cmc = 3.0, colorIdentity = listOf("B"), power = "2", toughness = "2", oracleText = "Whenever another creature you control dies, you gain 1 life. If you gained life this turn, put a +1/+1 counter on Marauding Blight-Priest.", tags = listOf(roleTag("lifegain_source")))),
        entry(card(id = "blind-obedience", name = "Blind Obedience", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("W"), oracleText = "Extort. Whenever you pay the extort cost, you gain 1 life and each opponent loses 1 life.", tags = listOf(roleTag("lifegain_source")))),
        entry(card(id = "whip-of-erebos", name = "Whip of Erebos", typeLine = "Legendary Enchantment", cmc = 3.0, colorIdentity = listOf("B"), oracleText = "Creatures you control have lifelink. {2}{B}{B}: Return target creature card from your graveyard to the battlefield. It gains haste.", tags = listOf(roleTag("lifegain_source")))),
        entry(card(id = "elspeth-sc-kv", name = "Elspeth, Sun's Champion", typeLine = "Legendary Planeswalker — Elspeth", cmc = 6.0, colorIdentity = listOf("W"), tags = listOf(roleTag("token_generator")))),
        entry(card(id = "dawn-of-hope", name = "Dawn of Hope", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("W"), oracleText = "Whenever you gain life, you may pay 1 life. If you do, create a 1/1 white Spirit creature token with flying and draw a card.", tags = listOf(roleTag("lifegain_payoff")))),
        entry(card(id = "swiftfoot-boots", name = "Swiftfoot Boots", typeLine = "Artifact — Equipment", cmc = 2.0, colorIdentity = emptyList())),
        entry(card(id = "lightning-greaves", name = "Lightning Greaves", typeLine = "Artifact — Equipment", cmc = 2.0, colorIdentity = emptyList())),
        entry(card(id = "basilisk-collar-kv", name = "Basilisk Collar", typeLine = "Artifact — Equipment", cmc = 2.0, colorIdentity = emptyList(), oracleText = "Equipped creature has deathtouch and lifelink. Equip {2}.", tags = listOf(roleTag("lifegain_source")))),
        entry(card(id = "angel-of-sanctions", name = "Angel of Sanctions", typeLine = "Creature — Angel", cmc = 5.0, colorIdentity = listOf("W"), power = "3", toughness = "5", oracleText = "Flying. When Angel of Sanctions enters the battlefield, exile target nonland permanent an opponent controls.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "speaker-of-the-heavens", name = "Speaker of the Heavens", typeLine = "Creature — Human Cleric", cmc = 3.0, colorIdentity = listOf("W"), power = "2", toughness = "2", oracleText = "Whenever you gain life, create a 1/1 white Cleric creature token.", tags = listOf(roleTag("lifegain_payoff"), roleTag("token_generator")))),
        entry(card(id = "congregate-kv", name = "Congregate", typeLine = "Instant", cmc = 4.0, colorIdentity = listOf("W"), oracleText = "You gain life equal to the number of creatures you control times 2.", tags = listOf(roleTag("lifegain_source")))),
    )
    val mainboard = withBasicsCommander(nonland, "Plains", "W")
    return AnalysisV3Fixture(
        id = 3, name = "Karlov of the Ghost Council (Lifegain)", anchor = "Karlov of the Ghost Council",
        format = DeckFormat.COMMANDER, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.W, ManaColor.B),
        // Commander prior workstream: Karlov's own ability (first strike/lifelink + a lifelink-
        // fueled pump) is the well-established WB lifegain-matters commander -- honest, independent
        // real-world read.
        commanderTags = listOf(CardTag.MIDRANGE, CardTag.LIFEGAIN),
        expectedMacro = "MIDRANGE", expectedPosture = NO_POSTURE, expectedThemes = "LIFEGAIN",
    )
}
