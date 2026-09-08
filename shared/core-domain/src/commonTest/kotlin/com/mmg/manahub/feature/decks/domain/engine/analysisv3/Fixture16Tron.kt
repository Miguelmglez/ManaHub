package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #16 (spec §9) — Tron, Modern, mono-Green ramp shell with colorless artifact payoffs, no
 * commander. Expected: macro MIDRANGE, posture RAMP, no themes.
 */
fun fixture16Tron(): AnalysisV3Fixture {
    val nonland = listOf(
        entry(card(id = "tr-sylvan-scrying", name = "Sylvan Scrying", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("G"), tags = listOf(CardTag.RAMP)), quantity = 4),
        entry(card(id = "tr-expedition-map", name = "Expedition Map", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.TUTOR)), quantity = 4),
        entry(card(id = "tr-eye-of-ugin", name = "Eye of Ugin", typeLine = "Legendary Land", cmc = 0.0, colorIdentity = emptyList()), quantity = 1),
        entry(card(id = "tr-karn-liberated", name = "Karn Liberated", typeLine = "Legendary Planeswalker — Karn", cmc = 6.0, colorIdentity = emptyList()), quantity = 4),
        entry(card(id = "tr-wurmcoil", name = "Wurmcoil Engine", typeLine = "Artifact Creature — Wurm", cmc = 6.0, colorIdentity = emptyList(), power = "6", toughness = "6"), quantity = 4),
        entry(card(id = "tr-ulamog", name = "Ulamog, the Ceaseless Hunger", typeLine = "Legendary Creature — Eldrazi", cmc = 10.0, colorIdentity = emptyList(), power = "10", toughness = "10", tags = listOf(CardTag.WIN_CON)), quantity = 4),
        entry(card(id = "tr-oblivion-stone", name = "Oblivion Stone", typeLine = "Artifact", cmc = 3.0, colorIdentity = emptyList(), tags = listOf(CardTag.WRATH)), quantity = 4),
        entry(card(id = "tr-relic-of-progenitus", name = "Relic of Progenitus", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList()), quantity = 4),
        entry(card(id = "tr-thragtusk", name = "Thragtusk", typeLine = "Creature — Beast", cmc = 5.0, colorIdentity = listOf("G"), power = "5", toughness = "5"), quantity = 4),
        entry(card(id = "tr-explore", name = "Explore", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("G"), tags = listOf(CardTag.RAMP)), quantity = 4),
        entry(card(id = "tr-beast-within-mo", name = "Beast Within", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("G"), tags = listOf(CardTag.REMOVAL)), quantity = 4),
        entry(card(id = "tr-nature's-claim", name = "Nature's Claim", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("G"), tags = listOf(CardTag.REMOVAL)), quantity = 4),
    )
    val mainboard = withBasicsSixty(nonland, "Forest", "G")
    return AnalysisV3Fixture(
        id = 16, name = "Tron (Modern)", anchor = "Tron",
        format = DeckFormat.MODERN, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.G),
        expectedMacro = "MIDRANGE", expectedPosture = "RAMP", expectedThemes = NO_THEMES,
    )
}
