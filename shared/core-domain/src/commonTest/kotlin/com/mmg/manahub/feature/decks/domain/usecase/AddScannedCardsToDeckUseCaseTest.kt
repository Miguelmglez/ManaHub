package com.mmg.manahub.feature.decks.domain.usecase
// COMMENTS_REVIEWED: 2026-09-16

import com.mmg.manahub.core.domain.repository.CardSlotWrite
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.model.DeckWithCards
import com.mmg.manahub.core.model.DeckSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class FakeScannedDeckRepository(
    var deck: DeckWithCards?,
) : DeckRepository {
    val replaceCalls = mutableListOf<Pair<String, List<CardSlotWrite>>>()
    var replacementFailure: Throwable? = null

    override fun observeAllDecks(): Flow<List<Deck>> = error("unused")
    override fun observeAllDeckSummaries(): Flow<List<DeckSummary>> = error("unused")
    override fun observeDecksContainingCard(scryfallId: String): Flow<List<Deck>> = error("unused")
    override fun observeDeckWithCards(deckId: String): Flow<DeckWithCards?> = flowOf(deck)
    override suspend fun createDeck(name: String, description: String, format: String): String = error("unused")
    override suspend fun updateDeck(deck: Deck) = error("unused")
    override suspend fun deleteDeck(deckId: String) = error("unused")
    override suspend fun addCardToDeck(
        deckId: String,
        scryfallId: String,
        quantity: Int,
        isSideboard: Boolean,
        source: DeckCardSource,
    ) = error("unused")
    override suspend fun removeCardFromDeck(deckId: String, scryfallId: String, isSideboard: Boolean) = error("unused")
    override suspend fun moveCardQuantity(deckId: String, scryfallId: String, fromSideboard: Boolean, quantity: Int) = error("unused")
    override suspend fun clearDeck(deckId: String) = error("unused")
    override suspend fun updateDeckAttribution(deckId: String, sourceUrl: String?, sourceAuthor: String?, sourceService: String?, importedAt: Long?) = error("unused")
    override suspend fun replaceAllCards(deckId: String, slots: List<Triple<String, Int, Boolean>>) = error("unused")
    override suspend fun updateArchetypeOverride(deckId: String, archetypeOverride: String?, themesOverride: List<String>, posture: String?) = error("unused")
    override suspend fun updateTribeOverride(deckId: String, tribeOverride: String?) = error("unused")
    override suspend fun updateStrategyLocked(deckId: String, locked: Boolean) = error("unused")
    override suspend fun replaceAllCardsWithSource(deckId: String, slots: List<CardSlotWrite>) {
        replacementFailure?.let { throw it }
        replaceCalls += deckId to slots
    }
}

private fun deckWithCards(
    commanderCardId: String? = null,
    mainboard: List<DeckSlot> = emptyList(),
    sideboard: List<DeckSlot> = emptyList(),
) = DeckWithCards(
    deck = Deck(id = "deck-1", name = "Test deck", commanderCardId = commanderCardId),
    mainboard = mainboard,
    sideboard = sideboard,
)

private fun scanned(entryId: String, scryfallId: String, quantity: Int = 1) = ScannedDeckCardInput(entryId, scryfallId, quantity)

class AddScannedCardsToDeckUseCaseTest {

    @Test
    fun addsNewEntriesToMainboardAndUsesUserSource() = runTest {
        val repository = FakeScannedDeckRepository(deckWithCards())

        val result = AddScannedCardsToDeckUseCase(repository)("deck-1", listOf(scanned("entry-1", "card-1", 2)), DeckBoard.MAINBOARD)

        assertEquals(setOf("entry-1"), result.committedEntryIds)
        assertEquals(2, result.committedCopies)
        assertEquals(listOf(CardSlotWrite("card-1", 2, false, DeckCardSource.USER)), repository.replaceCalls.single().second)
    }

    @Test
    fun addsNewEntriesToSideboard() = runTest {
        val repository = FakeScannedDeckRepository(deckWithCards())

        AddScannedCardsToDeckUseCase(repository)("deck-1", listOf(scanned("entry-1", "card-1")), DeckBoard.SIDEBOARD)

        assertEquals(listOf(CardSlotWrite("card-1", 1, true, DeckCardSource.USER)), repository.replaceCalls.single().second)
    }

    @Test
    fun mergesOnlyTheCorrectBoardAndPreservesExistingProvenance() = runTest {
        val repository = FakeScannedDeckRepository(
            deckWithCards(
                mainboard = listOf(DeckSlot("shared", 2, DeckCardSource.WIZARD)),
                sideboard = listOf(DeckSlot("shared", 3, DeckCardSource.SUGGESTION)),
            ),
        )

        AddScannedCardsToDeckUseCase(repository)("deck-1", listOf(scanned("entry-1", "shared", 4)), DeckBoard.MAINBOARD)

        assertEquals(
            listOf(
                CardSlotWrite("shared", 6, false, DeckCardSource.WIZARD),
                CardSlotWrite("shared", 3, true, DeckCardSource.SUGGESTION),
            ),
            repository.replaceCalls.single().second,
        )
    }

