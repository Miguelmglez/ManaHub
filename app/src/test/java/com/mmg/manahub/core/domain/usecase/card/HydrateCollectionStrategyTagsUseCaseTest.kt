package com.mmg.manahub.core.domain.usecase.card

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.CardTagsUpdate
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.domain.repository.CardStrategyTagsRepository
import com.mmg.manahub.core.domain.repository.CardStrategyTagsResult
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Coverage for [HydrateCollectionStrategyTagsUseCase] (bulk strategy-tag hydration, 2026-09-07):
 * candidates come from the existing self-terminating
 * [CardDao.getScryfallIdsMissingStrategyTags] query plus the per-printing repair net, a resolved
 * payload fans out to EVERY cached printing of that `oracle_id` (each with its own user-tag
 * union), the CPU-heavy on-device fallback is capped per run, and the "another run should follow"
 * signal is gated on provable progress so the worker chain can never loop forever.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HydrateCollectionStrategyTagsUseCaseTest {

    private val cardDao: CardDao = mockk(relaxed = true)
    private val repository: CardStrategyTagsRepository = mockk(relaxed = true)
    private val resolve: ResolveCardStrategyTagsUseCase = mockk(relaxed = true)
    private val userPreferences: UserPreferencesDataStore = mockk(relaxed = true)
    private val crashReporter: CrashReporter = mockk(relaxed = true)

    private val written = mutableListOf<CardTagsUpdate>()

    @Before
    fun setUp() {
        written.clear()
        val batch = slot<List<CardTagsUpdate>>()
        coEvery { cardDao.updateTagsAndSuggestionsBatch(capture(batch)) } answers { written += batch.captured }
        coEvery { cardDao.getScryfallIdsWithUnwrittenStrategyTags(any()) } returns emptyList()
    }

    private fun useCase() = HydrateCollectionStrategyTagsUseCase(
        cardDao = cardDao,
        cardStrategyTagsRepository = repository,
        resolveCardStrategyTags = resolve,
        userPreferences = userPreferences,
        crashReporter = crashReporter,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    private fun entity(id: String, oracleId: String, tags: String = "[]"): CardEntity =
        TestFixtures.buildCardEntity(scryfallId = id, tags = tags).copy(oracleId = oracleId)

    private fun found(vararg keys: String) =
        CardStrategyTagsResult.Found(tags = keys.map { CardTag(it, TagCategory.STRATEGY) })

    /** Echoes the prefetched payload back, unioned with the row's own tags json — the real
     *  use case's hit-path semantics, without pulling the whole analyzer into this suite. */
    private fun stubUnionResolve() {
        coEvery { resolve.resolveWithPrefetched(any(), any(), any(), any(), any()) } answers {
            val existing = secondArg<String?>().orEmpty()
            val hit = thirdArg<CardStrategyTagsResult?>() as? CardStrategyTagsResult.Found
            val own = if (existing.contains("my_custom_tag")) listOf(CardTag("my_custom_tag", TagCategory.CUSTOM)) else emptyList()
            ComputeCardTagsUseCase.Result(
                confirmedTags = (own + hit?.tags.orEmpty()).distinct(),
                suggestedTags = emptyList(),
            )
        }
    }

    private fun stubThresholds() {
        every { userPreferences.tagAutoThresholdFlow } returns flowOf(0.9f)
        every { userPreferences.tagSuggestThresholdFlow } returns flowOf(0.6f)
    }

    @Test
    fun `given no candidates when invoked then nothing is fetched and no follow-up run is requested`() = runTest {
        coEvery { cardDao.getScryfallIdsMissingStrategyTags(any()) } returns emptyList()

        val result = useCase()()

        assertEquals(0, result.candidateCount)
        assertFalse(result.hasMoreWork)
        coVerify(exactly = 0) { repository.getStrategyTagsBatch(any()) }
        assertTrue(written.isEmpty())
    }

    @Test
    fun `given candidates when invoked then only their oracle ids are batch-fetched, in ONE call`() = runTest {
        stubUnionResolve()
        coEvery { cardDao.getScryfallIdsMissingStrategyTags(any()) } returns listOf("card-1", "card-2")
        coEvery { cardDao.getByIds(any()) } returns listOf(entity("card-1", "oracle-1"), entity("card-2", "oracle-2"))
        val requested = slot<Set<String>>()
        coEvery { repository.getStrategyTagsBatch(capture(requested)) } returns
            mapOf("oracle-1" to found("ramp"), "oracle-2" to found("ramp"))
        coEvery { cardDao.getByOracleIds(any()) } returns
            listOf(entity("card-1", "oracle-1"), entity("card-2", "oracle-2"))

        val result = useCase()()

        coVerify(exactly = 1) { repository.getStrategyTagsBatch(any()) }
        assertEquals(setOf("oracle-1", "oracle-2"), requested.captured)
        assertEquals(2, result.precomputedCount)
        assertEquals(0, result.onDeviceCount)
        assertEquals(setOf("card-1", "card-2"), written.map { it.scryfallId }.toSet())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Per-printing write gap — the 2026-09-07 device-measured defect
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given two owned printings of one oracle id then BOTH are written, not just the candidate`() = runTest {
        stubUnionResolve()
        // Only ONE printing is a candidate: the oracle-keyed query excludes the sibling as soon as
        // the oracle_id is cached, so before the fan-out fix the sibling stayed '[]' forever.
        coEvery { cardDao.getScryfallIdsMissingStrategyTags(any()) } returns listOf("printing-a")
        coEvery { cardDao.getByIds(any()) } returns listOf(entity("printing-a", "oracle-1"))
        coEvery { repository.getStrategyTagsBatch(any()) } returns mapOf("oracle-1" to found("ramp"))
        coEvery { cardDao.getByOracleIds(listOf("oracle-1")) } returns
            listOf(entity("printing-a", "oracle-1"), entity("printing-b", "oracle-1"))

        val result = useCase()()

        assertEquals(setOf("printing-a", "printing-b"), written.map { it.scryfallId }.toSet())
        assertEquals(2, result.precomputedCount)
        assertEquals(1, result.repairedCount)
        assertTrue(written.all { it.tagsJson.contains("ramp") })
    }

    @Test
    fun `given a sibling printing carrying its OWN user tag then the sibling payload never clobbers it`() = runTest {
        stubUnionResolve()
        coEvery { cardDao.getScryfallIdsMissingStrategyTags(any()) } returns listOf("printing-a")
        coEvery { cardDao.getByIds(any()) } returns listOf(entity("printing-a", "oracle-1"))
        coEvery { repository.getStrategyTagsBatch(any()) } returns mapOf("oracle-1" to found("ramp"))
        coEvery { cardDao.getByOracleIds(any()) } returns listOf(
            entity("printing-a", "oracle-1"),
            entity("printing-b", "oracle-1", tags = """[{"k":"my_custom_tag","c":"CUSTOM"}]"""),
        )

        useCase()()

        val sibling = written.single { it.scryfallId == "printing-b" }
        assertTrue(sibling.tagsJson.contains("my_custom_tag"))
        assertTrue(sibling.tagsJson.contains("ramp"))
        // The candidate keeps its own (empty) starting point -- no cross-contamination either way.
        assertFalse(written.single { it.scryfallId == "printing-a" }.tagsJson.contains("my_custom_tag"))
    }

    @Test
    fun `given rows stranded by an earlier interrupted run then the repair query rewrites them from cache`() = runTest {
        stubUnionResolve()
        // Primary query is drained -- these rows are unreachable through it, by construction.
        coEvery { cardDao.getScryfallIdsMissingStrategyTags(any()) } returns emptyList()
        coEvery { cardDao.getScryfallIdsWithUnwrittenStrategyTags(any()) } returns listOf("stranded-1")
        coEvery { cardDao.getByIds(any()) } returns listOf(entity("stranded-1", "oracle-9"))
        coEvery { repository.getStrategyTagsBatch(any()) } returns mapOf("oracle-9" to found("card_draw"))
        coEvery { cardDao.getByOracleIds(any()) } returns listOf(entity("stranded-1", "oracle-9"))

        val result = useCase()()

        assertEquals(listOf("stranded-1"), written.map { it.scryfallId })
        assertTrue(written.single().tagsJson.contains("card_draw"))
        // The repair query is a stable fixed point, so it must NEVER drive the retry chain.
        assertFalse(result.hasMoreWork)
    }

    @Test
    fun `given a candidate with a blank oracle id then it is skipped - it can never have a precomputed row`() = runTest {
        stubUnionResolve()
        coEvery { cardDao.getScryfallIdsMissingStrategyTags(any()) } returns listOf("card-1", "card-2")
        coEvery { cardDao.getByIds(any()) } returns listOf(entity("card-1", "oracle-1"), entity("card-2", ""))
        val requested = slot<Set<String>>()
        coEvery { repository.getStrategyTagsBatch(capture(requested)) } returns mapOf("oracle-1" to found())
        coEvery { cardDao.getByOracleIds(any()) } returns listOf(entity("card-1", "oracle-1"))

        useCase()()

        assertEquals(setOf("oracle-1"), requested.captured)
    }

    @Test
    fun `given a precomputed hit on a card with user tags then the union is what gets persisted`() = runTest {
        stubUnionResolve()
        val userTagJson = """[{"k":"my_custom_tag","c":"CUSTOM"}]"""
        coEvery { cardDao.getScryfallIdsMissingStrategyTags(any()) } returns listOf("card-1")
        coEvery { cardDao.getByIds(any()) } returns listOf(entity("card-1", "oracle-1", tags = userTagJson))
        coEvery { repository.getStrategyTagsBatch(any()) } returns mapOf("oracle-1" to found("ramp"))
        coEvery { cardDao.getByOracleIds(any()) } returns listOf(entity("card-1", "oracle-1", tags = userTagJson))

        useCase()()

        coVerify(exactly = 1) { resolve.resolveWithPrefetched(any(), userTagJson, any(), any(), any()) }
        assertTrue(written.single().tagsJson.contains("my_custom_tag"))
        assertTrue(written.single().tagsJson.contains("ramp"))
    }

    @Test
    fun `given more batch misses than the on-device cap then only the cap runs the analyzer this pass`() = runTest {
        stubThresholds()
        val ids = (1..10).map { "card-$it" }
        coEvery { cardDao.getScryfallIdsMissingStrategyTags(any()) } returns ids
        coEvery { cardDao.getByIds(any()) } returns (1..10).map { entity("card-$it", "oracle-$it") }
        coEvery { repository.getStrategyTagsBatch(any()) } returns
            (1..10).associate { "oracle-$it" to CardStrategyTagsResult.NotFound }
        coEvery { resolve.invoke(any(), any(), any(), any()) } returns
            ComputeCardTagsUseCase.Result(confirmedTags = emptyList(), suggestedTags = emptyList())

        val result = useCase()(batchSize = 100, onDeviceCap = 3)

        assertEquals(3, result.onDeviceCount)
        assertEquals(0, result.precomputedCount)
        coVerify(exactly = 3) { resolve.invoke(any(), any(), any(), any()) }
    }

    @Test
    fun `given a full page of precomputed hits then a follow-up run is requested`() = runTest {
        stubUnionResolve()
        coEvery { cardDao.getScryfallIdsMissingStrategyTags(any()) } returns listOf("card-1", "card-2")
        coEvery { cardDao.getByIds(any()) } returns listOf(entity("card-1", "oracle-1"), entity("card-2", "oracle-2"))
        coEvery { repository.getStrategyTagsBatch(any()) } returns
            mapOf("oracle-1" to found(), "oracle-2" to found())
        coEvery { cardDao.getByOracleIds(any()) } returns
            listOf(entity("card-1", "oracle-1"), entity("card-2", "oracle-2"))

        val result = useCase()(batchSize = 2)

        assertTrue(result.hasMoreWork)
    }

    @Test
    fun `given a full page that produced zero precomputed hits then NO follow-up run is requested`() = runTest {
        stubThresholds()
        coEvery { cardDao.getScryfallIdsMissingStrategyTags(any()) } returns listOf("card-1", "card-2")
        coEvery { cardDao.getByIds(any()) } returns listOf(entity("card-1", "oracle-1"), entity("card-2", "oracle-2"))
        coEvery { repository.getStrategyTagsBatch(any()) } returns mapOf(
            "oracle-1" to CardStrategyTagsResult.NotFound,
            "oracle-2" to CardStrategyTagsResult.Error("offline"),
        )
        coEvery { resolve.invoke(any(), any(), any(), any()) } returns
            ComputeCardTagsUseCase.Result(confirmedTags = emptyList(), suggestedTags = emptyList())

        val result = useCase()(batchSize = 2)

        // A miss writes no cache row, so the same ids would be re-selected forever -- chaining on
        // anything but a provable candidate-list shrink is an unbounded worker loop.
        assertFalse(result.hasMoreWork)
    }

    @Test
    fun `given the batch fetch throws then the run degrades to the on-device path instead of failing`() = runTest {
        stubThresholds()
        coEvery { cardDao.getScryfallIdsMissingStrategyTags(any()) } returns listOf("card-1")
        coEvery { cardDao.getByIds(any()) } returns listOf(entity("card-1", "oracle-1"))
        coEvery { repository.getStrategyTagsBatch(any()) } throws IllegalStateException("offline")
        coEvery { resolve.invoke(any(), any(), any(), any()) } returns
            ComputeCardTagsUseCase.Result(confirmedTags = emptyList(), suggestedTags = emptyList())

        val result = useCase()()

        assertEquals(1, result.onDeviceCount)
        assertFalse(result.hasMoreWork)
    }

    @Test
    fun `given the candidate query throws then the run reports no work rather than propagating`() = runTest {
        coEvery { cardDao.getScryfallIdsMissingStrategyTags(any()) } throws IllegalStateException("db locked")

        val result = useCase()()

        assertEquals(0, result.candidateCount)
        assertFalse(result.hasMoreWork)
    }
}
