package com.mmg.manahub.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Multiplatform test (commonTest) for [CommunityDeckCard]'s computed [CommunityDeckCard.imageUrl] —
 * the Scryfall image CDN URL built directly from the Archidekt-supplied printing uid (no Scryfall
 * API call involved; verified live 2026-07-13).
 */
class CommunityDeckCardTest {

    private fun buildCard(scryfallId: String) = CommunityDeckCard(
        name = "Sanctum Weaver",
        quantity = 1,
        categories = emptyList(),
        oracleId = "oracle-uid",
        scryfallId = scryfallId,
    )

    @Test
    fun imageUrl_buildsScryfallCdnUrlFromPrintingUid() {
        val card = buildCard("4d42e22d-f60e-40c5-b069-5e1708f3bebc")

        assertEquals(
            "https://cards.scryfall.io/normal/front/4/d/4d42e22d-f60e-40c5-b069-5e1708f3bebc.jpg",
            card.imageUrl,
        )
    }

    @Test
    fun imageUrl_isNullWhenScryfallIdIsBlank() {
        assertNull(buildCard("").imageUrl)
    }

    @Test
    fun isCommander_and_isSideboard_matchCategoriesCaseInsensitively() {
        val commander = buildCard("id").copy(categories = listOf("commander"))
        val sideboard = buildCard("id").copy(categories = listOf("Sideboard"))
        val mainboard = buildCard("id").copy(categories = listOf("Mainboard"))

        assertTrue(commander.isCommander)
        assertTrue(sideboard.isSideboard)
        assertFalse(mainboard.isCommander)
        assertFalse(mainboard.isSideboard)
    }
}
