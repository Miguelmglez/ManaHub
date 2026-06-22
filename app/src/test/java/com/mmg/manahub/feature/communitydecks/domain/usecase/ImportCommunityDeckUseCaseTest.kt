package com.mmg.manahub.feature.communitydecks.domain.usecase

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckWithCards
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.model.CommunityDeck
import com.mmg.manahub.core.model.CommunityDeckCard
import com.mmg.manahub.core.model.CommunityDeckOwner
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ImportCommunityDeckUseCase].
 *
 * Verifies resilient card-by-card import: partial failures do not abort the
 * whole import, progress callbacks fire correctly, commander detection works,
 * sideboard categorization is respected, and attribution is stamped.
 */
class ImportCommunityDeckUseCaseTest {

    private val deckRepository: DeckRepository = mockk(relaxUnitFun = true)
    private val cardRepository: CardRepository = mockk()

    private lateinit var useCase: ImportCommunityDeckUseCase

    private val testDeckId = "deck-uuid-001"

    // ── Fixtures ────────────────────────────────────────────────────────────

    private val testOwner = CommunityDeckOwner(id = 1, username = "Author", avatarUrl = "")

    private fun buildCard(
        name: String,
        quantity: Int = 1,
        categories: List<String> = emptyList(),
        oracleId: String = "oracle-$name",
    ) = CommunityDeckCard(
        name = name,
        quantity = quantity,
        categories = categories,
        oracleId = oracleId,
    )

    private fun buildCommunityDeck(
        cards: List<CommunityDeckCard> = listOf(buildCard("Sol Ring"), buildCard("Command Tower")),
        format: String = "commander",
    ) = CommunityDeck(
        archidektId = 12345,
        name = "Test Deck",
        description = "A test deck",
        format = format,
        owner = testOwner,
        viewCount = 42,
        createdAt = "2026-01-01",
        updatedAt = "2026-06-01",
        cards = cards,
        sourceUrl = "https://archidekt.com/decks/12345",
    )

    private fun buildResolvedCard(scryfallId: String, name: String = "Card") = Card(
        scryfallId = scryfallId,
        name = name,
        printedName = null,
        manaCost = null,
        cmc = 0.0,
        colors = emptyList(),
        colorIdentity = emptyList(),
        typeLine = "Artifact",
        printedTypeLine = null,
        oracleText = null,
        printedText = null,
        keywords = emptyList(),
        power = null,
        toughness = null,
        loyalty = null,
        setCode = "test",
        setName = "Test Set",
        collectorNumber = "1",
        rarity = "common",
        releasedAt = "2026-01-01",
        frameEffects = emptyList(),
        promoTypes = emptyList(),
        lang = "en",
        imageNormal = null,
        imageArtCrop = null,
        imageBackNormal = null,
        priceUsd = null,
        priceUsdFoil = null,
        priceEur = null,
        priceEurFoil = null,
        legalityStandard = "legal",
        legalityPioneer = "legal",
        legalityModern = "legal",
        legalityCommander = "legal",
        flavorText = null,
        artist = null,
        scryfallUri = "",
    )

    private fun buildDeck(id: String = testDeckId) = Deck(
        id = id,
        name = "Test Deck",
        format = "commander",
    )

