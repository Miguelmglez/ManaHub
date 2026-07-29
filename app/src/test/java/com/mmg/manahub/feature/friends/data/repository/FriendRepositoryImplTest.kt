package com.mmg.manahub.feature.friends.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.dao.FriendDao
import com.mmg.manahub.core.data.remote.FriendRemoteDataSource
import com.mmg.manahub.core.data.remote.dto.FriendCardDto
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.model.Card
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
