package com.mmg.manahub.feature.draft.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.mmg.manahub.core.data.local.dao.DraftSetDao
import com.mmg.manahub.core.data.local.entity.DraftSetEntity
import com.mmg.manahub.core.data.network.ScryfallRequestQueue
import com.mmg.manahub.core.data.remote.CloudflareContentClient
import com.mmg.manahub.core.data.remote.ScryfallClient
import com.mmg.manahub.core.data.remote.dto.ContentVersionsDto
import com.mmg.manahub.core.data.remote.dto.SearchResultDto
import com.mmg.manahub.core.data.remote.dto.SetIndexEntryDto
import com.mmg.manahub.core.data.remote.dto.SetsIndexResponse
import com.mmg.manahub.core.model.DataResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class DraftRepositoryImplTest {

    private val context = mockk<Context>(relaxed = true)
    private val scryfallApi = mockk<ScryfallClient>()
    private val scryfallQueue = ScryfallRequestQueue()
    private val cloudflareClient = mockk<CloudflareContentClient>()
    private val draftSetDao = mockk<DraftSetDao>()
    private val gson = Gson()
    private val draftPrefs = mockk<SharedPreferences>()
    private val preferences = mutableMapOf<String, String>()
    private val manifest = mutableListOf<DraftSetEntity>()
    private var now = 1_000_000L

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: DraftRepositoryImpl

    private val emptyResult = SearchResultDto(totalCards = 0, hasMore = false, nextPage = null, data = emptyList())

    @Before
    fun setUp() {
        every { context.filesDir } returns tempFolder.root
        every { draftPrefs.getString(any(), any()) } answers { preferences[firstArg()] }
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { draftPrefs.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            preferences[firstArg()] = secondArg()
            editor
        }
        every { editor.remove(any()) } answers {
            preferences.remove(firstArg())
            editor
        }
        coEvery { draftSetDao.getLastCachedTime() } answers { manifest.maxOfOrNull { it.cachedAt } }
        coEvery { draftSetDao.getAllSetsSnapshot() } answers { manifest.sortedByDescending { it.releasedAt } }
        coEvery { draftSetDao.getSetByCode(any()) } answers {
            val code = firstArg<String>()
            manifest.firstOrNull { it.code == code }
        }
        coEvery { draftSetDao.replaceAll(any()) } coAnswers {
            manifest.clear()
            manifest.addAll(firstArg())
        }
        repository = createRepository()
    }

    private fun createRepository(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined,
    ) = DraftRepositoryImpl(
            context = context,
            scryfallApi = scryfallApi,
            scryfallQueue = scryfallQueue,
            cloudflareClient = cloudflareClient,
            draftSetDao = draftSetDao,
            gson = gson,
            draftPrefs = draftPrefs,
            ioDispatcher = dispatcher,
            nowMillis = { now },
        )

    @Test
    fun `getSetCardsPage with no extraPoolSets uses the exact unchanged query`() = runTest {
        val querySlot = slot<String>()
        coEvery {
            scryfallApi.searchCards(
                query = capture(querySlot),
                order = any(),
                dir = any(),
                unique = any(),
                page = any(),
            )
        } returns emptyResult

        val result = repository.getSetCardsPage("tdm", 1, emptyList())

        assertTrue(result is DataResult.Success)
        assertEquals("set:tdm lang:en", querySlot.captured)
    }

    @Test
    fun `getSetCardsPage with one extraPoolSets entry widens the query with a parenthesized OR`() = runTest {
        val querySlot = slot<String>()
        coEvery {
            scryfallApi.searchCards(
                query = capture(querySlot),
                order = any(),
                dir = any(),
                unique = any(),
                page = any(),
            )
        } returns emptyResult

        val result = repository.getSetCardsPage("sos", 1, listOf("soa"))

        assertTrue(result is DataResult.Success)
        assertEquals("(set:sos or set:soa) lang:en", querySlot.captured)
    }

    @Test
    fun `getSetCardsPage drops malformed extraPoolSets entries but keeps valid ones`() = runTest {
        val querySlot = slot<String>()
        coEvery {
            scryfallApi.searchCards(
                query = capture(querySlot),
                order = any(),
                dir = any(),
                unique = any(),
                page = any(),
            )
        } returns emptyResult

        val result = repository.getSetCardsPage(
            "sos",
            1,
            listOf("SOA", "sos or name:x", "a", "toolongcode"),
        )

        assertTrue(result is DataResult.Success)
        assertEquals("(set:sos or set:soa) lang:en", querySlot.captured)
    }

    @Test
    fun `fresh manifest serves cached sets without network`() = runTest {
        manifest += draftSet(version = "v1", cachedAt = now - 299_999L)

        val result = repository.getDraftableSets(forceRefresh = false) as DataResult.Success

        assertEquals(listOf("tdm"), result.data.map { it.code })
        assertFalse(result.isStale)
        coVerify(exactly = 0) { cloudflareClient.getSetsIndex() }
    }

    @Test
    fun `expired manifest replaces Room and exposes newly published sets`() = runTest {
        manifest += draftSet(version = "v1", cachedAt = now - 300_000L)
        coEvery { cloudflareClient.getSetsIndex() } returns setsIndex(
            setEntry("tdm", "v1"),
            setEntry("eoe", "v1"),
        )

        val result = repository.getDraftableSets(forceRefresh = false) as DataResult.Success

        assertEquals(setOf("tdm", "eoe"), result.data.map { it.code }.toSet())
        assertTrue(manifest.all { it.cachedAt == now })
        coVerify(exactly = 1) { cloudflareClient.getSetsIndex() }
    }

    @Test
    fun `expired v1 manifest downloads versioned v2 guide and tier then replaces caches`() = runTest {
        installLocalArtifacts(version = "v1")
        manifest += draftSet(version = "v1", cachedAt = now - 300_000L)
        coEvery { cloudflareClient.getSetsIndex() } returns setsIndex(setEntry("tdm", "v2"))
        coEvery { cloudflareClient.getSetGuide("tdm", "v2") } returns guideJson("New guide")
        coEvery { cloudflareClient.getSetTierList("tdm", "v2") } returns tierJson("New tier")

        val guide = repository.getSetGuide("tdm") as DataResult.Success
        val tier = repository.getSetTierList("tdm") as DataResult.Success

        assertEquals("New guide", guide.data.summary)
        assertEquals("New tier", tier.data.tiers.single().description)
        assertEquals("v2", preferences[GUIDE_PREFERENCE_KEY])
        assertEquals("v2", preferences[TIER_PREFERENCE_KEY])
        assertTrue(guideFile().readText().contains("New guide"))
        assertTrue(tierFile().readText().contains("New tier"))
        coVerify(exactly = 1) { cloudflareClient.getSetsIndex() }
        coVerify(exactly = 1) { cloudflareClient.getSetGuide("tdm", "v2") }
        coVerify(exactly = 1) { cloudflareClient.getSetTierList("tdm", "v2") }
    }

    @Test
    fun `same artifact versions refresh only the expired manifest`() = runTest {
        installLocalArtifacts(version = "v1")
        manifest += draftSet(version = "v1", cachedAt = now - 300_000L)
        coEvery { cloudflareClient.getSetsIndex() } returns setsIndex(setEntry("tdm", "v1"))

        val result = repository.getSetGuide("tdm") as DataResult.Success

        assertEquals("Old guide", result.data.summary)
        coVerify(exactly = 1) { cloudflareClient.getSetsIndex() }
        coVerify(exactly = 0) { cloudflareClient.getSetGuide(any(), any()) }
    }

    @Test
    fun `concurrent guide and tier loads coalesce an expired manifest fetch`() = runTest {
        repository = createRepository(StandardTestDispatcher(testScheduler))
        installLocalArtifacts(version = "v1")
        manifest += draftSet(version = "v1", cachedAt = now - 300_000L)
        val releaseManifest = CompletableDeferred<Unit>()
        coEvery { cloudflareClient.getSetsIndex() } coAnswers {
            releaseManifest.await()
            setsIndex(setEntry("tdm", "v1"))
        }

        val guide = async { repository.getSetGuide("tdm") }
        val tier = async { repository.getSetTierList("tdm") }
        runCurrent()
        releaseManifest.complete(Unit)

        assertTrue(guide.await() is DataResult.Success)
        assertTrue(tier.await() is DataResult.Success)
        coVerify(exactly = 1) { cloudflareClient.getSetsIndex() }
    }

    @Test
    fun `concurrent forced set refreshes coalesce when cache timestamps match`() = runTest {
        repository = createRepository(StandardTestDispatcher(testScheduler))
        manifest += draftSet(version = "v1", cachedAt = now)
        val releaseManifest = CompletableDeferred<Unit>()
        coEvery { cloudflareClient.getSetsIndex() } coAnswers {
            releaseManifest.await()
            setsIndex(setEntry("tdm", "v1"))
        }

        val first = async { repository.getDraftableSets(forceRefresh = true) }
        val second = async { repository.getDraftableSets(forceRefresh = true) }
        runCurrent()
        releaseManifest.complete(Unit)

        assertTrue(first.await() is DataResult.Success)
        assertTrue(second.await() is DataResult.Success)
        coVerify(exactly = 1) { cloudflareClient.getSetsIndex() }
    }

    @Test
    fun `manifest failure serves local guide and tier as stale without artifact requests`() = runTest {
        installLocalArtifacts(version = "v1")
        manifest += draftSet(version = "v1", cachedAt = now - 300_000L)
        coEvery { cloudflareClient.getSetsIndex() } throws IOException("offline")

        val guide = repository.getSetGuide("tdm") as DataResult.Success
        val tier = repository.getSetTierList("tdm") as DataResult.Success

        assertTrue(guide.isStale)
        assertTrue(tier.isStale)
        assertEquals("Old guide", guide.data.summary)
        assertEquals("Old tier", tier.data.tiers.single().description)
        coVerify(exactly = 1) { cloudflareClient.getSetsIndex() }
        coVerify(exactly = 0) { cloudflareClient.getSetGuide(any(), any()) }
        coVerify(exactly = 0) { cloudflareClient.getSetTierList(any(), any()) }
    }

    @Test
    fun `missing set clears unpublished local guide without requesting the artifact`() = runTest {
        installLocalArtifacts(version = "v1")
        File(guideFile().parentFile, "guide.json.tmp").writeText("partial")
        File(guideFile().parentFile, "guide.json.bak").writeText(guideJson("Backup guide"))
        manifest += draftSet(version = "v1", cachedAt = now).copy(id = "eoe", code = "eoe")

        val result = repository.getSetGuide("tdm")

        assertTrue(result is DataResult.Error)
        assertFalse(guideFile().exists())
        assertFalse(File(guideFile().parentFile, "guide.json.tmp").exists())
        assertFalse(File(guideFile().parentFile, "guide.json.bak").exists())
        assertFalse(preferences.containsKey(GUIDE_PREFERENCE_KEY))
        coVerify(exactly = 0) { cloudflareClient.getSetGuide(any(), any()) }
    }

    @Test
    fun `valid backup is restored and served stale while offline`() = runTest {
        guideFile().parentFile?.mkdirs()
        File(guideFile().parentFile, "guide.json.bak").writeText(guideJson("Recovered guide"))
        preferences[GUIDE_PREFERENCE_KEY] = "v1"
        manifest += draftSet(version = "v1", cachedAt = now - 300_000L)
        coEvery { cloudflareClient.getSetsIndex() } throws IOException("offline")

        val result = repository.getSetGuide("tdm") as DataResult.Success

        assertTrue(result.isStale)
        assertEquals("Recovered guide", result.data.summary)
        assertTrue(guideFile().exists())
        assertTrue(guideFile().readText().contains("Recovered guide"))
        assertFalse(File(guideFile().parentFile, "guide.json.bak").exists())
        coVerify(exactly = 0) { cloudflareClient.getSetGuide(any(), any()) }
    }

    @Test
    fun `malformed v2 guide and tier preserve v1 files preferences and models`() = runTest {
        installLocalArtifacts(version = "v1")
        manifest += draftSet(version = "v1", cachedAt = now - 300_000L)
        coEvery { cloudflareClient.getSetsIndex() } returns setsIndex(setEntry("tdm", "v2"))
        coEvery { cloudflareClient.getSetGuide("tdm", "v2") } returns "{malformed"
        coEvery { cloudflareClient.getSetTierList("tdm", "v2") } returns "{}"
        val oldGuideJson = guideFile().readText()
        val oldTierJson = tierFile().readText()

        val guide = repository.getSetGuide("tdm") as DataResult.Success
        val tier = repository.getSetTierList("tdm") as DataResult.Success
        val cooledDownGuide = repository.getSetGuide("tdm") as DataResult.Success
        val cooledDownTier = repository.getSetTierList("tdm") as DataResult.Success

        assertTrue(guide.isStale)
        assertTrue(tier.isStale)
        assertTrue(cooledDownGuide.isStale)
        assertTrue(cooledDownTier.isStale)
        assertEquals("Old guide", guide.data.summary)
        assertEquals("Old tier", tier.data.tiers.single().description)
        assertEquals(oldGuideJson, guideFile().readText())
        assertEquals(oldTierJson, tierFile().readText())
        assertEquals("v1", preferences[GUIDE_PREFERENCE_KEY])
        assertEquals("v1", preferences[TIER_PREFERENCE_KEY])
        coVerify(exactly = 1) { cloudflareClient.getSetGuide("tdm", "v2") }
        coVerify(exactly = 1) { cloudflareClient.getSetTierList("tdm", "v2") }
    }

    @Test
    fun `set lookup failure serves last known good guide as stale`() = runTest {
        installLocalArtifacts(version = "v1")
        manifest += draftSet(version = "v1", cachedAt = now)
        coEvery { draftSetDao.getSetByCode("tdm") } throws IOException("room unavailable")

        val result = repository.getSetGuide("tdm") as DataResult.Success

        assertTrue(result.isStale)
        assertEquals("Old guide", result.data.summary)
        coVerify(exactly = 0) { cloudflareClient.getSetGuide(any(), any()) }
    }

    @Test
    fun `replacement setup failure preserves the last valid guide file`() = runTest {
        installLocalArtifacts(version = "v1")
        manifest += draftSet(version = "v2", cachedAt = now)
        File(guideFile().parentFile, "guide.json.bak/blocker").apply {
            parentFile?.mkdirs()
            writeText("block replacement")
        }
        coEvery { cloudflareClient.getSetGuide("tdm", "v2") } returns guideJson("New guide")
        val oldGuideJson = guideFile().readText()

        val result = repository.getSetGuide("tdm") as DataResult.Success

        assertTrue(result.isStale)
        assertEquals("Old guide", result.data.summary)
        assertEquals(oldGuideJson, guideFile().readText())
        assertEquals("v1", preferences[GUIDE_PREFERENCE_KEY])
    }

    @Test
    fun `getSetGuide serves the parsed model from memory while the version is unchanged`() = runTest {
        installLocalArtifacts(version = "v1")
        manifest += draftSet(version = "v1", cachedAt = now)

        val first = (repository.getSetGuide("tdm") as DataResult.Success).data
        guideFile().writeText("not json")
        val second = (repository.getSetGuide("tdm") as DataResult.Success).data

        assertTrue(first === second)
        coVerify(exactly = 0) { cloudflareClient.getSetGuide(any(), any()) }
    }

    private fun installLocalArtifacts(version: String) {
        guideFile().parentFile?.mkdirs()
        guideFile().writeText(guideJson("Old guide"))
        tierFile().writeText(tierJson("Old tier"))
        preferences[GUIDE_PREFERENCE_KEY] = version
        preferences[TIER_PREFERENCE_KEY] = version
    }

    private fun guideFile(): File = File(tempFolder.root, "draft/tdm/guide.json")

    private fun tierFile(): File = File(tempFolder.root, "draft/tdm/tier-list.json")

    private fun guideJson(summary: String): String =
        """{"metadata":{"set_name":"Test"},"set_overview":{"summary":"$summary"}}"""

    private fun tierJson(description: String): String =
        """{"metadata":{"set_name":"Test"},"categories":[{"tier_label":"A","description":"$description","cards":[]}]}"""

    private fun setsIndex(vararg entries: SetIndexEntryDto) = SetsIndexResponse(
        indexVersion = "2",
        lastUpdated = "2026-09-23",
        sets = entries.toList(),
    )

    private fun setEntry(code: String, version: String) = SetIndexEntryDto(
        code = code,
        name = code.uppercase(),
        iconSvgUri = "",
        releasedAt = "2026-01-01",
        contentVersions = ContentVersionsDto(
            guide = version,
            tierList = version,
        ),
    )

    private fun draftSet(version: String, cachedAt: Long) = DraftSetEntity(
        id = "tdm",
        code = "tdm",
        name = "Test",
        releasedAt = "2026-01-01",
        iconSvgUri = "",
        guideVersion = version,
        tierListVersion = version,
        cachedAt = cachedAt,
    )

    private companion object {
        const val GUIDE_PREFERENCE_KEY = "pref_draft_tdm_guide_version"
        const val TIER_PREFERENCE_KEY = "pref_draft_tdm_tier_version"
    }
}
