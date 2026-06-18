package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.feature.decks.domain.engine.card
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exhaustive unit tests for [ImportDeckUseCase].
 *
 * Strategy:
 * - [com.mmg.manahub.feature.decks.domain.engine.DeckImportExportHelper] is a pure `object`
 *   with no external dependencies; it is used DIRECTLY (no mock) so the real parse→resolve→write
 *   pipeline is exercised end-to-end at the use-case level.
 * - [CardRepository.searchCardByName] and [DeckRepository.addCardToDeck] are MockK mocks so we
 *   control resolution outcomes and verify write calls without hitting Room or Scryfall.
 * - A failed [DataResult.Error] from [searchCardByName] is silently SKIPPED per contract; the
 *   remaining lines continue unaffected.
 * - The use case does NOT rename the deck — [ParsedDeckList] carries no name; the caller's
 *   existing name is preserved.
 * - Empty / blank text produces an empty [ParsedDeckList] → zero write calls; [Result.success].
 *
 * Card fixtures are built via the shared `card()` helper from EngineFixtures to avoid
 * constructing the full [Card] data class inline (which has ~30+ required params).
 */
class ImportDeckUseCaseTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────
    private val cardRepository = mockk<CardRepository>()
    private val deckRepository = mockk<DeckRepository>(relaxed = true)

    // ── System under test ─────────────────────────────────────────────────────
    private lateinit var useCase: ImportDeckUseCase

    // ── Constants ─────────────────────────────────────────────────────────────
    private val DECK_ID = "deck-import-1"

    // ── Card fixtures (reuse the shared engine helper) ────────────────────────
    private val bolt  = card(id = "bolt-1",    name = "Lightning Bolt",  typeLine = "Instant",  colorIdentity = listOf("R"), colors = listOf("R"))
    private val bears = card(id = "bears-1",   name = "Grizzly Bears",   typeLine = "Creature", colorIdentity = listOf("G"), colors = listOf("G"))
    private val negate = card(id = "negate-1", name = "Negate",          typeLine = "Instant",  colorIdentity = listOf("U"), colors = listOf("U"))
    private val sol    = card(id = "sol-1",    name = "Sol Ring",         typeLine = "Artifact", colorIdentity = emptyList(), colors = emptyList())
    private val elfLord = card(id = "elf-lord-1", name = "Elf Lord",      typeLine = "Legendary Creature — Elf", colorIdentity = listOf("G"), colors = listOf("G"))
    private val forest  = card(id = "forest-1",   name = "Forest",        typeLine = "Basic Land — Forest",       colorIdentity = listOf("G"), colors = emptyList())
    private val nature  = card(id = "nature-1",   name = "Nature's Lore", typeLine = "Sorcery",                   colorIdentity = listOf("G"), colors = listOf("G"))
    private val cardA   = card(id = "a-1", name = "Card A", typeLine = "Creature", colorIdentity = listOf("W"), colors = listOf("W"))
    private val cardB   = card(id = "b-1", name = "Card B", typeLine = "Instant",  colorIdentity = listOf("U"), colors = listOf("U"))
    private val cardC   = card(id = "c-1", name = "Card C", typeLine = "Sorcery",  colorIdentity = listOf("B"), colors = listOf("B"))
    private val plains  = card(id = "plains-1", name = "Plains",  typeLine = "Basic Land — Plains", colorIdentity = listOf("W"), colors = emptyList())

    @Before
    fun setUp() {
        useCase = ImportDeckUseCase(cardRepository, deckRepository)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 1 — Empty / blank input
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given empty string when invoke then no write calls and result is success`() = runTest {
        // Act
        val result = useCase(DECK_ID, "")

        // Assert — empty input: parse produces empty lists; nothing to resolve or write.
        assertTrue("Result must be success for empty input", result.isSuccess)
        coVerify(exactly = 0) { cardRepository.searchCardByName(any()) }
        coVerify(exactly = 0) { deckRepository.addCardToDeck(any(), any(), any(), any()) }
    }

    @Test
    fun `given blank string (whitespace only) when invoke then no write calls and result is success`() = runTest {
        // Act
        val result = useCase(DECK_ID, "   \n\t  ")

        // Assert
        assertTrue("Result must be success for blank input", result.isSuccess)
        coVerify(exactly = 0) { cardRepository.searchCardByName(any()) }
        coVerify(exactly = 0) { deckRepository.addCardToDeck(any(), any(), any(), any()) }
    }

    @Test
    fun `given text with only comment lines when invoke then no write calls and result is success`() = runTest {
        // Arrange — Moxfield exports always begin with "// Deck Name"; nothing to import.
        val text = "// My Commander Deck\n// Exported from Moxfield\n"

        // Act
        val result = useCase(DECK_ID, text)

        // Assert
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { cardRepository.searchCardByName(any()) }
    }

    @Test
    fun `given text with section headers only and no card lines when invoke then no writes issued`() = runTest {
        // Arrange — headers alone produce no ParsedLines.
        val text = "Deck\nSideboard\n"

        // Act
        val result = useCase(DECK_ID, text)

        // Assert
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { cardRepository.searchCardByName(any()) }
        coVerify(exactly = 0) { deckRepository.addCardToDeck(any(), any(), any(), any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 2 — Happy path: mainboard-only import
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given mainboard-only list when invoke then each card is resolved and written to mainboard`() = runTest {
        // Arrange
        coEvery { cardRepository.searchCardByName("Lightning Bolt") } returns DataResult.Success(bolt)
        coEvery { cardRepository.searchCardByName("Grizzly Bears")  } returns DataResult.Success(bears)

        val text = "4 Lightning Bolt\n2 Grizzly Bears"

        // Act
        val result = useCase(DECK_ID, text)

        // Assert
        assertTrue("Result must be success", result.isSuccess)
        coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, bolt.scryfallId,  4, false) }
        coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, bears.scryfallId, 2, false) }
    }

    @Test
    fun `given mainboard list with set code suffix when invoke then name is resolved correctly (set code stripped)`() =
        runTest {
            // Arrange — format: "4 Lightning Bolt (M11) 163" — set code must be stripped.
            coEvery { cardRepository.searchCardByName("Lightning Bolt") } returns DataResult.Success(bolt)

            // Act
            val result = useCase(DECK_ID, "4 Lightning Bolt (M11) 163")

            // Assert
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, bolt.scryfallId, 4, false) }
        }

    @Test
    fun `given mainboard list with foil marker when invoke then card is still resolved and written`() = runTest {
        // Arrange — format: "1 Sol Ring (VMA) 4 *F*"
        coEvery { cardRepository.searchCardByName("Sol Ring") } returns DataResult.Success(sol)

        // Act
        val result = useCase(DECK_ID, "1 Sol Ring (VMA) 4 *F*")

        // Assert — foil marker is stripped; card is resolved and written normally.
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, sol.scryfallId, 1, false) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 3 — Sideboard section
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given sideboard section when invoke then sideboard cards are written with isSideboard=true`() = runTest {
        // Arrange
        coEvery { cardRepository.searchCardByName("Negate") } returns DataResult.Success(negate)

        val text = "Sideboard\n3 Negate"

        // Act
        val result = useCase(DECK_ID, text)

        // Assert
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, negate.scryfallId, 3, true) }
    }

    @Test
    fun `given mixed mainboard and sideboard when invoke then each card is routed to the correct board`() = runTest {
        // Arrange
        coEvery { cardRepository.searchCardByName("Lightning Bolt") } returns DataResult.Success(bolt)
        coEvery { cardRepository.searchCardByName("Negate")         } returns DataResult.Success(negate)

        val text = "4 Lightning Bolt\n\nSideboard\n2 Negate"

        // Act
        val result = useCase(DECK_ID, text)

        // Assert
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, bolt.scryfallId,   4, false) }
        coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, negate.scryfallId, 2, true)  }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 4 — Commander section
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given commander section when invoke then commander is resolved and written to mainboard with quantity 1`() =
        runTest {
            // Arrange — DeckImportExportHelper forces commander quantity to 1 regardless of the
            // number on the line (ParsedLine.copy(quantity = 1) in the parser).
            coEvery { cardRepository.searchCardByName("Elf Lord") } returns DataResult.Success(elfLord)
            coEvery { cardRepository.searchCardByName("Forest")   } returns DataResult.Success(forest)

            val text = "Commander\n1 Elf Lord\n\nDeck\n30 Forest"

            // Act
            val result = useCase(DECK_ID, text)

            // Assert — commander written to mainboard (isSideboard = false), qty = 1.
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, elfLord.scryfallId, 1, false) }
            // Mainboard Forest also written.
            coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, forest.scryfallId, 30, false) }
        }

    @Test
    fun `given commander line with quantity 3 when invoke then quantity is clamped to 1 (parser contract)`() =
        runTest {
            // Arrange — the parser overrides any non-1 quantity in the Commander section.
            coEvery { cardRepository.searchCardByName("Elf Lord") } returns DataResult.Success(elfLord)

            val text = "Commander\n3 Elf Lord"

            // Act
            val result = useCase(DECK_ID, text)

            // Assert — quantity must be 1 per DeckImportExportHelper.parse contract.
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, elfLord.scryfallId, 1, false) }
        }

    @Test
    fun `given full Moxfield export text with commander mainboard and sideboard when invoke then all sections written correctly`() =
        runTest {
            // Arrange
            coEvery { cardRepository.searchCardByName("Elf Lord")      } returns DataResult.Success(elfLord)
            coEvery { cardRepository.searchCardByName("Nature's Lore") } returns DataResult.Success(nature)
            coEvery { cardRepository.searchCardByName("Negate")        } returns DataResult.Success(negate)

            val text = """
                // Commander Test Deck

                Commander
                1 Elf Lord

                Deck
                1 Nature's Lore

                Sideboard
                2 Negate
            """.trimIndent()

            // Act
            val result = useCase(DECK_ID, text)

            // Assert
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, elfLord.scryfallId, 1, false) }
            coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, nature.scryfallId,  1, false) }
            coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, negate.scryfallId,  2, true)  }
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 5 — Error tolerance: unresolvable lines are skipped, not aborting
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given one unresolvable line when invoke then that line is skipped and remaining lines write successfully`() =
        runTest {
            // Arrange — "Unknown Card" → DataResult.Error; "Lightning Bolt" → Success.
            coEvery { cardRepository.searchCardByName("Unknown Card")   } returns DataResult.Error("Not found")
            coEvery { cardRepository.searchCardByName("Lightning Bolt") } returns DataResult.Success(bolt)

            val text = "1 Unknown Card\n4 Lightning Bolt"

            // Act
            val result = useCase(DECK_ID, text)

            // Assert — overall result is success (no abort); bolt is written; unknown is skipped.
            assertTrue("Result must be success even when some lines fail", result.isSuccess)
            coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, bolt.scryfallId, 4, false) }
            // Only one write total (the unknown card produced no write).
            coVerify(exactly = 1) { deckRepository.addCardToDeck(any(), any(), any(), any()) }
        }

    @Test
    fun `given all lines unresolvable when invoke then no writes are issued and result is success`() = runTest {
        // Arrange
        coEvery { cardRepository.searchCardByName(any()) } returns DataResult.Error("Not found")

        val text = "4 Made Up Card A\n2 Made Up Card B"

        // Act
        val result = useCase(DECK_ID, text)

        // Assert — all lines failed; the operation still returns success (skip-not-abort contract).
        assertTrue("Result must be success when all lines fail to resolve", result.isSuccess)
        coVerify(exactly = 0) { deckRepository.addCardToDeck(any(), any(), any(), any()) }
    }

    @Test
    fun `given multiple resolvable and multiple unresolvable lines when invoke then only resolvable cards are written`() =
        runTest {
            // Arrange — 2 resolvable, 2 unresolvable, interleaved.
            coEvery { cardRepository.searchCardByName("Lightning Bolt") } returns DataResult.Success(bolt)
            coEvery { cardRepository.searchCardByName("Negate")         } returns DataResult.Success(negate)
            coEvery { cardRepository.searchCardByName("Fake Card A")    } returns DataResult.Error("Not found")
            coEvery { cardRepository.searchCardByName("Fake Card B")    } returns DataResult.Error("Not found")

            val text = "4 Lightning Bolt\n1 Fake Card A\n3 Negate\n2 Fake Card B"

            // Act
            val result = useCase(DECK_ID, text)

            // Assert — exactly 2 writes: bolt and negate.
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, bolt.scryfallId,   4, false) }
            coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, negate.scryfallId, 3, false) }
            coVerify(exactly = 2) { deckRepository.addCardToDeck(any(), any(), any(), any()) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 6 — No deck rename (contract verification)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given any deck list text when invoke then updateDeck is NEVER called (no rename)`() = runTest {
        // Arrange — ParsedDeckList carries no name; the use case must not rename the deck.
        coEvery { cardRepository.searchCardByName("Lightning Bolt") } returns DataResult.Success(bolt)

        // Act
        useCase(DECK_ID, "4 Lightning Bolt")

        // Assert — the DeckRepository.updateDeck method is never invoked.
        coVerify(exactly = 0) { deckRepository.updateDeck(any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 7 — Result failure path: repository write throws
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given deckRepository addCardToDeck throws when invoke then result is failure`() = runTest {
        // Arrange — resolution succeeds but the DB write throws.
        coEvery { cardRepository.searchCardByName("Lightning Bolt") } returns DataResult.Success(bolt)
        coEvery {
            deckRepository.addCardToDeck(DECK_ID, bolt.scryfallId, 4, false)
        } throws RuntimeException("DB error")

        // Act
        val result = useCase(DECK_ID, "4 Lightning Bolt")

        // Assert — a repository exception propagates as Result.failure (the outer runCatching catches it).
        assertFalse("Result must be failure when repository throws", result.isSuccess)
        assertTrue(result.isFailure)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 8 — Quantity integrity
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given single-copy line when invoke then addCardToDeck is called with quantity 1`() = runTest {
        // Arrange
        coEvery { cardRepository.searchCardByName("Sol Ring") } returns DataResult.Success(sol)

        // Act
        val result = useCase(DECK_ID, "1 Sol Ring")

        // Assert — quantity must be exactly 1.
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, sol.scryfallId, 1, false) }
    }

    @Test
    fun `given line with quantity 99 when invoke then addCardToDeck is called with quantity 99`() = runTest {
        // Arrange — the use case honours whatever quantity the parser produces.
        coEvery { cardRepository.searchCardByName("Plains") } returns DataResult.Success(plains)

        // Act
        val result = useCase(DECK_ID, "99 Plains")

        // Assert
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, plains.scryfallId, 99, false) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Group 9 — searchCardByName call count (once per parsed line, no batching/caching)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `given three lines when invoke then searchCardByName is called exactly three times — once per line`() =
        runTest {
            // Arrange
            coEvery { cardRepository.searchCardByName("Card A") } returns DataResult.Success(cardA)
            coEvery { cardRepository.searchCardByName("Card B") } returns DataResult.Success(cardB)
            coEvery { cardRepository.searchCardByName("Card C") } returns DataResult.Success(cardC)

            val text = "1 Card A\n2 Card B\n3 Card C"

            // Act
            useCase(DECK_ID, text)

            // Assert — one resolution call per line; the use case does not batch or cache.
            coVerify(exactly = 1) { cardRepository.searchCardByName("Card A") }
            coVerify(exactly = 1) { cardRepository.searchCardByName("Card B") }
            coVerify(exactly = 1) { cardRepository.searchCardByName("Card C") }
            coVerify(exactly = 3) { cardRepository.searchCardByName(any()) }
        }

    @Test
    fun `given same card name appearing twice in the list when invoke then searchCardByName is called twice`() =
        runTest {
            // Arrange — duplicate lines are independent parse entries; the use case calls resolve
            // once per ParsedLine (no dedup / no caching across lines).
            coEvery { cardRepository.searchCardByName("Lightning Bolt") } returns DataResult.Success(bolt)

            val text = "4 Lightning Bolt\n2 Lightning Bolt"

            // Act
            useCase(DECK_ID, text)

            // Assert — called twice (once per parsed line).
            coVerify(exactly = 2) { cardRepository.searchCardByName("Lightning Bolt") }
            // And two separate addCardToDeck calls with their respective quantities.
            coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, bolt.scryfallId, 4, false) }
            coVerify(exactly = 1) { deckRepository.addCardToDeck(DECK_ID, bolt.scryfallId, 2, false) }
        }
}
