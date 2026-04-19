package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.DeckSummaryRow
import com.mmg.manahub.core.data.local.entity.DeckCardCrossRef
import com.mmg.manahub.core.data.local.entity.DeckEntity
import com.mmg.manahub.core.data.local.entity.SyncStatus
import com.mmg.manahub.core.data.remote.decks.DeckCardDto
import com.mmg.manahub.core.data.remote.decks.DeckDto
import com.mmg.manahub.core.data.remote.decks.DeckRemoteDataSource
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.repository.CardRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.user.UserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for [DeckRepositoryImpl].
 *
 * REGRESSION GROUP: "addCardToDeck FK fix"
 * Before the fix, addCardToDeck() called cardRepository.getCardById() but ignored a
 * DataResult.Error response and proceeded to call deckDao.upsertDeckCard() anyway.
 * This caused an FK constraint exception (SQLiteConstraintException) at the DAO level
 * because deck_cards.scryfall_id → cards.scryfall_id is RESTRICT.
 * Fix: early return on DataResult.Error — upsertDeckCard is never called in that case.
 * These tests verify that contract explicitly.
 *
 * SYNC GROUPS (4–9): verify the bidirectional Supabase sync logic introduced alongside
 * DeckRemoteDataSource. All remote calls are mocked — no real network or DB is used.
 */
class DeckRepositoryImplTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val deckDao          = mockk<DeckDao>(relaxed = true)
    private val cardRepository   = mockk<CardRepository>()
    private val remoteDataSource = mockk<DeckRemoteDataSource>(relaxed = true)
    private val supabaseAuth     = mockk<Auth>(relaxed = true)
    private lateinit var repository: DeckRepositoryImpl

    // ── Constants ─────────────────────────────────────────────────────────────

    private val USER_ID   = "user-uuid-deck-001"
    private val REMOTE_ID = "supabase-deck-uuid-abc"

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private fun buildDeckEntity(
        id:          Long    = 1L,
        name:        String  = "My Deck",
        format:      String  = "commander",
        description: String? = null,
        coverCardId: String? = null,
        createdAt:   Long    = 1_000L,
        updatedAt:   Long    = 2_000L,
        syncStatus:  Int     = SyncStatus.PENDING_UPLOAD,
        remoteId:    String? = null,
    ) = DeckEntity(
        id          = id,
        name        = name,
        format      = format,
        description = description,
        coverCardId = coverCardId,
        createdAt   = createdAt,
        updatedAt   = updatedAt,
        syncStatus  = syncStatus,
        remoteId    = remoteId,
    )

    private fun buildDeck(
        id:          Long    = 1L,
        name:        String  = "My Deck",
        format:      String  = "commander",
        description: String? = null,
        coverCardId: String? = null,
        createdAt:   Long    = 1_000L,
        updatedAt:   Long    = 2_000L,
    ) = Deck(
        id          = id,
        name        = name,
        format      = format,
        description = description,
        coverCardId = coverCardId,
        createdAt   = createdAt,
        updatedAt   = updatedAt,
    )

    private fun buildSummaryRow(
        deckId:        Long,
        name:          String,
        format:        String  = "commander",
        scryfallId:    String? = null,
        isSideboard:   Boolean = false,
        quantity:      Int     = 1,
        colorIdentity: String? = null,
        imageArtCrop:  String? = null,
        coverCardId:   String? = null,
        updatedAt:     Long    = 1_000L,
    ) = DeckSummaryRow(
        deckId        = deckId,
        name          = name,
        format        = format,
        description   = null,
        coverCardId   = coverCardId,
        createdAt     = 0L,
        updatedAt     = updatedAt,
        scryfallId    = scryfallId,
        isSideboard   = isSideboard,
        quantity      = quantity,
        colorIdentity = colorIdentity,
        imageArtCrop  = imageArtCrop,
    )

    private fun buildDeckDto(
        id:        String? = REMOTE_ID,
        name:      String  = "My Deck",
        format:    String  = "commander",
        updatedAt: String? = null,
        isDeleted: Boolean = false,
    ) = DeckDto(
        id          = id,
        localId     = 1L,
        userId      = USER_ID,
        name        = name,
        format      = format,
        updatedAt   = updatedAt,
        isDeleted   = isDeleted,
    )

    private fun aUserInfo(userId: String = USER_ID): UserInfo =
        mockk<UserInfo>(relaxed = true) { every { id } returns userId }

    private fun givenLoggedIn(userId: String = USER_ID) {
        every { supabaseAuth.currentUserOrNull() } returns aUserInfo(userId)
    }

    private fun givenLoggedOut() {
        every { supabaseAuth.currentUserOrNull() } returns null
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        repository = DeckRepositoryImpl(
            deckDao          = deckDao,
            cardRepository   = cardRepository,
            remoteDataSource = remoteDataSource,
            supabaseAuth     = supabaseAuth,
            ioDispatcher     = UnconfinedTestDispatcher(),
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — REGRESSION: addCardToDeck early return on Error
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given getCardById returns Error when addCardToDeck then upsertDeckCard is NOT called`() = runTest {
        // Arrange
        coEvery { cardRepository.getCardById("card-001") } returns
            DataResult.Error("Network error — card not found")

        // Act
        repository.addCardToDeck(
            deckId      = 1L,
            scryfallId  = "card-001",
            quantity    = 1,
            isSideboard = false,
        )

        // Assert: FK guard — upsertDeckCard must never be called when card is missing
        coVerify(exactly = 0) { deckDao.upsertDeckCard(any()) }
    }

    @Test
    fun `given getCardById returns Success when addCardToDeck then upsertDeckCard IS called`() = runTest {
        // Arrange
        val card = com.mmg.manahub.util.TestFixtures.buildCard(scryfallId = "card-001")
        coEvery { cardRepository.getCardById("card-001") } returns DataResult.Success(card)
        coEvery { deckDao.upsertDeckCard(any()) } returns Unit

        // Act
        repository.addCardToDeck(
            deckId      = 1L,
            scryfallId  = "card-001",
            quantity    = 4,
            isSideboard = false,
        )

        // Assert
        coVerify(exactly = 1) { deckDao.upsertDeckCard(any()) }
    }

    @Test
    fun `given getCardById returns Success when addCardToDeck then crossRef has correct fields`() = runTest {
        // Arrange
        val card = com.mmg.manahub.util.TestFixtures.buildCard(scryfallId = "bolt-001")
        coEvery { cardRepository.getCardById("bolt-001") } returns DataResult.Success(card)

        val capturedRef = slot<DeckCardCrossRef>()
        coEvery { deckDao.upsertDeckCard(capture(capturedRef)) } returns Unit

        // Act
        repository.addCardToDeck(
            deckId      = 7L,
            scryfallId  = "bolt-001",
            quantity    = 3,
            isSideboard = true,
        )

        // Assert
        assertEquals(7L,        capturedRef.captured.deckId)
        assertEquals("bolt-001", capturedRef.captured.scryfallId)
        assertEquals(3,          capturedRef.captured.quantity)
        assertTrue(              capturedRef.captured.isSideboard)
    }

    @Test
    fun `given getCardById returns stale Success when addCardToDeck then upsertDeckCard IS called`() = runTest {
        // Arrange — stale data is still a Success: card exists in DB, FK can be satisfied
        val staleCard = com.mmg.manahub.util.TestFixtures.buildCard(scryfallId = "card-002")
        coEvery { cardRepository.getCardById("card-002") } returns
            DataResult.Success(staleCard, isStale = true)
        coEvery { deckDao.upsertDeckCard(any()) } returns Unit

        // Act
        repository.addCardToDeck(1L, "card-002", 1, false)

        // Assert: stale success should still proceed
        coVerify(exactly = 1) { deckDao.upsertDeckCard(any()) }
    }

    @Test
    fun `given getCardById returns Error with any message when addCardToDeck then no DAO call is made`() = runTest {
        // Arrange — boundary: ensure ANY error message triggers the guard, not just specific ones
        coEvery { cardRepository.getCardById("card-003") } returns
            DataResult.Error("")   // empty error message

        // Act
        repository.addCardToDeck(1L, "card-003", 2, false)

        // Assert
        coVerify(exactly = 0) { deckDao.upsertDeckCard(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — observeAllDeckSummaries: groupBy and cardCount
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given 2 cards in mainboard when observeAllDeckSummaries then cardCount is sum of quantities`() = runTest {
        // Arrange — one deck with two cards: 2x Lightning Bolt + 3x Island = 5 total mainboard
        val rows = listOf(
            buildSummaryRow(deckId = 1L, name = "Mono-R", scryfallId = "bolt",   quantity = 2, isSideboard = false),
            buildSummaryRow(deckId = 1L, name = "Mono-R", scryfallId = "island", quantity = 3, isSideboard = false),
        )
        coEvery { deckDao.observeDeckSummaryRows() } returns flowOf(rows)

        // Act
        val summaries = repository.observeAllDeckSummaries().first()

        // Assert
        assertEquals(1, summaries.size)
        assertEquals(5, summaries[0].cardCount)
    }

    @Test
    fun `given sideboard cards when observeAllDeckSummaries then sideboard cards are excluded from cardCount`() = runTest {
        // Arrange — 4 mainboard + 3 sideboard: cardCount must be 4 only
        val rows = listOf(
            buildSummaryRow(deckId = 1L, name = "D1", scryfallId = "card-A", quantity = 4, isSideboard = false),
            buildSummaryRow(deckId = 1L, name = "D1", scryfallId = "card-B", quantity = 3, isSideboard = true),
        )
        coEvery { deckDao.observeDeckSummaryRows() } returns flowOf(rows)

        // Act
        val summaries = repository.observeAllDeckSummaries().first()

        // Assert
        assertEquals(4, summaries[0].cardCount)
    }

    @Test
    fun `given 2 decks when observeAllDeckSummaries then both summaries are returned`() = runTest {
        // Arrange
        val rows = listOf(
            buildSummaryRow(deckId = 1L, name = "Deck A", scryfallId = "c1", quantity = 1, updatedAt = 2_000L),
            buildSummaryRow(deckId = 2L, name = "Deck B", scryfallId = "c2", quantity = 1, updatedAt = 1_000L),
        )
        coEvery { deckDao.observeDeckSummaryRows() } returns flowOf(rows)

        // Act
        val summaries = repository.observeAllDeckSummaries().first()

        // Assert: two distinct deck summaries
        assertEquals(2, summaries.size)
        val names = summaries.map { it.name }
        assertTrue(names.contains("Deck A"))
        assertTrue(names.contains("Deck B"))
    }

    @Test
    fun `given deck with no cards (null scryfallId) when observeAllDeckSummaries then cardCount is 0`() = runTest {
        // Arrange — LEFT JOIN returns a row with null scryfallId when deck has no cards
        val rows = listOf(
            buildSummaryRow(deckId = 1L, name = "Empty Deck", scryfallId = null, quantity = 0),
        )
        coEvery { deckDao.observeDeckSummaryRows() } returns flowOf(rows)

        // Act
        val summaries = repository.observeAllDeckSummaries().first()

        // Assert
        assertEquals(1, summaries.size)
        assertEquals(0, summaries[0].cardCount)
    }

    @Test
    fun `given empty rows when observeAllDeckSummaries then returns empty list`() = runTest {
        // Arrange
        coEvery { deckDao.observeDeckSummaryRows() } returns flowOf(emptyList())

        // Act
        val summaries = repository.observeAllDeckSummaries().first()

        // Assert
        assertTrue(summaries.isEmpty())
    }

    @Test
    fun `given summaries when observeAllDeckSummaries then sorted by updatedAt descending`() = runTest {
        // Arrange — Deck B was updated more recently
        val rows = listOf(
            buildSummaryRow(deckId = 1L, name = "Older Deck",  updatedAt = 1_000L),
            buildSummaryRow(deckId = 2L, name = "Newer Deck",  updatedAt = 9_000L),
        )
        coEvery { deckDao.observeDeckSummaryRows() } returns flowOf(rows)

        // Act
        val summaries = repository.observeAllDeckSummaries().first()

        // Assert
        assertEquals("Newer Deck", summaries[0].name)
        assertEquals("Older Deck", summaries[1].name)
    }

    @Test
    fun `given deck with colorIdentity JSON when observeAllDeckSummaries then colorIdentity set is built correctly`() = runTest {
        // Arrange — two cards with overlapping colors
        val rows = listOf(
            buildSummaryRow(deckId = 1L, name = "D", scryfallId = "c1",
                colorIdentity = """["R","G"]""", quantity = 1),
            buildSummaryRow(deckId = 1L, name = "D", scryfallId = "c2",
                colorIdentity = """["G","U"]""", quantity = 1),
        )
        coEvery { deckDao.observeDeckSummaryRows() } returns flowOf(rows)

        // Act
        val summaries = repository.observeAllDeckSummaries().first()

        // Assert: union of colors, no duplicates → {R, G, U}
        val colorId = summaries[0].colorIdentity
        assertEquals(setOf("R", "G", "U"), colorId)
    }

    @Test
    fun `given deck with malformed colorIdentity JSON when observeAllDeckSummaries then no crash and empty color set`() = runTest {
        // Arrange — edge: malformed JSON should be skipped gracefully
        val rows = listOf(
            buildSummaryRow(deckId = 1L, name = "D", scryfallId = "c1",
                colorIdentity = "not_valid_json", quantity = 1),
        )
        coEvery { deckDao.observeDeckSummaryRows() } returns flowOf(rows)

        // Act — must not crash
        val summaries = repository.observeAllDeckSummaries().first()

        // Assert: no crash; color identity is empty
        assertTrue(summaries[0].colorIdentity.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — createDeck / deleteDeck / removeCardFromDeck delegation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid deck when createDeck then delegates to deckDao insertDeck and returns id`() = runTest {
        // Arrange
        coEvery { deckDao.insertDeck(any()) } returns 55L
        val deck = buildDeck(id = 0L, name = "Commander Deck")

        // Act
        val id = repository.createDeck(deck)

        // Assert
        coVerify(exactly = 1) { deckDao.insertDeck(any()) }
        assertEquals(55L, id)
    }

    @Test
    fun `given deck id when deleteDeck then delegates to deckDao deleteDeck`() = runTest {
        // Arrange — no logged-in user so remote path is skipped
        givenLoggedOut()
        coEvery { deckDao.deleteDeck(any()) } returns Unit

        // Act
        repository.deleteDeck(3L)

        // Assert
        coVerify(exactly = 1) { deckDao.deleteDeck(3L) }
    }

    @Test
    fun `given card info when removeCardFromDeck then delegates to deckDao removeDeckCard`() = runTest {
        // Arrange
        coEvery { deckDao.removeDeckCard(any(), any(), any()) } returns Unit

        // Act
        repository.removeCardFromDeck(deckId = 2L, scryfallId = "bolt-001", isSideboard = false)

        // Assert
        coVerify(exactly = 1) { deckDao.removeDeckCard(2L, "bolt-001", false) }
    }

    @Test
    fun `given deck id when clearDeck then delegates to deckDao clearDeck`() = runTest {
        // Arrange
        coEvery { deckDao.clearDeck(any()) } returns Unit

        // Act
        repository.clearDeck(10L)

        // Assert
        coVerify(exactly = 1) { deckDao.clearDeck(10L) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — syncDeckNow
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given userId is null when syncDeckNow then remoteDataSource is never called`() = runTest {
        // Arrange
        givenLoggedOut()

        // Act
        repository.syncDeckNow(deckId = 1L)

        // Assert: no remote call should happen without an authenticated user
        coVerify(exactly = 0) { remoteDataSource.upsertDeck(any()) }
        coVerify(exactly = 0) { remoteDataSource.upsertDeckCards(any(), any()) }
    }

    @Test
    fun `given deck not found when syncDeckNow then remoteDataSource is never called`() = runTest {
        // Arrange
        givenLoggedIn()
        coEvery { deckDao.getDeckById(1L) } returns null

        // Act
        repository.syncDeckNow(deckId = 1L)

        // Assert: no entity found → nothing to push
        coVerify(exactly = 0) { remoteDataSource.upsertDeck(any()) }
    }

    @Test
    fun `given deck with syncStatus SYNCED when syncDeckNow then remoteDataSource is never called`() = runTest {
        // Arrange: deck is already in sync — skip the push to avoid redundant RPC calls
        givenLoggedIn()
        coEvery { deckDao.getDeckById(1L) } returns buildDeckEntity(syncStatus = SyncStatus.SYNCED)

        // Act
        repository.syncDeckNow(deckId = 1L)

        // Assert
        coVerify(exactly = 0) { remoteDataSource.upsertDeck(any()) }
    }

    @Test
    fun `given pending deck when syncDeckNow succeeds then updateSyncStatusAndRemoteId called with SYNCED and remoteId`() = runTest {
        // Arrange
        givenLoggedIn()
        coEvery { deckDao.getDeckById(1L) } returns buildDeckEntity(syncStatus = SyncStatus.PENDING_UPLOAD)
        coEvery { remoteDataSource.upsertDeck(any()) } returns Result.success(REMOTE_ID)
        coEvery { deckDao.getDeckCards(1L) } returns emptyList()
        coEvery { remoteDataSource.upsertDeckCards(any(), any()) } returns Result.success(Unit)

        // Act
        repository.syncDeckNow(deckId = 1L)

        // Assert: the deck is marked SYNCED with its new Supabase UUID
        coVerify(exactly = 1) {
            deckDao.updateSyncStatusAndRemoteId(1L, SyncStatus.SYNCED, REMOTE_ID)
        }
    }

    @Test
    fun `given upsertDeck fails when syncDeckNow then upsertDeckCards is NOT called and sync status stays PENDING_UPLOAD`() = runTest {
        // Arrange: upsertDeck returns a failure Result — stop the chain immediately
        givenLoggedIn()
        coEvery { deckDao.getDeckById(1L) } returns buildDeckEntity(syncStatus = SyncStatus.PENDING_UPLOAD)
        coEvery { remoteDataSource.upsertDeck(any()) } returns Result.failure(RuntimeException("network error"))

        // Act
        repository.syncDeckNow(deckId = 1L)

        // Assert: cards must not be pushed if the deck metadata failed
        coVerify(exactly = 0) { remoteDataSource.upsertDeckCards(any(), any()) }
        // And the local sync_status record must not be updated to SYNCED
        coVerify(exactly = 0) { deckDao.updateSyncStatusAndRemoteId(any(), any(), any()) }
    }

    @Test
    fun `given upsertDeckCards fails when syncDeckNow then updateSyncStatusAndRemoteId is NOT called`() = runTest {
        // Arrange: cards push fails — the deck should remain PENDING_UPLOAD for retry
        givenLoggedIn()
        coEvery { deckDao.getDeckById(1L) } returns buildDeckEntity(syncStatus = SyncStatus.PENDING_UPLOAD)
        coEvery { remoteDataSource.upsertDeck(any()) } returns Result.success(REMOTE_ID)
        coEvery { deckDao.getDeckCards(1L) } returns emptyList()
        coEvery { remoteDataSource.upsertDeckCards(any(), any()) } returns Result.failure(RuntimeException("timeout"))

        // Act
        repository.syncDeckNow(deckId = 1L)

        // Assert: status must not be updated when the cards step fails
        coVerify(exactly = 0) { deckDao.updateSyncStatusAndRemoteId(any(), any(), any()) }
    }

    @Test
    fun `given pending deck with cards when syncDeckNow then upsertDeckCards receives correct card list`() = runTest {
        // Arrange
        givenLoggedIn()
        coEvery { deckDao.getDeckById(1L) } returns buildDeckEntity(syncStatus = SyncStatus.PENDING_UPLOAD)
        coEvery { remoteDataSource.upsertDeck(any()) } returns Result.success(REMOTE_ID)
        coEvery { deckDao.getDeckCards(1L) } returns listOf(
            DeckCardCrossRef(deckId = 1L, scryfallId = "bolt-001", quantity = 4, isSideboard = false),
            DeckCardCrossRef(deckId = 1L, scryfallId = "sol-ring", quantity = 1, isSideboard = false),
        )
        coEvery { remoteDataSource.upsertDeckCards(any(), any()) } returns Result.success(Unit)

        val capturedCards = slot<List<DeckCardDto>>()
        coEvery { remoteDataSource.upsertDeckCards(eq(1L), capture(capturedCards)) } returns Result.success(Unit)

        // Act
        repository.syncDeckNow(deckId = 1L)

        // Assert: both cards are passed, with correct fields
        assertEquals(2, capturedCards.captured.size)
        assertTrue(capturedCards.captured.any { it.scryfallId == "bolt-001" && it.quantity == 4 })
        assertTrue(capturedCards.captured.any { it.scryfallId == "sol-ring" && it.quantity == 1 })
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — deleteDeck with sync
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given userId null when deleteDeck then deckDao deleteDeck called but remoteDataSource deleteDeck NOT called`() = runTest {
        // Arrange: not authenticated — only local delete
        givenLoggedOut()

        // Act
        repository.deleteDeck(5L)

        // Assert
        coVerify(exactly = 1) { deckDao.deleteDeck(5L) }
        coVerify(exactly = 0) { remoteDataSource.deleteDeck(any()) }
    }

    @Test
    fun `given authenticated user when deleteDeck then deckDao deleteDeck AND remoteDataSource deleteDeck are both called with deckId`() = runTest {
        // Arrange
        givenLoggedIn()

        // Act
        repository.deleteDeck(7L)

        // Assert: both local and remote deletes must be triggered
        coVerify(exactly = 1) { deckDao.deleteDeck(7L) }
        coVerify(exactly = 1) { remoteDataSource.deleteDeck(7L) }
    }

    @Test
    fun `given remoteDataSource deleteDeck fails when deleteDeck then local delete still completes (best-effort)`() = runTest {
        // Arrange: remote call wrapped in runCatching — failure must not bubble up
        givenLoggedIn()
        coEvery { remoteDataSource.deleteDeck(any()) } returns Result.failure(RuntimeException("server unavailable"))

        // Act — must not throw
        repository.deleteDeck(9L)

        // Assert: local delete still happened despite remote failure
        coVerify(exactly = 1) { deckDao.deleteDeck(9L) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — updateDeck preserves remoteId
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given deck with existing remoteId when updateDeck then remoteId is preserved in updated entity`() = runTest {
        // Arrange: the entity already has a Supabase UUID — updateDeck must not lose it
        val existingEntity = buildDeckEntity(id = 1L, remoteId = REMOTE_ID, syncStatus = SyncStatus.SYNCED)
        coEvery { deckDao.getDeckById(1L) } returns existingEntity

        val capturedEntity = slot<DeckEntity>()
        coEvery { deckDao.updateDeck(capture(capturedEntity)) } returns Unit

        // Act
        repository.updateDeck(buildDeck(id = 1L, name = "Updated Name"))

        // Assert: the remoteId from the existing DB record is carried through
        assertEquals(REMOTE_ID, capturedEntity.captured.remoteId)
    }

    @Test
    fun `given updateDeck called then syncStatus is set to PENDING_UPLOAD`() = runTest {
        // Arrange: even a SYNCED deck becomes dirty after a local edit
        val existingEntity = buildDeckEntity(id = 1L, remoteId = REMOTE_ID, syncStatus = SyncStatus.SYNCED)
        coEvery { deckDao.getDeckById(1L) } returns existingEntity

        val capturedEntity = slot<DeckEntity>()
        coEvery { deckDao.updateDeck(capture(capturedEntity)) } returns Unit

        // Act
        repository.updateDeck(buildDeck(id = 1L, name = "Updated Name"))

        // Assert: the edit flags the deck as needing a push
        assertEquals(SyncStatus.PENDING_UPLOAD, capturedEntity.captured.syncStatus)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — addCardToDeck / removeCardFromDeck mark dirty
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given card exists when addCardToDeck then markDeckDirty is called after upsertDeckCard`() = runTest {
        // Arrange: card resolves successfully → upsert then mark dirty
        val card = com.mmg.manahub.util.TestFixtures.buildCard(scryfallId = "bolt-001")
        coEvery { cardRepository.getCardById("bolt-001") } returns DataResult.Success(card)

        // Act
        repository.addCardToDeck(deckId = 1L, scryfallId = "bolt-001", quantity = 2, isSideboard = false)

        // Assert: both DAO calls must occur in order
        coVerify(exactly = 1) { deckDao.upsertDeckCard(any()) }
        coVerify(exactly = 1) { deckDao.markDeckDirty(1L, SyncStatus.PENDING_UPLOAD, any()) }
    }

    @Test
    fun `given card not found when addCardToDeck then markDeckDirty is NOT called`() = runTest {
        // Arrange: FK guard fires — upsert is skipped, so dirty mark must also be skipped
        coEvery { cardRepository.getCardById("missing-001") } returns DataResult.Error("not found")

        // Act
        repository.addCardToDeck(deckId = 1L, scryfallId = "missing-001", quantity = 1, isSideboard = false)

        // Assert
        coVerify(exactly = 0) { deckDao.markDeckDirty(any(), any(), any()) }
    }

    @Test
    fun `given removeCardFromDeck called then markDeckDirty is called`() = runTest {
        // Act
        repository.removeCardFromDeck(deckId = 2L, scryfallId = "bolt-001", isSideboard = false)

        // Assert: removing a card makes the deck dirty for sync
        coVerify(exactly = 1) { deckDao.markDeckDirty(2L, SyncStatus.PENDING_UPLOAD, any()) }
    }

    @Test
    fun `given clearDeck called then markDeckDirty is called`() = runTest {
        // Act
        repository.clearDeck(deckId = 3L)

        // Assert: clearing all cards also marks the deck as dirty
        coVerify(exactly = 1) { deckDao.markDeckDirty(3L, SyncStatus.PENDING_UPLOAD, any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — syncAllDirtyDecks
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given userId null when syncAllDirtyDecks then getPendingUploadDecks is never called`() = runTest {
        // Arrange
        givenLoggedOut()

        // Act
        repository.syncAllDirtyDecks()

        // Assert: short-circuits before touching the DAO
        coVerify(exactly = 0) { deckDao.getPendingUploadDecks() }
    }

    @Test
    fun `given 3 pending decks when syncAllDirtyDecks then syncDeckNow is attempted for each`() = runTest {
        // Arrange: three dirty decks waiting to be pushed
        givenLoggedIn()
        val pendingDecks = listOf(
            buildDeckEntity(id = 1L, syncStatus = SyncStatus.PENDING_UPLOAD),
            buildDeckEntity(id = 2L, syncStatus = SyncStatus.PENDING_UPLOAD),
            buildDeckEntity(id = 3L, syncStatus = SyncStatus.PENDING_UPLOAD),
        )
        coEvery { deckDao.getPendingUploadDecks() } returns pendingDecks
        // Each syncDeckNow will re-query getDeckById for the entity
        pendingDecks.forEach { entity ->
            coEvery { deckDao.getDeckById(entity.id) } returns entity
            coEvery { remoteDataSource.upsertDeck(any()) } returns Result.success(REMOTE_ID)
            coEvery { deckDao.getDeckCards(entity.id) } returns emptyList()
            coEvery { remoteDataSource.upsertDeckCards(entity.id, any()) } returns Result.success(Unit)
        }

        // Act
        repository.syncAllDirtyDecks()

        // Assert: upsertDeck called once per deck
        coVerify(exactly = 3) { remoteDataSource.upsertDeck(any()) }
    }

    @Test
    fun `given one sync fails when syncAllDirtyDecks then others still proceed (runCatching isolation)`() = runTest {
        // Arrange: second deck throws — the third must still be attempted
        givenLoggedIn()
        val pendingDecks = listOf(
            buildDeckEntity(id = 10L, syncStatus = SyncStatus.PENDING_UPLOAD),
            buildDeckEntity(id = 11L, syncStatus = SyncStatus.PENDING_UPLOAD),
            buildDeckEntity(id = 12L, syncStatus = SyncStatus.PENDING_UPLOAD),
        )
        coEvery { deckDao.getPendingUploadDecks() } returns pendingDecks

        coEvery { deckDao.getDeckById(10L) } returns pendingDecks[0]
        coEvery { deckDao.getDeckById(11L) } returns pendingDecks[1]
        coEvery { deckDao.getDeckById(12L) } returns pendingDecks[2]

        // Deck 10 succeeds
        coEvery { deckDao.getDeckCards(10L) } returns emptyList()
        coEvery { remoteDataSource.upsertDeckCards(10L, any()) } returns Result.success(Unit)

        // Deck 11 fails at upsertDeck — simulates the runCatching catching the exception
        coEvery { deckDao.getDeckCards(11L) } returns emptyList()

        // Deck 12 succeeds
        coEvery { deckDao.getDeckCards(12L) } returns emptyList()
        coEvery { remoteDataSource.upsertDeckCards(12L, any()) } returns Result.success(Unit)

        coEvery { remoteDataSource.upsertDeck(any()) }
            .returnsMany(
                Result.success(REMOTE_ID),              // deck 10
                Result.failure(RuntimeException("fail")), // deck 11
                Result.success(REMOTE_ID),              // deck 12
            )

        // Act — must not throw despite deck 11 failing
        repository.syncAllDirtyDecks()

        // Assert: upsertDeck was tried for all three decks
        coVerify(exactly = 3) { remoteDataSource.upsertDeck(any()) }
        // Deck 10 and 12 should be marked SYNCED; deck 11 should not
        coVerify(exactly = 1) { deckDao.updateSyncStatusAndRemoteId(10L, SyncStatus.SYNCED, REMOTE_ID) }
        coVerify(exactly = 0) { deckDao.updateSyncStatusAndRemoteId(11L, any(), any()) }
        coVerify(exactly = 1) { deckDao.updateSyncStatusAndRemoteId(12L, SyncStatus.SYNCED, REMOTE_ID) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 9 — pullIfStale
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given userId null when pullIfStale then getLastModified is never called`() = runTest {
        // Arrange
        givenLoggedOut()

        // Act
        repository.pullIfStale()

        // Assert: short-circuits before hitting the remote
        coVerify(exactly = 0) { remoteDataSource.getLastModified() }
    }

    @Test
    fun `given remote is not newer than local when pullIfStale then getDecksChangedSince is never called`() = runTest {
        // Arrange: remote MAX(updated_at) is older than local → no delta needed
        givenLoggedIn()
        val localMaxMs = System.currentTimeMillis()
        val remoteInstant = Instant.ofEpochMilli(localMaxMs - 10_000)   // 10 s in the past

        coEvery { remoteDataSource.getLastModified() } returns remoteInstant
        coEvery { deckDao.getMaxUpdatedAt() } returns localMaxMs

        // Act
        repository.pullIfStale()

        // Assert
        coVerify(exactly = 0) { remoteDataSource.getDecksChangedSince(any()) }
    }

    @Test
    fun `given remote is newer when pullIfStale then getDecksChangedSince is called with correct since timestamp`() = runTest {
        // Arrange: server has changes after our local watermark
        givenLoggedIn()
        val localMaxMs = 1_000_000L
        val remoteInstant = Instant.ofEpochMilli(localMaxMs + 60_000)  // 1 minute ahead

        coEvery { remoteDataSource.getLastModified() } returns remoteInstant
        coEvery { deckDao.getMaxUpdatedAt() } returns localMaxMs
        coEvery { remoteDataSource.getDecksChangedSince(any()) } returns Result.success(emptyList())

        // Act
        repository.pullIfStale()

        // Assert: called with the local watermark converted to an Instant
        val expectedSince = Instant.ofEpochMilli(localMaxMs)
        coVerify(exactly = 1) { remoteDataSource.getDecksChangedSince(expectedSince) }
    }

    @Test
    fun `given remote has deck with matching remoteId and newer updatedAt when pullIfStale then deckDao updateDeck is called`() = runTest {
        // Arrange: remote deck is strictly newer than local — we must overwrite
        givenLoggedIn()
        val localMaxMs   = 1_000_000L
        val localUpdated = 500_000L   // older than remote
        val remoteUpdatedIso = Instant.ofEpochMilli(localMaxMs + 5_000).toString()

        coEvery { remoteDataSource.getLastModified() } returns Instant.ofEpochMilli(localMaxMs + 10_000)
        coEvery { deckDao.getMaxUpdatedAt() } returns localMaxMs

        val localEntity = buildDeckEntity(id = 1L, remoteId = REMOTE_ID, updatedAt = localUpdated)
        coEvery { deckDao.getDeckByRemoteId(REMOTE_ID) } returns localEntity
        coEvery { remoteDataSource.getDecksChangedSince(any()) } returns Result.success(
            listOf(buildDeckDto(id = REMOTE_ID, updatedAt = remoteUpdatedIso))
        )

        // Act
        repository.pullIfStale()

        // Assert: updateDeck must be called to apply the remote changes
        coVerify(exactly = 1) { deckDao.updateDeck(any()) }
    }

    @Test
    fun `given remote has deck with matching remoteId but older updatedAt when pullIfStale then deckDao updateDeck is NOT called`() = runTest {
        // Arrange: local copy is newer — the remote update is stale, skip it
        givenLoggedIn()
        val localMaxMs    = 1_000_000L
        val localUpdated  = 900_000L
        // Remote timestamp is older than localUpdated
        val remoteUpdatedIso = Instant.ofEpochMilli(500_000L).toString()

        coEvery { remoteDataSource.getLastModified() } returns Instant.ofEpochMilli(localMaxMs + 10_000)
        coEvery { deckDao.getMaxUpdatedAt() } returns localMaxMs

        val localEntity = buildDeckEntity(id = 1L, remoteId = REMOTE_ID, updatedAt = localUpdated)
        coEvery { deckDao.getDeckByRemoteId(REMOTE_ID) } returns localEntity
        coEvery { remoteDataSource.getDecksChangedSince(any()) } returns Result.success(
            listOf(buildDeckDto(id = REMOTE_ID, updatedAt = remoteUpdatedIso))
        )

        // Act
        repository.pullIfStale()

        // Assert: local is already newer — no write needed
        coVerify(exactly = 0) { deckDao.updateDeck(any()) }
    }

    @Test
    fun `given getDecksChangedSince fails when pullIfStale then no deckDao write occurs`() = runTest {
        // Arrange: remote call fails — pullIfStale must be a no-op and not crash
        givenLoggedIn()
        val localMaxMs = 1_000_000L
        coEvery { remoteDataSource.getLastModified() } returns Instant.ofEpochMilli(localMaxMs + 10_000)
        coEvery { deckDao.getMaxUpdatedAt() } returns localMaxMs
        coEvery { remoteDataSource.getDecksChangedSince(any()) } returns
            Result.failure(RuntimeException("server error"))

        // Act — must not throw
        repository.pullIfStale()

        // Assert: no local writes when the delta fetch fails
        coVerify(exactly = 0) { deckDao.updateDeck(any()) }
        coVerify(exactly = 0) { deckDao.insertDeck(any()) }
    }
}
