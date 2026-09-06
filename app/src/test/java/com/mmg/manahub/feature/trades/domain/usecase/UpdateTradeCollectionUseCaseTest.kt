package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.data.local.dao.TradeCollectionSyncDao
import com.mmg.manahub.core.data.local.entity.TradeCollectionSyncEntity
import com.mmg.manahub.core.domain.repository.AddOutcome
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.model.TradeItem
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [UpdateTradeCollectionUseCase].
 *
 * All four collaborators ([UserCardRepository], [WishlistRepository], [OpenForTradeRepository],
 * [TradeCollectionSyncDao]) are fully mocked. [ioDispatcher] is [UnconfinedTestDispatcher] so the
 * internal `withContext(ioDispatcher)` hop resolves eagerly within `runTest` without needing manual
 * scheduler advancement.
 *
 * Covers:
 *  - GROUP 1: Normal mode — sent items (delete + open-for-trade removal), received items
 *    (add + wishlist decrement), sync record written
 *  - GROUP 2: Reverse mode — sent items restored, received items removed, sync record deleted
 *  - GROUP 3: Partial item failure — one bad item does not abort the rest of the sync
 *  - GROUP 4: Wishlist decrement matching — the use case forwards EACH item's own attributes,
 *    never a different item's (trades audit §2.11 regression)
 */
class UpdateTradeCollectionUseCaseTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val userCardRepository = mockk<UserCardRepository>(relaxed = true)
    private val wishlistRepository = mockk<WishlistRepository>(relaxed = true)
    private val openForTradeRepository = mockk<OpenForTradeRepository>(relaxed = true)
    private val syncDao = mockk<TradeCollectionSyncDao>(relaxed = true)

    private lateinit var useCase: UpdateTradeCollectionUseCase

    // ── Constants ─────────────────────────────────────────────────────────────

    private val PROPOSAL_ID = "proposal-id-001"
    private val USER_ID = "user-uuid-001"

    // ── Fixture helper ────────────────────────────────────────────────────────

    private fun buildItem(
        id: String = "item-id-001",
        cardId: String = "card-scryfall-001",
        userCardIdRef: String? = "uc-ref-001",
        quantity: Int? = 1,
        isFoil: Boolean? = false,
        condition: String? = "NM",
        language: String? = "en",
    ) = TradeItem(
        id = id,
        tradeProposalId = PROPOSAL_ID,
        fromUserId = "user-a",
        toUserId = "user-b",
        userCardIdRef = userCardIdRef,
        quantity = quantity,
        isFoil = isFoil,
        condition = condition,
        language = language,
        cardId = cardId,
        isReviewCollectionPlaceholder = false,
    )

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        // Write-path hardening audit (Phase 7, 2026-09-06): UpdateTradeCollectionUseCase now
        // reports a real deleteCard/removeByCollectionIdAndSync failure via
        // core/util/CrashlyticsHelper.recordNonFatal, which calls FirebaseCrashlytics.getInstance()
        // directly (not through an injected abstraction) -- must be statically mocked here per the
        // project's established pattern, or any GROUP 3 "throws" test crashes on the real,
        // uninitialized Firebase singleton instead of exercising the intended failure-isolation path.
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        coEvery { userCardRepository.addOrIncrement(any(), any(), any(), any(), any(), any(), any()) } returns
            AddOutcome.INCREMENTED_EXISTING
        useCase = UpdateTradeCollectionUseCase(
            userCardRepository = userCardRepository,
            wishlistRepository = wishlistRepository,
            openForTradeRepository = openForTradeRepository,
            syncDao = syncDao,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — Normal mode
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given sent item with userCardIdRef when normal mode then it is deleted from the collection`() = runTest {
        val sentItem = buildItem(id = "s1", userCardIdRef = "uc-ref-A")

        val result = useCase(PROPOSAL_ID, USER_ID, listOf(sentItem), emptyList())

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { userCardRepository.deleteCard("uc-ref-A") }
    }

    @Test
    fun `given sent item with userCardIdRef when normal mode then its open-for-trade entry is removed and synced`() = runTest {
        val sentItem = buildItem(id = "s1", userCardIdRef = "uc-ref-A")

        useCase(PROPOSAL_ID, USER_ID, listOf(sentItem), emptyList())

        coVerify(exactly = 1) { openForTradeRepository.removeByCollectionIdAndSync("uc-ref-A") }
    }

    @Test
    fun `given sent item with null userCardIdRef when normal mode then deleteCard and open-for-trade removal are both skipped`() = runTest {
        val sentItem = buildItem(id = "s1", userCardIdRef = null)

        val result = useCase(PROPOSAL_ID, USER_ID, listOf(sentItem), emptyList())

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { userCardRepository.deleteCard(any()) }
        coVerify(exactly = 0) { openForTradeRepository.removeByCollectionIdAndSync(any()) }
    }

    @Test
    fun `given received item when normal mode then it is added to the collection with isForTrade false`() = runTest {
        val receivedItem = buildItem(id = "r1", cardId = "card-received", isFoil = true, condition = "lp", language = "DE", quantity = 3)

        useCase(PROPOSAL_ID, USER_ID, emptyList(), listOf(receivedItem))

        coVerify(exactly = 1) {
            userCardRepository.addOrIncrement(
                scryfallId = "card-received",
                isFoil = true,
                condition = "LP",
                language = "de",
                isForTrade = false,
                userId = USER_ID,
                quantity = 3,
            )
        }
    }

    @Test
    fun `given normal mode when invoke succeeds then markSynced is called with the proposal and user id`() = runTest {
        val captured = slot<TradeCollectionSyncEntity>()
        coEvery { syncDao.markSynced(capture(captured)) } returns Unit

        useCase(PROPOSAL_ID, USER_ID, emptyList(), emptyList())

        assertEquals(PROPOSAL_ID, captured.captured.proposalId)
        assertEquals(USER_ID, captured.captured.userId)
        coVerify(exactly = 0) { syncDao.removeSyncRecord(any(), any()) }
    }

    @Test
    fun `given normal mode when invoke runs then reverse-mode side effects never fire`() = runTest {
        val sentItem = buildItem(id = "s1")
        val receivedItem = buildItem(id = "r1")

        useCase(PROPOSAL_ID, USER_ID, listOf(sentItem), listOf(receivedItem))

        coVerify(exactly = 0) { userCardRepository.decrementOrRemove(any(), any(), any(), any(), any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — Reverse mode
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given sent item when reverse mode then it is restored to the collection via addOrIncrement`() = runTest {
        val sentItem = buildItem(id = "s1", cardId = "card-sent", isFoil = true, condition = "mp", language = "FR", quantity = 2)

        useCase(PROPOSAL_ID, USER_ID, listOf(sentItem), emptyList(), reverse = true)

        coVerify(exactly = 1) {
            userCardRepository.addOrIncrement(
                scryfallId = "card-sent",
                isFoil = true,
                condition = "MP",
                language = "fr",
                isForTrade = false,
                userId = USER_ID,
                quantity = 2,
            )
        }
    }

    @Test
    fun `given received item when reverse mode then it is removed via decrementOrRemove`() = runTest {
        val receivedItem = buildItem(id = "r1", cardId = "card-received", isFoil = false, condition = "nm", language = "en", quantity = 1)

        useCase(PROPOSAL_ID, USER_ID, emptyList(), listOf(receivedItem), reverse = true)

        coVerify(exactly = 1) {
            userCardRepository.decrementOrRemove(
                userId = USER_ID,
                scryfallId = "card-received",
                isFoil = false,
                condition = "NM",
                language = "en",
                quantityToDeduct = 1,
            )
        }
    }

    @Test
    fun `given reverse mode when invoke succeeds then removeSyncRecord is called and markSynced is NOT`() = runTest {
        useCase(PROPOSAL_ID, USER_ID, emptyList(), emptyList(), reverse = true)

        coVerify(exactly = 1) { syncDao.removeSyncRecord(PROPOSAL_ID, USER_ID) }
        coVerify(exactly = 0) { syncDao.markSynced(any()) }
    }

    @Test
    fun `given reverse mode when invoke runs then normal-mode-only side effects never fire`() = runTest {
        val sentItem = buildItem(id = "s1", userCardIdRef = "uc-ref-A")
        val receivedItem = buildItem(id = "r1")

        useCase(PROPOSAL_ID, USER_ID, listOf(sentItem), listOf(receivedItem), reverse = true)

        coVerify(exactly = 0) { userCardRepository.deleteCard(any()) }
        coVerify(exactly = 0) { openForTradeRepository.removeByCollectionIdAndSync(any()) }
        coVerify(exactly = 0) { wishlistRepository.decrementByAttributes(any(), any(), any(), any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — Partial item failure
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given deleteCard throws for one sent item when normal mode then the other sent item is still processed and the overall result is success`() = runTest {
        val badItem = buildItem(id = "s1", userCardIdRef = "uc-ref-BAD")
        val goodItem = buildItem(id = "s2", userCardIdRef = "uc-ref-GOOD")
        coEvery { userCardRepository.deleteCard("uc-ref-BAD") } throws RuntimeException("card already gone")

        val result = useCase(PROPOSAL_ID, USER_ID, listOf(badItem, goodItem), emptyList())

        assertTrue("A single bad item must not fail the whole sync", result.isSuccess)
        coVerify(exactly = 1) { userCardRepository.deleteCard("uc-ref-GOOD") }
        coVerify(exactly = 1) { openForTradeRepository.removeByCollectionIdAndSync("uc-ref-GOOD") }
    }

    @Test
    fun `given deleteCard throws for a sent item when normal mode then the sync record is still written`() = runTest {
        val badItem = buildItem(id = "s1", userCardIdRef = "uc-ref-BAD")
        coEvery { userCardRepository.deleteCard(any()) } throws RuntimeException("db locked")

        val result = useCase(PROPOSAL_ID, USER_ID, listOf(badItem), emptyList())

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { syncDao.markSynced(any()) }
    }

    @Test
    fun `given openForTradeRepository throws for a sent item when normal mode then received items are still processed`() = runTest {
        val sentItem = buildItem(id = "s1", userCardIdRef = "uc-ref-A")
        val receivedItem = buildItem(id = "r1", cardId = "card-received")
        coEvery { openForTradeRepository.removeByCollectionIdAndSync(any()) } throws RuntimeException("network error")

        val result = useCase(PROPOSAL_ID, USER_ID, listOf(sentItem), listOf(receivedItem))

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { userCardRepository.addOrIncrement("card-received", false, "NM", "en", false, USER_ID, 1) }
    }

    @Test
    fun `given wishlist decrement throws for a received item when normal mode then the sync record is still written`() = runTest {
        val receivedItem = buildItem(id = "r1")
        coEvery { wishlistRepository.decrementByAttributes(any(), any(), any(), any(), any()) } throws RuntimeException("wishlist error")

        val result = useCase(PROPOSAL_ID, USER_ID, emptyList(), listOf(receivedItem))

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { syncDao.markSynced(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — Wishlist decrement matching (trades audit §2.11 regression)
    //
    //  The use case must forward EACH received item's own attributes to
    //  decrementByAttributes — never a hardcoded default or another item's variant.
    //  (The choice of WHICH wishlist row matches those attributes is
    //  WishlistRepositoryImpl's responsibility, covered separately in
    //  WishlistRepositoryImplTest.)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given two received items with different variants when normal mode then decrementByAttributes is called once per item with its own attributes`() = runTest {
        val foilItem = buildItem(id = "r1", cardId = "card-foil", isFoil = true, condition = "lp", language = "EN", quantity = 2)
        val nonFoilItem = buildItem(id = "r2", cardId = "card-nonfoil", isFoil = false, condition = "nm", language = "DE", quantity = 1)

        useCase(PROPOSAL_ID, USER_ID, emptyList(), listOf(foilItem, nonFoilItem))

        coVerify(exactly = 1) { wishlistRepository.decrementByAttributes("card-foil", 2, true, "LP", "en") }
        coVerify(exactly = 1) { wishlistRepository.decrementByAttributes("card-nonfoil", 1, false, "NM", "de") }
    }

    @Test
    fun `given a received item with null attributes when normal mode then decrementByAttributes falls back to NM en non-foil quantity 1`() = runTest {
        val item = buildItem(id = "r1", cardId = "card-x", isFoil = null, condition = null, language = null, quantity = null)

        useCase(PROPOSAL_ID, USER_ID, emptyList(), listOf(item))

        coVerify(exactly = 1) { wishlistRepository.decrementByAttributes("card-x", 1, false, "NM", "en") }
    }

    @Test
    fun `given a received item when normal mode then addOrIncrement and decrementByAttributes never swap each other's item attributes`() = runTest {
        // Regression guard: two items processed in the same forEach must not leak the
        // FIRST item's attributes into the SECOND item's calls (a copy-paste/closure bug
        // that would silently corrupt a different variant than the one actually traded).
        val itemA = buildItem(id = "r1", cardId = "card-a", isFoil = true, condition = "ex", language = "ja", quantity = 4)
        val itemB = buildItem(id = "r2", cardId = "card-b", isFoil = false, condition = "gd", language = "es", quantity = 5)

        useCase(PROPOSAL_ID, USER_ID, emptyList(), listOf(itemA, itemB))

        coVerify(exactly = 1) {
            userCardRepository.addOrIncrement("card-a", true, "EX", "ja", false, USER_ID, 4)
        }
        coVerify(exactly = 1) {
            userCardRepository.addOrIncrement("card-b", false, "GD", "es", false, USER_ID, 5)
        }
        coVerify(exactly = 1) { wishlistRepository.decrementByAttributes("card-a", 4, true, "EX", "ja") }
        coVerify(exactly = 1) { wishlistRepository.decrementByAttributes("card-b", 5, false, "GD", "es") }
    }
}
