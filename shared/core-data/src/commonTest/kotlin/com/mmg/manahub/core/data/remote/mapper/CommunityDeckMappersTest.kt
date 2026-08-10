package com.mmg.manahub.core.data.remote.mapper

import com.mmg.manahub.core.data.remote.dto.ArchidektCardDto
import com.mmg.manahub.core.data.remote.dto.ArchidektCardEntryDto
import com.mmg.manahub.core.data.remote.dto.ArchidektCategoryDto
import com.mmg.manahub.core.data.remote.dto.ArchidektDeckDetailDto
import com.mmg.manahub.core.data.remote.dto.ArchidektDeckSummaryDto
import com.mmg.manahub.core.data.remote.dto.ArchidektEditionDto
import com.mmg.manahub.core.data.remote.dto.ArchidektOracleCardDto
import com.mmg.manahub.core.data.remote.dto.ArchidektOwnerDto
import com.mmg.manahub.core.data.remote.dto.ArchidektPricesDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage for the rich-card-display fields added to the Archidekt DTO -> domain mapping
 * (2026-07-13): printing uid / edition / rarity / prices on [ArchidektDeckDetailDto], and
 * `featured`/`customFeatured` on [ArchidektDeckSummaryDto]. `commonMain`/`commonTest` — no
 * Android dependency, runs on every KMP target.
 */
class CommunityDeckMappersTest {

    private fun buildOracle(
        superTypes: List<String> = emptyList(),
        types: List<String> = listOf("Creature"),
        subTypes: List<String> = emptyList(),
    ) = ArchidektOracleCardDto(
        name = "Sanctum Weaver",
        uid = "oracle-uid",
        manaCost = "{1}{G}",
        cmc = 2.0,
        superTypes = superTypes,
        types = types,
        subTypes = subTypes,
    )

    private fun buildDetailDto(card: ArchidektCardDto) = ArchidektDeckDetailDto(
        id = 1,
        name = "Test Deck",
        cards = listOf(ArchidektCardEntryDto(quantity = 1, categories = listOf("Mainboard"), card = card)),
    )

    // ── Printing uid, edition, rarity, prices ───────────────────────────────

    @Test
    fun cardMapping_carriesPrintingUidEditionRarityAndPrices() {
        val dto = buildDetailDto(
            ArchidektCardDto(
                oracleCard = buildOracle(),
                uid = "4d42e22d-f60e-40c5-b069-5e1708f3bebc",
                edition = ArchidektEditionDto(editioncode = "mh2", editionname = "Modern Horizons 2"),
                collectorNumber = "142",
                rarity = "rare",
                prices = ArchidektPricesDto(tcg = 1.25, cm = 1.10),
            ),
        )

        val card = dto.toDomain().cards.single()

        assertEquals("4d42e22d-f60e-40c5-b069-5e1708f3bebc", card.scryfallId)
        assertEquals("oracle-uid", card.oracleId)
        assertEquals("mh2", card.setCode)
        assertEquals("Modern Horizons 2", card.setName)
        assertEquals("rare", card.rarity)
        assertEquals(1.25, card.priceUsd)
        assertEquals(1.10, card.priceEur)
        assertEquals(2.0, card.cmc)
        assertEquals("{1}{G}", card.manaCost)
    }

    @Test
    fun cardMapping_blankUidAndMissingEditionAndPricesFallBackToDefaults() {
        val dto = buildDetailDto(
            ArchidektCardDto(oracleCard = buildOracle(), uid = "", edition = null, prices = null),
        )

        val card = dto.toDomain().cards.single()

        assertEquals("", card.scryfallId)
        assertNull(card.imageUrl)
        assertEquals("", card.setCode)
        assertEquals("", card.setName)
        assertNull(card.priceUsd)
        assertNull(card.priceEur)
    }

    // ── Excluded-category recategorization (bug fix, 2026-07-22; refined 2026-07-22) ────────
    // Archidekt category `includedInDeck = false` (Maybeboard, or ANY arbitrary custom name like
    // "Bench"/"Cut" — Archidekt lets users name categories freely) means the entry does not count
    // toward the deck, even though it still appears in the flat `cards` array. Such entries are
    // KEPT (never dropped) and recategorized as sideboard-like via `excludedFromDeckCount` /
    // `isSideboard`, so they still surface (and can be moved to the mainboard) instead of
    // silently vanishing. The mapper never matches a category NAME directly — only the
    // `includedInDeck` flag.

