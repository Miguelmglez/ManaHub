package com.mmg.manahub.core.domain.repository
// COMMENTS_REVIEWED: 2026-09-21

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
import kotlin.test.assertNull

/** Exercises ONLY [DeckRepository.persistWizardBuild]'s commonMain default composition; every
 * member it does not call is `error("unused")`. */
private class FakePersistDeckRepository(private val existing: DeckWithCards?) : DeckRepository {
    val slots = mutableListOf<CardSlotWrite>()
    var updatedDeck: Deck? = null
        private set

    override fun observeAllDecks(): Flow<List<Deck>> = error("unused")
    override fun observeAllDeckSummaries(): Flow<List<DeckSummary>> = error("unused")
    override fun observeDecksContainingCard(scryfallId: String): Flow<List<Deck>> = error("unused")
    override fun observeDeckWithCards(deckId: String): Flow<DeckWithCards?> = flowOf(existing)
    override suspend fun createDeck(name: String, description: String, format: String): String = error("unused")
    override suspend fun updateDeck(deck: Deck) { updatedDeck = deck }
    override suspend fun deleteDeck(deckId: String) = error("unused")
    override suspend fun addCardToDeck(deckId: String, scryfallId: String, quantity: Int, isSideboard: Boolean, source: DeckCardSource) {
        slots += CardSlotWrite(scryfallId, quantity, isSideboard, source)
    }
    override suspend fun removeCardFromDeck(deckId: String, scryfallId: String, isSideboard: Boolean) = error("unused")
    override suspend fun moveCardQuantity(deckId: String, scryfallId: String, fromSideboard: Boolean, quantity: Int) = error("unused")
    override suspend fun clearDeck(deckId: String) { slots.clear() }
    override suspend fun updateDeckAttribution(deckId: String, sourceUrl: String?, sourceAuthor: String?, sourceService: String?, importedAt: Long?) = error("unused")
    override suspend fun replaceAllCards(deckId: String, slots: List<Triple<String, Int, Boolean>>) = error("unused")
    override suspend fun updateArchetypeOverride(deckId: String, archetypeOverride: String?, themesOverride: List<String>, posture: String?) = Unit
    override suspend fun updateTribeOverride(deckId: String, tribeOverride: String?) = Unit
    override suspend fun updateStrategyLocked(deckId: String, locked: Boolean) = Unit
}

class DeckRepositoryPersistWizardBuildDefaultTest {

    private val deck = Deck(id = "deck-1", name = "Wizard Deck", format = "commander")

    @Test
    fun `default method writes the commander onto the existing deck`() = runTest {
        val repo = FakePersistDeckRepository(DeckWithCards(deck = deck, mainboard = emptyList(), sideboard = emptyList()))

        repo.persistWizardBuild("deck-1", listOf(CardSlotWrite("card-1", 1)), null, emptyList(), null, null, true, commanderCardId = "cmd-1")

        assertEquals(listOf(CardSlotWrite("card-1", 1)), repo.slots)
        assertEquals("cmd-1", repo.updatedDeck?.commanderCardId)
        assertEquals("cmd-1", repo.updatedDeck?.coverCardId)
    }

    @Test
    fun `default method fails loudly when the commander write finds no deck, never skipping it`() = runTest {
        val repo = FakePersistDeckRepository(existing = null)

        assertFailsWith<IllegalStateException> {
            repo.persistWizardBuild("deck-1", listOf(CardSlotWrite("card-1", 1)), null, emptyList(), null, null, true, commanderCardId = "cmd-1")
        }
        assertNull(repo.updatedDeck)
    }

    @Test
    fun `default method never touches the deck row for a 60-card build`() = runTest {
        val repo = FakePersistDeckRepository(existing = null)

        repo.persistWizardBuild("deck-1", listOf(CardSlotWrite("card-1", 4)), null, emptyList(), null, null, true, commanderCardId = null)

        assertNull(repo.updatedDeck)
        assertEquals(4, repo.slots.single().quantity)
    }
}
