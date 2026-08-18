package com.mmg.manahub.feature.communitydecks.presentation

import com.mmg.manahub.core.model.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Direct unit coverage for [CommunityAdvancedFilters.toSearchFilters]'s Commander-only format
 * gating (edge-case audit, 2026-08-18).
 *
 * Prior to this test class, every assertion on `toSearchFilters` went through
 * [CommunityDecksSearchViewModel], which — before the [CommunityDecksSearchViewModel.onTrendingCommanderClick]
 * fix in the same pass — always happened to keep `formats` and `commander`/`edhBracket` in sync.
 * That meant this defense-in-depth gating (added so a stale Commander-only selection can never
 * leak into a non-Commander request) had never been independently proven against a deliberately
 * MISMATCHED [CommunityAdvancedFilters] state.
 */
class CommunityAdvancedFiltersTest {

    private fun fakeCard(name: String) = Card(
        scryfallId = name, name = name, printedName = null,
        manaCost = "{2}{U}", cmc = 3.0, colors = listOf("U"), colorIdentity = listOf("U"),
        typeLine = "Creature", printedTypeLine = null, oracleText = null, printedText = null,
        keywords = emptyList(), power = "1", toughness = "1", loyalty = null,
        setCode = "TST", setName = "Test Set", collectorNumber = "1", rarity = "common",
        releasedAt = "2025-01-01", frameEffects = emptyList(), promoTypes = emptyList(),
        lang = "en", imageNormal = null, imageArtCrop = null, imageBackNormal = null,
        priceUsd = null, priceUsdFoil = null, priceEur = null, priceEurFoil = null,
        legalityStandard = "legal", legalityPioneer = "legal", legalityModern = "legal",
        legalityCommander = "legal", flavorText = null, artist = null,
        scryfallUri = "https://scryfall.com/$name",
    )

    @Test
    fun `toSearchFilters omits commanderName and edhBracket when formats is not Commander even if both are populated`() {
        val mismatched = CommunityAdvancedFilters(
            formats = CommunityDeckFormatFilter.STANDARD,
            commander = fakeCard("Atraxa, Praetors' Voice"),
            edhBracket = 4,
        )

        val result = mismatched.toSearchFilters(deckName = null, orderBy = null, page = 1, pageSize = 20)

        assertNull(result.commanderName)
        assertNull(result.edhBracket)
    }

    @Test
    fun `toSearchFilters includes commanderName and edhBracket when formats is Commander`() {
        val filters = CommunityAdvancedFilters(
            formats = CommunityDeckFormatFilter.COMMANDER,
            commander = fakeCard("Atraxa, Praetors' Voice"),
            edhBracket = 4,
        )

        val result = filters.toSearchFilters(deckName = null, orderBy = null, page = 1, pageSize = 20)

        assertEquals("Atraxa, Praetors' Voice", result.commanderName)
        assertEquals(4, result.edhBracket)
    }
}
