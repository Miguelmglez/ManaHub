package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.usecase.card.ComputeCardTagsUseCase
import com.mmg.manahub.core.domain.usecase.card.ResolveCardStrategyTagsUseCase
import com.mmg.manahub.core.domain.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.util.TestFixtures
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CardRepositoryImpl].
 *
 * The CASCADE DELETE BUG regression group that used to live here (CardDao.upsert() must use
 * INSERT-IGNORE + @Update, NEVER OnConflictStrategy.REPLACE — REPLACE = DELETE+INSERT, which
 * CASCADEs the FK on user_cards and silently wipes UserCardEntity rows) tested
 * `refreshCollectionPrices()`, which was DELETED end-to-end (Backend & Performance Optimization
 * plan, WS1+WS3 Part B item 7a, 2026-07-28) — `PriceRefreshWorker` -> `RefreshCollectionPricesUseCase`
 * (`shared/core-data`) is now the sole price-refresh path, and it never calls `upsert`/`upsertAll`
 * at all (it writes prices via `CardDao.updatePricesBatch`, a plain `UPDATE` with no INSERT
 * involved, so the REPLACE-CASCADE hazard does not even apply to that path). The underlying
 * invariant is still enforced by [CardDao]'s own `@Insert(onConflict = OnConflictStrategy.IGNORE)` +
 * `@Update` implementation (see its class KDoc) — this file no longer needs to re-assert it via a
 * deleted repository method. WS6 (android-unit-test-writer) should add equivalent coverage against
 * `RefreshCollectionPricesUseCase`/`PriceRefreshWorker` instead.
 */
class CardRepositoryImplTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val cardDao   = mockk<CardDao>(relaxed = true)
    private val remote    = mockk<ScryfallRemoteDataSource>()
    private val userPrefs = mockk<UserPreferencesDataStore>()

    // Deck Engine Unification plan, D8, §5 Phase 5c: tag resolution moved to a background job
    // (see scheduleTagResolution/scheduleTagResolutionBatch) that this repository no longer builds
    // itself — relaxed so every existing test (none of which asserts on tag CONTENT) keeps working
    // without stubbing this on every call; a relaxed suspend call returns an empty-list Result and
    // never throws, so the background job's `.onFailure { recordSafeNonFatal(...) }` path (which
    // would hit the real, unmocked FirebaseCrashlytics in a JVM unit test) is never exercised here.
    private val resolveCardStrategyTags = mockk<ResolveCardStrategyTagsUseCase>(relaxed = true)

    private lateinit var repository: CardRepositoryImpl

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        // The background tag-resolution job's .onFailure branch (scheduleTagResolution/
        // scheduleTagResolutionBatch) calls recordSafeNonFatal, which hits the real
        // FirebaseCrashlytics.getInstance() outside a runCatching the test can't otherwise reach —
        // per feedback_crashlytics_helper_top_level_functions, mock it statically for every test.
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)

        val testDispatcher = UnconfinedTestDispatcher()

        // DataStore flows used inside the repository's tag-computation path.
        every { userPrefs.tagAutoThresholdFlow }    returns flowOf(SuggestTagsUseCase.DEFAULT_AUTO_THRESHOLD)
        every { userPrefs.tagSuggestThresholdFlow } returns flowOf(SuggestTagsUseCase.DEFAULT_SUGGEST_THRESHOLD)

        repository = CardRepositoryImpl(
            cardDao               = cardDao,
            remote                = remote,
            resolveCardStrategyTags = resolveCardStrategyTags,
            userPrefs             = userPrefs,
            ioDispatcher          = testDispatcher,
            defaultDispatcher     = testDispatcher,
            // Unconfined so the background enrichment job runs deterministically within runTest,
            // same rationale as ioDispatcher/defaultDispatcher above.
            appScope              = CoroutineScope(testDispatcher),
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — (RETIRED) CASCADE DELETE BUG REGRESSION (refreshCollectionPrices)
    //  See the class KDoc: refreshCollectionPrices() was deleted (WS1+WS3 Part B item 7a,
    //  2026-07-28); these 8 tests exercised it directly and were removed with it.
    // ══════════════════════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — getCardById cache logic
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given fresh cache when getCardById then returns cached card without calling remote`() = runTest {
        // Arrange — the cache hit condition is: isFresh(cachedAt) AND relatedUris != "{}".
        // buildFreshCardEntity defaults relatedUris to "{}", so we must override it to a
        // non-empty value to trigger the cache-hit path.
        val freshEntity = TestFixtures.buildFreshCardEntity("id-001").copy(
            relatedUris = """{"gatherer":"https://gatherer.wizards.com/Pages/Card/Details.aspx?multiverseid=209"}"""
        )
        coEvery { cardDao.getById("id-001") } returns freshEntity

        // Act
        val result = repository.getCardById("id-001")

        // Assert
        assertTrue(result is DataResult.Success)
        coVerify(exactly = 0) { remote.getCardById(any()) }
    }

    @Test
    fun `given expired cache when getCardById and remote succeeds then returns fresh data`() = runTest {
        // Arrange
        val expiredEntity = TestFixtures.buildExpiredCardEntity("id-001")
        val freshCard     = TestFixtures.buildCard("id-001")
        val freshEntity   = TestFixtures.buildFreshCardEntity("id-001")

        coEvery { cardDao.getById("id-001") } returnsMany listOf(expiredEntity, freshEntity)
        coEvery { remote.getCardById("id-001") } returns Result.success(freshCard)
        coEvery { cardDao.upsert(any()) } returns Unit
        coEvery { cardDao.clearStale(any()) } returns Unit

        // Act
        val result = repository.getCardById("id-001")

        // Assert
        assertTrue(result is DataResult.Success)
        coVerify(exactly = 1) { remote.getCardById("id-001") }
        // upsert was called — not a replace
        coVerify(exactly = 1) { cardDao.upsert(any()) }
    }

    @Test
    fun `given no cache and remote fails when getCardById then returns DataResult Error`() = runTest {
        // Arrange
        coEvery { cardDao.getById("id-001") } returns null
        coEvery { remote.getCardById("id-001") } returns
                Result.failure(RuntimeException("HTTP 404"))

        // Act
        val result = repository.getCardById("id-001")

        // Assert
        assertTrue(result is DataResult.Error)
        assertEquals("HTTP 404", (result as DataResult.Error).message)
    }

    @Test
    fun `given stale cache and remote fails when getCardById then returns stale data with isStale true`() = runTest {
        // Arrange
        val staleEntity = TestFixtures.buildStaleCardEntity("id-001")

        coEvery { cardDao.getById("id-001") } returns staleEntity
        coEvery { remote.getCardById("id-001") } returns
                Result.failure(RuntimeException("No network"))

        // Act
        val result = repository.getCardById("id-001")

        // Assert: fallback to stale cache — not an error
        assertTrue(result is DataResult.Success)
        assertTrue((result as DataResult.Success).isStale)
    }

    @Test
    fun `given getCardById caches new card then upsert preserves existing userTags`() = runTest {
        // Arrange — card already has user tags in cache (JSON format used by TagRecord: {k, c})
        val existingEntity = TestFixtures.buildExpiredCardEntity("id-001").copy(
            userTags = """[{"k":"my_tag","c":"CUSTOM"}]""",
        )
        val remoteCard    = TestFixtures.buildCard("id-001")
        val updatedEntity = TestFixtures.buildFreshCardEntity("id-001")

        coEvery { cardDao.getById("id-001") } returnsMany listOf(existingEntity, updatedEntity)
        coEvery { remote.getCardById("id-001") } returns Result.success(remoteCard)

        val capturedEntity = slot<CardEntity>()
        coEvery { cardDao.upsert(capture(capturedEntity)) } returns Unit
        coEvery { cardDao.clearStale(any()) } returns Unit

        // Act
        repository.getCardById("id-001")

        // Assert: user tags from the existing entity are carried over (not empty)
        assertTrue(
            "userTags must be preserved from existing entity",
            capturedEntity.captured.userTags.isNotBlank() && capturedEntity.captured.userTags != "[]"
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — updatePrices
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid prices when updatePrices then delegates to cardDao updatePrices`() = runTest {
        // Arrange
        coEvery { cardDao.updatePrices(any(), any(), any(), any(), any(), any()) } returns Unit

        // Act
        repository.updatePrices(
            scryfallId   = "id-001",
            priceUsd     = 1.50,
            priceUsdFoil = 5.00,
            priceEur     = 1.20,
            priceEurFoil = 4.00,
            updatedAt    = 123456789L,
        )

        // Assert
        coVerify(exactly = 1) {
            cardDao.updatePrices(
                scryfallId   = "id-001",
                priceUsd     = 1.50,
                priceUsdFoil = 5.00,
                priceEur     = 1.20,
                priceEurFoil = 4.00,
                updatedAt    = 123456789L,
            )
        }
    }

    @Test
    fun `given null prices when updatePrices then null values are forwarded to dao`() = runTest {
        // Arrange
        coEvery { cardDao.updatePrices(any(), any(), any(), any(), any(), any()) } returns Unit

        // Act
        repository.updatePrices(
            scryfallId   = "id-001",
            priceUsd     = null,
            priceUsdFoil = null,
            priceEur     = null,
            priceEurFoil = null,
            updatedAt    = 987654321L,
        )

        // Assert
        coVerify(exactly = 1) {
            cardDao.updatePrices(
                scryfallId   = "id-001",
                priceUsd     = null,
                priceUsdFoil = null,
                priceEur     = null,
                priceEurFoil = null,
                updatedAt    = 987654321L,
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP — Broken-image fix (2026-07-17): getCachedEnglishSiblings (Room-only, no network)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given cached English row matching set and collector number when getCachedEnglishSiblings then it is returned keyed by the pair`() = runTest {
        val englishEntity = TestFixtures.buildCardEntity(scryfallId = "en-001")
            .copy(setCode = "lea", collectorNumber = "5", lang = "en")
        coEvery { cardDao.getEnglishSiblings(listOf("lea"), listOf("5")) } returns listOf(englishEntity)

        val result = repository.getCachedEnglishSiblings(setOf("lea" to "5"))

        assertEquals(1, result.size)
        assertEquals("en-001", result["lea" to "5"]?.scryfallId)
    }

    @Test
    fun `given DAO cross-product returns a non-matching pair when getCachedEnglishSiblings then it is filtered out`() = runTest {
        // Room has no tuple-IN support: the DAO query is a cross product of the two IN lists.
        // A row that matches set OR number but not the exact pair must not leak into the result.
        val wrongPairEntity = TestFixtures.buildCardEntity(scryfallId = "en-wrong")
            .copy(setCode = "lea", collectorNumber = "999", lang = "en")
        coEvery { cardDao.getEnglishSiblings(listOf("lea"), listOf("5")) } returns listOf(wrongPairEntity)

        val result = repository.getCachedEnglishSiblings(setOf("lea" to "5"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `given empty pairs when getCachedEnglishSiblings then DAO is never queried and result is empty`() = runTest {
        val result = repository.getCachedEnglishSiblings(emptySet())

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { cardDao.getEnglishSiblings(any(), any()) }
    }

    @Test
    fun `given no cached English row for a pair when getCachedEnglishSiblings then that pair is simply absent`() = runTest {
        coEvery { cardDao.getEnglishSiblings(listOf("lea"), listOf("5")) } returns emptyList()

        val result = repository.getCachedEnglishSiblings(setOf("lea" to "5"))

        assertTrue(result.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP — Deck Engine Unification plan, D8, §5 Phase 5c: non-blocking tag resolution
    //  (card add / search / detail hot paths must not wait on Supabase/on-device tag analysis)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a failing resolveCardStrategyTags when getCardById caches a new card then the fetch still succeeds`() = runTest {
        // The background enrichment job's own failure must never surface as a fetch failure —
        // this is the "add must not fail if tag analysis fails" half of the D8 requirement.
        // fetchAndCacheFromRemote reads cardDao.getById TWICE (the initial cache check, then a
        // final re-read of the just-upserted row) — returnsMany mirrors the pre-existing
        // "given expired cache..." test's precedent for this exact two-call shape.
        coEvery { cardDao.getById("id-001") } returnsMany listOf(null, TestFixtures.buildFreshCardEntity("id-001"))
        coEvery { remote.getCardById("id-001") } returns Result.success(TestFixtures.buildCard("id-001"))
        coEvery { cardDao.upsert(any()) } returns Unit
        coEvery { cardDao.clearStale(any()) } returns Unit
        coEvery {
            resolveCardStrategyTags(any(), any(), any(), any())
        } throws RuntimeException("strategy tags unavailable")

        val result = repository.getCardById("id-001")

        assertTrue(result is DataResult.Success)
    }

    @Test
    fun `given getCardById caches a new card then upsert happens WITHOUT waiting on resolveCardStrategyTags`() = runTest {
        // A card add resolves via getCardById immediately on the cache write — tag resolution is
        // scheduled afterward on a separate background job, not awaited inline.
        coEvery { cardDao.getById("id-001") } returnsMany listOf(null, TestFixtures.buildFreshCardEntity("id-001"))
        coEvery { remote.getCardById("id-001") } returns Result.success(TestFixtures.buildCard("id-001"))
        coEvery { cardDao.upsert(any()) } returns Unit
        coEvery { cardDao.clearStale(any()) } returns Unit

        repository.getCardById("id-001")

        // The first upsert() writes preserved (pre-analysis) tags — never blocked by
        // resolveCardStrategyTags — and the background job runs the resolution afterward.
        coVerify(exactly = 1) { cardDao.upsert(any()) }
        coVerify(exactly = 1) { resolveCardStrategyTags(any(), any(), any(), any()) }
    }

    @Test
    fun `given resolveCardStrategyTags returns confirmed tags when getCardById caches a new card then the background job persists them`() = runTest {
        coEvery { cardDao.getById("id-001") } returnsMany listOf(null, TestFixtures.buildFreshCardEntity("id-001"))
        coEvery { remote.getCardById("id-001") } returns Result.success(TestFixtures.buildCard("id-001"))
        coEvery { cardDao.upsert(any()) } returns Unit
        coEvery { cardDao.clearStale(any()) } returns Unit
        val resolvedResult = ComputeCardTagsUseCase.Result(
            confirmedTags = listOf(CardTag.REMOVAL),
            suggestedTags = emptyList(),
        )
        coEvery { resolveCardStrategyTags(any(), any(), any(), any()) } returns resolvedResult

        repository.getCardById("id-001")

        coVerify(exactly = 1) {
            cardDao.updateTagsAndSuggestions(
                scryfallId = "id-001",
                tagsJson = any(),
                suggestedJson = any(),
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP — Strategy-tags backfill (2026-07-22): backfillMissingStrategyTags
    //  One-time, self-terminating startup pass for owned cards never resolved against the
    //  precomputed Supabase table (see CardRepository.backfillMissingStrategyTags KDoc).
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given no candidates when backfillMissingStrategyTags then nothing is resolved`() = runTest {
        coEvery { cardDao.getScryfallIdsMissingStrategyTags(40) } returns emptyList()

        repository.backfillMissingStrategyTags(40)

        coVerify(exactly = 0) { resolveCardStrategyTags(any(), any(), any(), any()) }
        coVerify(exactly = 0) { cardDao.updateTagsAndSuggestions(any(), any(), any()) }
    }

    @Test
    fun `given candidates when backfillMissingStrategyTags then each is resolved sequentially via the shared resolution path`() = runTest {
        val entity1 = TestFixtures.buildCardEntity(scryfallId = "id-001")
        val entity2 = TestFixtures.buildCardEntity(scryfallId = "id-002")
        coEvery { cardDao.getScryfallIdsMissingStrategyTags(40) } returns listOf("id-001", "id-002")
        coEvery { cardDao.getById("id-001") } returns entity1
        coEvery { cardDao.getById("id-002") } returns entity2
        coEvery { resolveCardStrategyTags(match { it.scryfallId == "id-001" }, any(), any(), any()) } returns
            ComputeCardTagsUseCase.Result(confirmedTags = listOf(CardTag.REMOVAL), suggestedTags = emptyList())
        coEvery { resolveCardStrategyTags(match { it.scryfallId == "id-002" }, any(), any(), any()) } returns
            ComputeCardTagsUseCase.Result(confirmedTags = listOf(CardTag.RAMP), suggestedTags = emptyList())

        repository.backfillMissingStrategyTags(40)

        // Each candidate's full resolve -> persist chain completes before the next candidate
        // starts (a plain sequential forEach, never N concurrent launches) -- mirrors
        // backfillMissingOracleIds/scheduleTagResolutionBatch's identical rationale.
        coVerifyOrder {
            cardDao.getById("id-001")
            resolveCardStrategyTags(match { it.scryfallId == "id-001" }, any(), any(), any())
            cardDao.updateTagsAndSuggestions(scryfallId = "id-001", tagsJson = any(), suggestedJson = any())
            cardDao.getById("id-002")
            resolveCardStrategyTags(match { it.scryfallId == "id-002" }, any(), any(), any())
            cardDao.updateTagsAndSuggestions(scryfallId = "id-002", tagsJson = any(), suggestedJson = any())
        }
    }

    @Test
    fun `given one candidate fails to resolve when backfillMissingStrategyTags then the rest of the batch still completes`() = runTest {
        val entity1 = TestFixtures.buildCardEntity(scryfallId = "id-fail")
        val entity2 = TestFixtures.buildCardEntity(scryfallId = "id-ok")
        coEvery { cardDao.getScryfallIdsMissingStrategyTags(40) } returns listOf("id-fail", "id-ok")
        coEvery { cardDao.getById("id-fail") } returns entity1
        coEvery { cardDao.getById("id-ok") } returns entity2
        coEvery { resolveCardStrategyTags(match { it.scryfallId == "id-fail" }, any(), any(), any()) } throws
            RuntimeException("strategy tags unavailable")
        coEvery { resolveCardStrategyTags(match { it.scryfallId == "id-ok" }, any(), any(), any()) } returns
            ComputeCardTagsUseCase.Result(confirmedTags = listOf(CardTag.REMOVAL), suggestedTags = emptyList())

        repository.backfillMissingStrategyTags(40)

        coVerify(exactly = 0) {
            cardDao.updateTagsAndSuggestions(scryfallId = "id-fail", tagsJson = any(), suggestedJson = any())
        }
        coVerify(exactly = 1) {
            cardDao.updateTagsAndSuggestions(scryfallId = "id-ok", tagsJson = any(), suggestedJson = any())
        }
    }

    @Test
    fun `given a candidate whose entity vanished before resolution when backfillMissingStrategyTags then it is skipped without aborting the batch`() = runTest {
        val entity2 = TestFixtures.buildCardEntity(scryfallId = "id-ok")
        coEvery { cardDao.getScryfallIdsMissingStrategyTags(40) } returns listOf("id-gone", "id-ok")
        coEvery { cardDao.getById("id-gone") } returns null
        coEvery { cardDao.getById("id-ok") } returns entity2
        coEvery { resolveCardStrategyTags(match { it.scryfallId == "id-ok" }, any(), any(), any()) } returns
            ComputeCardTagsUseCase.Result(confirmedTags = listOf(CardTag.REMOVAL), suggestedTags = emptyList())

        repository.backfillMissingStrategyTags(40)

        coVerify(exactly = 0) { resolveCardStrategyTags(match { it.scryfallId == "id-gone" }, any(), any(), any()) }
        coVerify(exactly = 1) {
            cardDao.updateTagsAndSuggestions(scryfallId = "id-ok", tagsJson = any(), suggestedJson = any())
        }
    }

    @Test
    fun `given the candidate query itself throws when backfillMissingStrategyTags then it is a silent no-op`() = runTest {
        coEvery { cardDao.getScryfallIdsMissingStrategyTags(40) } throws RuntimeException("DB error")

        // Must not propagate -- this is a best-effort startup pass, never allowed to crash app start.
        repository.backfillMissingStrategyTags(40)

        coVerify(exactly = 0) { resolveCardStrategyTags(any(), any(), any(), any()) }
    }
}
