package com.mmg.manahub.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Multiplatform test (commonTest) for [List.groupByCard] — Card Versions & Languages, Phase 1C.
 * Groups collection rows by (card identity, set), collapsing distinct printings/languages of the
 * same card within the same set into one [CollectionCardGroup].
 */
class CollectionCardGroupTest {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun buildCard(
        scryfallId: String = "scry-001",
        name: String = "Lightning Bolt",
        oracleId: String = "oracle-bolt",
        setCode: String = "lea",
        lang: String = "en",
    ) = Card(
        scryfallId = scryfallId,
        name = name,
        printedName = null,
        manaCost = "{R}",
        cmc = 1.0,
        colors = listOf("R"),
        colorIdentity = listOf("R"),
        typeLine = "Instant",
        printedTypeLine = null,
        oracleText = "Deals 3 damage to any target.",
        printedText = null,
        keywords = emptyList(),
        power = null,
        toughness = null,
        loyalty = null,
        setCode = setCode,
        setName = "Test Set",
        collectorNumber = "1",
        rarity = "common",
        releasedAt = "1993-08-05",
        frameEffects = emptyList(),
        promoTypes = emptyList(),
        lang = lang,
        imageNormal = null,
        imageArtCrop = null,
        imageBackNormal = null,
        priceUsd = null,
        priceUsdFoil = null,
        priceEur = null,
        priceEurFoil = null,
        legalityStandard = "legal",
        legalityPioneer = "legal",
        legalityModern = "legal",
        legalityCommander = "legal",
        flavorText = null,
        artist = null,
        scryfallUri = "https://scryfall.com/card/$setCode/1",
        oracleId = oracleId,
    )

    private fun buildUserCard(
        id: String = "uc-001",
        scryfallId: String = "scry-001",
        quantity: Int = 1,
        isFoil: Boolean = false,
        condition: String = "NM",
        language: String = "en",
        createdAt: Long = 1_000L,
    ) = UserCard(
        id = id,
        scryfallId = scryfallId,
        quantity = quantity,
        isFoil = isFoil,
        condition = condition,
        language = language,
        createdAt = createdAt,
    )

    private fun entry(
        id: String,
        card: Card,
        quantity: Int = 1,
        isFoil: Boolean = false,
        condition: String = "NM",
        language: String = "en",
        createdAt: Long = 1_000L,
    ) = UserCardWithCard(
        userCard = buildUserCard(
            id = id,
            scryfallId = card.scryfallId,
            quantity = quantity,
            isFoil = isFoil,
            condition = condition,
            language = language,
            createdAt = createdAt,
        ),
        card = card,
    )

    // ══════════════════════════════════════════════════════════════════════════
    //  Grouping by identity + set
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun sameIdentitySameSet_differentLanguagePrintings_collapseIntoOneGroup() {
        val english = buildCard(scryfallId = "scry-en", oracleId = "oracle-bolt", setCode = "lea", lang = "en")
        val spanish = buildCard(scryfallId = "scry-es", oracleId = "oracle-bolt", setCode = "lea", lang = "es")
        val entries = listOf(
            entry(id = "uc-1", card = english, language = "en"),
            entry(id = "uc-2", card = spanish, language = "es"),
        )

        val groups = entries.groupByCard()

        assertEquals(1, groups.size)
        assertEquals(2, groups[0].distinctCopies)
    }

    @Test
    fun sameIdentity_differentSets_produceSeparateGroups() {
        val alpha = buildCard(scryfallId = "scry-lea", oracleId = "oracle-bolt", setCode = "lea")
        val revised = buildCard(scryfallId = "scry-3ed", oracleId = "oracle-bolt", setCode = "3ed")
        val entries = listOf(
            entry(id = "uc-1", card = alpha),
            entry(id = "uc-2", card = revised),
        )

        val groups = entries.groupByCard()

        assertEquals(2, groups.size)
        assertTrue(groups.any { it.card.setCode == "lea" })
        assertTrue(groups.any { it.card.setCode == "3ed" })
    }

    @Test
    fun blankOracleId_fallsBackToNameMatch_forSameSet() {
        val printingOne = buildCard(scryfallId = "scry-1", oracleId = "", name = "Old Bolt", setCode = "lea")
        val printingTwo = buildCard(scryfallId = "scry-2", oracleId = "", name = "Old Bolt", setCode = "lea")
        val entries = listOf(
            entry(id = "uc-1", card = printingOne),
            entry(id = "uc-2", card = printingTwo),
        )

        val groups = entries.groupByCard()

        assertEquals(1, groups.size)
        assertEquals("lea|Old Bolt", groups[0].groupKey)
    }

