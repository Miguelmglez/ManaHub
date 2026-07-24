package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Deck Builder v2, Phase 1 -- [CollectionProfileUseCase] determinism + signal extraction. */
class CollectionProfileUseCaseTest {

    private val dispatcher = StandardTestDispatcher()
    private val useCase = CollectionProfileUseCase(ioDispatcher = dispatcher)

    @Test
    fun `color shares favor the collection's dominant color`() = runTest(dispatcher) {
        val collection = listOf(
            card(id = "1", name = "White A", manaCost = "{1}{W}{W}"),
            card(id = "2", name = "White B", manaCost = "{W}"),
            card(id = "3", name = "Blue A", manaCost = "{U}"),
        )
        val profile = useCase(collection)
        assertEquals(ManaColor.W, profile.colorShares.first().color)
        assertTrue(profile.colorShares.first().share > profile.colorShares.last().share)
        // pipCount is the RAW pip count (3 W pips: {1}{W}{W} + {W}), never a share rescaled to an
        // int -- CircularDistribution's centered "Total" must show a real quantity (see
        // feedback_circulardistribution_wizard_pipcount_not_scaled_share.md).
        assertEquals(3, profile.colorShares.first().pipCount)
        assertEquals(1, profile.colorShares.last().pipCount)
    }

    @Test
    fun `empty collection yields empty signals, never a crash`() = runTest(dispatcher) {
        val profile = useCase(emptyList())
        assertTrue(profile.colorShares.isEmpty())
        assertTrue(profile.dominantStrategies.isEmpty())
        assertTrue(profile.dominantTribes.isEmpty())
        assertTrue(profile.commanderCandidates.isEmpty())
    }

    @Test
    fun `dominant strategy counts distinct cards carrying a STRATEGY tag`() = runTest(dispatcher) {
        val collection = listOf(
            card(id = "1", name = "Tokens A", tags = listOf(CardTag.TOKENS)),
            card(id = "2", name = "Tokens B", tags = listOf(CardTag.TOKENS)),
            card(id = "3", name = "Lifegain A", tags = listOf(CardTag.LIFEGAIN)),
        )
        val profile = useCase(collection, limit = 5)
        assertEquals(CardTag.TOKENS, profile.dominantStrategies.first().tag)
        assertEquals(2, profile.dominantStrategies.first().copies)
    }

    @Test
    fun `dominant tribes are derived and pluralized, most common first`() = runTest(dispatcher) {
        val collection = listOf(
            card(id = "1", name = "Elf A", typeLine = "Creature — Elf"),
            card(id = "2", name = "Elf B", typeLine = "Creature — Elf"),
            card(id = "3", name = "Elf C", typeLine = "Creature — Elf"),
            card(id = "4", name = "Goblin A", typeLine = "Creature — Goblin"),
        )
        val profile = useCase(collection)
        val top = profile.dominantTribes.first()
        assertEquals("tribe:elf", top.tribeKey)
        assertEquals("Elves", top.displayLabel)
        assertEquals(3, top.copies)
    }

    @Test
    fun `commander candidates require Legendary Creature and rank by support plus popularity`() = runTest(dispatcher) {
        val commander = card(
            id = "cmd", name = "Test Commander", typeLine = "Legendary Creature — Human",
            colorIdentity = listOf("R"),
        )
        val nonCommander = card(id = "n1", name = "Not A Commander", typeLine = "Creature — Human")
        val supportingCard = card(id = "s1", name = "Support", colorIdentity = listOf("R"))
        val profile = useCase(listOf(commander, nonCommander, supportingCard))

        assertEquals(1, profile.commanderCandidates.size)
        assertEquals("Test Commander", profile.commanderCandidates.first().card.name)
    }

    @Test
    fun `a non-legendary creature is never a commander candidate`() = runTest(dispatcher) {
        val creature = card(name = "Just A Creature", typeLine = "Creature — Human")
        val profile = useCase(listOf(creature))
        assertTrue(profile.commanderCandidates.isEmpty())
    }

    @Test
    fun `two printings of the same card are treated as one card, never double-counted`() = runTest(dispatcher) {
        // Regression: a user routinely owns MULTIPLE PRINTINGS of the same named card (a regular +
        // a foil/promo copy, different scryfallId) -- this is normal collection state, not an edge
        // case (see project_card_versions_languages memory). Deduping by scryfallId used to treat
        // these as two DISTINCT cards, inflating every signal below.
        val printingA = card(id = "print-a", name = "Twin Card", manaCost = "{W}", tags = listOf(CardTag.TOKENS), typeLine = "Creature — Elf")
        val printingB = card(id = "print-b", name = "Twin Card", manaCost = "{W}", tags = listOf(CardTag.TOKENS), typeLine = "Creature — Elf")
        val profile = useCase(listOf(printingA, printingB))

        // colorShares: exactly ONE W pip counted (not two) -> a single 100% share entry.
        assertEquals(1, profile.colorShares.size)
        assertEquals(1f, profile.colorShares.first().share)
        assertEquals(1, profile.colorShares.first().pipCount)

        // dominantStrategies: ONE distinct card carrying TOKENS, not two.
        assertEquals(1, profile.dominantStrategies.first().copies)

        // dominantTribes: ONE distinct Elf, not two.
        assertEquals(1, profile.dominantTribes.first().copies)
    }

    @Test
    fun `duplicate printings of a supporting card do not inflate a commander candidate's support score`() = runTest(dispatcher) {
        val commander = card(id = "cmd", name = "Test Commander", typeLine = "Legendary Creature — Human", colorIdentity = listOf("R"))
        val support = card(id = "support-a", name = "Support Card", colorIdentity = listOf("R"))
        val supportDuplicatePrinting = card(id = "support-b", name = "Support Card", colorIdentity = listOf("R"))

        val single = useCase(listOf(commander, support))
        val withExtraPrinting = useCase(listOf(commander, support, supportDuplicatePrinting))

        assertEquals(
            single.commanderCandidates.first().supportScore,
            withExtraPrinting.commanderCandidates.first().supportScore,
            "a second printing of an already-counted supporting card must not change the support score",
        )
    }

    @Test
    fun `results are deterministic across repeated runs on the same input`() = runTest(dispatcher) {
        val collection = listOf(
            card(id = "1", name = "Card A", manaCost = "{W}", tags = listOf(CardTag.TOKENS), typeLine = "Creature — Elf"),
            card(id = "2", name = "Card B", manaCost = "{U}", tags = listOf(CardTag.LIFEGAIN), typeLine = "Creature — Goblin"),
        )
        val first = useCase(collection)
        val second = useCase(collection)
        assertEquals(first, second)
    }
}