    @Test
    fun cardWhoseOnlyCategoryIsExcludedIsKeptAsSideboard() {
        val dto = ArchidektDeckDetailDto(
            id = 1,
            name = "Test Deck",
            categories = listOf(ArchidektCategoryDto(name = "Maybeboard", includedInDeck = false)),
            cards = listOf(
                ArchidektCardEntryDto(
                    quantity = 1,
                    categories = listOf("Maybeboard"),
                    card = ArchidektCardDto(oracleCard = buildOracle(), uid = "printing-1"),
                ),
            ),
        )

        val card = dto.toDomain().cards.single()
        assertTrue(card.excludedFromDeckCount)
        assertTrue(card.isSideboard)
    }

    @Test
    fun cardWithArbitraryCustomExcludedCategoryNameIsKeptAsSideboard() {
        // Archidekt lets a user name a category anything (verified live: "The Cranberries",
        // "Tavs", etc.) and flag it `includedInDeck = false` — the mapper must key off the flag,
        // never a name allowlist, so a category like "Bench" resolves the same way as Maybeboard.
        val dto = ArchidektDeckDetailDto(
            id = 1,
            name = "Test Deck",
            categories = listOf(ArchidektCategoryDto(name = "Bench", includedInDeck = false)),
            cards = listOf(
                ArchidektCardEntryDto(
                    quantity = 1,
                    categories = listOf("Bench"),
                    card = ArchidektCardDto(oracleCard = buildOracle(), uid = "printing-1"),
                ),
            ),
        )

        val card = dto.toDomain().cards.single()
        assertTrue(card.excludedFromDeckCount)
        assertTrue(card.isSideboard)
    }

    // Corrected 2026-07-22 (same day as the fix above): this used to assert the OLD, buggy
    // ALL-of behavior (an entry co-tagged with a real "Mainboard" category was NOT excluded).
    // Verified live against archidekt.com/decks/1585124 ("Baby Lasagna"): Archidekt's
    // auto-categorization stamps a second functional/type category onto most cards, so 15 of its
    // 16 Maybeboard entries also carried an ordinary category like "Creature"/"Land"/"Recursion"
    // (e.g. "Sylvan Safekeeper" -> ["Maybeboard", "Endgame Plans", "Creature"]); only one entry
    // ("Defiling Daemogoth") had Maybeboard as its SOLE category. An ALL-of match therefore almost
    // never fires on real decks — the correct rule is ANY-of.
    @Test
    fun cardWithOneExcludedAndOneOrdinaryCategoryIsStillSideboard() {
        val dto = ArchidektDeckDetailDto(
            id = 1,
            name = "Test Deck",
            categories = listOf(
                ArchidektCategoryDto(name = "Maybeboard", includedInDeck = false),
                ArchidektCategoryDto(name = "Creature", includedInDeck = true),
            ),
            cards = listOf(
                ArchidektCardEntryDto(
                    quantity = 1,
                    categories = listOf("Maybeboard", "Creature"),
                    card = ArchidektCardDto(oracleCard = buildOracle(), uid = "printing-1"),
                ),
            ),
        )

        val card = dto.toDomain().cards.single()
        assertTrue(card.excludedFromDeckCount)
        assertTrue(card.isSideboard)
    }

    @Test
    fun cardWithOnlyAnOrdinaryCategoryIsNotSideboard() {
        // A plain in-deck card that never carries the excluded category at all (e.g. a real
        // mainboard "Creature") must stay in-deck under the ANY-of rule -- it must not
        // over-exclude ordinary cards just because SOME other category on the deck is excluded.
        val dto = ArchidektDeckDetailDto(
            id = 1,
            name = "Test Deck",
            categories = listOf(
                ArchidektCategoryDto(name = "Maybeboard", includedInDeck = false),
                ArchidektCategoryDto(name = "Creature", includedInDeck = true),
            ),
            cards = listOf(
                ArchidektCardEntryDto(
                    quantity = 1,
                    categories = listOf("Creature"),
                    card = ArchidektCardDto(oracleCard = buildOracle(), uid = "printing-1"),
                ),
            ),
        )

        val card = dto.toDomain().cards.single()
        assertFalse(card.excludedFromDeckCount)
        assertFalse(card.isSideboard)
    }

