package com.mmg.manahub.feature.draft.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.mmg.manahub.core.data.local.dao.DraftSetDao
import com.mmg.manahub.core.data.network.ScryfallRequestQueue
import com.mmg.manahub.core.data.remote.CloudflareContentClient
import com.mmg.manahub.core.data.remote.ScryfallClient
import com.mmg.manahub.core.data.remote.YouTubeClient
import com.mmg.manahub.core.data.remote.dto.SearchResultDto
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.data.local.entity.DraftSetEntity
import com.mmg.manahub.core.model.SetDraftGuide
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [DraftRepositoryImpl.getSetCardsPage], focused on the Scryfall pool query it
 * builds — in particular the `extraPoolSets` widening added for sets whose booster.json declares
 * cards from an additional Scryfall set (e.g. SOS's Mystical Archive sheet draws from `soa`).
 */
class DraftRepositoryImplTest {

    private val context = mockk<Context>(relaxed = true)
    private val scryfallApi = mockk<ScryfallClient>()
    private val scryfallQueue = ScryfallRequestQueue()
    private val youTubeClient = mockk<YouTubeClient>(relaxed = true)
    private val cloudflareClient = mockk<CloudflareContentClient>(relaxed = true)
    private val draftSetDao = mockk<DraftSetDao>(relaxed = true)
    private val gson = Gson()
    private val draftPrefs = mockk<SharedPreferences>(relaxed = true)

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: DraftRepositoryImpl

    private val emptyResult = SearchResultDto(totalCards = 0, hasMore = false, nextPage = null, data = emptyList())

    @Before
    fun setUp() {
        repository = DraftRepositoryImpl(
            context = context,
            scryfallApi = scryfallApi,
            scryfallQueue = scryfallQueue,
            youTubeClient = youTubeClient,
            cloudflareClient = cloudflareClient,
            draftSetDao = draftSetDao,
            gson = gson,
            draftPrefs = draftPrefs,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

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

        // "SOA" normalizes to "soa" (lowercase); "sos or name:x" (spaces/colon), "a" (too short),
        // and an 8-char code all fail the allowlist and must be dropped.
        val result = repository.getSetCardsPage(
            "sos",
            1,
            listOf("SOA", "sos or name:x", "a", "toolongcode"),
        )

        assertTrue(result is DataResult.Success)
        assertEquals("(set:sos or set:soa) lang:en", querySlot.captured)
    }

    @Test
    fun `getSetGuide serves the parsed model from memory while the version is unchanged`() = runTest {
        stubGuideSource(version = "v1")

        val first = (repository.getSetGuide("tdm") as DataResult.Success).data
        File(tempFolder.root, "draft/tdm/guide.json").writeText("not json")
        val second = (repository.getSetGuide("tdm") as DataResult.Success).data

        assertSame(first, second)
        coVerify(exactly = 1) { cloudflareClient.getSetGuide("tdm") }
    }

    @Test
    fun `getSetGuide re-downloads and re-parses when the published version changes`() = runTest {
        stubGuideSource(version = "v1")
        val first = (repository.getSetGuide("tdm") as DataResult.Success).data

        coEvery { draftSetDao.getSetByCode("tdm") } returns draftSet(version = "v2")
        val second = (repository.getSetGuide("tdm") as DataResult.Success<SetDraftGuide>).data

        assertNotSame(first, second)
        coVerify(exactly = 2) { cloudflareClient.getSetGuide("tdm") }
    }

    private fun stubGuideSource(version: String) {
        every { context.filesDir } returns tempFolder.root
        val storedVersion = mutableMapOf<String, String>()
        every { draftPrefs.getString(any(), any()) } answers { storedVersion[firstArg()] }
        every { draftPrefs.edit() } returns mockk(relaxed = true) {
            every { putString(any(), any()) } answers {
                storedVersion[firstArg()] = secondArg()
                self as SharedPreferences.Editor
            }
        }
        coEvery { draftSetDao.getSetByCode("tdm") } returns draftSet(version)
        coEvery { cloudflareClient.getSetGuide("tdm") } returns
            """{"metadata":{"set_name":"Test"},"set_overview":{"summary":"Fast format"}}"""
    }

    private fun draftSet(version: String) = DraftSetEntity(
        id = "tdm",
        code = "tdm",
        name = "Test",
        releasedAt = "",
        iconSvgUri = "",
        guideVersion = version,
        tierListVersion = version,
    )
}
