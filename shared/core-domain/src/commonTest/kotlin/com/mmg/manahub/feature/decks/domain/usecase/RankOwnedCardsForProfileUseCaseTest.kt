package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.StrategyProfile
import com.mmg.manahub.feature.decks.domain.engine.card
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deck Engine Unification plan (`docs/plans/deck-engine-unification-plan.md` §5 Phase 3.3/3.4, Flow
 * B/C) — [RankOwnedCardsForProfileUseCase]'s owned-collection ranking against a picked
 * [StrategyProfile].
 */
class RankOwnedCardsForProfileUseCaseTest {

    private val useCase = RankOwnedCardsForProfileUseCase()

    @Test
    fun `an unpinned profile -- no archetype, no themes, no tribe -- yields nothing`() {
        val owned = listOf(card(id = "c1", name = "Ramp Spell", tags = listOf(CardTag.RAMP)))
        val result = useCase(StrategyProfile.EMPTY, owned)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `a matching owned card is returned, a non-matching one is excluded`() {
        val matching = card(id = "c1", name = "Ramp Spell", tags = listOf(CardTag.RAMP))
        val nonMatching = card(id = "c2", name = "Mill Card", tags = listOf(CardTag.GRAVEYARD))
        val result = useCase(StrategyProfile(archetype = ArchetypeId.RAMP), listOf(matching, nonMatching))
        assertEquals(listOf("c1"), result.map { it.scryfallId })
    }

    @Test
    fun `duplicate printings of the same card name are deduped`() {
        val printingA = card(id = "c1", name = "Ramp Spell", tags = listOf(CardTag.RAMP))
        val printingB = card(id = "c1-alt", name = "Ramp Spell", tags = listOf(CardTag.RAMP))
        val result = useCase(StrategyProfile(archetype = ArchetypeId.RAMP), listOf(printingA, printingB))
        assertEquals(1, result.size)
    }

    @Test
    fun `results are capped at the limit`() {
        val owned = (1..20).map { i -> card(id = "c$i", name = "Ramp Spell $i", tags = listOf(CardTag.RAMP)) }
        val result = useCase(StrategyProfile(archetype = ArchetypeId.RAMP), owned, limit = 5)
        assertEquals(5, result.size)
    }

    @Test
    fun `an empty collection yields an empty list, never a crash`() {
        val result = useCase(StrategyProfile(archetype = ArchetypeId.RAMP), emptyList())
        assertTrue(result.isEmpty())
    }
}
