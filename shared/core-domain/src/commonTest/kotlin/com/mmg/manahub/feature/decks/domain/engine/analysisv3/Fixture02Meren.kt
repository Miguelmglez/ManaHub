package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #2 (spec §9) — Meren of Clan Nel Toth, Aristocrats. Commander, Golgari (BG).
 * Expected: macro MIDRANGE, posture ATTRITION, theme ARISTOCRATS.
 */
fun fixture02Meren(): AnalysisV3Fixture {
    val commander = card(
        id = "cmd-meren", name = "Meren of Clan Nel Toth", typeLine = "Legendary Creature — Human Shaman",
        cmc = 4.0, colorIdentity = listOf("B", "G"), power = "2", toughness = "2",
        oracleText = "Whenever another creature you control dies, you get an experience counter. At the beginning of your end step, choose target creature card in your graveyard with mana value less than or equal to the number of experience counters you have. Return that card to the battlefield.",
    )
    val nonland = listOf(
        entry(commander),
        entry(card(id = "viscera-seer", name = "Viscera Seer", typeLine = "Creature — Vampire Wizard", cmc = 1.0, colorIdentity = listOf("B"), power = "1", toughness = "1", tags = listOf(roleTag("sac_outlet")))),
        entry(card(id = "carrion-feeder", name = "Carrion Feeder", typeLine = "Creature — Zombie Warrior", cmc = 1.0, colorIdentity = listOf("B"), power = "1", toughness = "1", tags = listOf(roleTag("sac_outlet")))),
        entry(card(id = "nantuko-husk", name = "Nantuko Husk", typeLine = "Creature — Zombie", cmc = 3.0, colorIdentity = listOf("B"), power = "2", toughness = "2", tags = listOf(roleTag("sac_outlet")))),
        entry(card(id = "ashnods-altar", name = "Ashnod's Altar", typeLine = "Artifact", cmc = 3.0, colorIdentity = emptyList(), tags = listOf(roleTag("sac_outlet")))),
        entry(card(id = "woe-strider", name = "Woe Strider", typeLine = "Creature — God", cmc = 3.0, colorIdentity = listOf("B"), power = "3", toughness = "3", tags = listOf(roleTag("sac_outlet")))),
        entry(card(id = "blood-artist", name = "Blood Artist", typeLine = "Creature — Vampire", cmc = 2.0, colorIdentity = listOf("B"), power = "0", toughness = "1", tags = listOf(roleTag("death_payoff")))),
        entry(card(id = "zulaport-cutthroat", name = "Zulaport Cutthroat", typeLine = "Creature — Human Cleric", cmc = 2.0, colorIdentity = listOf("B"), power = "1", toughness = "2", tags = listOf(roleTag("death_payoff")))),
        entry(card(id = "midnight-reaper", name = "Midnight Reaper", typeLine = "Creature — Zombie Knight", cmc = 3.0, colorIdentity = listOf("B"), power = "2", toughness = "3", tags = listOf(roleTag("death_payoff")))),
        entry(card(id = "grave-pact", name = "Grave Pact", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("B"), tags = listOf(roleTag("death_payoff")))),
        entry(card(id = "dictate-of-erebos", name = "Dictate of Erebos", typeLine = "Enchantment", cmc = 4.0, colorIdentity = listOf("B"), tags = listOf(roleTag("death_payoff")))),
        entry(card(id = "bitterblossom", name = "Bitterblossom", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(roleTag("token_generator")))),
        entry(card(id = "reassembling-skeleton", name = "Reassembling Skeleton", typeLine = "Creature — Skeleton", cmc = 1.0, colorIdentity = listOf("B"), power = "1", toughness = "1", tags = listOf(roleTag("recursion")))),
        entry(card(id = "bloodghast", name = "Bloodghast", typeLine = "Creature — Vampire", cmc = 2.0, colorIdentity = listOf("B"), power = "2", toughness = "1", tags = listOf(roleTag("recursion")))),
        entry(card(id = "eternal-witness-mr", name = "Eternal Witness", typeLine = "Creature — Elf Shaman", cmc = 3.0, colorIdentity = listOf("G"), power = "2", toughness = "1", tags = listOf(roleTag("recursion")))),
        entry(card(id = "phyrexian-arena-mr", name = "Phyrexian Arena", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "toxic-deluge-mr", name = "Toxic Deluge", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("B"), tags = listOf(CardTag.WRATH))),
        entry(card(id = "beast-within-mr", name = "Beast Within", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("G"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "putrefy-mr", name = "Putrefy", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("B", "G"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "sol-ring-mr", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "farseek-mr", name = "Farseek", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), tags = listOf(CardTag.RAMP))),
        entry(card(id = "natures-lore", name = "Nature's Lore", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), tags = listOf(CardTag.RAMP))),
        entry(card(id = "demonic-tutor-mr", name = "Demonic Tutor", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(CardTag.TUTOR))),
        entry(card(id = "world-shaper", name = "World Shaper", typeLine = "Creature — Human Shaman", cmc = 4.0, colorIdentity = listOf("G"), power = "2", toughness = "5")),
        entry(card(id = "yavimaya-elder", name = "Yavimaya Elder", typeLine = "Creature — Elf Druid", cmc = 3.0, colorIdentity = listOf("G"), power = "1", toughness = "1")),
        entry(card(id = "acidic-slime", name = "Acidic Slime", typeLine = "Creature — Ooze", cmc = 5.0, colorIdentity = listOf("G"), power = "2", toughness = "2", tags = listOf(CardTag.REMOVAL))),
        // Phase 3a-FIX batch A: density expansion (25 -> 63 non-land, spec §9 realistic-deck fix).
        // Real Golgari Aristocrats staples -- strengthens DEATH (sac_outlet/death_payoff) plus the
        // recursion/removal_spot/card_draw density the ATTRITION posture gate reads (spec §3:
        // recursion >= 4 raw copies AND (card_draw + removal_spot) above MIDRANGE's own band sum).
        entry(card(id = "yahenni", name = "Yahenni, Undying Partisan", typeLine = "Legendary Creature — Human Shaman", cmc = 2.0, colorIdentity = listOf("B"), power = "2", toughness = "1", oracleText = "Indestructible as long as devotion to black is less than four. {B}: Sacrifice another creature. When you do, target opponent loses 1 life.", tags = listOf(roleTag("sac_outlet")))),
        entry(card(id = "poison-tip-archer", name = "Poison-Tip Archer", typeLine = "Creature — Elf Archer", cmc = 4.0, colorIdentity = listOf("B", "G"), power = "2", toughness = "2", oracleText = "Deathtouch. Whenever another creature you control dies, target opponent loses 1 life and you gain 1 life.", tags = listOf(roleTag("death_payoff")))),
        entry(card(id = "pitiless-plunderer", name = "Pitiless Plunderer", typeLine = "Creature — Orc Warlock", cmc = 2.0, colorIdentity = listOf("B"), power = "3", toughness = "2", oracleText = "Whenever another creature you control dies, you may create a Treasure token.", tags = listOf(roleTag("death_payoff")))),
        entry(card(id = "perilous-forays", name = "Perilous Forays", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("G"), oracleText = "Sacrifice a creature: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.", tags = listOf(roleTag("sac_outlet")))),
        entry(card(id = "grim-haruspex", name = "Grim Haruspex", typeLine = "Creature — Zombie Shaman", cmc = 3.0, colorIdentity = listOf("B"), power = "2", toughness = "2", oracleText = "Whenever a nontoken creature you control dies, draw a card.", tags = listOf(roleTag("death_payoff"), CardTag.DRAW_ENGINE))),
        entry(card(id = "sign-in-blood-mr", name = "Sign in Blood", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "read-the-bones-mr2", name = "Read the Bones", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("B"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "maelstrom-pulse", name = "Maelstrom Pulse", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("B", "G"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "feed-the-swarm", name = "Feed the Swarm", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "vraskas-contempt", name = "Vraska's Contempt", typeLine = "Instant", cmc = 4.0, colorIdentity = listOf("B"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "ravenous-chupacabra", name = "Ravenous Chupacabra", typeLine = "Creature — Zombie", cmc = 4.0, colorIdentity = listOf("B"), power = "2", toughness = "2", oracleText = "When Ravenous Chupacabra enters the battlefield, destroy target creature an opponent controls.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "regrowth-mr", name = "Regrowth", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), oracleText = "Return target card from your graveyard to your hand.", tags = listOf(roleTag("recursion")))),
        entry(card(id = "genesis-mr", name = "Genesis", typeLine = "Creature — Avatar", cmc = 4.0, colorIdentity = listOf("G"), power = "3", toughness = "3", oracleText = "At the beginning of your upkeep, you may return target creature card from your graveyard to your hand.", tags = listOf(roleTag("recursion")))),
        entry(card(id = "raise-dead-mr", name = "Raise Dead", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("B"), oracleText = "Return target creature card from your graveyard to your hand.", tags = listOf(roleTag("recursion")))),
        entry(card(id = "skullclamp-mr", name = "Skullclamp", typeLine = "Artifact — Equipment", cmc = 1.0, colorIdentity = emptyList())),
        entry(card(id = "grave-titan-mr", name = "Grave Titan", typeLine = "Creature — Zombie Giant", cmc = 6.0, colorIdentity = listOf("B"), power = "6", toughness = "6", oracleText = "When Grave Titan enters the battlefield, create two 2/2 black Zombie creature tokens. Whenever Grave Titan attacks, create two 2/2 black Zombie creature tokens tapped and attacking.", tags = listOf(roleTag("token_generator")))),
        entry(card(id = "massacre-wurm-mr", name = "Massacre Wurm", typeLine = "Creature — Wurm", cmc = 5.0, colorIdentity = listOf("B"), power = "6", toughness = "5", oracleText = "When Massacre Wurm enters the battlefield, creatures your opponents control get -2/-2 until end of turn. Whenever a creature an opponent controls dies, that player loses 2 life.", tags = listOf(CardTag.WRATH))),
        entry(card(id = "village-rites-mr", name = "Village Rites", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("B"), oracleText = "As an additional cost to cast this spell, sacrifice a creature. Draw two cards.", tags = listOf(roleTag("sac_outlet"), CardTag.DRAW_ENGINE))),
        entry(card(id = "diregraf-ghoul-mr", name = "Diregraf Ghoul", typeLine = "Creature — Zombie", cmc = 1.0, colorIdentity = listOf("B"), power = "2", toughness = "1")),
        entry(card(id = "deathgreeter-mr", name = "Deathgreeter", typeLine = "Creature — Human Shaman", cmc = 2.0, colorIdentity = listOf("B"), power = "1", toughness = "1", oracleText = "Whenever a creature dies, if it's the first creature that died this turn, you gain 1 life and draw a card.", tags = listOf(roleTag("death_payoff"), CardTag.DRAW_ENGINE))),
        entry(card(id = "golgari-signet-mr", name = "Golgari Signet", typeLine = "Artifact", cmc = 2.0, colorIdentity = listOf("B", "G"), tags = listOf(CardTag.RAMP))),
        entry(card(id = "wood-elves-mr", name = "Wood Elves", typeLine = "Creature — Elf Scout", cmc = 3.0, colorIdentity = listOf("G"), power = "1", toughness = "1", oracleText = "When Wood Elves enters the battlefield, search your library for a Forest card, put it onto the battlefield, then shuffle.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "vraska-golgari-queen", name = "Vraska, Golgari Queen", typeLine = "Legendary Planeswalker — Vraska", cmc = 3.0, colorIdentity = listOf("B", "G"))),
        entry(card(id = "nighthowler-mr", name = "Nighthowler", typeLine = "Enchantment Creature — Horror", cmc = 4.0, colorIdentity = listOf("B", "G"), power = "0", toughness = "0", oracleText = "Bestow. Nighthowler gets +1/+1 for each creature card in all graveyards.")),
        entry(card(id = "ophiomancer-mr", name = "Ophiomancer", typeLine = "Creature — Human Shaman", cmc = 3.0, colorIdentity = listOf("B"), power = "1", toughness = "3", oracleText = "At the beginning of each upkeep, if you control no Snakes, create a 1/1 black Snake creature token with deathtouch.", tags = listOf(roleTag("token_generator")))),
        entry(card(id = "gray-merchant-mr", name = "Gray Merchant of Asphodel", typeLine = "Creature — Zombie Cleric", cmc = 5.0, colorIdentity = listOf("B"), power = "4", toughness = "5", oracleText = "When Gray Merchant of Asphodel enters the battlefield, each opponent loses X life and you gain X life, where X is your devotion to black.", tags = listOf(roleTag("lifegain_source")))),
        entry(card(id = "attrition-mr", name = "Attrition", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("B", "G"), oracleText = "{B}, Sacrifice a creature: Destroy target creature.", tags = listOf(roleTag("sac_outlet")))),
        entry(card(id = "deadly-rollick", name = "Deadly Rollick", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("B"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "bone-splinters-mr", name = "Bone Splinters", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("B"), oracleText = "As an additional cost to cast this spell, sacrifice a creature. Destroy target creature.", tags = listOf(roleTag("sac_outlet"), CardTag.REMOVAL))),
        entry(card(id = "costly-plunder-mr", name = "Costly Plunder", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("B"), oracleText = "As an additional cost to cast this spell, sacrifice a creature or artifact. Draw two cards.", tags = listOf(roleTag("sac_outlet"), CardTag.DRAW_ENGINE))),
        entry(card(id = "golgari-charm-mr", name = "Golgari Charm", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("B", "G"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "putrid-leech-mr", name = "Putrid Leech", typeLine = "Creature — Human Warrior", cmc = 2.0, colorIdentity = listOf("B", "G"), power = "2", toughness = "2")),
        entry(card(id = "grismold-mr", name = "Grismold, the Dreadsower", typeLine = "Legendary Creature — Fungus Beast", cmc = 6.0, colorIdentity = listOf("B", "G"), power = "8", toughness = "8", oracleText = "Whenever another creature you control dies, create a 1/1 green Saproling creature token.", tags = listOf(roleTag("death_payoff"), roleTag("token_generator")))),
        entry(card(id = "splendid-reclamation-mr", name = "Splendid Reclamation", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("G"), oracleText = "Return each land card from your graveyard to the battlefield.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "bloodspore-thrinax-mr", name = "Bloodspore Thrinax", typeLine = "Creature — Hydra", cmc = 4.0, colorIdentity = listOf("B", "G"), power = "3", toughness = "3", oracleText = "When Bloodspore Thrinax dies, create a number of 1/1 green Saproling creature tokens equal to its power.", tags = listOf(roleTag("token_generator")))),
        entry(card(id = "nether-traitor-mr", name = "Nether Traitor", typeLine = "Creature — Zombie", cmc = 2.0, colorIdentity = listOf("B"), power = "2", toughness = "2", oracleText = "Haste. You may cast Nether Traitor from your graveyard if a black creature died this turn.", tags = listOf(roleTag("recursion")))),
        entry(card(id = "twilights-call-mr", name = "Twilight's Call", typeLine = "Sorcery", cmc = 5.0, colorIdentity = listOf("B"), oracleText = "Each player returns each creature card from their graveyard to the battlefield.", tags = listOf(roleTag("recursion")))),
        entry(card(id = "erebos-mr", name = "Erebos, God of the Dead", typeLine = "Legendary Enchantment Creature — God", cmc = 3.0, colorIdentity = listOf("B"), power = "5", toughness = "6", oracleText = "Indestructible. {1}{B}, Pay 2 life: Draw a card.", tags = listOf(CardTag.DRAW_ENGINE))),
    )
    val mainboard = withBasicsCommander(nonland, "Swamp", "B")
    return AnalysisV3Fixture(
        id = 2, name = "Meren of Clan Nel Toth (Aristocrats)", anchor = "Meren of Clan Nel Toth",
        format = DeckFormat.COMMANDER, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.B, ManaColor.G),
        // Commander prior workstream (2026-08-26): Meren is THE definitive recursive-value
        // aristocrats commander (its own text is a death-payoff -- "whenever another creature you
        // control dies, get an experience counter") -- MIDRANGE + ARISTOCRATS is an honest,
        // independently-established real-world read, not fitted to this fixture's own expectation.
        commanderTags = listOf(CardTag.MIDRANGE, CardTag.SACRIFICE),
        expectedMacro = "MIDRANGE", expectedPosture = "ATTRITION", expectedThemes = "ARISTOCRATS",
    )
}
