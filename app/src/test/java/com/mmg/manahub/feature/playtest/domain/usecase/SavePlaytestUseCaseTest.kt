package com.mmg.manahub.feature.playtest.domain.usecase

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.HandSnapshot
import com.mmg.manahub.core.model.PlaytestSetup
import com.mmg.manahub.core.domain.repository.PlaytestRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SavePlaytestUseCase].
 *
 * Verifies: configuredDrawCount persistence, per-card count collapsing, bottomed-only
 * cards, one-row-per-scryfallId, librarySize for commander, and delegation contract.
 */
class SavePlaytestUseCaseTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val repository = mockk<PlaytestRepository>()

    private lateinit var useCase: SavePlaytestUseCase

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeCard(id: String) = Card(
        scryfallId = id, name = "Card $id", printedName = null, manaCost = null, cmc = 0.0,
        colors = emptyList(), colorIdentity = emptyList(), typeLine = "Land",
        printedTypeLine = null, oracleText = null, printedText = null, keywords = emptyList(),
        power = null, toughness = null, loyalty = null, setCode = "TST", setName = "Test",
        collectorNumber = "1", rarity = "common", releasedAt = "2024-01-01",
        frameEffects = emptyList(), promoTypes = emptyList(), lang = "en",
        imageNormal = null, imageArtCrop = null, imageBackNormal = null,
        priceUsd = null, priceUsdFoil = null, priceEur = null, priceEurFoil = null,
        legalityStandard = "legal", legalityPioneer = "legal", legalityModern = "legal",
        legalityCommander = "legal", flavorText = null, artist = null,
        scryfallUri = "https://scryfall.com",
    )

    private fun makeSetup(
        drawCount: Int = 7,
        format: String = "standard",
        isOnThePlay: Boolean = true,
    ) = PlaytestSetup(
        deckId       = "deck-uuid",
        deckName     = "Test Deck",
        deckFormat   = format,
        drawCount    = drawCount,
        isOnThePlay  = isOnThePlay,
    )

    private fun makeSnapshot(
        hand: List<Card> = emptyList(),
        mulligansUsed: Int = 0,
        bottomedScryfallIds: List<String> = emptyList(),
        startedAt: Long = 1_000_000L,
    ) = HandSnapshot(
        id                  = 1,
        hand                = hand,
        library             = emptyList(),
        mulligansUsed       = mulligansUsed,
        bottomedScryfallIds = bottomedScryfallIds,
        startedAt           = startedAt,
    )

    @Before
    fun setUp() {
        useCase = SavePlaytestUseCase(repository)
        coEvery {
            repository.saveTest(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns 42L
    }

    // ── Group 1: configuredDrawCount stored as CONFIGURED value ──────────────

    @Test
    fun `given drawCount 7 and mulligansUsed 2 when save then configuredDrawCount is 7 not 5`() = runTest {
        // Even with 2 mulligans (finalHandSize would be 5), drawCount must remain 7.
        val setup    = makeSetup(drawCount = 7)
        val snapshot = makeSnapshot(mulligansUsed = 2)

        val configuredDrawCountSlot = slot<Int>()
        coEvery {
            repository.saveTest(
                deckId              = any(),
                deckFormat          = any(),
                configuredDrawCount = capture(configuredDrawCountSlot),
                mulligansUsed       = any(),
                librarySize         = any(),
                onThePlay           = any(),
                startedAt           = any(),
                cardCountsInHand    = any(),
                cardCountsBottomed  = any(),
            )
        } returns 42L

        useCase(setup, snapshot, librarySize = 53)

        assertEquals(7, configuredDrawCountSlot.captured)
    }

    @Test
    fun `given mulligansUsed 0 when save then mulligansUsed 0 is passed to repository`() = runTest {
        val mulligansSlot = slot<Int>()
        coEvery {
            repository.saveTest(
                deckId = any(), deckFormat = any(), configuredDrawCount = any(),
                mulligansUsed = capture(mulligansSlot), librarySize = any(),
                onThePlay = any(), startedAt = any(), cardCountsInHand = any(), cardCountsBottomed = any(),
            )
        } returns 42L

        useCase(makeSetup(), makeSnapshot(mulligansUsed = 0), librarySize = 53)

        assertEquals(0, mulligansSlot.captured)
    }

    // ── Group 2: per-card count collapsing ────────────────────────────────────

    @Test
    fun `given hand with 3 copies of same card when save then one row with count 3`() = runTest {
        val boltCard = makeCard("bolt")
        val snapshot = makeSnapshot(
            hand = listOf(boltCard, boltCard, boltCard),
        )
        val handCountsSlot = slot<Map<String, Int>>()
        coEvery {
            repository.saveTest(
                deckId = any(), deckFormat = any(), configuredDrawCount = any(),
                mulligansUsed = any(), librarySize = any(), onThePlay = any(), startedAt = any(),
                cardCountsInHand = capture(handCountsSlot), cardCountsBottomed = any(),
            )
        } returns 42L

        useCase(makeSetup(), snapshot, librarySize = 53)

        val counts = handCountsSlot.captured
        assertEquals(1, counts.size)           // only one entry — one scryfallId
        assertEquals(3, counts["bolt"])         // 3 copies collapsed
    }

    @Test
    fun `given hand with multiple distinct cards when save then each has its own count`() = runTest {
        val hand = listOf(makeCard("bolt"), makeCard("bolt"), makeCard("birds"), makeCard("land"))
        val snapshot = makeSnapshot(hand = hand)
        val handCountsSlot = slot<Map<String, Int>>()
        coEvery {
            repository.saveTest(
                deckId = any(), deckFormat = any(), configuredDrawCount = any(),
                mulligansUsed = any(), librarySize = any(), onThePlay = any(), startedAt = any(),
                cardCountsInHand = capture(handCountsSlot), cardCountsBottomed = any(),
            )
        } returns 42L

        useCase(makeSetup(), snapshot, librarySize = 53)

        val counts = handCountsSlot.captured
        assertEquals(2, counts["bolt"])
        assertEquals(1, counts["birds"])
        assertEquals(1, counts["land"])
    }

    // ── Group 3: bottomed-only cards ──────────────────────────────────────────

    @Test
    fun `given card bottomed but not in hand when save then copiesInHand is 0 and copiesBottomed is 1`() = runTest {
        // The card was only bottomed, never in the final kept hand.
        val snapshot = makeSnapshot(
            hand                = listOf(makeCard("birds")),
            bottomedScryfallIds = listOf("bolt"),  // bolt was bottomed, not in final hand
        )
        val handCountsSlot    = slot<Map<String, Int>>()
        val bottomedCountsSlot = slot<Map<String, Int>>()
        coEvery {
            repository.saveTest(
                deckId = any(), deckFormat = any(), configuredDrawCount = any(),
                mulligansUsed = any(), librarySize = any(), onThePlay = any(), startedAt = any(),
                cardCountsInHand = capture(handCountsSlot),
                cardCountsBottomed = capture(bottomedCountsSlot),
            )
        } returns 42L

        useCase(makeSetup(), snapshot, librarySize = 53)

        val handCounts    = handCountsSlot.captured
        val bottomedCounts = bottomedCountsSlot.captured

        // bolt was never in the final hand
        assertNull(handCounts["bolt"])
        // bolt was bottomed once
        assertEquals(1, bottomedCounts["bolt"])
    }

    @Test
    fun `given card bottomed twice when save then copiesBottomed is 2`() = runTest {
        // Two mulligans, each bottomed the same card.
        val snapshot = makeSnapshot(
            hand                = emptyList(),
            bottomedScryfallIds = listOf("bolt", "bolt"),
        )
        val bottomedCountsSlot = slot<Map<String, Int>>()
        coEvery {
            repository.saveTest(
                deckId = any(), deckFormat = any(), configuredDrawCount = any(),
                mulligansUsed = any(), librarySize = any(), onThePlay = any(), startedAt = any(),
                cardCountsInHand = any(), cardCountsBottomed = capture(bottomedCountsSlot),
            )
        } returns 42L

        useCase(makeSetup(), snapshot, librarySize = 53)

        assertEquals(2, bottomedCountsSlot.captured["bolt"])
    }

    // ── Group 4: librarySize for commander ────────────────────────────────────

    @Test
    fun `given commander format with librarySize 99 when save then repository receives 99`() = runTest {
        val librarySizeSlot = slot<Int>()
        coEvery {
            repository.saveTest(
                deckId = any(), deckFormat = any(), configuredDrawCount = any(),
                mulligansUsed = any(), librarySize = capture(librarySizeSlot),
                onThePlay = any(), startedAt = any(), cardCountsInHand = any(), cardCountsBottomed = any(),
            )
        } returns 42L

        useCase(makeSetup(format = "commander"), makeSnapshot(), librarySize = 99)

        assertEquals(99, librarySizeSlot.captured)
    }

    // ── Group 5: return value and startedAt forwarding ────────────────────────

    @Test
    fun `given repository returns session id when save then use case returns same id`() = runTest {
        coEvery {
            repository.saveTest(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns 999L

        val result = useCase(makeSetup(), makeSnapshot(), librarySize = 53)

        assertEquals(999L, result)
    }

    @Test
    fun `given snapshot startedAt when save then startedAt is forwarded to repository`() = runTest {
        val startedAtSlot = slot<Long>()
        coEvery {
            repository.saveTest(
                deckId = any(), deckFormat = any(), configuredDrawCount = any(),
                mulligansUsed = any(), librarySize = any(), onThePlay = any(),
                startedAt = capture(startedAtSlot),
                cardCountsInHand = any(), cardCountsBottomed = any(),
            )
        } returns 1L

        val expectedStartedAt = 1_234_567_890L
        useCase(makeSetup(), makeSnapshot(startedAt = expectedStartedAt), librarySize = 53)

        assertEquals(expectedStartedAt, startedAtSlot.captured)
    }

    @Test
    fun `given isOnThePlay true when save then onThePlay true is forwarded to repository`() = runTest {
        val onThePlaySlot = slot<Boolean>()
        coEvery {
            repository.saveTest(
                deckId = any(), deckFormat = any(), configuredDrawCount = any(),
                mulligansUsed = any(), librarySize = any(), onThePlay = capture(onThePlaySlot),
                startedAt = any(), cardCountsInHand = any(), cardCountsBottomed = any(),
            )
        } returns 1L

        useCase(makeSetup(isOnThePlay = true), makeSnapshot(), librarySize = 53)

        assertEquals(true, onThePlaySlot.captured)
    }

    // ── Group 6: empty hand ────────────────────────────────────────────────────

    @Test
    fun `given empty hand and no bottomed cards when save then empty maps are forwarded`() = runTest {
        val handCountsSlot    = slot<Map<String, Int>>()
        val bottomedCountsSlot = slot<Map<String, Int>>()
        coEvery {
            repository.saveTest(
                deckId = any(), deckFormat = any(), configuredDrawCount = any(),
                mulligansUsed = any(), librarySize = any(), onThePlay = any(), startedAt = any(),
                cardCountsInHand = capture(handCountsSlot),
                cardCountsBottomed = capture(bottomedCountsSlot),
            )
        } returns 1L

        useCase(makeSetup(), makeSnapshot(hand = emptyList(), bottomedScryfallIds = emptyList()), librarySize = 0)

        assertEquals(emptyMap<String, Int>(), handCountsSlot.captured)
        assertEquals(emptyMap<String, Int>(), bottomedCountsSlot.captured)
    }

    @Test
    fun `given same card in hand and bottomed when save then both maps contain that scryfallId`() = runTest {
        // A card can appear in both the kept hand AND the bottomed list
        // (e.g. 2 copies in hand, 1 was bottomed in a previous mulligan step).
        val boltCard = makeCard("bolt")
        val snapshot = makeSnapshot(
            hand                = listOf(boltCard, boltCard),
            bottomedScryfallIds = listOf("bolt"),
        )
        val handCountsSlot    = slot<Map<String, Int>>()
        val bottomedCountsSlot = slot<Map<String, Int>>()
        coEvery {
            repository.saveTest(
                deckId = any(), deckFormat = any(), configuredDrawCount = any(),
                mulligansUsed = any(), librarySize = any(), onThePlay = any(), startedAt = any(),
                cardCountsInHand = capture(handCountsSlot),
                cardCountsBottomed = capture(bottomedCountsSlot),
            )
        } returns 1L

        useCase(makeSetup(), snapshot, librarySize = 53)

        assertEquals(2, handCountsSlot.captured["bolt"])
        assertEquals(1, bottomedCountsSlot.captured["bolt"])
    }

    @Test
    fun `verify repository saveTest is called exactly once per invocation`() = runTest {
        useCase(makeSetup(), makeSnapshot(), librarySize = 53)

        coVerify(exactly = 1) {
            repository.saveTest(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }
}
