package com.mmg.manahub.feature.friends.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.dao.FriendDao
import com.mmg.manahub.core.data.remote.FriendRemoteDataSource
import com.mmg.manahub.core.data.remote.dto.FriendCardDto
import com.mmg.manahub.core.data.remote.dto.FriendCardSearchRowDto
import com.mmg.manahub.core.model.FriendCardCursor
import com.mmg.manahub.core.model.FriendCardSearchException
import com.mmg.manahub.core.model.FriendCardSearchParams
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.model.Card
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FriendRepositoryImpl.getFriendCollection]'s card-resolution path (Backend &
 * Performance Optimization plan, WS1+WS3 Part B item 10, 2026-07-28). Sole owning suite for this
 * N+1 fix: enrichment used to call `cardRepo.getCardById(dto.scryfallId)` per row (falls back to a
 * LIVE Scryfall fetch on a cache miss) -- now a single batched `warmCacheForIds` +
 * `getCardsByIds` (Room-only, no network fallback).
 */
class FriendRepositoryImplTest {

    private val dao = mockk<FriendDao>(relaxed = true)
    private val remote = mockk<FriendRemoteDataSource>()
    private val cardRepo = mockk<CardRepository>()
    private val progressionEventBus = mockk<ProgressionEventBus>(relaxed = true)
    private val crashReporter = mockk<CrashReporter>(relaxed = true)

    private val repository = FriendRepositoryImpl(dao, remote, cardRepo, progressionEventBus, crashReporter)

    private fun friendCardDto(scryfallId: String, sourceList: String = "collection") = FriendCardDto(
        sourceList = sourceList, scryfallId = scryfallId, quantity = 1, isFoil = false,
        condition = "NM", language = "en",
    )

    private fun minimalCard(id: String, name: String = "Card $id") = Card(
        scryfallId = id, name = name, printedName = null, manaCost = null, cmc = 1.0,
        colors = emptyList(), colorIdentity = emptyList(), typeLine = "Instant", printedTypeLine = null,
        oracleText = null, printedText = null, keywords = emptyList(), power = null, toughness = null,
        loyalty = null, setCode = "tst", setName = "Test Set", collectorNumber = "1", rarity = "common",
        releasedAt = "2020-01-01", frameEffects = emptyList(), promoTypes = emptyList(), lang = "en",
        imageNormal = null, imageArtCrop = null, imageBackNormal = null, priceUsd = null,
        priceUsdFoil = null, priceEur = null, priceEurFoil = null, legalityStandard = "legal",
        legalityPioneer = "legal", legalityModern = "legal", legalityCommander = "legal",
        flavorText = null, artist = null, scryfallUri = "https://scryfall.com/card/$id",
    )

    @Test
    fun `given 3 distinct card ids when getFriendCollection runs then warmCacheForIds and getCardsByIds are each called once and getCardById is never called`() =
        runTest {
            val dtos = listOf(friendCardDto("id-1"), friendCardDto("id-2"), friendCardDto("id-3"))
            coEvery {
                remote.getFriendCollection(
                    friendUserId = any(), list = any(), query = any(), sets = any(), rarities = any(),
                    colors = any(), foilOnly = any(), conditions = any(), languages = any(),
                    limit = any(), offset = any(),
                )
            } returns dtos
            coEvery { cardRepo.warmCacheForIds(any()) } returns Unit
            coEvery { cardRepo.getCardsByIds(any()) } returns
                listOf(minimalCard("id-1"), minimalCard("id-2"), minimalCard("id-3"))

            val result = repository.getFriendCollection(friendUserId = "friend-1", list = "collection", query = "")

            coVerify(exactly = 1) {
                cardRepo.warmCacheForIds(match { it.toSet() == setOf("id-1", "id-2", "id-3") })
            }
            coVerify(exactly = 1) { cardRepo.getCardsByIds(match { it.toSet() == setOf("id-1", "id-2", "id-3") }) }
            coVerify(exactly = 0) { cardRepo.getCardById(any()) }

            assertEquals(3, result.getOrNull()?.size)
        }

    @Test
    fun `given an id Room cannot resolve after the batch warm when getFriendCollection runs then that row is silently omitted, not fetched live`() =
        runTest {
            val dtos = listOf(friendCardDto("resolvable"), friendCardDto("unresolvable"))
            coEvery {
                remote.getFriendCollection(
                    friendUserId = any(), list = any(), query = any(), sets = any(), rarities = any(),
                    colors = any(), foilOnly = any(), conditions = any(), languages = any(),
                    limit = any(), offset = any(),
                )
            } returns dtos
            coEvery { cardRepo.warmCacheForIds(any()) } returns Unit
            // Only "resolvable" comes back -- "unresolvable" stays missing even after the warm.
            coEvery { cardRepo.getCardsByIds(any()) } returns listOf(minimalCard("resolvable"))

            val result = repository.getFriendCollection(friendUserId = "friend-1", list = "collection", query = "")

            val cards = result.getOrNull()
            assertEquals(1, cards?.size)
            assertEquals("resolvable", cards?.first()?.scryfallId)
            // The whole point of the fix: a Room-unresolvable id is degraded gracefully, never
            // re-fetched live via getCardById (the N+1 shape this workstream removed).
            coVerify(exactly = 0) { cardRepo.getCardById(any()) }
        }

