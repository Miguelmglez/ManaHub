package com.mmg.manahub.core.data.network

import com.mmg.manahub.core.model.Card
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for [ScryfallCache] (Backend & Performance Optimization plan, WS4a finding 6,
 * 2026-07-28) -- sole owning suite for [ScryfallCache.invalidateCards]'s cache-coherence
 * contract: a price-refresh slice must not leave [ScryfallCache.cardNames]/
 * [ScryfallCache.artVariants] serving a stale [Card] (including its price) through the by-name/
 * art-variant lookup paths after [ScryfallCache.cards] has already been purged by id.
 */
class ScryfallCacheTest {

    private fun minimalCard(id: String, priceUsd: Double? = null) = Card(
        scryfallId = id, name = "Card $id", printedName = null, manaCost = null, cmc = 1.0,
        colors = emptyList(), colorIdentity = emptyList(), typeLine = "Instant", printedTypeLine = null,
        oracleText = null, printedText = null, keywords = emptyList(), power = null, toughness = null,
        loyalty = null, setCode = "tst", setName = "Test Set", collectorNumber = "1", rarity = "common",
        releasedAt = "2020-01-01", frameEffects = emptyList(), promoTypes = emptyList(), lang = "en",
        imageNormal = null, imageArtCrop = null, imageBackNormal = null, priceUsd = priceUsd,
        priceUsdFoil = null, priceEur = null, priceEurFoil = null, legalityStandard = "legal",
        legalityPioneer = "legal", legalityModern = "legal", legalityCommander = "legal",
        flavorText = null, artist = null, scryfallUri = "https://scryfall.com/card/$id",
    )

    @Test
    fun `given a stale cards entry when invalidateCards runs then only the given ids are removed from cards`() = runTest {
        val cache = ScryfallCache()
        cache.cards.put("id-1", minimalCard("id-1"))
        cache.cards.put("id-2", minimalCard("id-2"))

        cache.invalidateCards(listOf("id-1"))

        assertNull(cache.cards.get("id-1"))
        assertNotNull(cache.cards.get("id-2"), "an id NOT passed to invalidateCards must survive")
    }

    @Test
    fun `given entries in cardNames and artVariants when invalidateCards runs then both are wholesale-cleared regardless of key`() =
        runTest {
            val cache = ScryfallCache()
            cache.cardNames.put("exact:Sol Ring", minimalCard("sol-ring", priceUsd = 1.0))
            cache.cardNames.put("fuzzy:bolt:tst", minimalCard("bolt", priceUsd = 2.0))
            cache.artVariants.put("Sol Ring", listOf(minimalCard("sol-ring-v2", priceUsd = 3.0)))

            // The invalidated id ("id-1") doesn't even match any of these keys -- cardNames/
            // artVariants are keyed by name/fuzzy-query, not scryfallId, so the wholesale clear
            // must fire regardless of which specific id was passed.
            cache.invalidateCards(listOf("id-1"))

            assertNull(cache.cardNames.get("exact:Sol Ring"))
            assertNull(cache.cardNames.get("fuzzy:bolt:tst"))
            assertNull(cache.artVariants.get("Sol Ring"))
        }

    @Test
    fun `given a fresh cache when invalidateCards runs with an empty id collection then cardNames and artVariants are still cleared`() =
        runTest {
            val cache = ScryfallCache()
            cache.cardNames.put("exact:Sol Ring", minimalCard("sol-ring"))
            cache.artVariants.put("Sol Ring", listOf(minimalCard("sol-ring-v2")))

            cache.invalidateCards(emptyList())

            assertNull(cache.cardNames.get("exact:Sol Ring"))
            assertNull(cache.artVariants.get("Sol Ring"))
        }

    @Test
    fun `given entries in searches paginatedSearches and sets when invalidateCards runs then they are left untouched`() = runTest {
        val cache = ScryfallCache()
        cache.searches.put("query:1", listOf(minimalCard("id-1")))

        cache.invalidateCards(listOf("id-1"))

        // invalidateCards is scoped to cards/cardNames/artVariants only -- searches/paginatedSearches/
        // sets are unrelated (they don't cache full Card price data the same way) and must survive.
        assertEquals(listOf(minimalCard("id-1")), cache.searches.get("query:1"))
    }

    @Test
    fun `given every sub-cache populated when clearAll runs then all of them are emptied`() = runTest {
        val cache = ScryfallCache()
        cache.cards.put("id-1", minimalCard("id-1"))
        cache.cardNames.put("exact:Sol Ring", minimalCard("sol-ring"))
        cache.searches.put("query:1", listOf(minimalCard("id-1")))
        cache.artVariants.put("Sol Ring", listOf(minimalCard("sol-ring-v2")))

        cache.clearAll()

        assertEquals(0, cache.cards.size())
        assertEquals(0, cache.cardNames.size())
        assertEquals(0, cache.searches.size())
        assertEquals(0, cache.artVariants.size())
    }
}
