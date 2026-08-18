package com.mmg.manahub.feature.draft.data

import android.content.Context
import com.google.gson.Gson
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.dao.DraftSessionDao
import com.mmg.manahub.core.data.remote.CloudflareContentClient
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.model.BasicLandSlot
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DraftCard
import com.mmg.manahub.core.model.DraftConfig
import com.mmg.manahub.core.model.DraftDeck
import com.mmg.manahub.core.model.DraftMode
import com.mmg.manahub.core.model.DraftResult
import com.mmg.manahub.core.model.DraftSeat
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.core.model.DraftState
import com.mmg.manahub.core.model.DraftStatus
import com.mmg.manahub.core.model.PassDirection
import com.mmg.manahub.feature.draft.domain.usecase.GetDraftableSetsUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetCardsPageUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetTierListUseCase
import com.mmg.manahub.feature.draft.engine.DraftTestFixtures
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
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
    private val cardRepository = mockk<CardRepository>(relaxed = true)
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
            cardRepository = cardRepository,
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

    // ── completeAndSaveDeck (Phase D: user-curated mainboard/sideboard/basics) ─────────────────

    @Test
    fun `completeAndSaveDeck writes sideboard slots with isSideboard true`() = runTest {
        val mainboardCard = DraftCard(DraftTestFixtures.fakeCard(1))
        val sideboardCard = DraftCard(DraftTestFixtures.fakeCard(2))
        val seat = DraftSeat(index = 0, isHuman = true, pool = listOf(mainboardCard, sideboardCard))
        val deck = DraftDeck(
            mainboard = listOf(mainboardCard),
            basics = emptyList(),
            sideboard = listOf(sideboardCard),
        )
        coEvery { deckRepository.createDeck(any(), any(), any()) } returns "deck-1"
        val slotsCapture = slot<List<Triple<String, Int, Boolean>>>()
        coEvery { deckRepository.replaceAllCards("deck-1", capture(slotsCapture)) } just Runs

        val result = repository.completeAndSaveDeck(DraftResult(seat, deck))

        assertTrue(result is DataResult.Success)
        val slots = slotsCapture.captured
        assertTrue(Triple("id-1", 1, false) in slots)
        assertTrue(Triple("id-2", 1, true) in slots)
    }

    @Test
    fun `completeAndSaveDeck groups duplicate sideboard copies into one summed slot`() = runTest {
        val dupeCard = DraftCard(DraftTestFixtures.fakeCard(2))
        val seat = DraftSeat(index = 0, isHuman = true, pool = listOf(dupeCard, dupeCard))
        val deck = DraftDeck(mainboard = emptyList(), basics = emptyList(), sideboard = listOf(dupeCard, dupeCard))
        coEvery { deckRepository.createDeck(any(), any(), any()) } returns "deck-2"
        val slotsCapture = slot<List<Triple<String, Int, Boolean>>>()
        coEvery { deckRepository.replaceAllCards("deck-2", capture(slotsCapture)) } just Runs

        repository.completeAndSaveDeck(DraftResult(seat, deck))

        assertEquals(listOf(Triple("id-2", 2, true)), slotsCapture.captured)
    }

    @Test
    fun `completeAndSaveDeck resolves a curated basic land's scryfallId and writes it to the mainboard`() = runTest {
        val seat = DraftSeat(index = 0, isHuman = true, pool = emptyList())
        val deck = DraftDeck(
            mainboard = emptyList(),
            basics = listOf(BasicLandSlot(scryfallId = "", name = "Forest", count = 8)),
        )
        coEvery { deckRepository.createDeck(any(), any(), any()) } returns "deck-3"
        coEvery { cardRepository.searchCardByName("Forest") } returns
            DataResult.Success(DraftTestFixtures.fakeCard(99).copy(scryfallId = "forest-id"))
        val slotsCapture = slot<List<Triple<String, Int, Boolean>>>()
        coEvery { deckRepository.replaceAllCards("deck-3", capture(slotsCapture)) } just Runs

        repository.completeAndSaveDeck(DraftResult(seat, deck))

        assertEquals(listOf(Triple("forest-id", 8, false)), slotsCapture.captured)
    }

    @Test
    fun `cancelSession deletes the persisted session derived from the state's set and mode`() = runTest {
        val state = DraftState(
            config = DraftConfig(setCode = "TST", mode = DraftMode.DRAFT),
            round = 1,
            pickNumber = 1,
            seats = listOf(DraftSeat(index = 0, isHuman = true)),
            packsInFlight = emptyMap(),
            passDirection = PassDirection.LEFT,
            status = DraftStatus.DRAFTING,
        )

        repository.cancelSession(state)

        // deriveSessionId lowercases the set code — mirrors saveSession/markCompleteForSet.
        coVerify { draftSessionDao.deleteById("tst-DRAFT") }
    }
}