    @Test
    fun blankOracleId_differentNames_areNotGrouped() {
        val bolt = buildCard(scryfallId = "scry-1", oracleId = "", name = "Lightning Bolt", setCode = "lea")
        val counterspell = buildCard(scryfallId = "scry-2", oracleId = "", name = "Counterspell", setCode = "lea")
        val entries = listOf(
            entry(id = "uc-1", card = bolt),
            entry(id = "uc-2", card = counterspell),
        )

        val groups = entries.groupByCard()

        assertEquals(2, groups.size)
        assertTrue(groups.any { it.card.name == "Lightning Bolt" })
        assertTrue(groups.any { it.card.name == "Counterspell" })
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Representative selection + groupKey
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun representative_isTheFirstCopyAddedByCreatedAtAscending() {
        val firstPrint = buildCard(scryfallId = "scry-first", oracleId = "oracle-bolt", setCode = "lea")
        val secondPrint = buildCard(scryfallId = "scry-second", oracleId = "oracle-bolt", setCode = "lea")
        val entries = listOf(
            // Added out of order: the LATER copy is listed first in the input list.
            entry(id = "uc-later", card = secondPrint, createdAt = 5_000L),
            entry(id = "uc-earlier", card = firstPrint, createdAt = 1_000L),
        )

        val groups = entries.groupByCard()

        assertEquals(1, groups.size)
        // Representative must be the copy with the SMALLEST createdAt, regardless of list order.
        assertEquals("scry-first", groups[0].card.scryfallId)
        // latestAddedAt still tracks the most recent addition for group sort order.
        assertEquals(5_000L, groups[0].latestAddedAt)
    }

    @Test
    fun groupKey_isSetCodePipeIdentity() {
        val card = buildCard(scryfallId = "scry-1", oracleId = "oracle-bolt", setCode = "lea")
        val groups = listOf(entry(id = "uc-1", card = card)).groupByCard()

        assertEquals("lea|oracle-bolt", groups[0].groupKey)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Aggregation: totalQuantity / hasFoil / distinctCopies / latestAddedAt
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun totalQuantity_sumsAcrossAllCopiesInTheGroup() {
        val card = buildCard(scryfallId = "scry-1", oracleId = "oracle-bolt", setCode = "lea")
        val entries = listOf(
            entry(id = "uc-1", card = card, quantity = 2),
            entry(id = "uc-2", card = card, quantity = 3),
        )

        val groups = entries.groupByCard()

        assertEquals(5, groups[0].totalQuantity)
    }

    @Test
    fun hasFoil_isTrueWhenAnyCopyInTheGroupIsFoil() {
        val card = buildCard(scryfallId = "scry-1", oracleId = "oracle-bolt", setCode = "lea")
        val entriesWithFoil = listOf(
            entry(id = "uc-1", card = card, isFoil = false),
            entry(id = "uc-2", card = card, isFoil = true),
        )
        val entriesWithoutFoil = listOf(
            entry(id = "uc-3", card = card, isFoil = false),
        )

        assertTrue(entriesWithFoil.groupByCard()[0].hasFoil)
        assertFalse(entriesWithoutFoil.groupByCard()[0].hasFoil)
    }

    @Test
    fun distinctCopies_countsDistinctScryfallIdFoilConditionLanguageTuples() {
        val card = buildCard(scryfallId = "scry-1", oracleId = "oracle-bolt", setCode = "lea")
        val entries = listOf(
            // Two rows sharing the EXACT SAME (scryfallId, isFoil, condition, language) tuple —
            // must count as ONE distinct copy, not two.
            entry(id = "uc-1", card = card, isFoil = false, condition = "NM", language = "en"),
            entry(id = "uc-2", card = card, isFoil = false, condition = "NM", language = "en"),
            // A third row with a different condition IS a distinct tuple.
            entry(id = "uc-3", card = card, isFoil = false, condition = "LP", language = "en"),
        )

        val groups = entries.groupByCard()

        assertEquals(1, groups.size)
        assertEquals(2, groups[0].distinctCopies)
        // totalQuantity still counts every row (including the duplicate tuple).
        assertEquals(3, groups[0].totalQuantity)
    }

    @Test
    fun latestAddedAt_isTheMaxCreatedAtAcrossCopies() {
        val card = buildCard(scryfallId = "scry-1", oracleId = "oracle-bolt", setCode = "lea")
        val entries = listOf(
            entry(id = "uc-1", card = card, createdAt = 1_000L),
            entry(id = "uc-2", card = card, createdAt = 9_000L),
            entry(id = "uc-3", card = card, createdAt = 4_000L),
        )

        val groups = entries.groupByCard()

        assertEquals(9_000L, groups[0].latestAddedAt)
    }

    @Test
    fun emptyList_producesNoGroups() {
        assertTrue(emptyList<UserCardWithCard>().groupByCard().isEmpty())
    }
}
