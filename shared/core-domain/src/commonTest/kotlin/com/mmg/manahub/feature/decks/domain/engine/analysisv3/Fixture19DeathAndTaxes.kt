package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #19 — mono-White "Death and Taxes" (Hatebears/Prison), Modern, no commander. 60-card
 * PRISON corpus-expansion workstream (2026-08-26): the corpus previously had ZERO 60-card PRISON
 * fixture. Real, long-stable archetype (Thalia's own 2011 printing has anchored this shell for
 * over a decade, still played today) built around cheap disruptive creatures that tax or deny an
 * opponent's plan one at a time, rather than a symmetric Commander-style stax lock -- the SAME
 * `stax_piece` role as fixture #10 (Grand Arbiter, Commander), now measured at 60-card density.
 * See this workstream's own gate report for whether `stax_piece`'s missing producer/payoff axis
 * edge (fixture #10's diagnosed defect) reproduces here too.
 * Expected: macro PRISON, no posture, no themes.
 */
fun fixture19DeathAndTaxes(): AnalysisV3Fixture {
    val nonland = listOf(
        entry(
            card(
                id = "dt-thalia", name = "Thalia, Guardian of Thraben", typeLine = "Legendary Creature — Human Soldier",
                cmc = 2.0, colorIdentity = listOf("W"), power = "2", toughness = "1",
                oracleText = "First strike. Noncreature spells cost {1} more to cast.",
                tags = listOf(roleTag("stax_piece")),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "dt-leonin-arbiter", name = "Leonin Arbiter", typeLine = "Creature — Cat Cleric",
                cmc = 2.0, colorIdentity = listOf("W"), power = "2", toughness = "2",
                oracleText = "If an opponent would search a library, that player searches the top four cards of that library instead unless they pay {2}.",
                tags = listOf(roleTag("stax_piece")),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "dt-aven-mindcensor", name = "Aven Mindcensor", typeLine = "Creature — Bird Cleric",
                cmc = 2.0, colorIdentity = listOf("W"), power = "2", toughness = "2",
                oracleText = "Flying. If an opponent would search a library, search only the top four cards of that library instead.",
                tags = listOf(roleTag("stax_piece")),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "dt-skyclave-apparition", name = "Skyclave Apparition", typeLine = "Creature — Spirit",
                cmc = 3.0, colorIdentity = listOf("W"), power = "3", toughness = "2",
                oracleText = "Flying. When Skyclave Apparition enters the battlefield, exile up to one target nonland, nontoken permanent an opponent controls with mana value 4 or less. That permanent's owner creates an X/X colorless Illusion creature token, where X is the exiled permanent's mana value.",
                tags = listOf(CardTag.REMOVAL),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "dt-giver-of-runes", name = "Giver of Runes", typeLine = "Creature — Kor Cleric",
                cmc = 1.0, colorIdentity = listOf("W"), power = "1", toughness = "2",
                oracleText = "{T}: Another target creature you control gains protection from colorless or from the color of your choice until end of turn.",
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "dt-recruiter-of-the-guard", name = "Recruiter of the Guard", typeLine = "Creature — Human Scout",
                cmc = 2.0, colorIdentity = listOf("W"), power = "1", toughness = "3",
                oracleText = "When Recruiter of the Guard enters the battlefield, look at the top five cards of your library. You may reveal a creature card with power 2 or less from among them and put it into your hand. Put the rest on the bottom in a random order.",
                tags = listOf(CardTag.TUTOR),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "dt-prismatic-ending", name = "Prismatic Ending", typeLine = "Instant",
                cmc = 1.0, colorIdentity = listOf("W"),
                oracleText = "This spell costs {1} more to cast for each color among permanents you don't control. Choose a color. Exile target nonland permanent that is one or more of that color.",
                tags = listOf(CardTag.REMOVAL),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "dt-flickerwisp", name = "Flickerwisp", typeLine = "Creature — Elemental",
                cmc = 3.0, colorIdentity = listOf("W"), power = "3", toughness = "1",
                oracleText = "Flash. Flying. When Flickerwisp enters the battlefield, exile another target permanent. If that card is a permanent card, return it to the battlefield under its owner's control at the beginning of the next end step.",
                tags = listOf(roleTag("blink_effect")),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "dt-restoration-angel", name = "Restoration Angel", typeLine = "Creature — Angel",
                cmc = 4.0, colorIdentity = listOf("W"), power = "3", toughness = "4",
                oracleText = "Flash. Flying. When Restoration Angel enters the battlefield, you may exile target non-Angel creature you control, then return that card to the battlefield under your control.",
                tags = listOf(roleTag("blink_effect")),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "dt-palace-jailer", name = "Palace Jailer", typeLine = "Legendary Creature — Human Soldier",
                cmc = 3.0, colorIdentity = listOf("W"), power = "2", toughness = "2",
                oracleText = "When Palace Jailer enters the battlefield, you become the monarch. When Palace Jailer enters the battlefield, exile target creature an opponent controls until an opponent becomes the monarch.",
                tags = listOf(CardTag.REMOVAL),
            ),
            quantity = 4,
        ),
    )
    val mainboard = withBasicsSixty(nonland, "Plains", "W")
    return AnalysisV3Fixture(
        id = 19, name = "Death and Taxes (Mono-White Hatebears, Modern)", anchor = "Death and Taxes",
        format = DeckFormat.MODERN, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.W),
        expectedMacro = "PRISON", expectedPosture = NO_POSTURE, expectedThemes = NO_THEMES,
    )
}
