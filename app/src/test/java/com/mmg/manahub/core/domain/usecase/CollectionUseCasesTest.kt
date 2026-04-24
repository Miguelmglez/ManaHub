package com.mmg.manahub.core.domain.usecase

import app.cash.turbine.test
import com.mmg.manahub.core.domain.model.UserCardWithCard
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.collection.GetCollectionUseCase
import com.mmg.manahub.core.domain.usecase.collection.RemoveCardUseCase
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [GetCollectionUseCase] and [RemoveCardUseCase].
 *
 * These use cases are thin wrappers. The tests verify correct delegation and
 * that the Flow contract is preserved.
 */
class CollectionUseCasesTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val userCardRepository = mockk<UserCardRepository>()

    private lateinit var getCollection: GetCollectionUseCase
    private lateinit var removeCard:    RemoveCardUseCase

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        getCollection = GetCollectionUseCase(repository = userCardRepository)
        removeCard    = RemoveCardUseCase(repository = userCardRepository)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GetCollectionUseCase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given collection has cards when getCollection is invoked then emits the cards`() = runTest {
        // Arrange
        val expected: List<UserCardWithCard> = listOf(
            TestFixtures.buildUserCardWithCard(),
            TestFixtures.buildUserCardWithCard(
                userCard = TestFixtures.buildUserCard(id = "id-002", scryfallId = "id-002"),
                card     = TestFixtures.buildCard(scryfallId = "id-002", name = "Counterspell"),
            ),
        )
        every { userCardRepository.observeCollection() } returns flowOf(expected)

        // Act & Assert
        getCollection().test {
            val emission = awaitItem()
            assertEquals(2, emission.size)
            assertEquals("id-001", emission[0].userCard.scryfallId)
            assertEquals("id-002", emission[1].userCard.scryfallId)
            awaitComplete()
        }
    }

    @Test
    fun `given empty collection when getCollection is invoked then emits empty list`() = runTest {
        // Arrange
        every { userCardRepository.observeCollection() } returns flowOf(emptyList())

        // Act & Assert
        getCollection().test {
            val emission = awaitItem()
            assertTrue(emission.isEmpty())
            awaitComplete()
        }
    }

    @Test
    fun `given multiple emissions when getCollection then all emissions are forwarded`() = runTest {
        // Arrange — simulates a card being added mid-observation
        val initial  = listOf(TestFixtures.buildUserCardWithCard())
        val updated  = initial + TestFixtures.buildUserCardWithCard(
            userCard = TestFixtures.buildUserCard(id = "id-002", scryfallId = "id-002"),
            card     = TestFixtures.buildCard(scryfallId = "id-002"),
        )
        every { userCardRepository.observeCollection() } returns flowOf(initial, updated)

        // Act & Assert
        getCollection().test {
            assertEquals(1, awaitItem().size)
            assertEquals(2, awaitItem().size)
            awaitComplete()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  RemoveCardUseCase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid id when removeCard then deleteCard is called with that id`() = runTest {
        // Arrange
        coEvery { userCardRepository.deleteCard(any()) } returns Unit

        // Act
        removeCard(userCardId = "id-5")

        // Assert
        coVerify(exactly = 1) { userCardRepository.deleteCard("id-5") }
    }

    @Test
    fun `given id zero when removeCard then deleteCard is still delegated`() = runTest {
        // Arrange — edge case: id = 0 is technically valid at the use-case boundary
        coEvery { userCardRepository.deleteCard(any()) } returns Unit

        // Act
        removeCard(userCardId = "id-0")

        // Assert
        coVerify(exactly = 1) { userCardRepository.deleteCard("id-0") }
    }

    @Test
    fun `given different ids when removeCard called twice then each id is deleted separately`() = runTest {
        // Arrange
        coEvery { userCardRepository.deleteCard(any()) } returns Unit

        // Act
        removeCard(userCardId = "id-1")
        removeCard(userCardId = "id-2")

        // Assert
        coVerify(exactly = 1) { userCardRepository.deleteCard("id-1") }
        coVerify(exactly = 1) { userCardRepository.deleteCard("id-2") }
    }
}
