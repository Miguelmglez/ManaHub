package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #5 (spec §9) — Urza, Lord High Artificer, Artifacts Combo. Commander, mono-Blue.
 * Expected: macro COMBO, no posture, theme ARTIFACTS.
 */
fun fixture05Urza(): AnalysisV3Fixture {
    val commander = card(
        id = "cmd-urza", name = "Urza, Lord High Artificer", typeLine = "Legendary Creature — Human Artificer",
        cmc = 4.0, colorIdentity = listOf("U"), power = "3", toughness = "3",
        oracleText = "Whenever you cast a historic spell, create a 0/0 colorless Construct artifact creature token with \"This creature gets +1/+1 for each artifact you control.\" {5}, {T}: Create a token that's a copy of target artifact you control.",
    )
    val nonland = listOf(
        entry(commander),
        entry(card(id = "basalt-monolith-uz", name = "Basalt Monolith", typeLine = "Artifact", cmc = 3.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "rings-of-brighthearth-uz", name = "Rings of Brighthearth", typeLine = "Artifact", cmc = 4.0, colorIdentity = emptyList())),
        entry(card(id = "mana-vault", name = "Mana Vault", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "grim-monolith", name = "Grim Monolith", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "voltaic-key", name = "Voltaic Key", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList())),
        entry(card(id = "thassas-oracle-uz", name = "Thassa's Oracle", typeLine = "Creature — Merfolk Wizard", cmc = 1.0, colorIdentity = listOf("U"), power = "1", toughness = "3", tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "merchant-scroll-uz", name = "Merchant Scroll", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.TUTOR))),
        entry(card(id = "mystical-tutor-uz", name = "Mystical Tutor", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.TUTOR))),
        entry(card(id = "fabricate-uz", name = "Fabricate", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.TUTOR))),
        entry(card(id = "swan-song-uz", name = "Swan Song", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), oracleText = "Counter target enchantment, artifact, or spell.")),
        entry(card(id = "counterspell-uz", name = "Counterspell", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Counter target spell.")),
        entry(card(id = "fierce-guardianship-uz", name = "Fierce Guardianship", typeLine = "Instant", cmc = 5.0, colorIdentity = listOf("U"), oracleText = "Counter target spell.")),
        entry(card(id = "arcane-signet-uz", name = "Arcane Signet", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList())),
        entry(card(id = "sol-ring-uz", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "mana-crypt-uz", name = "Mana Crypt", typeLine = "Artifact", cmc = 0.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "brainstorm-uz", name = "Brainstorm", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "ponder-uz", name = "Ponder", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "vanquisher-banner-uz", name = "Vanquisher's Banner", typeLine = "Legendary Artifact", cmc = 5.0, colorIdentity = emptyList(), tags = listOf(roleTag("artifact_payoff")))),
        entry(card(id = "etherium-sculptor", name = "Etherium Sculptor", typeLine = "Creature — Human Artificer", cmc = 2.0, colorIdentity = listOf("U"), power = "1", toughness = "2", tags = listOf(roleTag("artifact_payoff")))),
        entry(card(id = "master-transmuter", name = "Master Transmuter", typeLine = "Creature — Human Artificer", cmc = 3.0, colorIdentity = listOf("U"), power = "1", toughness = "2", tags = listOf(roleTag("artifact_payoff")))),
        entry(card(id = "foundry-inspector", name = "Foundry Inspector", typeLine = "Creature — Construct", cmc = 2.0, colorIdentity = emptyList(), power = "1", toughness = "3", tags = listOf(roleTag("artifact_payoff")))),
        entry(card(id = "karn-scion", name = "Karn, Scion of Urza", typeLine = "Legendary Planeswalker — Karn", cmc = 4.0, colorIdentity = emptyList())),
        entry(card(id = "cyclonic-rift-uz", name = "Cyclonic Rift", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"))),
        entry(card(id = "solemn-simulacrum-uz", name = "Solemn Simulacrum", typeLine = "Artifact Creature — Golem", cmc = 4.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP), power = "2", toughness = "2")),
        // Phase 3a-FIX batch A: density expansion (24 -> 63 non-land, spec §9 realistic-deck fix).
        // Real mono-U artifact-combo staples. The ARTIFACTS axis's producer half is already
        // structural (SynergyGraph.structuralProducerAxes reads raw Card.typeLine density, per
        // phase 2's memory -- every "Artifact"-typed card below adds real producer signal with no
        // tag needed); this batch focuses new roleTag("artifact_payoff") on the CONSUMER half plus
        // a realistic tutor/counterspell/card-draw shell for the COMBO macro's own axes.
        entry(card(id = "thopter-foundry", name = "Thopter Foundry", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), oracleText = "{1}, Sacrifice an artifact: Create a 1/1 colorless Thopter artifact creature token with flying. You gain 1 life.", tags = listOf(roleTag("artifact_payoff")))),
        entry(card(id = "sculpting-steel", name = "Sculpting Steel", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"))),
        entry(card(id = "chief-engineer", name = "Chief Engineer", typeLine = "Creature — Human Artificer", cmc = 3.0, colorIdentity = listOf("U"), power = "2", toughness = "2", oracleText = "You may cast artifact spells as though they had flash. You may pay {U} rather than pay the mana cost for artifact spells you cast.", tags = listOf(roleTag("cost_reducer")))),
        entry(card(id = "trinket-mage", name = "Trinket Mage", typeLine = "Creature — Human Wizard", cmc = 3.0, colorIdentity = listOf("U"), power = "2", toughness = "2", tags = listOf(CardTag.TUTOR))),
        entry(card(id = "trophy-mage", name = "Trophy Mage", typeLine = "Creature — Human Wizard", cmc = 3.0, colorIdentity = listOf("U"), power = "2", toughness = "2", tags = listOf(CardTag.TUTOR))),
        entry(card(id = "tribute-mage", name = "Tribute Mage", typeLine = "Creature — Human Wizard", cmc = 2.0, colorIdentity = listOf("U"), power = "2", toughness = "1", tags = listOf(CardTag.TUTOR))),
        entry(card(id = "whir-of-invention", name = "Whir of Invention", typeLine = "Instant", cmc = 5.0, colorIdentity = listOf("U"), tags = listOf(CardTag.TUTOR))),
        entry(card(id = "muddle-the-mixture", name = "Muddle the Mixture", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Choose one — Counter target spell with mana value 2; or search your library for a card with mana value 2, reveal it, put it into your hand, then shuffle.", tags = listOf(CardTag.TUTOR))),
        entry(card(id = "pact-of-negation", name = "Pact of Negation", typeLine = "Instant", cmc = 0.0, colorIdentity = listOf("U"), oracleText = "Counter target spell.")),
        entry(card(id = "metallic-rebuke", name = "Metallic Rebuke", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("U"), oracleText = "Counter target spell. This spell costs {2} less to cast if you control an artifact.")),
        entry(card(id = "mystic-confluence", name = "Mystic Confluence", typeLine = "Instant", cmc = 5.0, colorIdentity = listOf("U"), oracleText = "Choose two — Counter target spell unless its controller pays {3}; return target permanent to its owner's hand; draw a card.")),
        entry(card(id = "time-warp-uz", name = "Time Warp", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("U"), tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "frantic-search-uz", name = "Frantic Search", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "preordain-uz", name = "Preordain", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "serum-visions-uz", name = "Serum Visions", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "thirst-for-knowledge-uz", name = "Thirst for Knowledge", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "minds-eye", name = "Mind's Eye", typeLine = "Artifact", cmc = 4.0, colorIdentity = emptyList(), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "staff-of-domination", name = "Staff of Domination", typeLine = "Artifact", cmc = 5.0, colorIdentity = emptyList())),
        entry(card(id = "kuldotha-forgemaster", name = "Kuldotha Forgemaster", typeLine = "Artifact Creature — Golem", cmc = 4.0, colorIdentity = emptyList(), power = "3", toughness = "4", oracleText = "Sacrifice three artifacts: Search your library for an artifact card, put it onto the battlefield, then shuffle.", tags = listOf(roleTag("artifact_payoff")))),
        entry(card(id = "third-path-iconoclast", name = "Third Path Iconoclast", typeLine = "Creature — Human Wizard", cmc = 3.0, colorIdentity = listOf("U"), power = "2", toughness = "3", oracleText = "Whenever you cast a noncreature spell, create a 1/1 colorless Servo artifact creature token, then if you cast an instant or sorcery spell, that token becomes a copy of a Construct token with power and toughness each equal to the number of artifacts and creatures you control.", tags = listOf(roleTag("artifact_payoff")))),
        entry(card(id = "emry-lurker", name = "Emry, Lurker of the Loch", typeLine = "Legendary Creature — Merfolk Wizard", cmc = 2.0, colorIdentity = listOf("U"), power = "1", toughness = "2", oracleText = "When Emry, Lurker of the Loch enters the battlefield, mill four cards. Tap an untapped artifact you control: Choose target artifact card in your graveyard with mana value less than or equal to the number of artifacts you control. You may cast that card this turn.", tags = listOf(roleTag("artifact_payoff")))),
        entry(card(id = "metalwork-colossus", name = "Metalwork Colossus", typeLine = "Artifact Creature — Golem", cmc = 10.0, colorIdentity = emptyList(), power = "10", toughness = "10")),
        entry(card(id = "hangarback-walker", name = "Hangarback Walker", typeLine = "Artifact Creature — Construct", cmc = 2.0, colorIdentity = emptyList(), power = "0", toughness = "0", oracleText = "When Hangarback Walker dies, create a 1/1 colorless Thopter artifact creature token with flying for each +1/+1 counter on it.", tags = listOf(roleTag("token_generator")))),
        entry(card(id = "salvage-titan", name = "Salvage Titan", typeLine = "Artifact Creature — Golem", cmc = 5.0, colorIdentity = emptyList(), power = "6", toughness = "6", oracleText = "Whenever one or more artifacts you control are put into a graveyard, put that many +1/+1 counters on Salvage Titan.", tags = listOf(roleTag("artifact_payoff")))),
        entry(card(id = "karn-great-creator", name = "Karn, the Great Creator", typeLine = "Legendary Planeswalker — Karn", cmc = 4.0, colorIdentity = emptyList(), tags = listOf(CardTag.TUTOR))),
        entry(card(id = "krark-clan-ironworks", name = "Krark-Clan Ironworks", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), oracleText = "{T}, Sacrifice an artifact: Add three mana of any one color.", tags = listOf(roleTag("artifact_payoff")))),
        entry(card(id = "everflowing-chalice", name = "Everflowing Chalice", typeLine = "Artifact", cmc = 0.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "worn-powerstone", name = "Worn Powerstone", typeLine = "Artifact", cmc = 3.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "thran-dynamo-uz", name = "Thran Dynamo", typeLine = "Artifact", cmc = 4.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        entry(card(id = "padeem-consul", name = "Padeem, Consul of Innovation", typeLine = "Legendary Creature — Human Artificer", cmc = 3.0, colorIdentity = listOf("U"), power = "1", toughness = "4", oracleText = "Flying. Whenever you cast a noncreature artifact spell, draw a card.", tags = listOf(roleTag("artifact_payoff")))),
        entry(card(id = "sai-master-thopterist", name = "Sai, Master Thopterist", typeLine = "Legendary Creature — Human Artificer", cmc = 3.0, colorIdentity = listOf("U"), power = "1", toughness = "3", oracleText = "Whenever you cast a noncreature artifact spell, create a 1/1 colorless Thopter artifact creature token with flying.", tags = listOf(roleTag("artifact_payoff")))),
        entry(card(id = "unwinding-clock", name = "Unwinding Clock", typeLine = "Artifact", cmc = 3.0, colorIdentity = emptyList())),
        entry(card(id = "senseis-top", name = "Sensei's Divining Top", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "chromatic-star", name = "Chromatic Star", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "prophetic-prism", name = "Prophetic Prism", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "semblance-anvil", name = "Semblance Anvil", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), oracleText = "As Semblance Anvil enters the battlefield, imprint a nonland card from your hand. Spells you cast that share a card type with the imprinted card cost {2} less to cast.", tags = listOf(roleTag("cost_reducer")))),
        entry(card(id = "mystic-forge", name = "Mystic Forge", typeLine = "Artifact", cmc = 3.0, colorIdentity = emptyList())),
        entry(card(id = "grand-architect", name = "Grand Architect", typeLine = "Creature — Human Wizard", cmc = 3.0, colorIdentity = listOf("U"), power = "0", toughness = "4", oracleText = "{T}: Add {U}. Artifact creatures you control have \"{T}: Add {U}.\" Blue spells you cast that target only a single artifact creature you control cost {2} less to cast.", tags = listOf(roleTag("artifact_payoff")))),
        entry(card(id = "cryptic-command", name = "Cryptic Command", typeLine = "Instant", cmc = 4.0, colorIdentity = listOf("U"), oracleText = "Choose two — Counter target spell; return target permanent to its owner's hand; draw a card; tap all creatures your opponents control.")),
    )
    val mainboard = withBasicsCommander(nonland, "Island", "U")
    return AnalysisV3Fixture(
        id = 5, name = "Urza, Lord High Artificer (Artifact Combo)", anchor = "Urza, Lord High Artificer",
        format = DeckFormat.COMMANDER, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.U),
        // Commander prior workstream: Urza, Lord High Artificer is THE iconic COMBO commander in
        // paper/cEDH play (artifact-token/mana combo engine) -- honest, well-established real-world
        // read, independent of this fixture's own decklist.
        commanderTags = listOf(CardTag.COMBO, CardTag("artifacts_matter", TagCategory.STRATEGY)),
        expectedMacro = "COMBO", expectedPosture = NO_POSTURE, expectedThemes = "ARTIFACTS",
    )
}
