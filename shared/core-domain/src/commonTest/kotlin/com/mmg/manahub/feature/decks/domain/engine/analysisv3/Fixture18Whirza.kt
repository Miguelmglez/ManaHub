package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #18 — "Whirza" (Urza artifact-combo), Modern, mono-Blue, no commander. 60-card COMBO
 * corpus-expansion workstream (2026-08-26): the corpus previously had ZERO 60-card COMBO fixture.
 * Real, long-stable Modern archetype centered on [Urza, Lord High Artificer] (Modern-legal since
 * Dominaria, still a recognized archetype today) chaining cheap artifacts into a Thopter Foundry /
 * Sword of the Meek value-and-tokens engine, closed out by Time Sieve's extra-turn loop -- the SAME
 * ARTIFACTS shape fixture #5 (Urza, Commander) already demonstrates resolves cleanly to COMBO, now
 * tested at 60-card scale and density.
 * Expected: macro COMBO, no posture, theme ARTIFACTS.
 */
fun fixture18Whirza(): AnalysisV3Fixture {
    val nonland = listOf(
        entry(
            card(
                id = "wz-urza", name = "Urza, Lord High Artificer", typeLine = "Legendary Creature — Human Artificer",
                cmc = 4.0, colorIdentity = listOf("U"), power = "3", toughness = "3",
                oracleText = "Whenever you cast a historic spell, create a 0/0 colorless Construct artifact creature token with \"This creature gets +1/+1 for each artifact you control.\" {5}, {T}: Create a token that's a copy of target artifact you control.",
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "wz-thopter-foundry", name = "Thopter Foundry", typeLine = "Artifact",
                cmc = 2.0, colorIdentity = emptyList(),
                oracleText = "{1}, Sacrifice an artifact: Create a 1/1 colorless Thopter artifact creature token with flying. You gain 1 life.",
                tags = listOf(roleTag("artifact_payoff")),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "wz-sword-of-the-meek", name = "Sword of the Meek", typeLine = "Legendary Artifact — Equipment",
                cmc = 1.0, colorIdentity = emptyList(),
                oracleText = "Equipped creature gets +1/+1. Whenever a 1/1 Thopter you control enters the battlefield, you may return Sword of the Meek from your graveyard to the battlefield. Equip {1}.",
                tags = listOf(roleTag("artifact_payoff"), roleTag("recursion")),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "wz-chief-engineer", name = "Chief Engineer", typeLine = "Creature — Human Artificer",
                cmc = 3.0, colorIdentity = listOf("U"), power = "2", toughness = "2",
                oracleText = "You may cast artifact spells as though they had flash. You may pay {U} rather than pay the mana cost for artifact spells you cast.",
                tags = listOf(roleTag("cost_reducer")),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "wz-emry", name = "Emry, Lurker of the Loch", typeLine = "Legendary Creature — Merfolk Wizard",
                cmc = 2.0, colorIdentity = listOf("U"), power = "1", toughness = "2",
                oracleText = "When Emry, Lurker of the Loch enters the battlefield, mill four cards. Tap an untapped artifact you control: Choose target artifact card in your graveyard with mana value less than or equal to the number of artifacts you control. You may cast that card this turn.",
                tags = listOf(roleTag("artifact_payoff"), roleTag("recursion")),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "wz-whir-of-invention", name = "Whir of Invention", typeLine = "Instant",
                cmc = 5.0, colorIdentity = listOf("U"),
                oracleText = "Choose an artifact card, then search your library for a card with the chosen mana value or less, reveal it, put it into your hand, then shuffle. Affinity for artifacts.",
                tags = listOf(CardTag.TUTOR),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "wz-sai", name = "Sai, Master Thopterist", typeLine = "Legendary Creature — Human Artificer",
                cmc = 3.0, colorIdentity = listOf("U"), power = "1", toughness = "3",
                oracleText = "Whenever you cast a noncreature artifact spell, create a 1/1 colorless Thopter artifact creature token with flying. Sacrifice an artifact creature: Draw a card. Activate only once each turn.",
                tags = listOf(roleTag("artifact_payoff"), roleTag("token_generator")),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "wz-time-sieve", name = "Time Sieve", typeLine = "Artifact",
                cmc = 4.0, colorIdentity = emptyList(),
                oracleText = "{5}, {T}, Sacrifice five artifacts: Take an extra turn after this one.",
                tags = listOf(CardTag.WIN_CON),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "wz-mishras-bauble", name = "Mishra's Bauble", typeLine = "Artifact",
                cmc = 0.0, colorIdentity = emptyList(),
                oracleText = "(Mishra's Bauble enters the battlefield tapped.) {T}, Sacrifice Mishra's Bauble: Draw a card.",
                tags = listOf(CardTag.DRAW_ENGINE),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "wz-chromatic-star", name = "Chromatic Star", typeLine = "Artifact",
                cmc = 1.0, colorIdentity = emptyList(),
                oracleText = "{1}, {T}, Sacrifice Chromatic Star: Add one mana of any color. Draw a card.",
                tags = listOf(CardTag.DRAW_ENGINE),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "wz-mind-stone", name = "Mind Stone", typeLine = "Artifact",
                cmc = 2.0, colorIdentity = emptyList(),
                oracleText = "{T}: Add {C}. {1}, {T}, Sacrifice Mind Stone: Draw a card.",
                tags = listOf(CardTag.RAMP),
            ),
            quantity = 4,
        ),
    )
    val mainboard = withBasicsSixty(nonland, "Island", "U")
    return AnalysisV3Fixture(
        id = 18, name = "Whirza (Urza Artifact Combo, Modern)", anchor = "Whirza",
        format = DeckFormat.MODERN, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.U),
        expectedMacro = "COMBO", expectedPosture = NO_POSTURE, expectedThemes = "ARTIFACTS",
    )
}
