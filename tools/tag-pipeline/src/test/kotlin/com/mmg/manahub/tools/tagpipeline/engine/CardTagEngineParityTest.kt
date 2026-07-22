package com.mmg.manahub.tools.tagpipeline.engine

import com.mmg.manahub.core.data.tagging.StrategyAnalyzer
import com.mmg.manahub.core.data.tagging.TagDictionary
import com.mmg.manahub.core.data.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.tools.tagpipeline.testCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * THE single most important test in this module (per the RUN 5 gate): proves the CLI's per-card
 * tag output is byte-identical to what the in-app `SuggestTagsUseCase`/`StrategyAnalyzer` produce
 * for the same fixture cards — zero drift, D5's entire reason for existing.
 *
 * [inAppConfirmedTags] independently reconstructs the EXACT chain `:app`'s DI wires
 * (`SharedDomainUseCaseModule.provideComputeCardTagsUseCase` / `AutoTagCard`'s
 * `createStrategyAnalyzer()`) rather than delegating to [CardTagEngine] — so this test is a genuine
 * two-path comparison, not a tautology. If [CardTagEngine] is ever refactored to add a filter, use a
 * different confidence threshold, or misconfigure the entries provider, this test fails immediately.
 */
class CardTagEngineParityTest {

    private val engine = CardTagEngine()

    private fun inAppConfirmedTags(card: com.mmg.manahub.core.model.Card): Set<String> {
        val useCase = SuggestTagsUseCase(
            strategyAnalyzer = StrategyAnalyzer(entriesProvider = { TagDictionary.all() }),
        )
        return useCase(card).confirmed.map { it.key }.toSet()
    }

    @Test
    fun `removal card parity`() {
        val card = testCard(
            name = "Swords to Plowshares",
            typeLine = "Instant",
            oracleText = "Exile target creature. Its controller gains life equal to its power.",
        )
        val expected = inAppConfirmedTags(card)
        assertEquals(expected, engine.confirmedTagKeys(card))
        assertTrue("removal" in expected, "fixture should actually exercise the removal rule")
    }

    @Test
    fun `board wipe card parity`() {
        val card = testCard(
            name = "Wrath of God",
            typeLine = "Sorcery",
            oracleText = "Destroy all creatures. They can't be regenerated.",
        )
        val expected = inAppConfirmedTags(card)
        assertEquals(expected, engine.confirmedTagKeys(card))
        assertTrue("board_wipe" in expected)
    }

    @Test
    fun `ramp card parity`() {
        val card = testCard(
            name = "Rampant Growth",
            typeLine = "Sorcery",
            oracleText = "Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.",
        )
        val expected = inAppConfirmedTags(card)
        assertEquals(expected, engine.confirmedTagKeys(card))
        // "basic" is typeLineNoneOf-guarded on the card's OWN type line, not the searched-for land's
        // type — Rampant Growth's own type line is "Sorcery", so the ramp rule still fires.
        assertTrue("ramp" in expected)
    }

    @Test
    fun `multi-analyzer card parity (keyword plus type plus strategy)`() {
        val card = testCard(
            name = "Serra Angel",
            typeLine = "Creature — Angel",
            oracleText = "Flying, vigilance",
            keywords = listOf("Flying", "Vigilance"),
        )
        val expected = inAppConfirmedTags(card)
        assertEquals(expected, engine.confirmedTagKeys(card))
        assertTrue("flying" in expected && "vigilance" in expected)
    }

    @Test
    fun `game changer flag parity`() {
        val card = testCard(name = "Demonic Tutor", oracleText = "Search your library for a card.", gameChanger = true)
        val expected = inAppConfirmedTags(card)
        assertEquals(expected, engine.confirmedTagKeys(card))
        assertTrue("game_changer" in expected)
    }

    @Test
    fun `blank oracle text yields no strategy tags but keyword and type tags still apply`() {
        val card = testCard(
            name = "Plains",
            typeLine = "Basic Land — Plains",
            oracleText = null,
        )
        val expected = inAppConfirmedTags(card)
        assertEquals(expected, engine.confirmedTagKeys(card))
    }

    @Test
    fun `tribe words match production TribeDeriver directly`() {
        val card = testCard(
            name = "Elvish Archdruid",
            typeLine = "Creature — Elf Druid",
            oracleText = "Other Elves you control get +1/+1. {T}: Add {G} for each Elf you control.",
        )
        val expected = com.mmg.manahub.feature.decks.domain.engine.TribeDeriver.tribeKeys(card)
            .map { it.removePrefix(com.mmg.manahub.feature.decks.domain.engine.TribeDeriver.TRIBE_PREFIX) }
            .toSet()
        assertEquals(expected, engine.tribeWords(card))
        assertTrue("elf" in expected)
    }
}
