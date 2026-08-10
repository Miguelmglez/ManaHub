package com.mmg.manahub.feature.communitydecks.data.remote

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.entity.CommunityDeckCacheEntity
import com.mmg.manahub.core.data.remote.dto.ArchidektCardDto
import com.mmg.manahub.core.data.remote.dto.ArchidektCardEntryDto
import com.mmg.manahub.core.data.remote.dto.ArchidektDeckDetailDto
import com.mmg.manahub.core.data.remote.dto.ArchidektEditionDto
import com.mmg.manahub.core.data.remote.dto.ArchidektOracleCardDto
import com.mmg.manahub.core.data.remote.dto.ArchidektOwnerDto
import com.mmg.manahub.core.data.remote.dto.ArchidektPricesDto
import com.mmg.manahub.core.data.remote.mapper.toDomain
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the Room cache blob round-trip stays tolerant across the 2026-07-13 rich-card-display
 * DTO additions (edition/rarity/prices/superTypes/subTypes/cmc/layout on the detail DTO,
 * featured/customFeatured on the summary DTO — see CLAUDE.md "Database (Room v42)" +
 * ADR-004 §1 conventions). No Room migration was needed since these are pure cache-blob fields.
 */
class CommunityDecksMappersTest {

    @Before
    fun setUp() {
        // CommunityDeckCacheEntity.toDomain()'s corrupt-blob guard calls the static
        // recordNonFatal(...) helper (FirebaseCrashlytics.getInstance()) outside a runCatching the
        // test can't otherwise reach.
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun buildDto() = ArchidektDeckDetailDto(
        id = 1,
        name = "Test Deck",
        owner = ArchidektOwnerDto(id = 1, username = "user", avatar = ""),
        cards = listOf(
            ArchidektCardEntryDto(
                quantity = 1,
                categories = listOf("Mainboard"),
                card = ArchidektCardDto(
                    oracleCard = ArchidektOracleCardDto(
                        name = "Sanctum Weaver",
                        uid = "oracle-uid",
                        manaCost = "{1}{G}",
                        cmc = 2.0,
                        superTypes = emptyList(),
                        types = listOf("Creature"),
                        subTypes = listOf("Elf", "Druid"),
                    ),
                    uid = "4d42e22d-f60e-40c5-b069-5e1708f3bebc",
                    edition = ArchidektEditionDto(editioncode = "mh2", editionname = "Modern Horizons 2"),
                    rarity = "rare",
                    prices = ArchidektPricesDto(tcg = 1.25, cm = 1.10),
                ),
            ),
        ),
    )

    @Test
    fun toCacheEntity_thenToDomain_roundTripsAllNewFields() {
        val dto = buildDto()

        val entity = dto.toCacheEntity()
        val domain = entity.toDomain()
        assertNotNull(domain)

        val card = domain!!.cards.single()
        assertEquals("4d42e22d-f60e-40c5-b069-5e1708f3bebc", card.scryfallId)
        assertEquals("mh2", card.setCode)
        assertEquals("Modern Horizons 2", card.setName)
        assertEquals("rare", card.rarity)
        assertEquals(1.25, card.priceUsd)
        assertEquals(1.10, card.priceEur)
        assertEquals("Creature — Elf Druid", card.typeLine)
    }

    /**
     * Simulates a blob cached BEFORE this change (missing every new key). The decoder must
     * tolerate it via `coerceInputValues` + all-defaulted new fields — this is the exact
     * scenario a user's already-cached deck hits after an app update.
     */
    @Test
    fun toDomain_decodesPreExistingBlobMissingNewFields() {
        val legacyJson = """
            {
              "id": 1,
              "name": "Legacy Cached Deck",
              "owner": {"id": 1, "username": "user", "avatar": ""},
              "cards": [
                {
                  "quantity": 1,
                  "categories": ["Mainboard"],
                  "card": {
                    "oracleCard": {"name": "Sol Ring", "uid": "oracle-uid", "manaCost": "{1}"},
                    "uid": "printing-uid"
                  }
                }
              ]
            }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val dto = json.decodeFromString(ArchidektDeckDetailDto.serializer(), legacyJson)
        val domain = dto.toDomain()

        val card = domain.cards.single()
        assertEquals("Sol Ring", card.name)
        assertEquals("printing-uid", card.scryfallId)
        assertEquals("", card.setCode)
        assertEquals("", card.rarity)
        assertTrue(card.priceUsd == null)
    }

    /**
     * Regression test (post-review hardening): a corrupted/unparseable [responseJson] blob must
     * degrade to `null` (treated as a cache miss upstream) instead of throwing out of [toDomain].
     * Before this fix, [CommunityDecksRepositoryImpl.getDeckById] would call this path a second
     * time from its own catch block to attempt a stale-cache fallback against the SAME corrupt
     * row, re-throwing uncaught and crashing the caller's `viewModelScope.launch`.
     */
    @Test
    fun toDomain_returnsNullOnCorruptBlob() {
        val entity = CommunityDeckCacheEntity(
            archidektId = 1,
            name = "Corrupt Cached Deck",
            ownerUsername = "user",
            format = "commander",
            description = "",
            viewCount = 0,
            cardCount = 0,
            responseJson = "{ this is not valid json at all",
        )

        val domain = entity.toDomain()

        assertNull(domain)
    }
}
