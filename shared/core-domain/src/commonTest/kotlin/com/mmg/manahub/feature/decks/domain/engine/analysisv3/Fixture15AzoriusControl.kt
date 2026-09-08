package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #15 (spec §9) — Azorius Control, Modern, no commander.
 * Expected: macro CONTROL, no posture, no themes.
 */
fun fixture15AzoriusControl(): AnalysisV3Fixture {
    val nonland = listOf(
        entry(card(id = "az-cryptic", name = "Cryptic Command", typeLine = "Instant", cmc = 4.0, colorIdentity = listOf("U"), oracleText = "Choose two — Counter target spell."), quantity = 4),
        entry(card(id = "az-counterspell", name = "Counterspell", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Counter target spell."), quantity = 4),
        entry(card(id = "az-remand", name = "Remand", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Counter target spell."), quantity = 4),
        entry(card(id = "az-wrath", name = "Supreme Verdict", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("U", "W"), tags = listOf(CardTag.WRATH)), quantity = 4),
        entry(card(id = "az-terminus", name = "Terminus", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("W"), tags = listOf(CardTag.WRATH)), quantity = 4),
        entry(card(id = "az-path", name = "Path to Exile", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL)), quantity = 4),
        entry(card(id = "az-jvp", name = "Jace, the Mind Sculptor", typeLine = "Legendary Planeswalker — Jace", cmc = 4.0, colorIdentity = listOf("U"), tags = listOf(CardTag.WIN_CON)), quantity = 4),
        entry(card(id = "az-serum-visions", name = "Serum Visions", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE)), quantity = 4),
        entry(card(id = "az-teferi-hero", name = "Teferi, Hero of Dominaria", typeLine = "Legendary Planeswalker — Teferi", cmc = 5.0, colorIdentity = listOf("W", "U")), quantity = 4),
        entry(card(id = "az-snapcaster", name = "Snapcaster Mage", typeLine = "Creature — Human Wizard", cmc = 2.0, colorIdentity = listOf("U"), power = "2", toughness = "1"), quantity = 4),
        entry(card(id = "az-vendilion", name = "Vendilion Clique", typeLine = "Creature — Faerie Wizard", cmc = 3.0, colorIdentity = listOf("W", "U"), power = "3", toughness = "1"), quantity = 4),
        entry(card(id = "az-restoration-angel-mo", name = "Restoration Angel", typeLine = "Creature — Angel", cmc = 4.0, colorIdentity = listOf("W"), power = "3", toughness = "4"), quantity = 4),
        entry(card(id = "az-condescend", name = "Condescend", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Counter target spell unless its controller pays {X}."), quantity = 4),
    )
    val mainboard = withBasicsSixty(nonland, "Island", "U")
    return AnalysisV3Fixture(
        id = 15, name = "Azorius Control (Modern)", anchor = "Azorius Control",
        format = DeckFormat.MODERN, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.W, ManaColor.U),
        expectedMacro = "CONTROL", expectedPosture = NO_POSTURE, expectedThemes = NO_THEMES,
    )
}
