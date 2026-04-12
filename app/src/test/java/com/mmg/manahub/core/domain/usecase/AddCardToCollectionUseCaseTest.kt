package com.mmg.manahub.core.domain.usecase

import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.model.UserCard
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AddCardToCollectionUseCase].
 *
 * This use case is the single entry point for adding cards to the collection.
 * It first ensures the card is cached in Room via [CardRepository.getCardById],
 * then delegates to [UserCardRepository.addOrIncrement].
 */
class AddCardToCollectionUseCaseTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val cardRepository     = mockk<CardRepository>()
    private val userCardRepository = mockk<UserCardRepository>(relaxed = true)

    private lateinit var useCase: AddCardToCollectionUseCase

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        useCase = AddCardToCollectionUseCase(
            cardRepository     = cardRepository,
            userCardRepository = userCardRepository,
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — Success paths
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given card exists in cache when invoke then card is added to collection`() = runTest {
        // Arrange
        val card = TestFixtures.buildCard("id-001")
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(card)

        // Act
        val result = useCase(scryfallId = "id-001")

        // Assert
        assertTrue(result is DataResult.Success)
        coVerify(exactly = 1) { userCardRepository.addOrIncrement(any()) }
    }

    @Test
    fun `given default params when invoke then UserCard is created with NM condition and en language`() = runTest {
        // Arrange
        val card = TestFixtures.buildCard("id-001")
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(card)

        val userCardSlot = slot<UserCard>()
        coEvery { userCardRepository.addOrIncrement(capture(userCardSlot)) } returns Unit

        // Act
        useCase(scryfallId = "id-001")

        // Assert default values
        val captured = userCardSlot.captured
        assertEquals("id-001", captured.scryfallId)
        assertEquals("NM",     captured.condition)
        assertEquals("en",     captured.language)
        assertEquals(1,        captured.quantity)
        assertEquals(false,    captured.isFoil)
        assertEquals(false,    captured.isAlternativeArt)
        assertEquals(false,    captured.isInWishlist)
    }

    @Test
    fun `given foil flag when invoke then UserCard has isFoil true`() = runTest {
        // Arrange
        val card = TestFixtures.buildCard("id-001")
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(card)

        val userCardSlot = slot<UserCard>()
        coEvery { userCardRepository.addOrIncrement(capture(userCardSlot)) } returns Unit

        // Act
        useCase(scryfallId = "id-001", isFoil = true)

        // Assert
        assertTrue(userCardSlot.captured.isFoil)
    }

    @Test
    fun `given alternative art flag when invoke then UserCard has isAlternativeArt true`() = runTest {
        // Arrange
        val card = TestFixtures.buildCard("id-001")
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(card)

        val userCardSlot = slot<UserCard>()
        coEvery { userCardRepository.addOrIncrement(capture(userCardSlot)) } returns Unit

        // Act
        useCase(scryfallId = "id-001", isAlternativeArt = true)

        // Assert
        assertTrue(userCardSlot.captured.isAlternativeArt)
    }

    @Test
    fun `given quantity 3 when invoke then UserCard has quantity 3`() = runTest {
        // Arrange
        val card = TestFixtures.buildCard("id-001")
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(card)

        val userCardSlot = slot<UserCard>()
        coEvery { userCardRepository.addOrIncrement(capture(userCardSlot)) } returns Unit

        // Act
        useCase(scryfallId = "id-001", quantity = 3)

        // Assert
        assertEquals(3, userCardSlot.captured.quantity)
    }

    @Test
    fun `given LP condition and de language when invoke then UserCard preserves those values`() = runTest {
        // Arrange
        val card = TestFixtures.buildCard("id-001")
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(card)

        val userCardSlot = slot<UserCard>()
        coEvery { userCardRepository.addOrIncrement(capture(userCardSlot)) } returns Unit

        // Act
        useCase(scryfallId = "id-001", condition = "LP", language = "de")

        // Assert
        assertEquals("LP", userCardSlot.captured.condition)
        assertEquals("de", userCardSlot.captured.language)
    }

    @Test
    fun `given card is added twice with same unique key when invoke twice then addOrIncrement is called twice`() = runTest {
        // Arrange — each call to addOrIncrement is handled by the repository layer;
        // the use case itself simply delegates both calls
        val card = TestFixtures.buildCard("id-001")
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(card)

        // Act
        useCase(scryfallId = "id-001")
        useCase(scryfallId = "id-001")

        // Assert: use case called addOrIncrement twice — deduplication is repo's responsibility
        coVerify(exactly = 2) { userCardRepository.addOrIncrement(any()) }
    }

    @Test
    fun `given foil and non-foil for same card when invoke then both are forwarded as separate calls`() = runTest {
        // Arrange
        val card = TestFixtures.buildCard("id-001")
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(card)

        // Act
        useCase(scryfallId = "id-001", isFoil = false)
        useCase(scryfallId = "id-001", isFoil = true)

        // Assert: two separate addOrIncrement calls — foil/non-foil are distinct rows
        coVerify(exactly = 2) { userCardRepository.addOrIncrement(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — Error path: card not found / network unavailable
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given getCardById fails when invoke then returns DataResult Error without adding to collection`() = runTest {
        // Arrange
        coEvery { cardRepository.getCardById("id-unknown") } returns
                DataResult.Error("HTTP 404")

        // Act
        val result = useCase(scryfallId = "id-unknown")

        // Assert
        assertTrue(result is DataResult.Error)
        assertEquals("HTTP 404", (result as DataResult.Error).message)
        coVerify(exactly = 0) { userCardRepository.addOrIncrement(any()) }
    }

    @Test
    fun `given network unavailable and no cache when invoke then returns DataResult Error`() = runTest {
        // Arrange
        coEvery { cardRepository.getCardById(any()) } returns
                DataResult.Error("No local data and network unavailable")

        // Act
        val result = useCase(scryfallId = "id-offline")

        // Assert
        assertTrue(result is DataResult.Error)
        // collection must not be touched when card fetch fails
        coVerify(exactly = 0) { userCardRepository.addOrIncrement(any()) }
    }

    @Test
    fun `given stale cache success when invoke then card is still added to collection`() = runTest {
        // Arrange — stale cache is still DataResult.Success with isStale = true
        val card = TestFixtures.buildCard("id-001")
        coEvery { cardRepository.getCardById("id-001") } returns
                DataResult.Success(data = card, isStale = true)

        // Act
        val result = useCase(scryfallId = "id-001")

        // Assert: stale data is acceptable — card should still be added
        assertTrue(result is DataResult.Success)
        coVerify(exactly = 1) { userCardRepository.addOrIncrement(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — Card is NOT added to wishlist
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given invoke with default params when UserCard created then isInWishlist is false`() = runTest {
        // Arrange — this use case is exclusively for the collection, not the wishlist
        val card = TestFixtures.buildCard("id-001")
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(card)

        val userCardSlot = slot<UserCard>()
        coEvery { userCardRepository.addOrIncrement(capture(userCardSlot)) } returns Unit

        // Act
        useCase(scryfallId = "id-001")

        // Assert
        assertEquals(false, userCardSlot.captured.isInWishlist)
    }
}
