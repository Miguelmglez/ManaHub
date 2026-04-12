package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.DeckSummaryRow
import com.mmg.manahub.core.data.local.entity.DeckCardCrossRef
import com.mmg.manahub.core.data.local.entity.DeckEntity
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.repository.CardRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
 */
class DeckRepositoryImplTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val deckDao        = mockk<DeckDao>(relaxed = true)
    private val cardRepository = mockk<CardRepository>()
    private lateinit var repository: DeckRepositoryImpl

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private fun buildDeckEntity(
        id:          Long   = 1L,
        name:        String = "My Deck",
        format:      String = "commander",
        description: String? = null,
        coverCardId: String? = null,
        createdAt:   Long   = 1_000L,
        updatedAt:   Long   = 2_000L,
    ) = DeckEntity(
        id          = id,
        name        = name,
        format      = format,
        description = description,
        coverCardId = coverCardId,
        createdAt   = createdAt,
        updatedAt   = updatedAt,
    )

    private fun buildDeck(
        id:          Long   = 1L,
        name:        String = "My Deck",
        format:      String = "commander",
        description: String? = null,
        coverCardId: String? = null,
        createdAt:   Long   = 1_000L,
        updatedAt:   Long   = 2_000L,
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

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        repository = DeckRepositoryImpl(deckDao, cardRepository)
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
        assertEquals(7L,       capturedRef.captured.deckId)
        assertEquals("bolt-001", capturedRef.captured.scryfallId)
        assertEquals(3,        capturedRef.captured.quantity)
        assertTrue(            capturedRef.captured.isSideboard)
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
        // Arrange
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
}
