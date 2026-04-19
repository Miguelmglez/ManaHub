package com.mmg.manahub.core.data.remote.collection

import com.mmg.manahub.core.data.local.entity.SyncStatus
import com.mmg.manahub.core.data.local.entity.UserCardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the mapper extension functions in [UserCardCollectionDto].
 *
 * These functions are pure (no I/O, no DI), so they are tested with plain JUnit assertions.
 *
 * Covers:
 *  - [UserCardEntity.toUpsertParams]: all fields mapped to the correct param names
 *  - [UserCardCollectionDto.toEntity]: syncStatus = SYNCED, remoteId = dto.id
 *  - [UserCardCollectionDto.toEntity] with id = null: remoteId = null
 *  - Roundtrip: entity → params fields match → dto → entity field parity
 */
class UserCardCollectionDtoTest {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val USER_ID   = "user-uuid-001"
    private val REMOTE_ID = "remote-uuid-abc"

    private fun aFullEntity(remoteId: String? = REMOTE_ID) = UserCardEntity(
        id               = 99L,
        scryfallId       = "card-scryfall-id",
        quantity         = 3,
        isFoil           = true,
        isAlternativeArt = true,
        condition        = "LP",
        language         = "ja",
        isForTrade       = true,
        isInWishlist     = false,
        minTradeValue    = 12.50,
        notes            = "signed by artist",
        acquiredAt       = 1_700_000_000_000L,
        addedAt          = 1_600_000_000_000L,
        syncStatus       = SyncStatus.PENDING_UPLOAD,
        remoteId         = remoteId,
    )

    private fun aFullDto(id: String? = REMOTE_ID) = UserCardCollectionDto(
        id               = id,
        userId           = USER_ID,
        scryfallId       = "card-scryfall-id",
        quantity         = 3,
        isFoil           = true,
        isAlternativeArt = true,
        condition        = "LP",
        language         = "ja",
        isForTrade       = true,
        isInWishlist     = false,
        minTradeValue    = 12.50,
        notes            = "signed by artist",
        acquiredAt       = 1_700_000_000_000L,
        addedAt          = 1_600_000_000_000L,
        updatedAt        = null,
        isDeleted        = false,
    )

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — UserCardEntity.toUpsertParams
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a full entity when toUpsertParams then pUserId is set to the provided userId`() {
        val params = aFullEntity().toUpsertParams(USER_ID)
        assertEquals(USER_ID, params.pUserId)
    }

    @Test
    fun `given a full entity when toUpsertParams then pScryfallId matches entity scryfallId`() {
        val params = aFullEntity().toUpsertParams(USER_ID)
        assertEquals("card-scryfall-id", params.pScryfallId)
    }

    @Test
    fun `given a full entity when toUpsertParams then pQuantity matches entity quantity`() {
        val params = aFullEntity().toUpsertParams(USER_ID)
        assertEquals(3, params.pQuantity)
    }

    @Test
    fun `given a foil entity when toUpsertParams then pIsFoil is true`() {
        val params = aFullEntity().toUpsertParams(USER_ID)
        assertEquals(true, params.pIsFoil)
    }

    @Test
    fun `given an entity with alternative art when toUpsertParams then pIsAlternativeArt is true`() {
        val params = aFullEntity().toUpsertParams(USER_ID)
        assertEquals(true, params.pIsAlternativeArt)
    }

    @Test
    fun `given a full entity when toUpsertParams then pCondition matches entity condition`() {
        val params = aFullEntity().toUpsertParams(USER_ID)
        assertEquals("LP", params.pCondition)
    }

    @Test
    fun `given a full entity when toUpsertParams then pLanguage matches entity language`() {
        val params = aFullEntity().toUpsertParams(USER_ID)
        assertEquals("ja", params.pLanguage)
    }

    @Test
    fun `given a for-trade entity when toUpsertParams then pIsForTrade is true`() {
        val params = aFullEntity().toUpsertParams(USER_ID)
        assertEquals(true, params.pIsForTrade)
    }

    @Test
    fun `given a non-wishlist entity when toUpsertParams then pIsInWishlist is false`() {
        val params = aFullEntity().toUpsertParams(USER_ID)
        assertEquals(false, params.pIsInWishlist)
    }

    @Test
    fun `given a full entity when toUpsertParams then pMinTradeValue matches entity minTradeValue`() {
        val params = aFullEntity().toUpsertParams(USER_ID)
        assertEquals(12.50, params.pMinTradeValue!!, 0.001)
    }

    @Test
    fun `given a full entity when toUpsertParams then pNotes matches entity notes`() {
        val params = aFullEntity().toUpsertParams(USER_ID)
        assertEquals("signed by artist", params.pNotes)
    }

    @Test
    fun `given a full entity when toUpsertParams then pAcquiredAt matches entity acquiredAt`() {
        val params = aFullEntity().toUpsertParams(USER_ID)
        assertEquals(1_700_000_000_000L, params.pAcquiredAt)
    }

    @Test
    fun `given a full entity when toUpsertParams then pAddedAt matches entity addedAt`() {
        val params = aFullEntity().toUpsertParams(USER_ID)
        assertEquals(1_600_000_000_000L, params.pAddedAt)
    }

    @Test
    fun `given entity with remoteId when toUpsertParams then pRemoteId carries the remoteId`() {
        val params = aFullEntity(remoteId = REMOTE_ID).toUpsertParams(USER_ID)
        assertEquals(REMOTE_ID, params.pRemoteId)
    }

    @Test
    fun `given entity without remoteId when toUpsertParams then pRemoteId is null`() {
        val params = aFullEntity(remoteId = null).toUpsertParams(USER_ID)
        assertNull(params.pRemoteId)
    }

    @Test
    fun `given entity with null optional fields when toUpsertParams then nullable params are null`() {
        val entity = UserCardEntity(
            scryfallId = "id-001",
            quantity   = 1,
            addedAt    = 0L,
            // all nullable fields left as default null
        )
        val params = entity.toUpsertParams(USER_ID)
        assertNull(params.pMinTradeValue)
        assertNull(params.pNotes)
        assertNull(params.pAcquiredAt)
        assertNull(params.pRemoteId)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — UserCardCollectionDto.toEntity: core mapping
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a full dto when toEntity then syncStatus is SYNCED`() {
        val entity = aFullDto().toEntity()
        assertEquals(SyncStatus.SYNCED, entity.syncStatus)
    }

