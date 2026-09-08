package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #21 — Jund (Black/Red/Green Midrange), Modern, no commander. 60-card MIDRANGE
 * corpus-expansion workstream (2026-08-26): a second 60-card MIDRANGE witness (alongside fixture
 * #16, Tron), a different colour identity and a completely different plan (removal + 2-for-1s +
 * efficient threats, no ramp/Tron-lock shell) so the MIDRANGE reading is not anchored on one build.
 * THE defining Modern midrange archetype for over a decade (Tarmogoyf/Dark Confidant/Liliana of the
 * Veil have anchored this shell since Modern's own inception) -- no banned pieces, maximally stable.
 * Runs a heavier land count (24) than the corpus's other 60-card fixtures so far, since a 3-colour
 * grindy midrange pile genuinely needs it (unlike Burn's 12 or Merfolk's 12).
 * Expected: macro MIDRANGE, no posture, no themes (a coherent goodstuff pile with no single
 * dedicated synergy axis -- removal + card advantage IS the plan, not a build-around).
 */
fun fixture21Jund(): AnalysisV3Fixture {
    val nonland = listOf(
        entry(
            card(
                id = "jd-tarmogoyf", name = "Tarmogoyf", typeLine = "Creature — Lhurgoyf",
                cmc = 2.0, colorIdentity = listOf("G"), power = "3", toughness = "4",
                oracleText = "Tarmogoyf's power is equal to the number of card types among cards in all graveyards and its toughness is that number plus 1.",
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "jd-dark-confidant", name = "Dark Confidant", typeLine = "Creature — Human Wizard",
                cmc = 2.0, colorIdentity = listOf("B"), power = "2", toughness = "1",
                oracleText = "At the beginning of your upkeep, reveal the top card of your library and put that card into your hand. You lose life equal to its mana value.",
                tags = listOf(CardTag.DRAW_ENGINE),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "jd-liliana-veil", name = "Liliana of the Veil", typeLine = "Legendary Planeswalker — Liliana",
                cmc = 3.0, colorIdentity = listOf("B"),
                oracleText = "+1: Each player discards a card. -2: Target player sacrifices a creature. -6: Separate all permanents target player controls into two piles. That player sacrifices all permanents in the pile of their choice.",
                tags = listOf(roleTag("planeswalker"), CardTag.REMOVAL),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "jd-thoughtseize", name = "Thoughtseize", typeLine = "Sorcery",
                cmc = 1.0, colorIdentity = listOf("B"),
                oracleText = "Target player reveals their hand. You choose a nonland card from it. That player discards that card. You lose 2 life.",
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "jd-kolaghans-command", name = "Kolaghan's Command", typeLine = "Instant",
                cmc = 2.0, colorIdentity = listOf("B", "R"),
                oracleText = "Choose two — Kolaghan's Command deals 2 damage to target creature or player; or destroy target artifact; or return target creature card from your graveyard to your hand; or target player discards a card.",
                tags = listOf(CardTag.REMOVAL),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "jd-lightning-bolt", name = "Lightning Bolt", typeLine = "Instant",
                cmc = 1.0, colorIdentity = listOf("R"),
                oracleText = "Lightning Bolt deals 3 damage to any target.",
                tags = listOf(CardTag.REMOVAL),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "jd-bloodbraid-elf", name = "Bloodbraid Elf", typeLine = "Creature — Elf Berserker",
                cmc = 4.0, colorIdentity = listOf("R", "G"), power = "3", toughness = "2",
                oracleText = "Haste. When Bloodbraid Elf enters the battlefield, cascade. (Exile cards from the top of your library until you exile a nonland card that costs less. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order.)",
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "jd-tireless-tracker", name = "Tireless Tracker", typeLine = "Creature — Human Scout",
                cmc = 3.0, colorIdentity = listOf("G"), power = "3", toughness = "2",
                oracleText = "Whenever a land enters the battlefield under your control, investigate. (Create a colorless Clue artifact token with \"{2}, Sacrifice this artifact: Draw a card.\") Whenever you sacrifice a Clue, put a +1/+1 counter on Tireless Tracker.",
                tags = listOf(CardTag.DRAW_ENGINE),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "jd-fatal-push", name = "Fatal Push", typeLine = "Instant",
                cmc = 1.0, colorIdentity = listOf("B"),
                oracleText = "Destroy target creature if it has mana value 2 or less. Revolt — Destroy that creature if it has mana value 4 or less instead if a permanent you controlled left the battlefield this turn.",
                tags = listOf(CardTag.REMOVAL),
            ),
            quantity = 4,
        ),
    )
    val mainboard = withBasicsSixty(nonland, "Swamp", "B")
    return AnalysisV3Fixture(
        id = 21, name = "Jund (Black/Red/Green Midrange, Modern)", anchor = "Jund",
        format = DeckFormat.MODERN, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.B, ManaColor.R, ManaColor.G),
        expectedMacro = "MIDRANGE", expectedPosture = NO_POSTURE, expectedThemes = NO_THEMES,
    )
}
