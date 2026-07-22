package com.mmg.manahub.core.domain.usecase

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
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
 * It never constructs a [com.mmg.manahub.core.model.UserCard] object — that is
 * the repository's responsibility.
 */
class AddCardToCollectionUseCaseTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val cardRepository      = mockk<CardRepository>()
    private val userCardRepository  = mockk<UserCardRepository>(relaxed = true)
    private val progressionEventBus = mockk<ProgressionEventBus>(relaxed = true)

    private lateinit var useCase: AddCardToCollectionUseCase

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        useCase = AddCardToCollectionUseCase(
            cardRepository      = cardRepository,
            userCardRepository  = userCardRepository,
            progressionEventBus = progressionEventBus,
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
                isForTrade       = false,
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
                isForTrade       = any(),
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
                isForTrade       = any(),
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
                isForTrade       = any(),
                userId           = "user-abc",
            )
        }
    }

    @Test
    fun `given invoke called twice then addOrIncrement is called twice`() = runTest {
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(TestFixtures.buildCard("id-001"))

        useCase(scryfallId = "id-001")
        useCase(scryfallId = "id-001")

        coVerify(exactly = 2) { userCardRepository.addOrIncrement(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given stale cache success when invoke then card is still added to collection`() = runTest {
        val card = TestFixtures.buildCard("id-001")
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(data = card, isStale = true)

        val result = useCase(scryfallId = "id-001")

        assertTrue(result is DataResult.Success)
        coVerify(exactly = 1) { userCardRepository.addOrIncrement(any(), any(), any(), any(), any(), any()) }
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

        coVerify(exactly = 0) { userCardRepository.addOrIncrement(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given network unavailable and no cache when invoke then returns DataResult Error`() = runTest {
        coEvery { cardRepository.getCardById(any()) } returns DataResult.Error("No local data and network unavailable")

        val result = useCase(scryfallId = "id-offline")

        assertTrue(result is DataResult.Error)
        coVerify(exactly = 0) { userCardRepository.addOrIncrement(any(), any(), any(), any(), any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — isForTrade defaults to false for collection entries
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given invoke with default params then isForTrade forwarded as false`() = runTest {
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(TestFixtures.buildCard("id-001"))

        useCase(scryfallId = "id-001")

        coVerify(exactly = 1) {
            userCardRepository.addOrIncrement(
                scryfallId       = any(),
                isFoil           = any(),
                condition        = any(),
                language         = any(),
                isForTrade       = false,
                userId           = any(),
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — Broken-image fix (2026-07-17): best-effort English-sibling warm-cache
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given non-English card when invoke then English sibling is warmed via getCardBySetAndNumber`() = runTest {
        val foreignCard = TestFixtures.buildCard("id-es").copy(lang = "es", setCode = "lea", collectorNumber = "5")
        coEvery { cardRepository.getCardById("id-es") } returns DataResult.Success(foreignCard)
        coEvery { cardRepository.getCardBySetAndNumber("lea", "5") } returns
            DataResult.Success(TestFixtures.buildCard("id-en"))

        useCase(scryfallId = "id-es")

        coVerify(exactly = 1) { cardRepository.getCardBySetAndNumber("lea", "5") }
    }

    @Test
    fun `given English card when invoke then getCardBySetAndNumber is never called`() = runTest {
        coEvery { cardRepository.getCardById("id-001") } returns DataResult.Success(TestFixtures.buildCard("id-001"))

        useCase(scryfallId = "id-001")

        coVerify(exactly = 0) { cardRepository.getCardBySetAndNumber(any(), any()) }
    }

    @Test
    fun `given non-English card when English sibling fetch fails then add still succeeds`() = runTest {
        val foreignCard = TestFixtures.buildCard("id-es2").copy(lang = "es", setCode = "lea", collectorNumber = "6")
        coEvery { cardRepository.getCardById("id-es2") } returns DataResult.Success(foreignCard)
        coEvery { cardRepository.getCardBySetAndNumber("lea", "6") } throws RuntimeException("network down")

        val result = useCase(scryfallId = "id-es2")

        assertTrue(result is DataResult.Success)
        coVerify(exactly = 1) { userCardRepository.addOrIncrement(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given non-English card when addReturningOutcome invoked then English sibling is warmed`() = runTest {
        val foreignCard = TestFixtures.buildCard("id-es3").copy(lang = "de", setCode = "lea", collectorNumber = "7")
        coEvery { cardRepository.getCardById("id-es3") } returns DataResult.Success(foreignCard)
        coEvery { cardRepository.getCardBySetAndNumber("lea", "7") } returns
            DataResult.Success(TestFixtures.buildCard("id-en3"))

        val result = useCase.addReturningOutcome(scryfallId = "id-es3")

        assertTrue(result is DataResult.Success)
        coVerify(exactly = 1) { cardRepository.getCardBySetAndNumber("lea", "7") }
    }
}
