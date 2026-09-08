package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #22 — Grixis Control (Blue/Black/Red), Modern, no commander. 60-card CONTROL
 * corpus-expansion workstream (2026-08-26): a second 60-card CONTROL witness, a DIFFERENT colour
 * pair from fixture #15 (Azorius Control, WU) so the CONTROL reading is not anchored on one build --
 * counterspells + unconditional spot removal + a board wipe + Snapcaster/Jace value, the archetype's
 * defining Modern shape since Snapcaster Mage was legal. No banned pieces, long-stable.
 * Expected: macro CONTROL, posture TEMPO (2 distinct counterspells x4 = 8 raw copies, clears the
 * spec §3 TEMPO_COUNTERSPELL_MIN of 6), no themes.
 */
fun fixture22GrixisControl(): AnalysisV3Fixture {
    val nonland = listOf(
        entry(
            card(
                id = "gx-snapcaster-mage", name = "Snapcaster Mage", typeLine = "Creature — Human Wizard",
                cmc = 2.0, colorIdentity = listOf("U"), power = "2", toughness = "1",
                oracleText = "Flash. When Snapcaster Mage enters the battlefield, target instant or sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal to its mana cost.",
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "gx-cryptic-command", name = "Cryptic Command", typeLine = "Instant",
                cmc = 4.0, colorIdentity = listOf("U"),
                oracleText = "Choose two — Counter target spell; or return target permanent to its owner's hand; or draw a card; or tap all creatures your opponents control.",
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "gx-counterspell", name = "Counterspell", typeLine = "Instant",
                cmc = 2.0, colorIdentity = listOf("U"),
                oracleText = "Counter target spell.",
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "gx-fatal-push", name = "Fatal Push", typeLine = "Instant",
                cmc = 1.0, colorIdentity = listOf("B"),
                oracleText = "Destroy target creature if it has mana value 2 or less. Revolt — Destroy that creature if it has mana value 4 or less instead if a permanent you controlled left the battlefield this turn.",
                tags = listOf(CardTag.REMOVAL),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "gx-terminate", name = "Terminate", typeLine = "Instant",
                cmc = 2.0, colorIdentity = listOf("B", "R"),
                oracleText = "Destroy target creature. It can't be regenerated.",
                tags = listOf(CardTag.REMOVAL),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "gx-anger-of-the-gods", name = "Anger of the Gods", typeLine = "Sorcery",
                cmc = 2.0, colorIdentity = listOf("R"),
                oracleText = "Anger of the Gods deals 3 damage to each creature. If a creature dealt damage this way would die this turn, exile it instead.",
                tags = listOf(CardTag.WRATH),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "gx-jace-tms", name = "Jace, the Mind Sculptor", typeLine = "Legendary Planeswalker — Jace",
                cmc = 4.0, colorIdentity = listOf("U"),
                oracleText = "+2: Look at the top card of target player's library. You may put that card into that player's graveyard. +0: Draw three cards, then put two cards from your hand on top of your library in any order. -1: Return target creature to its owner's hand. -12: Exile all cards from target player's library, then that player shuffles their hand into their library.",
                tags = listOf(roleTag("planeswalker"), CardTag.WIN_CON),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "gx-serum-visions", name = "Serum Visions", typeLine = "Sorcery",
                cmc = 1.0, colorIdentity = listOf("U"),
                oracleText = "Draw two cards. Then put a card from your hand on top of your library.",
                tags = listOf(CardTag.DRAW_ENGINE),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "gx-fact-or-fiction", name = "Fact or Fiction", typeLine = "Instant",
                cmc = 3.0, colorIdentity = listOf("U"),
                oracleText = "Reveal the top five cards of your library. An opponent separates those cards into two piles. Put one pile into your hand and the other into your graveyard.",
                tags = listOf(CardTag.DRAW_ENGINE),
            ),
            quantity = 4,
        ),
    )
    val mainboard = withBasicsSixty(nonland, "Island", "U")
    return AnalysisV3Fixture(
        id = 22, name = "Grixis Control (Modern)", anchor = "Grixis Control",
        format = DeckFormat.MODERN, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.U, ManaColor.B, ManaColor.R),
        expectedMacro = "CONTROL", expectedPosture = "TEMPO", expectedThemes = NO_THEMES,
    )
}