    @Before
    fun setUp() {
        // The use case logs telemetry via FirebaseCrashlytics (unresolved-card breadcrumbs,
        // high-failure-rate / import-failed non-fatals) outside any runCatching, which throws
        // "Default FirebaseApp is not initialized" without a static mock.
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        coEvery { deckRepository.createDeck(any(), any(), any()) } returns testDeckId

        useCase = ImportCommunityDeckUseCase(
            deckRepository = deckRepository,
            cardRepository = cardRepository,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ── Group 1: Happy path — all cards resolve ─────────────────────────────

    @Test
    fun `given all cards resolve when invoke then returns Success with correct counts`() = runTest {
        // Arrange
        val deck = buildCommunityDeck()
        coEvery { cardRepository.searchCardByName("Sol Ring") } returns
            DataResult.Success(buildResolvedCard("sf-001", "Sol Ring"))
        coEvery { cardRepository.searchCardByName("Command Tower") } returns
            DataResult.Success(buildResolvedCard("sf-002", "Command Tower"))

        // Act
        val result = useCase(deck)

        // Assert
        assertTrue(result is ImportCommunityDeckUseCase.ImportResult.Success)
        val success = result as ImportCommunityDeckUseCase.ImportResult.Success
        assertEquals(testDeckId, success.deckId)
        assertEquals(2, success.resolvedCount)
        assertEquals(0, success.failedCount)
    }

    @Test
    fun `given all cards resolve when invoke then deck is created with correct params`() = runTest {
        // Arrange
        val deck = buildCommunityDeck(format = "modern")
        coEvery { cardRepository.searchCardByName(any()) } returns
            DataResult.Success(buildResolvedCard("sf-001"))

        // Act
        useCase(deck)

        // Assert
        coVerify {
            deckRepository.createDeck(
                name = "Test Deck",
                description = "A test deck",
                format = "modern",
            )
        }
    }

    @Test
    fun `given all cards resolve when invoke then each card is added to deck`() = runTest {
        // Arrange
        val deck = buildCommunityDeck()
        coEvery { cardRepository.searchCardByName("Sol Ring") } returns
            DataResult.Success(buildResolvedCard("sf-001"))
        coEvery { cardRepository.searchCardByName("Command Tower") } returns
            DataResult.Success(buildResolvedCard("sf-002"))

        // Act
        useCase(deck)

        // Assert
        coVerify { deckRepository.addCardToDeck(testDeckId, "sf-001", 1, false) }
        coVerify { deckRepository.addCardToDeck(testDeckId, "sf-002", 1, false) }
    }

    @Test
    fun `given all cards resolve when invoke then attribution is set`() = runTest {
        // Arrange
        val deck = buildCommunityDeck()
        coEvery { cardRepository.searchCardByName(any()) } returns
            DataResult.Success(buildResolvedCard("sf-001"))

        // Act
        useCase(deck)

        // Assert
        coVerify {
            deckRepository.updateDeckAttribution(
                deckId = testDeckId,
                sourceUrl = "https://archidekt.com/decks/12345",
                sourceAuthor = "Author",
                sourceService = "archidekt",
                importedAt = any(),
            )
        }
    }

    // ── Group 2: Partial resolution ─────────────────────────────────────────

    @Test
    fun `given some cards fail when invoke then returns Success with correct counts`() = runTest {
        // Arrange
        val deck = buildCommunityDeck(
            cards = listOf(
                buildCard("Sol Ring"),
                buildCard("Unknown Card"),
                buildCard("Command Tower"),
            ),
        )
        coEvery { cardRepository.searchCardByName("Sol Ring") } returns
            DataResult.Success(buildResolvedCard("sf-001"))
        coEvery { cardRepository.searchCardByName("Unknown Card") } returns
            DataResult.Error("Not found")
        coEvery { cardRepository.searchCardByName("Command Tower") } returns
            DataResult.Success(buildResolvedCard("sf-002"))

        // Act
        val result = useCase(deck) as ImportCommunityDeckUseCase.ImportResult.Success

        // Assert
        assertEquals(2, result.resolvedCount)
        assertEquals(1, result.failedCount)
        assertEquals(testDeckId, result.deckId)
    }

    @Test
    fun `given some cards fail when invoke then only resolved cards are added to deck`() = runTest {
        // Arrange
        val deck = buildCommunityDeck(
            cards = listOf(buildCard("Sol Ring"), buildCard("Bad Card")),
        )
        coEvery { cardRepository.searchCardByName("Sol Ring") } returns
            DataResult.Success(buildResolvedCard("sf-001"))
        coEvery { cardRepository.searchCardByName("Bad Card") } returns
            DataResult.Error("Not found")

        // Act
        useCase(deck)

        // Assert
        coVerify(exactly = 1) { deckRepository.addCardToDeck(testDeckId, "sf-001", any(), any()) }
        coVerify(exactly = 1) { deckRepository.addCardToDeck(any(), any(), any(), any()) }
    }

    // ── Group 3: No cards resolve ───────────────────────────────────────────

    @Test
    fun `given no cards resolve when invoke then returns Success with zero resolved`() = runTest {
        // Arrange
        val deck = buildCommunityDeck(
            cards = listOf(buildCard("Unknown A"), buildCard("Unknown B")),
        )
        coEvery { cardRepository.searchCardByName(any()) } returns DataResult.Error("Not found")

        // Act
        val result = useCase(deck) as ImportCommunityDeckUseCase.ImportResult.Success

        // Assert
        assertEquals(0, result.resolvedCount)
        assertEquals(2, result.failedCount)
        assertEquals(testDeckId, result.deckId)
    }

    @Test
    fun `given no cards resolve when invoke then deck is still created with attribution`() = runTest {
        // Arrange
        val deck = buildCommunityDeck(cards = listOf(buildCard("Unknown")))
        coEvery { cardRepository.searchCardByName(any()) } returns DataResult.Error("Not found")

        // Act
        useCase(deck)

        // Assert
        coVerify(exactly = 1) { deckRepository.createDeck(any(), any(), any()) }
        coVerify(exactly = 1) { deckRepository.updateDeckAttribution(any(), any(), any(), any(), any()) }
    }

    // ── Group 4: Commander detection ────────────────────────────────────────

    @Test
    fun `given card with Commander category when invoke then commander is set on deck`() = runTest {
        // Arrange
        val commanderCard = buildCard("Atraxa", categories = listOf("Commander"))
        val regularCard = buildCard("Sol Ring")
        val deck = buildCommunityDeck(cards = listOf(commanderCard, regularCard))

        coEvery { cardRepository.searchCardByName("Atraxa") } returns
            DataResult.Success(buildResolvedCard("sf-atraxa"))
        coEvery { cardRepository.searchCardByName("Sol Ring") } returns
            DataResult.Success(buildResolvedCard("sf-sol"))

        val createdDeck = buildDeck()
        coEvery { deckRepository.observeDeckWithCards(testDeckId) } returns
            flowOf(DeckWithCards(deck = createdDeck, mainboard = emptyList(), sideboard = emptyList()))

        // Act
        useCase(deck)

        // Assert — updateDeck is called to set the commander.
        coVerify {
            deckRepository.updateDeck(match { it.commanderCardId == "sf-atraxa" })
        }
    }

    @Test
    fun `given commander card not resolved when invoke then no commander is set`() = runTest {
        // Arrange
        val commanderCard = buildCard("Unknown Commander", categories = listOf("Commander"))
        val deck = buildCommunityDeck(cards = listOf(commanderCard))

        coEvery { cardRepository.searchCardByName("Unknown Commander") } returns
            DataResult.Error("Not found")

        // Act
        useCase(deck)

        // Assert — updateDeck should NOT be called for commander.
        coVerify(exactly = 0) { deckRepository.updateDeck(any()) }
    }

    @Test
    fun `given multiple Commander cards when invoke then only first is used`() = runTest {
        // Arrange — partner commanders: only the first resolved one is set.
        val cmdr1 = buildCard("Commander A", categories = listOf("Commander"))
        val cmdr2 = buildCard("Commander B", categories = listOf("Commander"))
        val deck = buildCommunityDeck(cards = listOf(cmdr1, cmdr2))

        coEvery { cardRepository.searchCardByName("Commander A") } returns
            DataResult.Success(buildResolvedCard("sf-cmdr-a"))
        coEvery { cardRepository.searchCardByName("Commander B") } returns
            DataResult.Success(buildResolvedCard("sf-cmdr-b"))

        val createdDeck = buildDeck()
        coEvery { deckRepository.observeDeckWithCards(testDeckId) } returns
            flowOf(DeckWithCards(deck = createdDeck, mainboard = emptyList(), sideboard = emptyList()))

        // Act
        useCase(deck)

        // Assert — the FIRST commander is used.
        coVerify {
            deckRepository.updateDeck(match { it.commanderCardId == "sf-cmdr-a" })
        }
    }

    // ── Group 5: Sideboard categorization ───────────────────────────────────

    @Test
    fun `given card with Sideboard category when invoke then card is added as sideboard`() = runTest {
        // Arrange
        val sideboardCard = buildCard("Negate", categories = listOf("Sideboard"))
        val deck = buildCommunityDeck(cards = listOf(sideboardCard))

        coEvery { cardRepository.searchCardByName("Negate") } returns
            DataResult.Success(buildResolvedCard("sf-negate"))

        // Act
        useCase(deck)

        // Assert
        coVerify {
            deckRepository.addCardToDeck(
                deckId = testDeckId,
                scryfallId = "sf-negate",
                quantity = 1,
                isSideboard = true,
            )
        }
    }

    @Test
    fun `given card without Sideboard category when invoke then card is added as mainboard`() = runTest {
        // Arrange
        val mainCard = buildCard("Lightning Bolt")
        val deck = buildCommunityDeck(cards = listOf(mainCard))

        coEvery { cardRepository.searchCardByName("Lightning Bolt") } returns
            DataResult.Success(buildResolvedCard("sf-bolt"))

        // Act
        useCase(deck)

        // Assert
        coVerify {
            deckRepository.addCardToDeck(
                deckId = testDeckId,
                scryfallId = "sf-bolt",
                quantity = 1,
                isSideboard = false,
            )
        }
    }

    // ── Group 6: Quantity is respected ───────────────────────────────────────

    @Test
    fun `given card with quantity 4 when invoke then quantity is passed through`() = runTest {
        // Arrange
        val card = buildCard("Lightning Bolt", quantity = 4)
        val deck = buildCommunityDeck(cards = listOf(card))

        coEvery { cardRepository.searchCardByName("Lightning Bolt") } returns
            DataResult.Success(buildResolvedCard("sf-bolt"))

        // Act
        useCase(deck)

        // Assert
        coVerify {
            deckRepository.addCardToDeck(testDeckId, "sf-bolt", 4, false)
        }
    }

    // ── Group 7: Progress callback ──────────────────────────────────────────

    @Test
    fun `given 3 cards when invoke then onProgress is called after each card`() = runTest {
        // Arrange
        val deck = buildCommunityDeck(
            cards = listOf(buildCard("A"), buildCard("B"), buildCard("C")),
        )
        coEvery { cardRepository.searchCardByName("A") } returns
            DataResult.Success(buildResolvedCard("sf-a"))
        coEvery { cardRepository.searchCardByName("B") } returns
            DataResult.Error("Not found")
        coEvery { cardRepository.searchCardByName("C") } returns
            DataResult.Success(buildResolvedCard("sf-c"))

        val progressCalls = mutableListOf<Pair<Int, Int>>()

        // Act
        useCase(deck) { processed, total -> progressCalls.add(processed to total) }

        // Assert — one callback per card.
        assertEquals(3, progressCalls.size)
        assertEquals(1 to 3, progressCalls[0])  // A resolved → 1 processed
        assertEquals(2 to 3, progressCalls[1])  // B failed → 2 processed
        assertEquals(3 to 3, progressCalls[2])  // C resolved → 3 processed
    }

    // ── Group 8: Exception during creation ──────────────────────────────────

    @Test
    fun `given createDeck throws when invoke then returns Error`() = runTest {
        // Arrange
        coEvery { deckRepository.createDeck(any(), any(), any()) } throws
            RuntimeException("DB full")

        val deck = buildCommunityDeck()

        // Act
        val result = useCase(deck)

        // Assert
        assertTrue(result is ImportCommunityDeckUseCase.ImportResult.Error)
        assertEquals("DB full", (result as ImportCommunityDeckUseCase.ImportResult.Error).message)
    }

    @Test
    fun `given addCardToDeck throws when invoke then returns Error`() = runTest {
        // Arrange
        val deck = buildCommunityDeck(cards = listOf(buildCard("Sol Ring")))
        coEvery { cardRepository.searchCardByName("Sol Ring") } returns
            DataResult.Success(buildResolvedCard("sf-001"))
        coEvery { deckRepository.addCardToDeck(any(), any(), any(), any()) } throws
            RuntimeException("Write failed")

        // Act
        val result = useCase(deck)

        // Assert
        assertTrue(result is ImportCommunityDeckUseCase.ImportResult.Error)
        assertEquals("Write failed", (result as ImportCommunityDeckUseCase.ImportResult.Error).message)
    }

    @Test
    fun `given exception with null message when invoke then returns fallback error`() = runTest {
        // Arrange
        coEvery { deckRepository.createDeck(any(), any(), any()) } throws RuntimeException()

        val deck = buildCommunityDeck()

        // Act
        val result = useCase(deck) as ImportCommunityDeckUseCase.ImportResult.Error

        // Assert
        assertEquals("Import failed", result.message)
    }

    // ── Group 9: Empty cards list ───────────────────────────────────────────

    @Test
    fun `given deck with empty cards list when invoke then returns Success with zero counts`() = runTest {
        // Arrange
        val deck = buildCommunityDeck(cards = emptyList())

        // Act
        val result = useCase(deck) as ImportCommunityDeckUseCase.ImportResult.Success

        // Assert
        assertEquals(testDeckId, result.deckId)
        assertEquals(0, result.resolvedCount)
        assertEquals(0, result.failedCount)
    }

    @Test
    fun `given deck with empty cards list when invoke then attribution is still set`() = runTest {
        // Arrange
        val deck = buildCommunityDeck(cards = emptyList())

        // Act
        useCase(deck)

        // Assert
        coVerify { deckRepository.updateDeckAttribution(any(), any(), any(), any(), any()) }
    }

    // ── Group 10: Commander also sets cover card ─────────────────────────────

    @Test
    fun `given commander resolved and deck has no cover when invoke then cover is set to commander`() = runTest {
        // Arrange
        val commanderCard = buildCard("Atraxa", categories = listOf("Commander"))
        val deck = buildCommunityDeck(cards = listOf(commanderCard))

        coEvery { cardRepository.searchCardByName("Atraxa") } returns
            DataResult.Success(buildResolvedCard("sf-atraxa"))

        val createdDeck = buildDeck().copy(coverCardId = null)
        coEvery { deckRepository.observeDeckWithCards(testDeckId) } returns
            flowOf(DeckWithCards(deck = createdDeck, mainboard = emptyList(), sideboard = emptyList()))

        // Act
        useCase(deck)

        // Assert — cover is set to commander when deck has no cover.
        coVerify {
            deckRepository.updateDeck(match {
                it.commanderCardId == "sf-atraxa" && it.coverCardId == "sf-atraxa"
            })
        }
    }

    @Test
    fun `given commander resolved and deck has cover when invoke then cover is preserved`() = runTest {
        // Arrange
        val commanderCard = buildCard("Atraxa", categories = listOf("Commander"))
        val deck = buildCommunityDeck(cards = listOf(commanderCard))

        coEvery { cardRepository.searchCardByName("Atraxa") } returns
            DataResult.Success(buildResolvedCard("sf-atraxa"))

        val createdDeck = buildDeck().copy(coverCardId = "existing-cover")
        coEvery { deckRepository.observeDeckWithCards(testDeckId) } returns
            flowOf(DeckWithCards(deck = createdDeck, mainboard = emptyList(), sideboard = emptyList()))

        // Act
        useCase(deck)

        // Assert — existing cover is preserved.
        coVerify {
            deckRepository.updateDeck(match { it.coverCardId == "existing-cover" })
        }
    }
}
