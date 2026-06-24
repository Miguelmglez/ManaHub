package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.mmg.manahub.core.gamification.FixedClock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import java.util.Locale

/**
 * Unit tests for [XpGranter]: idempotency, daily collection cap, deck/day cap, friend/week cap,
 * win-bonus stacking, and level-up math.
 *
 * The clock is pinned to a fixed instant so day/week window queries are deterministic. The DAO is
 * mocked; `grantXpAtomically` returns true unless a test overrides it (duplicate path).
 */
class XpGranterTest {

    private lateinit var dao: GamificationDao
    private lateinit var dataStore: UserPreferencesDataStore
    private lateinit var granter: XpGranter

    // Pinned to a Thursday so week-window math is stable across runs.
    private val fixedInstant: Instant = Instant.parse("2026-06-11T12:00:00Z")
    private val timeZone: TimeZone = TimeZone.UTC
    private val now: Instant get() = fixedInstant

    // Stable per-device id used to scope local-id-derived ledger keys (ADR-002 §L3).
    private val deviceId = "device-A"

    // Saved so the Monday-boundary tests can mutate Locale.getDefault() and restore it after.
    private val originalLocale: Locale = Locale.getDefault()

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        dataStore = mockk(relaxed = true)
        coEvery { dataStore.getOrCreateGamificationDeviceId() } returns deviceId
        val clock = FixedClock(fixedInstant)
        granter = XpGranter(dao, clock, timeZone, dataStore)

