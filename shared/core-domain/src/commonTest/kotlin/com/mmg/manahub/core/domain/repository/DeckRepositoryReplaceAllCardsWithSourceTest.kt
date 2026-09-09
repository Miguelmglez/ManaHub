package com.mmg.manahub.core.domain.repository
// COMMENTS_REVIEWED: 2026-09-09

import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.model.DeckWithCards
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Minimal in-memory [DeckRepository] fake exercising ONLY [DeckRepository.replaceAllCardsWithSource]'s
 * default-method composition (clearDeck + addCardToDeck per slot) -- every other member is `error("unused")`
 * since this suite only cares about that one code path. */
private class FakeAtomicityDeckRepository : DeckRepository {
    val mainboard = mutableListOf<CardSlotWrite>()
    var clearDeckCallCount = 0
        private set
    var addCardToDeckCallCount = 0
        private set

    /** When set, [addCardToDeck] throws once it is called for this scryfallId -- simulating a
     * mid-loop failure (a Room write error, a coroutine cancellation) partway through the default
     * `replaceAllCardsWithSource` composition. */
    var failOnScryfallId: String? = null

    override fun observeAllDecks(): Flow<List<Deck>> = error("unused")
    override fun observeAllDeckSummaries(): Flow<List<DeckSummary>> = error("unused")
    override fun observeDecksContainingCard(scryfallId: String): Flow<List<Deck>> = error("unused")
    override fun observeDeckWithCards(deckId: String): Flow<DeckWithCards?> = flowOf(null)
    override suspend fun createDeck(name: String, description: String, format: String): String = error("unused")
    override suspend fun updateDeck(deck: Deck) = error("unused")
    override suspend fun deleteDeck(deckId: String) = error("unused")
    override suspend fun addCardToDeck(deckId: String, scryfallId: String, quantity: Int, isSideboard: Boolean, source: DeckCardSource) {
        addCardToDeckCallCount++
        if (scryfallId == failOnScryfallId) throw IllegalStateException("simulated write failure on $scryfallId")
        mainboard += CardSlotWrite(scryfallId, quantity, isSideboard, source)
    }
    override suspend fun removeCardFromDeck(deckId: String, scryfallId: String, isSideboard: Boolean) = error("unused")
    override suspend fun moveCardQuantity(deckId: String, scryfallId: String, fromSideboard: Boolean, quantity: Int) = error("unused")
    override suspend fun clearDeck(deckId: String) {
        clearDeckCallCount++
        mainboard.clear()
    }
    override suspend fun updateDeckAttribution(deckId: String, sourceUrl: String?, sourceAuthor: String?, sourceService: String?, importedAt: Long?) = error("unused")
    override suspend fun replaceAllCards(deckId: String, slots: List<Triple<String, Int, Boolean>>) = error("unused")
    override suspend fun updateArchetypeOverride(deckId: String, archetypeOverride: String?, themesOverride: List<String>, posture: String?) = error("unused")
    override suspend fun updateTribeOverride(deckId: String, tribeOverride: String?) = error("unused")
    override suspend fun updateStrategyLocked(deckId: String, locked: Boolean) = error("unused")
}

/**
 * Deck Wizard Commander v3 plan, Phase 2 gate item D — write-path test for
 * [DeckRepository.replaceAllCardsWithSource] "at whatever level is testable today" (the campaign
 * instruction, since Run 3 documented this default method as NOT a real Room transaction — see
 * `docs/deck-wizard-state.md`'s Phase 2 changelog note 4 / the progress tracker's Run 3 note 4).
 *
 * This suite proves BOTH halves honestly: the happy path replaces atomically-in-EFFECT (old
 * contents gone, new contents present, in one logical call), and the failure path proves the
 * documented gap is real -- a mid-loop exception leaves the deck EMPTY (clearDeck already ran) with
 * only the slots placed before the failure, not the pre-call state. This is exactly D12's
 * cancellation-safety guarantee being UNMET, captured as a running regression test rather than a
 * prose claim -- when Phase 6 replaces this with a real `@Transaction` DAO override, the failure-path
 * assertions in this file are expected to FLIP (the deck should show its OLD contents unchanged, not
 * an empty/partial board), which is the intended signal that the hardening landed.
 */
class DeckRepositoryReplaceAllCardsWithSourceTest {

    @Test
    fun `happy path -- replaces the full mainboard in one logical call`() = runTest {
        val repo = FakeAtomicityDeckRepository()
        repo.mainboard += CardSlotWrite("old-card-1", 1)
        repo.mainboard += CardSlotWrite("old-card-2", 1)

        repo.replaceAllCardsWithSource(
            "deck-1",
            listOf(
                CardSlotWrite("new-card-1", 1, source = DeckCardSource.WIZARD),
                CardSlotWrite("new-card-2", 1, source = DeckCardSource.USER),
            ),
        )

        assertEquals(1, repo.clearDeckCallCount)
        assertEquals(2, repo.addCardToDeckCallCount)
        assertEquals(setOf("new-card-1", "new-card-2"), repo.mainboard.map { it.scryfallId }.toSet())
        assertTrue("old-card-1" !in repo.mainboard.map { it.scryfallId }, "old contents must be fully replaced")
    }

    @Test
    fun `failure path -- a mid-loop exception leaves the deck EMPTY, not unchanged (D12 UNMET, documented not fixed)`() = runTest {
        val repo = FakeAtomicityDeckRepository()
        repo.mainboard += CardSlotWrite("pre-existing-card", 1)
        repo.failOnScryfallId = "card-3"

        assertFailsWith<IllegalStateException> {
            repo.replaceAllCardsWithSource(
                "deck-1",
                listOf(
                    CardSlotWrite("card-1", 1),
                    CardSlotWrite("card-2", 1),
                    CardSlotWrite("card-3", 1), // throws here
                    CardSlotWrite("card-4", 1), // never reached
                ),
            )
        }

        // The REAL, documented gap: clearDeck already committed before the loop failed, so the
        // draft is neither "fully replaced" nor "left untouched" -- it is PARTIALLY written (card-1
        // and card-2 present, card-3/card-4 missing, the pre-existing card gone). A real Room
        // @Transaction override would roll this back to the pre-existing state instead.
        assertEquals(1, repo.clearDeckCallCount, "clearDeck runs unconditionally before the loop -- this IS the atomicity gap")
        assertEquals(setOf("card-1", "card-2"), repo.mainboard.map { it.scryfallId }.toSet())
        assertTrue("pre-existing-card" !in repo.mainboard.map { it.scryfallId }, "the pre-existing draft is already gone by the time the failure surfaces -- NOT 'untouched'")
        assertTrue("card-3" !in repo.mainboard.map { it.scryfallId } && "card-4" !in repo.mainboard.map { it.scryfallId })
    }
}
