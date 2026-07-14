package com.mmg.manahub.feature.draft.data

import android.content.Context
import com.google.gson.Gson
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.dao.DraftSessionDao
import com.mmg.manahub.core.data.remote.CloudflareContentClient
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.feature.draft.domain.usecase.GetDraftableSetsUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetCardsPageUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetTierListUseCase
import com.mmg.manahub.feature.draft.engine.DraftTestFixtures
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DraftSimRepositoryImpl.getDraftableSimSet], focused on parsing
 * booster.json's optional `extraPoolSets` field and threading it into the pool fetch — this is
 * what lets SOS's Mystical Archive sheet (drawn from Scryfall set `soa`) resolve on-device
 * instead of silently shrinking packs from 14 to 13 cards.
 */
class DraftSimRepositoryImplTest {

    private val context = mockk<Context>(relaxed = true)
    private val cloudflareClient = mockk<CloudflareContentClient>()
    private val getDraftableSets = mockk<GetDraftableSetsUseCase>()
    private val getSetTierList = mockk<GetSetTierListUseCase>()
    private val getSetCardsPage = mockk<GetSetCardsPageUseCase>()
    private val deckRepository = mockk<DeckRepository>(relaxed = true)
    private val draftSessionDao = mockk<DraftSessionDao>(relaxed = true)
    private val crashReporter = mockk<CrashReporter>(relaxed = true)
    private val gson = Gson()

    private lateinit var repository: DraftSimRepositoryImpl

    private val draftSet = DraftSet(
        id = "sos",
        code = "sos",
        name = "Secrets of Strixhaven",
        releasedAt = "2026-01-01",
        iconSvgUri = "",
        guideVersion = "v1",
        tierListVersion = "v1",
        boosterVersion = "v1",
    )

    @Before
    fun setUp() {
        repository = DraftSimRepositoryImpl(
            context = context,
            cloudflareClient = cloudflareClient,
            getDraftableSets = getDraftableSets,
            getSetTierList = getSetTierList,
            getSetCardsPage = getSetCardsPage,
            deckRepository = deckRepository,
            draftSessionDao = draftSessionDao,
            gson = gson,
            ioDispatcher = UnconfinedTestDispatcher(),
            crashReporter = crashReporter,
        )

        coEvery { getDraftableSets(forceRefresh = true) } returns DataResult.Success(listOf(draftSet))
        coEvery { getSetTierList("sos") } returns DataResult.Error("no tier list")
    }

    @Test
    fun `booster json with extraPoolSets sanitizes and forwards only valid codes`() = runTest {
        coEvery { cloudflareClient.getSetBooster("sos") } returns """
            {
              "setCode": "sos",
              "schemaVersion": 1,
              "extraPoolSets": ["soa", "SOA", "sos or name:x", "a", "toolongcode"],
              "boosters": [],
              "sheets": {}
            }
        """.trimIndent()

        val extraSetsSlot = slot<List<String>>()
        coEvery {
            getSetCardsPage("sos", 1, capture(extraSetsSlot))
        } returns DataResult.Success(listOf(DraftTestFixtures.fakeCard(1)) to false)

        val result = repository.getDraftableSimSet("sos")

        assertTrue(result is DataResult.Success)
        // "SOA" normalizes to "soa" (duplicate, dropped by distinct()); the malformed entries
        // (spaces/colon, too short, too long) are dropped entirely.
        assertEquals(listOf("soa"), extraSetsSlot.captured)
    }

    @Test
    fun `booster json without extraPoolSets forwards an empty list (zero behavior change)`() = runTest {
        coEvery { cloudflareClient.getSetBooster("sos") } returns """
            {
              "setCode": "sos",
              "schemaVersion": 1,
              "boosters": [],
              "sheets": {}
            }
        """.trimIndent()

        val extraSetsSlot = slot<List<String>>()
        coEvery {
            getSetCardsPage("sos", 1, capture(extraSetsSlot))
        } returns DataResult.Success(listOf(DraftTestFixtures.fakeCard(1)) to false)

        val result = repository.getDraftableSimSet("sos")

        assertTrue(result is DataResult.Success)
        assertEquals(emptyList<String>(), extraSetsSlot.captured)
    }
}