    @Test
    fun `given a dto with id when toEntity then remoteId equals dto id`() {
        val entity = aFullDto(id = REMOTE_ID).toEntity()
        assertEquals(REMOTE_ID, entity.remoteId)
    }

    @Test
    fun `given a dto with null id when toEntity then remoteId is null`() {
        val entity = aFullDto(id = null).toEntity()
        assertNull(entity.remoteId)
    }

    @Test
    fun `given a full dto when toEntity then scryfallId is preserved`() {
        val entity = aFullDto().toEntity()
        assertEquals("card-scryfall-id", entity.scryfallId)
    }

    @Test
    fun `given a full dto when toEntity then quantity is preserved`() {
        val entity = aFullDto().toEntity()
        assertEquals(3, entity.quantity)
    }

    @Test
    fun `given a foil dto when toEntity then isFoil is true`() {
        val entity = aFullDto().toEntity()
        assertEquals(true, entity.isFoil)
    }

    @Test
    fun `given a full dto when toEntity then condition is preserved`() {
        val entity = aFullDto().toEntity()
        assertEquals("LP", entity.condition)
    }

    @Test
    fun `given a full dto when toEntity then language is preserved`() {
        val entity = aFullDto().toEntity()
        assertEquals("ja", entity.language)
    }

    @Test
    fun `given a full dto when toEntity then isForTrade is preserved`() {
        val entity = aFullDto().toEntity()
        assertEquals(true, entity.isForTrade)
    }

    @Test
    fun `given a full dto when toEntity then isInWishlist is preserved`() {
        val entity = aFullDto().toEntity()
        assertEquals(false, entity.isInWishlist)
    }

    @Test
    fun `given a full dto when toEntity then minTradeValue is preserved`() {
        val entity = aFullDto().toEntity()
        assertEquals(12.50, entity.minTradeValue!!, 0.001)
    }

    @Test
    fun `given a full dto when toEntity then notes is preserved`() {
        val entity = aFullDto().toEntity()
        assertEquals("signed by artist", entity.notes)
    }

    @Test
    fun `given a full dto when toEntity then acquiredAt is preserved`() {
        val entity = aFullDto().toEntity()
        assertEquals(1_700_000_000_000L, entity.acquiredAt)
    }

    @Test
    fun `given a full dto when toEntity then addedAt is preserved`() {
        val entity = aFullDto().toEntity()
        assertEquals(1_600_000_000_000L, entity.addedAt)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — Roundtrip: entity → toUpsertParams → dto → toEntity
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Validates that the fields which survive the roundtrip (entity → params → dto → entity)
     * retain their values. The local-only fields (id, syncStatus) are expected to change.
     */
    @Test
    fun `given a full entity when roundtrip through params and dto then business fields are preserved`() {
        // Arrange
        val original = aFullEntity(remoteId = REMOTE_ID)

        // Act: step 1 — convert to upsert params
        val params = original.toUpsertParams(USER_ID)

        // Simulate server responding with the same data (reconstructed as a DTO)
        val dto = UserCardCollectionDto(
            id               = REMOTE_ID,
            userId           = params.pUserId,
            scryfallId       = params.pScryfallId,
            quantity         = params.pQuantity,
            isFoil           = params.pIsFoil,
            isAlternativeArt = params.pIsAlternativeArt,
            condition        = params.pCondition,
            language         = params.pLanguage,
            isForTrade       = params.pIsForTrade,
            isInWishlist     = params.pIsInWishlist,
            minTradeValue    = params.pMinTradeValue,
            notes            = params.pNotes,
            acquiredAt       = params.pAcquiredAt,
            addedAt          = params.pAddedAt,
        )

        // Act: step 2 — convert back to entity
        val roundTripped = dto.toEntity()

        // Assert: business fields match
        assertEquals(original.scryfallId,       roundTripped.scryfallId)
        assertEquals(original.quantity,          roundTripped.quantity)
        assertEquals(original.isFoil,           roundTripped.isFoil)
        assertEquals(original.isAlternativeArt, roundTripped.isAlternativeArt)
        assertEquals(original.condition,         roundTripped.condition)
        assertEquals(original.language,          roundTripped.language)
        assertEquals(original.isForTrade,        roundTripped.isForTrade)
        assertEquals(original.isInWishlist,      roundTripped.isInWishlist)
        assertEquals(original.minTradeValue,     roundTripped.minTradeValue)
        assertEquals(original.notes,             roundTripped.notes)
        assertEquals(original.acquiredAt,        roundTripped.acquiredAt)
        assertEquals(original.addedAt,           roundTripped.addedAt)

        // Assert: sync fields are normalised (SYNCED + remoteId from dto)
        assertEquals(SyncStatus.SYNCED, roundTripped.syncStatus)
        assertEquals(REMOTE_ID,         roundTripped.remoteId)
    }
}
