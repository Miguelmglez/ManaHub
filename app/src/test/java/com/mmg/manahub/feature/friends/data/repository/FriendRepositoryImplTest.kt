package com.mmg.manahub.feature.friends.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.dao.FriendDao
import com.mmg.manahub.core.data.local.entity.FriendEntity
import com.mmg.manahub.core.data.remote.FriendRemoteDataSource
import com.mmg.manahub.core.data.remote.dto.FriendCardDto
import com.mmg.manahub.core.data.remote.dto.FriendCardSearchRowDto
import com.mmg.manahub.core.model.FriendCardCursor
import com.mmg.manahub.core.model.FriendCardSearchException
import com.mmg.manahub.core.model.FriendCardSearchParams
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.data.remote.FriendRequestWithProfile
import com.mmg.manahub.core.data.remote.FriendWithProfile
import com.mmg.manahub.core.data.remote.OutgoingRequestWithProfile
import com.mmg.manahub.core.data.remote.dto.AcceptInviteResultDto
import com.mmg.manahub.core.data.remote.dto.FriendshipDto
import com.mmg.manahub.core.model.FriendshipGoneException
import com.mmg.manahub.core.model.Card
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

    private class InMemoryOwnerStore(var owner: String?) : FriendsCacheOwnerStore {
        override suspend fun get(): String? = owner
        override suspend fun set(userId: String?) { owner = userId }
    }

    private val ownerStore = InMemoryOwnerStore(owner = "user-me")
    private var activeUser: String? = "user-me"

    private val repository = FriendRepositoryImpl(
        dao, remote, cardRepo, progressionEventBus, crashReporter,
        cacheOwner = ownerStore,
        activeUserId = { activeUser },
    )

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

    // ── Restore P5 (D6 / F-08 / F-10): FriendAdded emission ─────────────────────

    private val me = "user-me"

    private fun friend(friendshipId: String, userId: String) =
        FriendWithProfile(id = friendshipId, friendUserId = userId, nickname = "N", gameTag = "T", avatarUrl = null)

    private fun emittedFriendKeys(): List<String> {
        val events = mutableListOf<ProgressionEvent>()
        coVerify(atLeast = 0) { progressionEventBus.emit(capture(events)) }
        return events.filterIsInstance<ProgressionEvent.FriendAdded>().map { it.idempotencyKey }
    }

    @Test
    fun `given the accept changed no row when acceptRequest then it fails without local delete or reward`() = runTest {
        coEvery { remote.acceptRequestReturning("fs-1") } returns Result.failure(FriendshipGoneException())
        coEvery { remote.getPendingRequests(me) } returns Result.success(emptyList())

        val result = repository.acceptRequest("fs-1", me)

        assertTrue(result.exceptionOrNull() is FriendshipGoneException)
        coVerify(exactly = 0) { dao.deleteRequest(any()) }
        coVerify(exactly = 1) { remote.getPendingRequests(me) }
        assertTrue(emittedFriendKeys().isEmpty())
    }

    @Test
    fun `given the accept returned the row when acceptRequest then the requester user id is rewarded once`() = runTest {
        coEvery { remote.acceptRequestReturning("fs-1") } returns
            Result.success(FriendshipDto("fs-1", userId1 = "user-requester", userId2 = me, status = "ACCEPTED", createdAt = "x"))
        coEvery { dao.getFriendUserIds() } returns listOf("user-old")
        coEvery { remote.getFriends(me) } returns
            Result.success(listOf(friend("fs-0", "user-old"), friend("fs-1", "user-requester")))

        val result = repository.acceptRequest("fs-1", me)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { dao.deleteRequest("fs-1") }
        assertEquals(listOf("friend:user-requester"), emittedFriendKeys())
    }

    @Test
    fun `given an invite redemption succeeds then the inviter is rewarded`() = runTest {
        coEvery { remote.acceptInvite("CODE1234") } returns AcceptInviteResultDto(inviterId = "user-inviter")
        coEvery { dao.getFriendUserIds() } returns listOf("user-old")
        coEvery { remote.getFriends(me) } returns
            Result.success(listOf(friend("fs-0", "user-old"), friend("fs-5", "user-inviter")))

        val result = repository.acceptInvite("CODE1234")

        assertTrue(result.isSuccess)
        // The cache refresh shows the inviter first; the reward is emitted once, not again by the refresh.
        coVerify(exactly = 1) { dao.replaceAll(match { list -> list.any { it.friendUserId == "user-inviter" } }, null, null) }
        assertEquals(listOf("friend:user-inviter"), emittedFriendKeys())
    }

    @Test
    fun `an invite response from a previous account cannot refresh or reward the new account`() = runTest {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<AcceptInviteResultDto>()
        coEvery { remote.acceptInvite("CODE1234") } coAnswers {
            started.complete(Unit)
            response.await()
        }

        val accept = async { repository.acceptInvite("CODE1234") }
        started.await()
        activeUser = "user-new"
        repository.claimLocalCache("user-new")
        response.complete(AcceptInviteResultDto(inviterId = "user-a-inviter"))

        assertTrue(accept.await().isFailure)
        coVerify(exactly = 0) { remote.getFriends(any()) }
        coVerify(exactly = 0) { dao.replaceAll(any(), any(), any()) }
        assertTrue(emittedFriendKeys().isEmpty())
    }

    @Test
    fun `an accept response from a previous account cannot delete the new account request or reward it`() = runTest {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Result<FriendshipDto>>()
        coEvery { remote.acceptRequestReturning("fs-1") } coAnswers {
            started.complete(Unit)
            response.await()
        }

        val accept = async { repository.acceptRequest("fs-1", me) }
        started.await()
        activeUser = "user-new"
        repository.claimLocalCache("user-new")
        response.complete(Result.success(FriendshipDto("fs-1", "user-requester", me, "ACCEPTED", "x")))

        assertTrue(accept.await().isFailure)
        coVerify(exactly = 0) { dao.deleteRequest(any()) }
        coVerify(exactly = 0) { remote.getFriends(any()) }
        assertTrue(emittedFriendKeys().isEmpty())
    }

    @Test
    fun `given a refresh finds a newly accepted friend then the requester side is rewarded`() = runTest {
        coEvery { dao.getFriendUserIds() } returns listOf("user-old")
        coEvery { remote.getFriends(me) } returns
            Result.success(listOf(friend("fs-0", "user-old"), friend("fs-9", "user-new")))

        repository.refreshFriends(me)

        assertEquals(listOf("friend:user-new"), emittedFriendKeys())
    }

    @Test
    fun `given an empty prior cache then the first refresh uses persistent friend ledger keys`() = runTest {
        coEvery { dao.getFriendUserIds() } returns emptyList()
        coEvery { remote.getFriends(me) } returns Result.success(listOf(friend("fs-0", "user-a"), friend("fs-1", "user-b")))

        repository.refreshFriends(me)

        assertEquals(listOf("friend:user-a", "friend:user-b"), emittedFriendKeys())
    }

    @Test
    fun `given a friend removed and re-added then the ledger key is the same so the replay is a duplicate`() = runTest {
        coEvery { remote.removeFriend("fs-1") } returns Result.success(Unit)
        coEvery { dao.getFriendUserIds() } returnsMany listOf(listOf("user-x"), listOf("user-x"))
        coEvery { remote.getFriends(me) } returnsMany listOf(
            Result.success(listOf(friend("fs-0", "user-x"), friend("fs-1", "user-b"))),
            Result.success(listOf(friend("fs-0", "user-x"), friend("fs-2", "user-b"))),
        )

        repository.refreshFriends(me)
        repository.removeFriend("fs-1")
        repository.refreshFriends(me)

        assertEquals(listOf("friend:user-b", "friend:user-b"), emittedFriendKeys())
    }

    @Test
    fun `given the delete matched no row when removeFriend then the local friend is kept`() = runTest {
        coEvery { remote.removeFriend("fs-1") } returns Result.failure(FriendshipGoneException())

        val result = repository.removeFriend("fs-1")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { dao.deleteFriend(any()) }
    }

    // ── P7 (F-02 / F-05 / F-06): atomic replace + account scoping ─────────────

    private fun request(id: String, from: String) =
        FriendRequestWithProfile(id = id, fromUserId = from, fromNickname = "N", fromGameTag = "T", fromAvatarUrl = null, createdAt = 0L)

    private fun outgoing(id: String, to: String) =
        OutgoingRequestWithProfile(id = id, toUserId = to, toNickname = "N", toGameTag = "T", toAvatarUrl = null, createdAt = 0L)

    @Test
    fun `refreshAll writes the three lists in one atomic replace`() = runTest {
        coEvery { remote.getFriends(me) } returns Result.success(listOf(friend("fs-1", "user-a")))
        coEvery { remote.getPendingRequests(me) } returns Result.success(listOf(request("fs-2", "user-b")))
        coEvery { remote.getOutgoingRequests(me) } returns Result.success(listOf(outgoing("fs-3", "user-c")))

        val result = repository.refreshAll(me)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            dao.replaceAll(
                match { it.map { f -> f.id } == listOf("fs-1") },
                match { it.map { r -> r.id } == listOf("fs-2") },
                match { it.map { o -> o.id } == listOf("fs-3") },
            )
        }
        coVerify(exactly = 0) { dao.clearFriends() }
        coVerify(exactly = 0) { dao.clearAll() }
    }

    @Test
    fun `refreshAll keeps the cached list whose fetch failed and reports the failure`() = runTest {
        val offline = IllegalStateException("offline")
        coEvery { remote.getFriends(me) } returns Result.success(listOf(friend("fs-1", "user-a")))
        coEvery { remote.getPendingRequests(me) } returns Result.failure(offline)
        coEvery { remote.getOutgoingRequests(me) } returns Result.success(emptyList())

        val result = repository.refreshAll(me)

        assertEquals(offline, result.exceptionOrNull())
        coVerify(exactly = 1) { dao.replaceAll(any(), null, emptyList()) }
    }

    @Test
    fun `a refresh for an account that is no longer signed in never touches the cache`() = runTest {
        activeUser = "user-other"
        coEvery { remote.getFriends(me) } returns Result.success(listOf(friend("fs-1", "user-a")))

        repository.refreshFriends(me)

        coVerify(exactly = 0) { dao.replaceAll(any(), any(), any()) }
        coVerify(exactly = 0) { dao.clearAll() }
        assertEquals("user-me", ownerStore.owner)
    }

    @Test
    fun `account B sees no account A friends while cache claim is delayed or fails`() = runTest {
        val activeSession = MutableStateFlow<String?>(me)
        val cachedRows = MutableStateFlow(
            listOf(FriendEntity("fs-a", "friend-a", "Alice", "#AAAAAA", null))
        )
        every { dao.observeFriends() } returns cachedRows
        val scopedRepository = FriendRepositoryImpl(
            dao, remote, cardRepo, progressionEventBus, crashReporter,
            cacheOwner = ownerStore,
            activeUserId = { activeUser },
            activeSessionFlow = activeSession,
        )

        assertEquals("friend-a", scopedRepository.observeFriends().first().single().userId)
        activeUser = "user-b"
        activeSession.value = "user-b"

        assertTrue(scopedRepository.observeFriends().first().isEmpty())
        assertEquals(me, ownerStore.owner)
    }

    @Test
    fun `a response arriving after account switch cannot overwrite the new account cache`() = runTest {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Result<List<FriendWithProfile>>>()
        coEvery { remote.getFriends(me) } coAnswers {
            started.complete(Unit)
            response.await()
        }

        val refresh = async { repository.refreshFriends(me) }
        started.await()
        activeUser = "user-new"
        repository.claimLocalCache("user-new")
        response.complete(Result.success(listOf(friend("fs-old", "user-old"))))
        refresh.await()

        coVerify(exactly = 1) { dao.clearAll() }
        coVerify(exactly = 0) { dao.replaceAll(any(), any(), any()) }
        assertEquals("user-new", ownerStore.owner)
    }

    @Test
    fun `claiming the cache for another account wipes it and records the new owner`() = runTest {
        repository.claimLocalCache("user-new")

        coVerify(exactly = 1) { dao.clearAll() }
        assertEquals("user-new", ownerStore.owner)
    }

    @Test
    fun `claiming the cache for its current owner keeps it`() = runTest {
        repository.claimLocalCache(me)

        coVerify(exactly = 0) { dao.clearAll() }
    }

    @Test
    fun `a cache with no recorded owner is wiped before the first write`() = runTest {
        ownerStore.owner = null
        coEvery { remote.getOutgoingRequests(me) } returns Result.success(emptyList())

        repository.refreshOutgoingRequests(me)

        coVerify(exactly = 1) { dao.clearAll() }
        coVerify(exactly = 1) { dao.replaceAll(null, null, emptyList()) }
        assertEquals(me, ownerStore.owner)
    }

    @Test
    fun `clearLocalCache drops every row and the owner`() = runTest {
        repository.clearLocalCache()

        coVerify(exactly = 1) { dao.clearAll() }
        assertNull(ownerStore.owner)
    }
}
