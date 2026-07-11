package com.mmg.manahub.core.data.remote.mapper

import com.mmg.manahub.core.data.remote.dto.CardDto
import com.mmg.manahub.core.data.remote.dto.LegalitiesDto
import com.mmg.manahub.core.data.remote.dto.PricesDto
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Coverage for [CardDto.toDomain]'s D14 `produced_mana` → compact WUBRG-string conversion
 * (Deck Doctor Community/Archetype plan, Phase 0.3). `commonMain`/`commonTest` — no Android
 * dependency, runs on every KMP target.
 */
class CardDtoMapperTest {

    private fun minimalDto(producedMana: List<String>? = null): CardDto = CardDto(
        id = "test-id",
        name = "Test Card",
        lang = "en",
        colorIdentity = emptyList(),
        producedMana = producedMana,
        keywords = emptyList(),
        setCode = "TST",
        setName = "Test Set",
        collectorNumber = "1",
        rarity = "common",
        releasedAt = "2024-01-01",
        prices = PricesDto(),
        legalities = LegalitiesDto(
            standard = "legal", pioneer = "legal", modern = "legal",
            legacy = "legal", vintage = "legal", commander = "legal", pauper = "legal",
        ),
        scryfallUri = "https://scryfall.com/card/tst/1",
    )

    @Test
    fun `null produced_mana maps to an empty string`() {
        val card = minimalDto(producedMana = null).toDomain()
        assertEquals("", card.producedMana)
    }

    @Test
    fun `empty produced_mana list maps to an empty string`() {
        val card = minimalDto(producedMana = emptyList()).toDomain()
        assertEquals("", card.producedMana)
    }

    @Test
    fun `single color produced_mana maps to that letter`() {
        val card = minimalDto(producedMana = listOf("G")).toDomain()
        assertEquals("G", card.producedMana)
    }

    @Test
    fun `multi color produced_mana is canonically ordered WUBRG regardless of input order`() {
        // Arcane Signet-style rainbow fixer: Scryfall may return colours in any order.
        val card = minimalDto(producedMana = listOf("R", "W", "B", "U", "G")).toDomain()
        assertEquals("WUBRG", card.producedMana)
    }

    @Test
    fun `duplicate letters in produced_mana are deduplicated`() {
        val card = minimalDto(producedMana = listOf("U", "U", "B")).toDomain()
        assertEquals("UB", card.producedMana)
    }

    @Test
    fun `lowercase letters in produced_mana are normalized to uppercase output`() {
        val card = minimalDto(producedMana = listOf("w", "u")).toDomain()
        assertEquals("WU", card.producedMana)
    }

    @Test
    fun `non-WUBRG entries like C for colorless are dropped`() {
        // Scryfall uses "C" for colourless production (e.g. Wastes) — not a WUBRG letter,
        // so it is intentionally dropped from the compact subset string.
        val card = minimalDto(producedMana = listOf("C")).toDomain()
        assertEquals("", card.producedMana)
    }
}
