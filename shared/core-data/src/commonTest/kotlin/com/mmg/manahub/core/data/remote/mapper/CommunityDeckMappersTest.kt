package com.mmg.manahub.core.data.remote.mapper

import com.mmg.manahub.core.data.remote.dto.ArchidektCardDto
import com.mmg.manahub.core.data.remote.dto.ArchidektCardEntryDto
import com.mmg.manahub.core.data.remote.dto.ArchidektDeckDetailDto
import com.mmg.manahub.core.data.remote.dto.ArchidektDeckSummaryDto
import com.mmg.manahub.core.data.remote.dto.ArchidektEditionDto
import com.mmg.manahub.core.data.remote.dto.ArchidektOracleCardDto
import com.mmg.manahub.core.data.remote.dto.ArchidektOwnerDto
import com.mmg.manahub.core.data.remote.dto.ArchidektPricesDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
