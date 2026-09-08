package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #4 (spec §9) — Omnath, Locus of Rage, Landfall. Commander, Temur (URG).
 * Expected: macro MIDRANGE, posture RAMP, theme LANDFALL.
 */
fun fixture04Omnath(): AnalysisV3Fixture {
    val commander = card(
        id = "cmd-omnath", name = "Omnath, Locus of Rage", typeLine = "Legendary Creature — Elemental",
        cmc = 5.0, colorIdentity = listOf("R", "G", "U"), power = "6", toughness = "6",
        oracleText = "Whenever a land enters the battlefield under your control, create a 5/5 red Elemental creature token. Whenever a land you control is put into a graveyard, Omnath, Locus of Rage deals 3 damage to any target.",
    )
    val nonland = listOf(
        entry(commander),
        entry(card(id = "avenger-of-zendikar-om", name = "Avenger of Zendikar", typeLine = "Creature — Plant", cmc = 6.0, colorIdentity = listOf("G"), power = "5", toughness = "5", tags = listOf(roleTag("landfall_payoff")))),
        entry(card(id = "scute-swarm-om", name = "Scute Swarm", typeLine = "Creature — Insect", cmc = 3.0, colorIdentity = listOf("G"), power = "1", toughness = "1", tags = listOf(roleTag("landfall_payoff")))),
        entry(card(id = "tireless-tracker-om", name = "Tireless Tracker", typeLine = "Creature — Human Scout", cmc = 3.0, colorIdentity = listOf("G"), power = "3", toughness = "2", tags = listOf(roleTag("landfall_payoff")))),
        entry(card(id = "rampaging-baloths-om", name = "Rampaging Baloths", typeLine = "Creature — Beast", cmc = 5.0, colorIdentity = listOf("G"), power = "6", toughness = "6", tags = listOf(roleTag("landfall_payoff")))),
        entry(card(id = "lotus-cobra", name = "Lotus Cobra", typeLine = "Creature — Snake", cmc = 2.0, colorIdentity = listOf("G"), power = "1", toughness = "1", tags = listOf(roleTag("landfall_payoff")))),
        // Phase 2b (deck-analysis-engine-v3-spec.md §5.2 LANDFALL fix): SynergyGraph.isLandBasedRamp
        // is text-pattern-based BY DESIGN (the only way to distinguish a land-fetching Cultivate
        // from a mana-rock Sol Ring, both plain CardTag.RAMP) -- these were authored with
        // `oracleText = null`, so the detector had nothing to read. Real oracle text restores the
        // signal; Sol Ring below stays untouched (a mana rock correctly has no land-search text and
        // must stay out of LANDFALL).
        entry(card(id = "cultivate-om", name = "Cultivate", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("G"), oracleText = "Search your library for up to two basic land cards, reveal those cards, put one onto the battlefield tapped and the other into your hand, then shuffle.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "kodamas-reach-om", name = "Kodama's Reach", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("G"), oracleText = "Search your library for up to two basic land cards, reveal those cards, and put one onto the battlefield tapped. Put the other land card into your hand, then shuffle.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "rampant-growth-om", name = "Rampant Growth", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), oracleText = "Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "farseek-om", name = "Farseek", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), oracleText = "Search your library for a land card that isn't a basic land, put it onto the battlefield tapped, then shuffle.", tags = listOf(CardTag.RAMP))),
        // Nature's Lore's REAL oracle text says "Forest card", never the literal substring "land" --
        // isLandBasedRamp's regex requires "land" between "search" and "battlefield", so this card
        // stays outside LANDFALL even with real, honest oracle text. Left as-is (not rewritten to
        // force a match -- see this phase's report, gate item 8) since the axis lights regardless
        // via the other six.
        entry(card(id = "natures-lore-om", name = "Nature's Lore", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), oracleText = "Search your library for a Forest card, put it onto the battlefield, then shuffle.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "crucible-om", name = "Crucible of Worlds", typeLine = "Artifact", cmc = 3.0, colorIdentity = emptyList())),
        entry(card(id = "ramunap-excavator-om", name = "Ramunap Excavator", typeLine = "Creature — Human Nomad", cmc = 2.0, colorIdentity = listOf("G"), power = "2", toughness = "3")),
        entry(card(id = "world-shaper-om", name = "World Shaper", typeLine = "Creature — Human Shaman", cmc = 4.0, colorIdentity = listOf("G"), power = "2", toughness = "5")),
        entry(card(id = "beast-within-om", name = "Beast Within", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("G"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "bolt-om", name = "Lightning Bolt", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "sol-ring-om", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "arcane-signet-om", name = "Arcane Signet", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList())),
        entry(card(id = "great-henge-om", name = "The Great Henge", typeLine = "Legendary Artifact", cmc = 6.0, colorIdentity = listOf("G"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "chandra-om", name = "Chandra, Torch of Defiance", typeLine = "Legendary Planeswalker — Chandra", cmc = 4.0, colorIdentity = listOf("R"))),
        entry(card(id = "explosive-vegetation", name = "Explosive Vegetation", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("G"), oracleText = "Search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "sylvan-library", name = "Sylvan Library", typeLine = "Enchantment", cmc = 1.0, colorIdentity = listOf("G"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "sakura-tribe-elder", name = "Sakura-Tribe Elder", typeLine = "Creature — Snake Shaman", cmc = 2.0, colorIdentity = listOf("G"), power = "1", toughness = "1", oracleText = "Sacrifice Sakura-Tribe Elder: Search your library for a basic land card, put that card onto the battlefield tapped, then shuffle.", tags = listOf(CardTag.RAMP))),
        // Phase 3a-FIX batch A: density expansion (22 -> 63 non-land, spec §9 realistic-deck fix).
        // Real Temur ramp/landfall staples -- pushes ramp raw count to ~24 (RAMP posture gate:
        // >= 1.4x MIDRANGE's ideal 8 = 11.2) with real land-to-battlefield oracle text on every new
        // fetch effect (isLandBasedRamp fires honestly, strengthening LANDFALL's producer half too),
        // plus more landfall_payoff creatures/planeswalkers on the consumer half.
        entry(card(id = "skyshroud-claim", name = "Skyshroud Claim", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("G"), oracleText = "Search your library for up to two Forest cards, put them onto the battlefield, then shuffle.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "circuitous-route", name = "Circuitous Route", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("G"), oracleText = "Search your library for a basic land card and/or a nonbasic land card you control an untapped copy of, put them onto the battlefield tapped, then shuffle.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "migration-path", name = "Migration Path", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("G"), oracleText = "Search your library for up to two basic land cards. Put one onto the battlefield tapped and the other into your hand, then shuffle.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "three-visits", name = "Three Visits", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), oracleText = "Search your library for a Forest or Gate card, put it onto the battlefield tapped, then shuffle.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "nissas-pilgrimage", name = "Nissa's Pilgrimage", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), oracleText = "Search your library for up to two basic land cards, reveal them, and put them into your hand, then shuffle. If you control seven or more lands, draw a card.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "solemn-simulacrum-om", name = "Solemn Simulacrum", typeLine = "Artifact Creature — Golem", cmc = 4.0, colorIdentity = emptyList(), power = "2", toughness = "2", oracleText = "When Solemn Simulacrum enters the battlefield, search your library for a basic land card, put it onto the battlefield tapped, then shuffle.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "wood-elves-om", name = "Wood Elves", typeLine = "Creature — Elf Scout", cmc = 3.0, colorIdentity = listOf("G"), power = "1", toughness = "1", oracleText = "When Wood Elves enters the battlefield, search your library for a Forest card, put it onto the battlefield, then shuffle.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "search-for-tomorrow", name = "Search for Tomorrow", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), oracleText = "Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "harrow-om", name = "Harrow", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("G"), oracleText = "Sacrifice a land. Search your library for up to two basic land cards, put them onto the battlefield untapped, then shuffle.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "zendikar-resurgent", name = "Zendikar Resurgent", typeLine = "Enchantment", cmc = 5.0, colorIdentity = listOf("G"), oracleText = "Whenever a land enters the battlefield under your control, draw a card. Creatures you control get +1/+1. Whenever you tap a land for mana, add an additional one mana of that type.", tags = listOf(roleTag("landfall_payoff")))),
        entry(card(id = "aesi-tyrant", name = "Aesi, Tyrant of Gyre Strait", typeLine = "Legendary Creature — Serpent", cmc = 5.0, colorIdentity = listOf("G", "U"), power = "5", toughness = "5", oracleText = "You may play an additional land on each of your turns. Whenever one or more lands you control enter the battlefield, draw a card and add one mana of any color.", tags = listOf(roleTag("landfall_payoff"), roleTag("extra_land_drop")))),
        entry(card(id = "ancient-greenwarden", name = "Ancient Greenwarden", typeLine = "Creature — Treefolk Druid", cmc = 6.0, colorIdentity = listOf("G"), power = "5", toughness = "5", oracleText = "You may play two additional lands on each of your turns. You may play lands from your graveyard.", tags = listOf(roleTag("landfall_payoff"), roleTag("extra_land_drop")))),
        entry(card(id = "chaos-warp-om", name = "Chaos Warp", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "reality-shift-om", name = "Reality Shift", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "pongify-om", name = "Pongify", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "rapid-hybridization-om", name = "Rapid Hybridization", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "cyclonic-rift-om", name = "Cyclonic Rift", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), tags = listOf(CardTag.WRATH))),
        entry(card(id = "fact-or-fiction-om", name = "Fact or Fiction", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "growth-spiral-om", name = "Growth Spiral", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("G", "U"), oracleText = "Draw a card. You may put a land card from your hand onto the battlefield.", tags = listOf(CardTag.RAMP, CardTag.DRAW_ENGINE))),
        entry(card(id = "rishkars-expertise", name = "Rishkar's Expertise", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("G"), oracleText = "Draw cards equal to the greatest power among creatures you control. You may cast a spell with mana value 4 or less from your hand without paying its mana cost.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "woodfall-primus", name = "Woodfall Primus", typeLine = "Creature — Elemental", cmc = 7.0, colorIdentity = listOf("G"), power = "6", toughness = "6")),
        entry(card(id = "terastodon-om", name = "Terastodon", typeLine = "Creature — Elephant", cmc = 8.0, colorIdentity = listOf("G"), power = "9", toughness = "9")),
        entry(card(id = "mind-stone-om", name = "Mind Stone", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "fellwar-stone-om", name = "Fellwar Stone", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "thran-dynamo-om", name = "Thran Dynamo", typeLine = "Artifact", cmc = 4.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "nissa-shakes-world", name = "Nissa, Who Shakes the World", typeLine = "Legendary Planeswalker — Nissa", cmc = 5.0, colorIdentity = listOf("G"), oracleText = "Your Forests are 1/1 Elemental creatures with reach that are still lands. Whenever a land enters the battlefield under your control, put a +1/+1 counter on target Elemental you control.", tags = listOf(roleTag("landfall_payoff")))),
        entry(card(id = "bloom-tender-om", name = "Bloom Tender", typeLine = "Creature — Elf Druid", cmc = 2.0, colorIdentity = listOf("G"), power = "0", toughness = "2", tags = listOf(CardTag.RAMP))),
        entry(card(id = "selvala-om", name = "Selvala, Heart of the Wilds", typeLine = "Legendary Creature — Elf Scout", cmc = 2.0, colorIdentity = listOf("G"), power = "1", toughness = "1", tags = listOf(CardTag.RAMP))),
        entry(card(id = "guardian-project-om", name = "Guardian Project", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("G"), oracleText = "If a nontoken creature you control enters the battlefield, you may draw a card unless a card with the same name is already in your graveyard.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "beast-whisperer-om", name = "Beast Whisperer", typeLine = "Creature — Human Shaman", cmc = 4.0, colorIdentity = listOf("G"), power = "2", toughness = "3", oracleText = "Whenever you cast a creature spell, draw a card.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "genesis-wave-om", name = "Genesis Wave", typeLine = "Sorcery", cmc = 5.0, colorIdentity = listOf("G"), tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "craterhoof-behemoth-om", name = "Craterhoof Behemoth", typeLine = "Creature — Beast", cmc = 8.0, colorIdentity = listOf("G"), power = "8", toughness = "8", tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "regrowth-om", name = "Regrowth", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), oracleText = "Return target card from your graveyard to your hand.", tags = listOf(roleTag("recursion")))),
        entry(card(id = "eternal-witness-om", name = "Eternal Witness", typeLine = "Creature — Elf Shaman", cmc = 3.0, colorIdentity = listOf("G"), power = "2", toughness = "1", oracleText = "When Eternal Witness enters the battlefield, return target card from your graveyard to your hand.", tags = listOf(roleTag("recursion")))),
        entry(card(id = "krosan-grip-om", name = "Krosan Grip", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("G"), tags = listOf(roleTag("removal_artifact_enchant")))),
        entry(card(id = "natures-claim-om", name = "Nature's Claim", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("G"), tags = listOf(roleTag("removal_artifact_enchant")))),
        entry(card(id = "frost-titan-om", name = "Frost Titan", typeLine = "Creature — Giant", cmc = 6.0, colorIdentity = listOf("U"), power = "6", toughness = "6")),
        entry(card(id = "consecrated-sphinx-om", name = "Consecrated Sphinx", typeLine = "Creature — Sphinx", cmc = 6.0, colorIdentity = listOf("U"), power = "4", toughness = "6", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "woodland-bellower-om", name = "Woodland Bellower", typeLine = "Creature — Elemental", cmc = 6.0, colorIdentity = listOf("G"), power = "7", toughness = "6")),
        entry(card(id = "pelakka-wurm-om", name = "Pelakka Wurm", typeLine = "Creature — Wurm", cmc = 7.0, colorIdentity = listOf("G"), power = "7", toughness = "7", oracleText = "When Pelakka Wurm enters the battlefield, you gain 7 life and draw a card. When Pelakka Wurm dies, create a 3/3 green Wurm creature token.", tags = listOf(roleTag("lifegain_source")))),
        entry(card(id = "explore-om", name = "Explore", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), oracleText = "Draw a card. You may play an additional land this turn.", tags = listOf(CardTag.RAMP, CardTag.DRAW_ENGINE))),
    )
    val mainboard = withBasicsCommander(nonland, "Forest", "G")
    return AnalysisV3Fixture(
        id = 4, name = "Omnath, Locus of Rage (Landfall)", anchor = "Omnath, Locus of Rage",
        format = DeckFormat.COMMANDER, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.R, ManaColor.G, ManaColor.U),
        // Commander prior workstream: Omnath's own text is a landfall payoff (token generation +
        // damage off lands entering/leaving) -- a big-mana ramp-value MIDRANGE read + LANDFALL
        // theme, honest and independent of this fixture's own decklist composition.
        commanderTags = listOf(CardTag.MIDRANGE, CardTag("lands_matter", TagCategory.STRATEGY)),
        expectedMacro = "MIDRANGE", expectedPosture = "RAMP", expectedThemes = "LANDFALL",
    )
}