    @Test
    fun cardWithNoCategoriesIsNotSideboard() {
        val dto = ArchidektDeckDetailDto(
            id = 1,
            name = "Test Deck",
            categories = listOf(ArchidektCategoryDto(name = "Maybeboard", includedInDeck = false)),
            cards = listOf(
                ArchidektCardEntryDto(
                    quantity = 1,
                    categories = null,
                    card = ArchidektCardDto(oracleCard = buildOracle(), uid = "printing-1"),
                ),
            ),
        )

        val card = dto.toDomain().cards.single()
        assertFalse(card.excludedFromDeckCount)
        assertFalse(card.isSideboard)
    }

    @Test
    fun cardWithNoCategoryDtosDefinedAtAllIsKept() {
        // No `categories` block on the deck detail at all -> excludedCategoryNames is empty ->
        // every resolvable entry survives, matching pre-fix behavior for decks with no
        // Maybeboard/cut category.
        val dto = buildDetailDto(ArchidektCardDto(oracleCard = buildOracle(), uid = "printing-1"))

        assertEquals(1, dto.toDomain().cards.size)
    }

    @Test
    fun genuineSideboardCategoryIsStillSideboardRegardlessOfExcludedFlag() {
        // A genuine "Sideboard" category (includedInDeck = true, since Archidekt still counts
        // sideboard cards as part of the overall deck object) must keep working exactly as before.
        val dto = ArchidektDeckDetailDto(
            id = 1,
            name = "Test Deck",
            categories = listOf(ArchidektCategoryDto(name = "Sideboard", includedInDeck = true)),
            cards = listOf(
                ArchidektCardEntryDto(
                    quantity = 1,
                    categories = listOf("Sideboard"),
                    card = ArchidektCardDto(oracleCard = buildOracle(), uid = "printing-1"),
                ),
            ),
        )

        val card = dto.toDomain().cards.single()
        assertFalse(card.excludedFromDeckCount)
        assertTrue(card.isSideboard)
    }

    // ── typeLine composition ────────────────────────────────────────────────

    @Test
    fun typeLine_withSubtypesUsesEmDashSeparator() {
        val dto = buildDetailDto(
            ArchidektCardDto(
                oracleCard = buildOracle(
                    superTypes = listOf("Legendary"),
                    types = listOf("Creature"),
                    subTypes = listOf("Elf", "Druid"),
                ),
            ),
        )

        assertEquals("Legendary Creature — Elf Druid", dto.toDomain().cards.single().typeLine)
    }

    @Test
    fun typeLine_withoutSubtypesOmitsEmDash() {
        val dto = buildDetailDto(
            ArchidektCardDto(oracleCard = buildOracle(types = listOf("Instant"), subTypes = emptyList())),
        )

        assertEquals("Instant", dto.toDomain().cards.single().typeLine)
    }

    // ── featured / customFeatured preference ────────────────────────────────

    private fun buildSummaryDto(featured: String = "", customFeatured: String = "") = ArchidektDeckSummaryDto(
        id = 1,
        name = "Test Deck",
        owner = ArchidektOwnerDto(id = 1, username = "user", avatar = ""),
        featured = featured,
        customFeatured = customFeatured,
    )

    @Test
    fun summaryMapping_carriesDeckFormatId() {
        val dto = buildSummaryDto().copy(deckFormat = 3) // Commander

        val summary = dto.toDomain()

        assertEquals(3, summary.deckFormatId)
        assertEquals("commander", summary.format)
    }

    @Test
    fun featuredImageUrl_prefersCustomFeaturedWhenNonBlank() {
        val dto = buildSummaryDto(
            featured = "https://storage.googleapis.com/archidekt-card-images/auto/uid_art_crop.jpg",
            customFeatured = "https://storage.googleapis.com/archidekt-card-images/custom/uid_art_crop.jpg",
        )

        assertEquals(
            "https://storage.googleapis.com/archidekt-card-images/custom/uid_art_crop.jpg",
            dto.toDomain().featuredImageUrl,
        )
    }

    @Test
    fun featuredImageUrl_fallsBackToFeaturedWhenCustomFeaturedIsBlank() {
        val dto = buildSummaryDto(
            featured = "https://storage.googleapis.com/archidekt-card-images/auto/uid_art_crop.jpg",
            customFeatured = "",
        )

        assertEquals(
            "https://storage.googleapis.com/archidekt-card-images/auto/uid_art_crop.jpg",
            dto.toDomain().featuredImageUrl,
        )
    }

    @Test
    fun featuredImageUrl_isNullWhenBothAreBlank() {
        assertNull(buildSummaryDto().toDomain().featuredImageUrl)
    }
}