    @Test
    fun `given a friend collection with duplicate scryfallIds when getFriendCollection runs then the batch resolves ids only once`() =
        runTest {
            val dtos = listOf(friendCardDto("id-1"), friendCardDto("id-1", sourceList = "wishlist"))
            coEvery {
                remote.getFriendCollection(
                    friendUserId = any(), list = any(), query = any(), sets = any(), rarities = any(),
                    colors = any(), foilOnly = any(), conditions = any(), languages = any(),
                    limit = any(), offset = any(),
                )
            } returns dtos
            coEvery { cardRepo.warmCacheForIds(any()) } returns Unit
            coEvery { cardRepo.getCardsByIds(any()) } returns listOf(minimalCard("id-1"))

            repository.getFriendCollection(friendUserId = "friend-1", list = "collection", query = "")

            coVerify(exactly = 1) { cardRepo.warmCacheForIds(match { it == listOf("id-1") }) }
            coVerify(exactly = 1) { cardRepo.getCardsByIds(match { it == listOf("id-1") }) }
        }

    private fun searchRow(id: String, rowId: String = "row-$id", hasMore: Boolean = false, unindexed: Int = 0) =
        FriendCardSearchRowDto(
            sourceList = "collection", rowId = rowId, scryfallId = id, quantity = 2, isFoil = true,
            condition = "NM", language = "en", cardName = "Server $id", setCode = "srv", rarity = "rare",
            sortKey = "0server $id", hasMore = hasMore, unindexedCount = unindexed,
        )

    @Test
    fun `searchFriendCards keeps a row Room cannot resolve, built from the server metadata`() = runTest {
        coEvery { remote.searchFriendCards(any(), any(), any(), any(), any()) } returns
            listOf(searchRow("cached", unindexed = 4), searchRow("missing", unindexed = 4))
        coEvery { cardRepo.getCardsByIds(any()) } returns listOf(minimalCard("cached", name = "Local Name"))

        val page = repository.searchFriendCards("friend-1", "collection", FriendCardSearchParams(), null, 50).getOrThrow()

        assertEquals(2, page.cards.size)
        assertEquals("Local Name", page.cards[0].name)
        val fallback = page.cards[1]
        assertEquals("Server missing", fallback.name)
        assertEquals("srv", fallback.setCode)
        assertEquals("rare", fallback.rarity)
        assertNull(fallback.imageNormal)
        assertNull(fallback.priceUsd)
        assertEquals("row-missing", fallback.rowId)
        assertTrue(fallback.isFoil)
        assertEquals(setOf("missing"), page.unresolvedIds)
        assertEquals(4, page.unindexedCount)
        // The Scryfall warm is off the page path: it can stall for a whole 429 cooldown.
        coVerify(exactly = 0) { cardRepo.warmCacheForIds(any()) }
        coVerify(exactly = 0) { cardRepo.getCardById(any()) }
    }

    @Test
    fun `hydrateFriendCardMetadata warms once then reads Room`() = runTest {
        coEvery { cardRepo.warmCacheForIds(any()) } returns Unit
        coEvery { cardRepo.getCardsByIds(any()) } returns listOf(minimalCard("x", name = "Resolved"))

        val resolved = repository.hydrateFriendCardMetadata(listOf("x", "y"))

        assertEquals(setOf("x"), resolved.keys)
        coVerify(exactly = 1) { cardRepo.warmCacheForIds(listOf("x", "y")) }
    }

    @Test
    fun `hydrateFriendCardMetadata gives up on a stalled warm and still reads Room`() = runTest {
        coEvery { cardRepo.warmCacheForIds(any()) } coAnswers { kotlinx.coroutines.delay(60_000) }
        coEvery { cardRepo.getCardsByIds(any()) } returns listOf(minimalCard("x"))

        val resolved = repository.hydrateFriendCardMetadata(listOf("x"))

        assertEquals(setOf("x"), resolved.keys)
    }

    @Test
    fun `searchFriendCards exposes the last row as the next cursor when has_more is true`() = runTest {
        coEvery { remote.searchFriendCards(any(), any(), any(), any(), any()) } returns
            listOf(searchRow("a"), searchRow("b", hasMore = true))
        coEvery { cardRepo.getCardsByIds(any()) } returns emptyList()

        val page = repository.searchFriendCards("friend-1", "trade", FriendCardSearchParams(), null, 2).getOrThrow()

        assertTrue(page.hasMore)
        assertEquals(FriendCardCursor("0server b", "row-b"), page.nextCursor)
    }

    @Test
    fun `searchFriendCards last page has no cursor and an empty page skips the cache read`() = runTest {
        coEvery { remote.searchFriendCards(any(), any(), any(), any(), any()) } returns emptyList()

        val page = repository.searchFriendCards("friend-1", "wishlist", FriendCardSearchParams(), null, 50).getOrThrow()

        assertFalse(page.hasMore)
        assertNull(page.nextCursor)
        assertEquals(0, page.unindexedCount)
        coVerify(exactly = 0) { cardRepo.getCardsByIds(any()) }
    }

    @Test
    fun `searchFriendCards surfaces the typed remote error`() = runTest {
        coEvery { remote.searchFriendCards(any(), any(), any(), any(), any()) } throws
            FriendCardSearchException.AccessDenied()

        val result = repository.searchFriendCards("friend-1", "collection", FriendCardSearchParams(), null, 50)

        assertTrue(result.exceptionOrNull() is FriendCardSearchException.AccessDenied)
    }
}