    @Test
    fun duplicateIdsAreGroupedAndBothStableEntryIdsAreCommitted() = runTest {
        val repository = FakeScannedDeckRepository(deckWithCards())

        val result = AddScannedCardsToDeckUseCase(repository)(
            "deck-1",
            listOf(scanned("entry-a", "card-1", 2), scanned("entry-b", "card-1", 3)),
            DeckBoard.MAINBOARD,
        )

        assertEquals(setOf("entry-a", "entry-b"), result.committedEntryIds)
        assertEquals(5, result.committedCopies)
        assertEquals(1, repository.replaceCalls.size)
        assertEquals(listOf(CardSlotWrite("card-1", 5)), repository.replaceCalls.single().second)
    }

    @Test
    fun blocksCommanderOnMainboardButAllowsItOnSideboard() = runTest {
        val mainRepository = FakeScannedDeckRepository(deckWithCards(commanderCardId = "commander"))
        val mainResult = AddScannedCardsToDeckUseCase(mainRepository)(
            "deck-1",
            listOf(scanned("blocked", "commander")),
            DeckBoard.MAINBOARD,
        )

        assertEquals(setOf("blocked"), mainResult.blockedCommanderEntryIds)
        assertEquals(0, mainRepository.replaceCalls.size)

        val sideRepository = FakeScannedDeckRepository(deckWithCards(commanderCardId = "commander"))
        val sideResult = AddScannedCardsToDeckUseCase(sideRepository)(
            "deck-1",
            listOf(scanned("allowed", "commander")),
            DeckBoard.SIDEBOARD,
        )

        assertEquals(emptySet(), sideResult.blockedCommanderEntryIds)
        assertEquals(setOf("allowed"), sideResult.committedEntryIds)
        assertEquals(1, sideRepository.replaceCalls.size)
    }

    @Test
    fun mixedCommanderBulkWritesAllowedEntriesAndBlocksOnlyCommanderEntry() = runTest {
        val repository = FakeScannedDeckRepository(deckWithCards(commanderCardId = "commander"))

        val result = AddScannedCardsToDeckUseCase(repository)(
            "deck-1",
            listOf(scanned("commander-entry", "commander"), scanned("other-entry", "other", 2)),
            DeckBoard.MAINBOARD,
        )

        assertEquals(setOf("commander-entry"), result.blockedCommanderEntryIds)
        assertEquals(setOf("other-entry"), result.committedEntryIds)
        assertEquals(2, result.committedCopies)
        assertEquals(listOf(CardSlotWrite("other", 2)), repository.replaceCalls.single().second)
    }

    @Test
    fun missingDeckDoesNotWrite() = runTest {
        val repository = FakeScannedDeckRepository(null)

        assertFailsWith<IllegalStateException> {
            AddScannedCardsToDeckUseCase(repository)("deck-1", listOf(scanned("entry-1", "card-1")), DeckBoard.MAINBOARD)
        }
        assertEquals(0, repository.replaceCalls.size)
    }

    @Test
    fun repositoryFailurePropagatesWithoutSuccessResult() = runTest {
        val repository = FakeScannedDeckRepository(deckWithCards()).apply {
            replacementFailure = IllegalStateException("write failed")
        }

        assertFailsWith<IllegalStateException> {
            AddScannedCardsToDeckUseCase(repository)("deck-1", listOf(scanned("entry-1", "card-1")), DeckBoard.MAINBOARD)
        }
    }

    @Test
    fun emptyOrFullyBlockedInputDoesNotReplace() = runTest {
        val emptyRepository = FakeScannedDeckRepository(deckWithCards())
        val emptyResult = AddScannedCardsToDeckUseCase(emptyRepository)("deck-1", emptyList(), DeckBoard.MAINBOARD)
        assertEquals(0, emptyRepository.replaceCalls.size)
        assertEquals(0, emptyResult.committedCopies)

        val blockedRepository = FakeScannedDeckRepository(deckWithCards(commanderCardId = "commander"))
        val blockedResult = AddScannedCardsToDeckUseCase(blockedRepository)(
            "deck-1",
            listOf(scanned("entry-1", "commander"), scanned("entry-2", "commander")),
            DeckBoard.MAINBOARD,
        )
        assertEquals(0, blockedRepository.replaceCalls.size)
        assertEquals(setOf("entry-1", "entry-2"), blockedResult.blockedCommanderEntryIds)
    }

    @Test
    fun bulkUsesExactlyOneReplacement() = runTest {
        val repository = FakeScannedDeckRepository(deckWithCards())
        val entries = (1..40).map { scanned("entry-$it", "card-$it") }

        AddScannedCardsToDeckUseCase(repository)("deck-1", entries, DeckBoard.MAINBOARD)

        assertEquals(1, repository.replaceCalls.size)
        assertEquals(40, repository.replaceCalls.single().second.size)
    }

    @Test
    fun rejectsBlankDeckIdAndNonPositiveQuantity() = runTest {
        val repository = FakeScannedDeckRepository(deckWithCards())
        assertFailsWith<IllegalArgumentException> {
            AddScannedCardsToDeckUseCase(repository)("  ", emptyList(), DeckBoard.MAINBOARD)
        }
        assertFailsWith<IllegalArgumentException> {
            AddScannedCardsToDeckUseCase(repository)("deck-1", listOf(scanned("entry-1", "card-1", 0)), DeckBoard.MAINBOARD)
        }
        assertEquals(0, repository.replaceCalls.size)
    }
}
