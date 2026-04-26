package com.mmg.manahub.core.domain.usecase

import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AddCardToCollectionUseCase].
 *
 * The use case ensures the card is cached in Room via [CardRepository.getCardById],
 * then delegates to [UserCardRepository.addOrIncrement] with the individual parameters.
 * It never constructs a [com.mmg.manahub.core.domain.model.UserCard] object — that is
 * the repository's responsibility.
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
    fun `given card exists when invoke then returns DataResult Success`() = runTest {
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(TestFixtures.buildCard("id-001"))

        val result = useCase(scryfallId = "id-001")

        assertTrue(result is DataResult.Success)
    }

    @Test
    fun `given card exists when invoke then addOrIncrement is called once`() = runTest {
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(TestFixtures.buildCard("id-001"))

        useCase(scryfallId = "id-001")

        coVerify(exactly = 1) {
            userCardRepository.addOrIncrement(
                scryfallId       = "id-001",
                isFoil           = false,
                condition        = "NM",
                language         = "en",
                isAlternativeArt = false,
                isForTrade       = false,
                isInWishlist     = false,
                userId           = null,
            )
        }
    }

    @Test
    fun `given foil flag true when invoke then addOrIncrement receives isFoil true`() = runTest {
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(TestFixtures.buildCard("id-001"))

        useCase(scryfallId = "id-001", isFoil = true)

        coVerify(exactly = 1) {
            userCardRepository.addOrIncrement(
                scryfallId       = "id-001",
                isFoil           = true,
                condition        = any(),
                language         = any(),
                isAlternativeArt = any(),
                isForTrade       = any(),
                isInWishlist     = any(),
                userId           = any(),
            )
        }
    }

    @Test
    fun `given alternative art flag when invoke then addOrIncrement receives isAlternativeArt true`() = runTest {
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(TestFixtures.buildCard("id-001"))

        useCase(scryfallId = "id-001", isAlternativeArt = true)

        coVerify(exactly = 1) {
            userCardRepository.addOrIncrement(
                scryfallId       = "id-001",
                isFoil           = any(),
                condition        = any(),
                language         = any(),
                isAlternativeArt = true,
                isForTrade       = any(),
                isInWishlist     = any(),
                userId           = any(),
            )
        }
    }

    @Test
    fun `given LP condition and de language when invoke then addOrIncrement receives those values`() = runTest {
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(TestFixtures.buildCard("id-001"))

        useCase(scryfallId = "id-001", condition = "LP", language = "de")

        coVerify(exactly = 1) {
            userCardRepository.addOrIncrement(
                scryfallId       = "id-001",
                isFoil           = any(),
                condition        = "LP",
                language         = "de",
                isAlternativeArt = any(),
                isForTrade       = any(),
                isInWishlist     = any(),
                userId           = any(),
            )
        }
    }

    @Test
    fun `given userId when invoke then addOrIncrement receives the userId`() = runTest {
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(TestFixtures.buildCard("id-001"))

        useCase(scryfallId = "id-001", userId = "user-abc")

        coVerify(exactly = 1) {
            userCardRepository.addOrIncrement(
                scryfallId       = "id-001",
                isFoil           = any(),
                condition        = any(),
                language         = any(),
                isAlternativeArt = any(),
                isForTrade       = any(),
                isInWishlist     = any(),
                userId           = "user-abc",
            )
        }
    }

    @Test
    fun `given invoke called twice then addOrIncrement is called twice`() = runTest {
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(TestFixtures.buildCard("id-001"))

        useCase(scryfallId = "id-001")
        useCase(scryfallId = "id-001")

        coVerify(exactly = 2) { userCardRepository.addOrIncrement(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given stale cache success when invoke then card is still added to collection`() = runTest {
        val card = TestFixtures.buildCard("id-001")
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(data = card, isStale = true)

        val result = useCase(scryfallId = "id-001")

        assertTrue(result is DataResult.Success)
        coVerify(exactly = 1) { userCardRepository.addOrIncrement(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — Error paths: card not found / network unavailable
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given getCardById fails when invoke then returns DataResult Error`() = runTest {
        coEvery { cardRepository.getCardById("id-unknown") } returns DataResult.Error("HTTP 404")

        val result = useCase(scryfallId = "id-unknown")

        assertTrue(result is DataResult.Error)
        assertEquals("HTTP 404", (result as DataResult.Error).message)
    }

    @Test
    fun `given getCardById fails when invoke then addOrIncrement is NOT called`() = runTest {
        coEvery { cardRepository.getCardById("id-unknown") } returns DataResult.Error("HTTP 404")

        useCase(scryfallId = "id-unknown")

        coVerify(exactly = 0) { userCardRepository.addOrIncrement(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given network unavailable and no cache when invoke then returns DataResult Error`() = runTest {
        coEvery { cardRepository.getCardById(any()) } returns DataResult.Error("No local data and network unavailable")

        val result = useCase(scryfallId = "id-offline")

        assertTrue(result is DataResult.Error)
        coVerify(exactly = 0) { userCardRepository.addOrIncrement(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — isInWishlist defaults to false for collection entries
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given invoke with default params then isInWishlist forwarded as false`() = runTest {
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(TestFixtures.buildCard("id-001"))

        useCase(scryfallId = "id-001")

        coVerify(exactly = 1) {
            userCardRepository.addOrIncrement(
                scryfallId       = any(),
                isFoil           = any(),
                condition        = any(),
                language         = any(),
                isAlternativeArt = any(),
                isForTrade       = any(),
                isInWishlist     = false,
                userId           = any(),
            )
        }
    }
}
