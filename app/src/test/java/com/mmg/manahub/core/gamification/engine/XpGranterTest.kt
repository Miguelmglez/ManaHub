package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.entity.PlayerProgressionEntity
import com.mmg.manahub.core.data.local.entity.XpTransactionEntity
import com.mmg.manahub.core.gamification.domain.LevelCurve
import com.mmg.manahub.core.gamification.domain.XpConfig
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.XpSourceCategory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests for [XpGranter]: idempotency, daily collection cap, deck/day cap, friend/week cap,
 * win-bonus stacking, and level-up math.
 *
 * The clock is pinned to a fixed instant so day/week window queries are deterministic. The DAO is
 * mocked; `grantXpAtomically` returns true unless a test overrides it (duplicate path).
 */
class XpGranterTest {

    private lateinit var dao: GamificationDao
    private lateinit var granter: XpGranter

    // Pinned to a Thursday so week-window math is stable across runs.
    private val fixedInstant: Instant = Instant.parse("2026-06-11T12:00:00Z")
    private val zoneId: ZoneId = ZoneId.of("UTC")
    private val now: Instant get() = fixedInstant

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        val clock = Clock.fixed(fixedInstant, zoneId)
        granter = XpGranter(dao, clock, zoneId)

        // Default happy-path stubs. Individual tests override as needed.
        coEvery { dao.hasTransaction(any()) } returns false
        coEvery { dao.getProgression(any()) } returns null
        coEvery { dao.sumXpForCategorySince(any(), any()) } returns 0
        coEvery { dao.countDistinctSourceRefForCategorySince(any(), any()) } returns 0
        coEvery { dao.grantXpAtomically(any(), any(), any(), any()) } returns true
    }

    @Test
    fun `game without win grants only the logged amount`() = runTest {
        val outcome = granter.grant(
            ProgressionEvent.GameFinished(
                sessionId = 1L, isLocalWin = false, mode = "casual", playerCount = 2,
                durationMs = 0L, winTurn = null, localFinalLife = null, occurredAt = now,
            )
        )
        assertEquals(XpConfig.gameLogged, outcome.xpGranted)
    }

    @Test
    fun `local win stacks the win bonus on top of the logged amount`() = runTest {
        val outcome = granter.grant(
            ProgressionEvent.GameFinished(
                sessionId = 2L, isLocalWin = true, mode = "casual", playerCount = 2,
                durationMs = 0L, winTurn = 7, localFinalLife = 12, occurredAt = now,
            )
        )
        assertEquals(XpConfig.gameLogged + XpConfig.localWin, outcome.xpGranted)
    }

    @Test
    fun `duplicate idempotency key short-circuits before any grant`() = runTest {
        coEvery { dao.hasTransaction("survey:5") } returns true

        val outcome = granter.grant(
            ProgressionEvent.SurveyCompleted(surveyId = 5L, sessionId = 1L, occurredAt = now)
        )

        assertEquals(0, outcome.xpGranted)
        coVerify(exactly = 0) { dao.grantXpAtomically(any(), any(), any(), any()) }
    }

    @Test
    fun `atomic grant rejection (race) yields a no-op outcome`() = runTest {
        // hasTransaction misses (false) but the atomic insert loses the race and returns false.
        coEvery { dao.grantXpAtomically(any(), any(), any(), any()) } returns false

        val outcome = granter.grant(
            ProgressionEvent.SurveyCompleted(surveyId = 9L, sessionId = 1L, occurredAt = now)
        )

        assertEquals(0, outcome.xpGranted)
        assertFalse(outcome.leveledUp)
    }

    @Test
    fun `collection add is clamped to the remaining daily cap`() = runTest {
        // Already 90 XP from collection today; cap is 100 => only 10 remaining.
        coEvery {
            dao.sumXpForCategorySince(XpSourceCategory.COLLECTION.name, any())
        } returns 90

        // Raw would be 5 unique * 5 = 25, but only 10 may be granted.
        val txnSlot = slot<XpTransactionEntity>()
        coEvery { dao.grantXpAtomically(capture(txnSlot), any(), any(), any()) } returns true

        val outcome = granter.grant(
            ProgressionEvent.CardsAdded(addedCopies = 0, addedUnique = 5, occurredAt = now)
        )

        assertEquals(10, outcome.xpGranted)
        assertEquals(10, txnSlot.captured.amount)
        assertEquals(XpSourceCategory.COLLECTION.name, txnSlot.captured.sourceCategory)
    }

    @Test
    fun `collection add is a no-op when the daily cap is already exhausted`() = runTest {
        coEvery {
            dao.sumXpForCategorySince(XpSourceCategory.COLLECTION.name, any())
        } returns XpConfig.collectionDailyCapXp

        val outcome = granter.grant(
            ProgressionEvent.CardsAdded(addedCopies = 3, addedUnique = 2, occurredAt = now)
        )

        assertEquals(0, outcome.xpGranted)
        coVerify(exactly = 0) { dao.grantXpAtomically(any(), any(), any(), any()) }
    }

    @Test
    fun `card scan counts toward the same collection cap`() = runTest {
        coEvery {
            dao.sumXpForCategorySince(XpSourceCategory.COLLECTION.name, any())
        } returns 96 // 4 remaining

        // 3 cards * 3 = 9 raw, clamped to 4.
        val outcome = granter.grant(
            ProgressionEvent.CardScanned(scanBatchId = "batch-1", count = 3, occurredAt = now)
        )

        assertEquals(4, outcome.xpGranted)
    }

    @Test
    fun `deck created is rewarded under the daily deck cap`() = runTest {
        coEvery {
            dao.countDistinctSourceRefForCategorySince(XpSourceCategory.DECK.name, any())
        } returns 2 // under the cap of 3

        val outcome = granter.grant(
            ProgressionEvent.DeckCreated(deckId = "deck-1", format = "standard", occurredAt = now)
        )

        assertEquals(XpConfig.deckCreated, outcome.xpGranted)
    }

    @Test
    fun `deck created is a no-op once the daily deck cap is reached`() = runTest {
        coEvery {
            dao.countDistinctSourceRefForCategorySince(XpSourceCategory.DECK.name, any())
        } returns XpConfig.maxRewardedDecksPerDay

        val outcome = granter.grant(
            ProgressionEvent.DeckCreated(deckId = "deck-4", format = "standard", occurredAt = now)
        )

        assertEquals(0, outcome.xpGranted)
        coVerify(exactly = 0) { dao.grantXpAtomically(any(), any(), any(), any()) }
    }

    @Test
    fun `plain deck save grants no xp but is not an error`() = runTest {
        val outcome = granter.grant(
            ProgressionEvent.DeckSaved(deckId = "deck-1", cardCount = 60, occurredAt = now)
        )
        assertEquals(0, outcome.xpGranted)
        coVerify(exactly = 0) { dao.grantXpAtomically(any(), any(), any(), any()) }
    }

    @Test
    fun `friend added is a no-op once the weekly friend cap is reached`() = runTest {
        coEvery {
            dao.countDistinctSourceRefForCategorySince(XpSourceCategory.SOCIAL.name, any())
        } returns XpConfig.maxRewardedFriendsPerWeek

        val outcome = granter.grant(
            ProgressionEvent.FriendAdded(friendId = "friend-6", occurredAt = now)
        )

        assertEquals(0, outcome.xpGranted)
    }

    @Test
    fun `friend added is rewarded under the weekly friend cap`() = runTest {
        coEvery {
            dao.countDistinctSourceRefForCategorySince(XpSourceCategory.SOCIAL.name, any())
        } returns 4

        val outcome = granter.grant(
            ProgressionEvent.FriendAdded(friendId = "friend-5", occurredAt = now)
        )

        assertEquals(XpConfig.friendAdded, outcome.xpGranted)
    }

    @Test
    fun `tournament win stacks the win bonus`() = runTest {
        val outcome = granter.grant(
            ProgressionEvent.TournamentCompleted(
                tournamentId = 1L, type = "swiss", isLocalWinner = true, occurredAt = now,
            )
        )
        assertEquals(XpConfig.tournamentCompleted + XpConfig.tournamentWon, outcome.xpGranted)
    }

    @Test
    fun `level-up math computes the new level and leveledUp flag from the curve`() = runTest {
        // Start the player just below the level-2 threshold so a grant crosses it.
        val level2Floor = LevelCurve.cumulativeXpForLevel[2]
        val startXp = level2Floor - 1
        coEvery { dao.getProgression(any()) } returns PlayerProgressionEntity(
            id = PlayerProgressionEntity.SINGLETON_ID,
            totalXp = startXp,
            level = LevelCurve.levelForTotalXp(startXp),
            updatedAt = 0L,
        )

        val newTotalSlot = slot<Long>()
        val newLevelSlot = slot<Int>()
        coEvery {
            dao.grantXpAtomically(any(), capture(newTotalSlot), capture(newLevelSlot), any())
        } returns true

        // Survey grants 25 XP, enough to cross into level 2 from one-below-threshold.
        val outcome = granter.grant(
            ProgressionEvent.SurveyCompleted(surveyId = 1L, sessionId = 1L, occurredAt = now)
        )

        val expectedTotal = startXp + XpConfig.surveyCompleted
        assertEquals(expectedTotal, newTotalSlot.captured)
        assertEquals(LevelCurve.levelForTotalXp(expectedTotal), newLevelSlot.captured)
        assertEquals(LevelCurve.levelForTotalXp(expectedTotal), outcome.newLevel)
        assertTrue("crossing the level-2 threshold must set leveledUp", outcome.leveledUp)
    }
}
