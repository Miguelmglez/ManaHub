package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.feature.decks.presentation.engine.ManaColor
import com.mmg.manahub.feature.decks.presentation.engine.SeedStrategy
import com.mmg.manahub.feature.decks.presentation.engine.card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [InferDeckIdentityUseCase]: color-identity union (WUBRG only), best-overlap strategy
 * matching, seedTags assembly, and the empty-seeds no-op.
 */
class InferDeckIdentityUseCaseTest {

    private val useCase = InferDeckIdentityUseCase()

    @Test
    fun `empty seeds yield empty identity null strategy and empty seedTags`() {
        val result = useCase(emptyList())

        assertTrue(result.colorIdentity.isEmpty())
        assertNull(result.strategy)
        assertTrue(result.seedTags.isEmpty())
    }

    @Test
    fun `color identity is the WUBRG union and drops C and unknown symbols`() {
        val seeds = listOf(
            card(id = "a", colorIdentity = listOf("U", "G")),
            card(id = "b", colorIdentity = listOf("G", "C")),  // C must be dropped
            card(id = "c", colorIdentity = listOf("X")),       // unknown must be dropped
        )

        val result = useCase(seeds)

        assertEquals(setOf(ManaColor.U, ManaColor.G), result.colorIdentity)
    }

    @Test
    fun `strategy is the one with the highest primaryTags overlap`() {
        // CONTROL primaryTags = CONTROL, COUNTERSPELL, WRATH, REMOVAL.
        // Two control tags vs a single aggro tag → CONTROL wins.
        val seeds = listOf(
            card(id = "a", tags = listOf(CardTag.COUNTERSPELL, CardTag.WRATH)),
            card(id = "b", tags = listOf(CardTag.AGGRO)),
        )

        val result = useCase(seeds)

        assertEquals(SeedStrategy.CONTROL, result.strategy)
        // seedTags include CONTROL's primaryTags…
        assertTrue(result.seedTags.any { it.key == CardTag.CONTROL.key })
        assertTrue(result.seedTags.any { it.key == CardTag.COUNTERSPELL.key })
        // …and the seeds' own identity tags (AGGRO is an ARCHETYPE).
        assertTrue(result.seedTags.any { it.key == CardTag.AGGRO.key })
    }

    @Test
    fun `strategy is null when seeds carry no recognizable archetype tags`() {
        // KEYWORD/TYPE category tags are not identity categories → no strategy overlap.
        val seeds = listOf(
            card(id = "a", tags = listOf(CardTag("flying", com.mmg.manahub.core.domain.model.TagCategory.KEYWORD))),
        )

        val result = useCase(seeds)

        assertNull(result.strategy)
        assertTrue(result.seedTags.isEmpty())
    }

    @Test
    fun `userTags also contribute to strategy detection`() {
        val seeds = listOf(
            card(id = "a", userTags = listOf(CardTag.TOKENS)),
        )

        val result = useCase(seeds)

        // TOKENS is a primary tag of the TOKENS strategy.
        assertEquals(SeedStrategy.TOKENS, result.strategy)
    }

    @Test
    fun `seedTags are de-duplicated by key`() {
        val seeds = listOf(
            // GRAVEYARD is both a seed identity tag and GRAVEYARD strategy's primary tag.
            card(id = "a", tags = listOf(CardTag.GRAVEYARD, CardTag.SACRIFICE)),
        )

        val result = useCase(seeds)

        assertEquals(SeedStrategy.GRAVEYARD, result.strategy)
        val keys = result.seedTags.map { it.key }
        assertEquals(keys.distinct().size, keys.size)
    }
}
