package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.StrategyProfile
import com.mmg.manahub.feature.decks.domain.engine.card
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deck Engine Unification plan (`docs/plans/deck-engine-unification-plan.md` §5 Phase 3.3/3.4, Flow
 * B/C) — [RankOwnedCardsForProfileUseCase]'s owned-collection ranking against a picked
 * [StrategyProfile].
 *
 * Deck Analysis Engine v3 compat pass (2026-08-26): every fixture below used `ArchetypeId.RAMP` +
 * `CardTag.RAMP` purely as a matching-key example (archetype's own seed tags vs. an owned card's
 * tag) -- RAMP moved from `ArchetypeId` to `PostureId`, and `StrategyProfile` does not model
 * posture, so `CardTag.RAMP` no longer resolves through any archetype's seed tags at all. Swapped
 * to `ArchetypeId.AGGRO`/`CardTag.AGGRO` throughout, preserving the exact same matching-mechanism
 * intent with a real, still-mapped archetype/tag pair.
 */
class RankOwnedCardsForProfileUseCaseTest {

    private val useCase = RankOwnedCardsForProfileUseCase()

    @Test
    fun `an unpinned profile -- no archetype, no themes, no tribe -- yields nothing`() {
        val owned = listOf(card(id = "c1", name = "Aggro Spell", tags = listOf(CardTag.AGGRO)))
        val result = useCase(StrategyProfile.EMPTY, owned)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `a matching owned card is returned, a non-matching one is excluded`() {
        val matching = card(id = "c1", name = "Aggro Spell", tags = listOf(CardTag.AGGRO))
        val nonMatching = card(id = "c2", name = "Mill Card", tags = listOf(CardTag.GRAVEYARD))
        val result = useCase(StrategyProfile(archetype = ArchetypeId.AGGRO), listOf(matching, nonMatching))
        assertEquals(listOf("c1"), result.map { it.scryfallId })
    }

    @Test
    fun `duplicate printings of the same card name are deduped`() {
        val printingA = card(id = "c1", name = "Aggro Spell", tags = listOf(CardTag.AGGRO))
        val printingB = card(id = "c1-alt", name = "Aggro Spell", tags = listOf(CardTag.AGGRO))
        val result = useCase(StrategyProfile(archetype = ArchetypeId.AGGRO), listOf(printingA, printingB))
        assertEquals(1, result.size)
    }

    @Test
    fun `results are capped at the limit`() {
        val owned = (1..20).map { i -> card(id = "c$i", name = "Aggro Spell $i", tags = listOf(CardTag.AGGRO)) }
        val result = useCase(StrategyProfile(archetype = ArchetypeId.AGGRO), owned, limit = 5)
        assertEquals(5, result.size)
    }

    @Test
    fun `an empty collection yields an empty list, never a crash`() {
        val result = useCase(StrategyProfile(archetype = ArchetypeId.AGGRO), emptyList())
        assertTrue(result.isEmpty())
    }

    // ── Color-identity filter (bug fix -- Deck Wizard entry-flow audit) ──────────
    // StrategyProfile.colors was captured on every Flow B/C pick but never actually consulted here,
    // so an off-color owned card could surface as a suggested seed for a deck whose colors the user
    // had already chosen in the SAME flow. See RankOwnedCardsForProfileUseCase's KDoc on the filter.

    @Test
    fun `once colors are picked, a strategy-matching but off-color card is excluded`() {
        val offColor = card(id = "c1", name = "Aggro Spell", tags = listOf(CardTag.AGGRO), colorIdentity = listOf("U"))
        val result = useCase(StrategyProfile(archetype = ArchetypeId.AGGRO, colors = setOf(ManaColor.R)), listOf(offColor))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `once colors are picked, a strategy-matching card within those colors is still included`() {
        val onColor = card(id = "c1", name = "Aggro Spell", tags = listOf(CardTag.AGGRO), colorIdentity = listOf("R"))
        val result = useCase(StrategyProfile(archetype = ArchetypeId.AGGRO, colors = setOf(ManaColor.R)), listOf(onColor))
        assertEquals(listOf("c1"), result.map { it.scryfallId })
    }

    @Test
    fun `a card whose color identity is a strict subset of the picked colors is included`() {
        val partial = card(id = "c1", name = "Aggro Spell", tags = listOf(CardTag.AGGRO), colorIdentity = listOf("R"))
        val result = useCase(StrategyProfile(archetype = ArchetypeId.AGGRO, colors = setOf(ManaColor.R, ManaColor.G)), listOf(partial))
        assertEquals(listOf("c1"), result.map { it.scryfallId })
    }

    @Test
    fun `with no colors picked yet, ranking falls back to strategy fit alone -- unchanged behavior`() {
        val anyColor = card(id = "c1", name = "Aggro Spell", tags = listOf(CardTag.AGGRO), colorIdentity = listOf("U"))
        val result = useCase(StrategyProfile(archetype = ArchetypeId.AGGRO), listOf(anyColor))
        assertEquals(listOf("c1"), result.map { it.scryfallId })
    }
}
