package com.mmg.manahub.core.data.remote.collection

import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the mapper extension functions in [UserCardCollectionDto].
 *
 * Pure mapping — no I/O, no DI — tested with plain JUnit assertions.
 *
 * Covers:
 *  - [UserCardCollectionDto.toEntity]: all fields mapped correctly
 *  - [UserCardCollectionEntity.toDto]: all fields mapped correctly, null userId becomes ""
 *  - Roundtrip parity: entity → dto → entity (business fields survive intact)
 */
class UserCardCollectionDtoTest {

    private val ENTRY_ID   = "550e8400-e29b-41d4-a716-446655440001"
    private val USER_ID    = "550e8400-e29b-41d4-a716-446655440002"
    private val SCRYFALL_ID = "card-scryfall-id-abc"
    private val NOW        = 1_700_000_000_000L
    private val CREATED    = 1_600_000_000_000L

    private fun aFullDto(isDeleted: Boolean = false) = UserCardCollectionDto(
        id              = ENTRY_ID,
        userId          = USER_ID,
        scryfallId      = SCRYFALL_ID,
        quantity        = 3,
        isFoil          = true,
        condition       = "LP",
        language        = "ja",
        isAlternativeArt = true,
        isForTrade      = true,
        isInWishlist    = false,
        isDeleted       = isDeleted,
        updatedAt       = NOW,
        createdAt       = CREATED,
    )

    private fun aFullEntity(userId: String? = USER_ID) = UserCardCollectionEntity(
        id              = ENTRY_ID,
        userId          = userId,
        scryfallId      = SCRYFALL_ID,
        quantity        = 3,
        isFoil          = true,
        condition       = "LP",
        language        = "ja",
        isAlternativeArt = true,
        isForTrade      = true,
        isInWishlist    = false,
        isDeleted       = false,
        updatedAt       = NOW,
        createdAt       = CREATED,
    )

    // ── GROUP 1 — UserCardCollectionDto.toEntity ──────────────────────────────

    @Test
    fun `toEntity preserves id`() {
        assertEquals(ENTRY_ID, aFullDto().toEntity().id)
    }

    @Test
    fun `toEntity preserves userId`() {
        assertEquals(USER_ID, aFullDto().toEntity().userId)
    }

    @Test
    fun `toEntity preserves scryfallId`() {
        assertEquals(SCRYFALL_ID, aFullDto().toEntity().scryfallId)
    }

    @Test
    fun `toEntity preserves quantity`() {
        assertEquals(3, aFullDto().toEntity().quantity)
    }

    @Test
    fun `toEntity preserves isFoil`() {
        assertTrue(aFullDto().toEntity().isFoil)
    }

    @Test
    fun `toEntity preserves condition`() {
        assertEquals("LP", aFullDto().toEntity().condition)
    }

    @Test
    fun `toEntity preserves language`() {
        assertEquals("ja", aFullDto().toEntity().language)
    }

    @Test
    fun `toEntity preserves isAlternativeArt`() {
        assertTrue(aFullDto().toEntity().isAlternativeArt)
    }

    @Test
    fun `toEntity preserves isForTrade`() {
        assertTrue(aFullDto().toEntity().isForTrade)
    }

    @Test
    fun `toEntity preserves isInWishlist`() {
        assertFalse(aFullDto().toEntity().isInWishlist)
    }

    @Test
    fun `toEntity preserves isDeleted true`() {
        assertTrue(aFullDto(isDeleted = true).toEntity().isDeleted)
    }

    @Test
    fun `toEntity preserves updatedAt`() {
        assertEquals(NOW, aFullDto().toEntity().updatedAt)
    }

    @Test
    fun `toEntity preserves createdAt`() {
        assertEquals(CREATED, aFullDto().toEntity().createdAt)
    }

    // ── GROUP 2 — UserCardCollectionEntity.toDto ──────────────────────────────

    @Test
    fun `toDto preserves id`() {
        assertEquals(ENTRY_ID, aFullEntity().toDto().id)
    }

    @Test
    fun `toDto preserves scryfallId`() {
        assertEquals(SCRYFALL_ID, aFullEntity().toDto().scryfallId)
    }

    @Test
    fun `toDto preserves quantity`() {
        assertEquals(3, aFullEntity().toDto().quantity)
    }

    @Test
    fun `toDto preserves isFoil`() {
        assertTrue(aFullEntity().toDto().isFoil)
    }

    @Test
    fun `toDto preserves condition`() {
        assertEquals("LP", aFullEntity().toDto().condition)
    }

    @Test
    fun `toDto preserves language`() {
        assertEquals("ja", aFullEntity().toDto().language)
    }

    @Test
    fun `toDto null userId becomes empty string`() {
        assertEquals("", aFullEntity(userId = null).toDto().userId)
    }

    @Test
    fun `toDto preserves updatedAt`() {
        assertEquals(NOW, aFullEntity().toDto().updatedAt)
    }

    @Test
    fun `toDto preserves createdAt`() {
        assertEquals(CREATED, aFullEntity().toDto().createdAt)
    }

    // ── GROUP 3 — Roundtrip: entity → dto → entity ────────────────────────────

    @Test
    fun `roundtrip entity to dto to entity preserves all business fields`() {
        val original = aFullEntity()
        val roundTripped = original.toDto().toEntity()

        assertEquals(original.id,              roundTripped.id)
        assertEquals(original.scryfallId,      roundTripped.scryfallId)
        assertEquals(original.quantity,         roundTripped.quantity)
        assertEquals(original.isFoil,          roundTripped.isFoil)
        assertEquals(original.condition,        roundTripped.condition)
        assertEquals(original.language,         roundTripped.language)
        assertEquals(original.isAlternativeArt, roundTripped.isAlternativeArt)
        assertEquals(original.isForTrade,       roundTripped.isForTrade)
        assertEquals(original.isInWishlist,     roundTripped.isInWishlist)
        assertEquals(original.isDeleted,        roundTripped.isDeleted)
        assertEquals(original.updatedAt,        roundTripped.updatedAt)
        assertEquals(original.createdAt,        roundTripped.createdAt)
    }
}
