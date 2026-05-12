package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.DeckSummaryRow
import com.mmg.manahub.core.data.local.entity.DeckCardEntity
import com.mmg.manahub.core.data.local.entity.DeckEntity
import com.mmg.manahub.core.domain.model.Deck
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DeckRepositoryImpl].
 *
 * The repository is local-first: all mutations write to Room and bump [DeckEntity.updatedAt].
 * Sync to Supabase is [com.mmg.manahub.core.sync.SyncManager]'s responsibility and is NOT
 * tested here. All DAO calls are mocked — no real database is used.
 *
 * Covers:
 *  - GROUP 1: createDeck  — UUID generation, entity construction, DAO delegation
 *  - GROUP 2: updateDeck  — early return when entity not found, field propagation
 *  - GROUP 3: deleteDeck  — soft delete via softDeleteDeck (not hard delete)
 *  - GROUP 4: addCardToDeck — card entity construction, deck updatedAt bump
 *  - GROUP 5: removeCardFromDeck — DAO delegation, deck updatedAt bump
 *  - GROUP 6: clearDeck   — DAO delegation, deck updatedAt bump
 *  - GROUP 7: observeAllDeckSummaries — groupBy, cardCount, colorIdentity, sorting
 */
class DeckRepositoryImplTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val deckDao = mockk<DeckDao>(relaxed = true)
    private lateinit var repository: DeckRepositoryImpl

    // ── Constants ─────────────────────────────────────────────────────────────

    private val DECK_ID = "deck-uuid-001"

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private fun buildDeckEntity(
        id:          String  = DECK_ID,
        name:        String  = "My Deck",
        format:      String  = "commander",
        description: String  = "",
        coverCardId: String? = null,
        isDeleted:   Boolean = false,
        createdAt:   Long    = 1_000L,
        updatedAt:   Long    = 2_000L,
    ) = DeckEntity(
        id          = id,
        userId      = null,
        name        = name,
        format      = format,
        description = description,
        coverCardId = coverCardId,
        isDeleted   = isDeleted,
        createdAt   = createdAt,
        updatedAt   = updatedAt,
    )

    private fun buildSummaryRow(
        deckId:        String  = DECK_ID,
        name:          String  = "My Deck",
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
        description   = "",
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
        repository = DeckRepositoryImpl(
            deckDao      = deckDao,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — createDeck
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid inputs when createDeck then deckDao upsertDeck is called once`() = runTest {
        repository.createDeck(name = "Commander Deck", description = "My build", format = "commander")

        verify(exactly = 1) { deckDao.upsertDeck(any()) }
    }

    @Test
    fun `given valid inputs when createDeck then returned id is a non-blank UUID`() = runTest {
        val id = repository.createDeck("Commander Deck", "description", "commander")

        assertTrue(id.isNotBlank())
        assertEquals(36, id.length)  // UUID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
    }

    @Test
    fun `given same inputs when createDeck called twice then two distinct ids are returned`() = runTest {
        val id1 = repository.createDeck("Deck A", "", "casual")
        val id2 = repository.createDeck("Deck B", "", "casual")

        assertTrue(id1 != id2)
    }

    @Test
    fun `given valid inputs when createDeck then entity has correct name format and description`() = runTest {
        val captured = slot<DeckEntity>()
        every { deckDao.upsertDeck(capture(captured)) } returns Unit

        repository.createDeck(name = "Burn Deck", description = "fast aggro", format = "pioneer")

        assertEquals("Burn Deck",   captured.captured.name)
        assertEquals("fast aggro",  captured.captured.description)
        assertEquals("pioneer",     captured.captured.format)
    }

    @Test
    fun `given valid inputs when createDeck then entity isDeleted is false`() = runTest {
        val captured = slot<DeckEntity>()
        every { deckDao.upsertDeck(capture(captured)) } returns Unit

        repository.createDeck("New Deck", "", "casual")

        assertFalse(captured.captured.isDeleted)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — updateDeck
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given deck exists when updateDeck then deckDao upsertDeck is called with updated name`() = runTest {
        every { deckDao.getDeckById(DECK_ID) } returns buildDeckEntity()
        val captured = slot<DeckEntity>()
        every { deckDao.upsertDeck(capture(captured)) } returns Unit

        repository.updateDeck(Deck(id = DECK_ID, name = "Updated Name", format = "commander"))

        assertEquals("Updated Name", captured.captured.name)
    }

    @Test
    fun `given deck not found when updateDeck then deckDao upsertDeck is NOT called`() = runTest {
        every { deckDao.getDeckById(DECK_ID) } returns null

        repository.updateDeck(Deck(id = DECK_ID, name = "Ghost", format = "commander"))

        verify(exactly = 0) { deckDao.upsertDeck(any()) }
    }

    @Test
    fun `given deck exists when updateDeck then updatedAt is bumped`() = runTest {
        every { deckDao.getDeckById(DECK_ID) } returns buildDeckEntity(updatedAt = 100L)
        val captured = slot<DeckEntity>()
        every { deckDao.upsertDeck(capture(captured)) } returns Unit

        repository.updateDeck(Deck(id = DECK_ID, name = "New Name", format = "commander"))

        assertTrue("updatedAt must be bumped", captured.captured.updatedAt > 100L)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — deleteDeck (soft delete)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given deck id when deleteDeck then deckDao softDeleteDeck is called`() = runTest {
        repository.deleteDeck(DECK_ID)

        verify(exactly = 1) { deckDao.softDeleteDeck(eq(DECK_ID), any()) }
    }

    @Test
    fun `given deck id when deleteDeck then deckDao upsertDeck is NOT called (no hard replace)`() = runTest {
        repository.deleteDeck(DECK_ID)

        verify(exactly = 0) { deckDao.upsertDeck(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — addCardToDeck
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given card info when addCardToDeck then deckDao upsertDeckCard is called`() = runTest {
        every { deckDao.getDeckById(DECK_ID) } returns buildDeckEntity()

        repository.addCardToDeck(deckId = DECK_ID, scryfallId = "bolt-001", quantity = 4, isSideboard = false)

        verify(exactly = 1) { deckDao.upsertDeckCard(any()) }
    }

    @Test
    fun `given card info when addCardToDeck then entity has correct deckId scryfallId quantity and isSideboard`() = runTest {
        every { deckDao.getDeckById(DECK_ID) } returns buildDeckEntity()
        val captured = slot<DeckCardEntity>()
        every { deckDao.upsertDeckCard(capture(captured)) } returns Unit

        repository.addCardToDeck(deckId = DECK_ID, scryfallId = "bolt-001", quantity = 3, isSideboard = true)

        assertEquals(DECK_ID,    captured.captured.deckId)
        assertEquals("bolt-001", captured.captured.scryfallId)
        assertEquals(3,          captured.captured.quantity)
        assertTrue(              captured.captured.isSideboard)
    }

    @Test
    fun `given deck exists when addCardToDeck then deck updatedAt is bumped`() = runTest {
        every { deckDao.getDeckById(DECK_ID) } returns buildDeckEntity(updatedAt = 100L)
        val captured = slot<DeckEntity>()
        every { deckDao.upsertDeck(capture(captured)) } returns Unit

        repository.addCardToDeck(DECK_ID, "bolt-001", 1, false)

        assertTrue("updatedAt must be bumped", captured.captured.updatedAt > 100L)
    }

    @Test
    fun `given deck not found when addCardToDeck then card is still upserted`() = runTest {
        every { deckDao.getDeckById(DECK_ID) } returns null

        repository.addCardToDeck(DECK_ID, "island-001", 1, false)

        // Card upsert always happens; deck-bump is skipped silently when deck not found
        verify(exactly = 1) { deckDao.upsertDeckCard(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — removeCardFromDeck
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given card info when removeCardFromDeck then deckDao removeDeckCard is called with correct params`() = runTest {
        every { deckDao.getDeckById(DECK_ID) } returns buildDeckEntity()

        repository.removeCardFromDeck(deckId = DECK_ID, scryfallId = "bolt-001", isSideboard = false)

        verify(exactly = 1) { deckDao.removeDeckCard(DECK_ID, "bolt-001", false) }
    }

    @Test
    fun `given deck exists when removeCardFromDeck then deck updatedAt is bumped`() = runTest {
        every { deckDao.getDeckById(DECK_ID) } returns buildDeckEntity(updatedAt = 100L)
        val captured = slot<DeckEntity>()
        every { deckDao.upsertDeck(capture(captured)) } returns Unit

        repository.removeCardFromDeck(DECK_ID, "bolt-001", false)

        assertTrue("updatedAt must be bumped", captured.captured.updatedAt > 100L)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — clearDeck
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given deck id when clearDeck then deckDao clearDeckCards is called`() = runTest {
        every { deckDao.getDeckById(DECK_ID) } returns buildDeckEntity()

        repository.clearDeck(DECK_ID)

        verify(exactly = 1) { deckDao.clearDeckCards(DECK_ID) }
    }

    @Test
    fun `given deck exists when clearDeck then deck updatedAt is bumped`() = runTest {
        every { deckDao.getDeckById(DECK_ID) } returns buildDeckEntity(updatedAt = 100L)
        val captured = slot<DeckEntity>()
        every { deckDao.upsertDeck(capture(captured)) } returns Unit

        repository.clearDeck(DECK_ID)

        assertTrue("updatedAt must be bumped", captured.captured.updatedAt > 100L)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — observeAllDeckSummaries: groupBy and aggregation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given 2 mainboard cards when observeAllDeckSummaries then cardCount is sum of quantities`() = runTest {
        val rows = listOf(
            buildSummaryRow(deckId = "d1", name = "Mono-R", scryfallId = "bolt",   quantity = 2, isSideboard = false),
            buildSummaryRow(deckId = "d1", name = "Mono-R", scryfallId = "island", quantity = 3, isSideboard = false),
        )
        every { deckDao.observeDeckSummaryRows() } returns flowOf(rows)

        val summaries = repository.observeAllDeckSummaries().first()

        assertEquals(1, summaries.size)
        assertEquals(5, summaries[0].cardCount)
    }

    @Test
    fun `given sideboard cards when observeAllDeckSummaries then sideboard cards excluded from cardCount`() = runTest {
        val rows = listOf(
            buildSummaryRow(deckId = "d1", name = "D1", scryfallId = "card-A", quantity = 4, isSideboard = false),
            buildSummaryRow(deckId = "d1", name = "D1", scryfallId = "card-B", quantity = 3, isSideboard = true),
        )
        every { deckDao.observeDeckSummaryRows() } returns flowOf(rows)

        val summaries = repository.observeAllDeckSummaries().first()

        assertEquals(4, summaries[0].cardCount)
    }

    @Test
    fun `given 2 decks when observeAllDeckSummaries then both summaries are returned`() = runTest {
        val rows = listOf(
            buildSummaryRow(deckId = "d1", name = "Deck A", scryfallId = "c1", quantity = 1, updatedAt = 2_000L),
            buildSummaryRow(deckId = "d2", name = "Deck B", scryfallId = "c2", quantity = 1, updatedAt = 1_000L),
        )
        every { deckDao.observeDeckSummaryRows() } returns flowOf(rows)

        val summaries = repository.observeAllDeckSummaries().first()

        assertEquals(2, summaries.size)
        val names = summaries.map { it.name }
        assertTrue(names.contains("Deck A"))
        assertTrue(names.contains("Deck B"))
    }

    @Test
    fun `given deck with null scryfallId when observeAllDeckSummaries then cardCount is 0`() = runTest {
        // LEFT JOIN returns a row with null scryfallId when deck has no cards
        val rows = listOf(
            buildSummaryRow(deckId = "d1", name = "Empty Deck", scryfallId = null, quantity = 0),
        )
        every { deckDao.observeDeckSummaryRows() } returns flowOf(rows)

        val summaries = repository.observeAllDeckSummaries().first()

        assertEquals(1, summaries.size)
        assertEquals(0, summaries[0].cardCount)
    }

    @Test
    fun `given empty rows when observeAllDeckSummaries then returns empty list`() = runTest {
        every { deckDao.observeDeckSummaryRows() } returns flowOf(emptyList())

        val summaries = repository.observeAllDeckSummaries().first()

        assertTrue(summaries.isEmpty())
    }

    @Test
    fun `given summaries when observeAllDeckSummaries then sorted by updatedAt descending`() = runTest {
        val rows = listOf(
            buildSummaryRow(deckId = "d1", name = "Older Deck", updatedAt = 1_000L),
            buildSummaryRow(deckId = "d2", name = "Newer Deck", updatedAt = 9_000L),
        )
        every { deckDao.observeDeckSummaryRows() } returns flowOf(rows)

        val summaries = repository.observeAllDeckSummaries().first()

        assertEquals("Newer Deck", summaries[0].name)
        assertEquals("Older Deck", summaries[1].name)
    }

    @Test
    fun `given deck with overlapping colorIdentity when observeAllDeckSummaries then union set is built correctly`() = runTest {
        val rows = listOf(
            buildSummaryRow(deckId = "d1", name = "D", scryfallId = "c1",
                colorIdentity = """["R","G"]""", quantity = 1),
            buildSummaryRow(deckId = "d1", name = "D", scryfallId = "c2",
                colorIdentity = """["G","U"]""", quantity = 1),
        )
        every { deckDao.observeDeckSummaryRows() } returns flowOf(rows)

        val summaries = repository.observeAllDeckSummaries().first()

        assertEquals(setOf("R", "G", "U"), summaries[0].colorIdentity)
    }

    @Test
    fun `given deck with malformed colorIdentity when observeAllDeckSummaries then no crash and empty color set`() = runTest {
        val rows = listOf(
            buildSummaryRow(deckId = "d1", name = "D", scryfallId = "c1",
                colorIdentity = "not_valid_json", quantity = 1),
        )
        every { deckDao.observeDeckSummaryRows() } returns flowOf(rows)

        // Must not crash — invalid JSON is silently skipped
        val summaries = repository.observeAllDeckSummaries().first()

        assertTrue(summaries[0].colorIdentity.isEmpty())
    }
}