        // Default happy-path stubs. Individual tests override as needed.
        coEvery { dao.hasTransaction(any()) } returns false
        coEvery { dao.getProgression(any()) } returns null
        coEvery { dao.sumXpForCategorySince(any(), any()) } returns 0
        coEvery { dao.countDistinctSourceRefForCategorySince(any(), any()) } returns 0
        // Default happy path: the atomic grant applies. The new total/level are computed by the DAO
        // from the delta + the (mocked) current progression; mirror that here from getProgression().
        coEvery {
            dao.grantXpAtomically(any(), any(), any(), any())
        } coAnswers { appliedResult(secondArg<Int>()) }
    }

    /** Builds an applied [GamificationDao.GrantResult] from the current (mocked) progression + delta. */
    private suspend fun appliedResult(amount: Int): GamificationDao.GrantResult {
        val current = dao.getProgression()?.totalXp ?: 0L
        val newTotal = current + amount
        return GamificationDao.GrantResult(
            applied = true,
            previousTotalXp = current,
            newTotalXp = newTotal,
            previousLevel = LevelCurve.levelForTotalXp(current),
            newLevel = LevelCurve.levelForTotalXp(newTotal),
        )
    }

    /** A duplicate/no-op result that leaves progression unchanged. */
    private fun noopResult(): GamificationDao.GrantResult = GamificationDao.GrantResult(
        applied = false,
        previousTotalXp = 0L,
        newTotalXp = 0L,
        previousLevel = LevelCurve.MIN_LEVEL,
        newLevel = LevelCurve.MIN_LEVEL,
    )

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
        // survey is device-scoped, so the pre-check sees the prefixed key.
        coEvery { dao.hasTransaction("dev:$deviceId:survey:5") } returns true

        val outcome = granter.grant(
            ProgressionEvent.SurveyCompleted(surveyId = 5L, sessionId = 1L, occurredAt = now)
        )

        assertEquals(0, outcome.xpGranted)
        coVerify(exactly = 0) { dao.grantXpAtomically(any(), any(), any(), any()) }
    }

    @Test
    fun `atomic grant rejection (race) yields a no-op outcome`() = runTest {
        // hasTransaction misses (false) but the atomic insert loses the race and is not applied.
        coEvery { dao.grantXpAtomically(any(), any(), any(), any()) } returns noopResult()

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
        coEvery {
            dao.grantXpAtomically(capture(txnSlot), any(), any(), any())
        } coAnswers { appliedResult(secondArg<Int>()) }

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

    // ── Weekly friend-cap window starts on Monday regardless of Locale ────────────────

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    /**
     * Drives a [ProgressionEvent.FriendAdded] grant and returns the `sinceMillis` the granter used for
     * the weekly SOCIAL cap query — i.e. the computed start-of-week boundary.
     */
    private suspend fun weeklyWindowStartFor(locale: Locale): Long {
        Locale.setDefault(locale)
        // Re-create the granter so it is constructed under the test locale (no hidden state, but explicit).
        val granterUnderLocale = XpGranter(dao, FixedClock(fixedInstant), timeZone, dataStore)
        val sinceSlot = slot<Long>()
        coEvery {
            dao.countDistinctSourceRefForCategorySince(XpSourceCategory.SOCIAL.name, capture(sinceSlot))
        } returns 0
        granterUnderLocale.grant(ProgressionEvent.FriendAdded(friendId = "friend-x", occurredAt = now))
        return sinceSlot.captured
    }

    @Test
    fun `weekly friend cap window starts on Monday under a Sunday-first locale`() = runTest {
        // The clock is a Thursday (2026-06-11); the ISO-week Monday is 2026-06-08T00:00:00Z.
        val today = fixedInstant.toLocalDateTime(timeZone).date
        val expectedMonday = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
            .atStartOfDayIn(timeZone).toEpochMilliseconds()

        // Locale.US treats Sunday as the first day of the week; the window must still start on Monday.
        val sinceUs = weeklyWindowStartFor(Locale.US)

        assertEquals(expectedMonday, sinceUs)
    }

    @Test
    fun `weekly friend cap window is the same Monday across Sunday-first and Monday-first locales`() = runTest {
        val sinceUs = weeklyWindowStartFor(Locale.US)        // Sunday-first
        val sinceFrance = weeklyWindowStartFor(Locale.FRANCE) // Monday-first

        // Both must resolve to the identical Monday boundary — proving locale-independence.
        assertEquals(sinceUs, sinceFrance)
    }

    // ── L3: per-device idempotency-key scoping ────────────────────────────────────────

    /** Captures the idempotency key the granter writes to the ledger for [event] under [deviceUuid]. */
    private suspend fun capturedKeyFor(event: ProgressionEvent, deviceUuid: String): String {
        val freshDao = mockk<GamificationDao>(relaxed = true)
        coEvery { freshDao.hasTransaction(any()) } returns false
        coEvery { freshDao.getProgression(any()) } returns null
        coEvery { freshDao.sumXpForCategorySince(any(), any()) } returns 0
        coEvery { freshDao.countDistinctSourceRefForCategorySince(any(), any()) } returns 0
        val keySlot = slot<XpTransactionEntity>()
        coEvery { freshDao.grantXpAtomically(capture(keySlot), any(), any(), any()) } coAnswers {
            GamificationDao.GrantResult(
                applied = true, previousTotalXp = 0L, newTotalXp = secondArg<Int>().toLong(),
                previousLevel = LevelCurve.MIN_LEVEL, newLevel = LevelCurve.MIN_LEVEL,
            )
        }
        val ds = mockk<UserPreferencesDataStore>(relaxed = true)
        coEvery { ds.getOrCreateGamificationDeviceId() } returns deviceUuid
        XpGranter(freshDao, FixedClock(fixedInstant), timeZone, ds).grant(event)
        return keySlot.captured.idempotencyKey
    }

    @Test
    fun `local-id-derived key gets a different ledger key per device for the same session`() = runTest {
        val event = ProgressionEvent.GameFinished(
            sessionId = 42L, isLocalWin = false, mode = "casual", playerCount = 2,
            durationMs = 0L, winTurn = null, localFinalLife = null, occurredAt = now,
        )
        val keyDeviceA = capturedKeyFor(event, "device-A")
        val keyDeviceB = capturedKeyFor(event, "device-B")

        // Two distinct guest devices must NOT collide on the same local session id.
        assertEquals("dev:device-A:game:42:result", keyDeviceA)
        assertEquals("dev:device-B:game:42:result", keyDeviceB)
        assertTrue("distinct devices must produce distinct keys", keyDeviceA != keyDeviceB)
    }

    @Test
    fun `globally-stable key is NOT device-prefixed and is identical across devices`() = runTest {
        // app_open is per-user-per-day (globally stable) → must dedupe to ONE grant across devices.
        val event = ProgressionEvent.AppOpenedToday(localDate = "2026-06-11", occurredAt = now)
        val keyDeviceA = capturedKeyFor(event, "device-A")
        val keyDeviceB = capturedKeyFor(event, "device-B")

        assertEquals("app_open:2026-06-11", keyDeviceA)
        assertEquals(keyDeviceA, keyDeviceB)
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

        // The DAO computes the new total/level from the (mocked) progression + the delta it is passed;
        // `appliedResult` mirrors that math so the returned outcome carries the real curve-derived level.
        coEvery {
            dao.grantXpAtomically(any(), any(), any(), any())
        } coAnswers { appliedResult(secondArg<Int>()) }

        // Survey grants 25 XP, enough to cross into level 2 from one-below-threshold.
        val outcome = granter.grant(
            ProgressionEvent.SurveyCompleted(surveyId = 1L, sessionId = 1L, occurredAt = now)
        )

        val expectedTotal = startXp + XpConfig.surveyCompleted
        assertEquals(LevelCurve.levelForTotalXp(expectedTotal), outcome.newLevel)
        assertTrue("crossing the level-2 threshold must set leveledUp", outcome.leveledUp)
    }
}
