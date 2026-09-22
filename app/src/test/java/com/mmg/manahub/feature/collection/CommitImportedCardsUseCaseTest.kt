package com.mmg.manahub.feature.collection

import com.mmg.manahub.core.data.queue.InMemoryCardQueueStore
import com.mmg.manahub.core.data.queue.PersistentCardQueueRepository
import com.mmg.manahub.core.domain.repository.AddOutcome
import com.mmg.manahub.core.domain.repository.CollectionAddRequest
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.collection.CardCommit
import com.mmg.manahub.core.domain.usecase.collection.CommitImportedCardsUseCase
import com.mmg.manahub.core.domain.usecase.queue.AddAllToCollectionResult
import com.mmg.manahub.core.domain.usecase.queue.CardQueueActions
import com.mmg.manahub.core.model.QueuedCard
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The import commit path: batched, transactional per slice, and never through the XP-emitting scan path. */
class CommitImportedCardsUseCaseTest {

    private val userCardRepository = mockk<UserCardRepository>(relaxed = true)

    private fun commit(id: String, quantity: Int = 1) = CardCommit(id, isFoil = false, condition = "NM", language = "en", quantity = quantity)

    @Test
    fun `writes every entry in slices of one batch call each`() = runTest {
        coEvery { userCardRepository.addOrIncrementBatch(any(), any()) } answers {
            firstArg<List<CollectionAddRequest>>().map { AddOutcome.CREATED_NEW }
        }
        val entries = (1..1_200).map { commit("id-$it", quantity = 2) }

        val result = CommitImportedCardsUseCase(userCardRepository).commit(entries)

        coVerify(exactly = 3) { userCardRepository.addOrIncrementBatch(any(), null) }
        coVerify(exactly = 0) { userCardRepository.addOrIncrement(any(), any(), any(), any(), any(), any(), any()) }
        assertEquals(0, result.failedEntries)
        assertEquals(2_400, result.committedCopies)
        assertEquals(1_200, result.entrySucceeded.size)
    }

    @Test
    fun `a failed slice fails only its own entries`() = runTest {
        var call = 0
        coEvery { userCardRepository.addOrIncrementBatch(any(), any()) } answers {
            call++
            if (call == 2) throw IllegalStateException("db locked")
            firstArg<List<CollectionAddRequest>>().map { AddOutcome.CREATED_NEW }
        }
        val entries = (1..600).map { commit("id-$it") }

        val result = CommitImportedCardsUseCase(userCardRepository).commit(entries)

        assertEquals(100, result.failedEntries)
        assertEquals(500, result.committedCopies)
        assertTrue(result.entrySucceeded.take(500).all { it })
        assertTrue(result.entrySucceeded.drop(500).none { it })
    }

    @Test
    fun `import queue add-all commits through the import committer and empties the queue`() = runTest {
        coEvery { userCardRepository.addOrIncrementBatch(any(), any()) } answers {
            firstArg<List<CollectionAddRequest>>().map { AddOutcome.CREATED_NEW }
        }
        val queue = PersistentCardQueueRepository(InMemoryCardQueueStore())
        queue.addAll(
            listOf("a", "b").map { id ->
                QueuedCard(TestFixtures.buildCard(scryfallId = id), 1, false, "en", "NM", "lea", 0L)
            }
        )
        val actions = CardQueueActions(
            queueRepository = queue,
            committer = CommitImportedCardsUseCase(userCardRepository),
            addToWishlist = mockk(relaxed = true),
        )
        var outcome: AddAllToCollectionResult? = null

        actions.addAllToCollection(this) { outcome = it }
        testScheduler.advanceUntilIdle()

        assertEquals(AddAllToCollectionResult.Success(2), outcome)
        assertTrue(queue.queue.value.isEmpty())
        coVerify(exactly = 1) { userCardRepository.addOrIncrementBatch(any(), null) }
    }
}
