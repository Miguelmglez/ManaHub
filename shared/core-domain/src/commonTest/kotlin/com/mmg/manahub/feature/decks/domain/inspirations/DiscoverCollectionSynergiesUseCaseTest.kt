package com.mmg.manahub.feature.decks.domain.inspirations

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.feature.decks.domain.engine.SectionMembership
import com.mmg.manahub.feature.decks.domain.engine.SectionQueryContext
import com.mmg.manahub.feature.decks.domain.engine.SynergyEngineState
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.AnalysisV3Fixtures
import com.mmg.manahub.feature.decks.domain.engine.card
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscoverCollectionSynergiesUseCaseTest {

    private val useCase = DiscoverCollectionSynergiesUseCase()

    private fun roleTag(key: String) = CardTag(key, TagCategory.ROLE)

    private fun owned(card: Card, quantity: Int = 1) =
        UserCardWithCard(UserCard(id = "uc-${card.scryfallId}", scryfallId = card.scryfallId, quantity = quantity), card)

    private fun creature(id: String, name: String, vararg roles: String, typeLine: String = "Creature", oracle: String? = null) =
        card(id = id, name = name, typeLine = typeLine, oracleText = oracle, tags = roles.map(::roleTag))

    @Test
    fun `a producer and a payoff on one axis form a complete engine`() {
        val outlet = creature("c1", "Outlet", "sac_outlet")
        val payoff = creature("c2", "Payoff", "death_payoff")

        val result = useCase.discover(listOf(owned(outlet), owned(payoff)), DeckFormat.MODERN)

        val death = assertNotNull(result.engines.firstOrNull { it.axis == "DEATH" })
        assertEquals(SynergyEngineState.COMPLETE, death.state)
        assertEquals(listOf("Outlet"), death.producers.map { it.card.name })
        assertTrue(death.payoffs.any { it.card.name == "Payoff" })
        assertEquals("engine:DEATH:producers", death.producersSectionId)
    }

    @Test
    fun `a lone producer side below half its ideal is not an engine`() {
        val outlet = creature("c1", "Outlet", "sac_outlet")

        val result = useCase.discover(listOf(owned(outlet, quantity = 1)), DeckFormat.MODERN)

        assertNull(result.engines.firstOrNull { it.axis == "DEATH" })
    }

    @Test
    fun `a lone producer side at half its ideal shows as missing payoffs`() {
        val outlet = creature("c1", "Outlet", "sac_outlet")

        val result = useCase.discover(listOf(owned(outlet, quantity = 4)), DeckFormat.MODERN)

        assertEquals(SynergyEngineState.MISSING_PAYOFFS, result.engines.first { it.axis == "DEATH" }.state)
    }

    @Test
    fun `owned copies are clamped to what a deck can run`() {
        val groupCard = creature("c1", "Group Card", "group_effect")

        val result = useCase.discover(listOf(owned(groupCard, quantity = 30)), DeckFormat.MODERN)

        assertNull(result.engines.firstOrNull { it.axis == "GROUP" })
    }

    @Test
    fun `payoff-optional axes render producers only`() {
        val millers = (1..4).map { creature("m$it", "Miller $it", "mill_opponent") }

        val result = useCase.discover(millers.map { owned(it, quantity = 4) }, DeckFormat.MODERN)

        val mill = result.engines.first { it.axis == "MILL_OPP" }
        assertTrue(mill.isProducerOnly)
        assertTrue(mill.payoffs.isEmpty())
        assertEquals(4, mill.producers.size)
    }

    @Test
    fun `cards illegal in the deck format never enter the pool`() {
        val outlet = card(id = "c1", name = "Outlet", typeLine = "Creature", tags = listOf(roleTag("sac_outlet")), legalityModern = "not_legal")
        val payoff = creature("c2", "Payoff", "death_payoff")

        val result = useCase.discover(listOf(owned(outlet), owned(payoff)), DeckFormat.MODERN)

        assertTrue(result.engines.none { engine -> engine.producers.any { it.card.name == "Outlet" } })
        val casual = useCase.discover(listOf(owned(outlet), owned(payoff)), DeckFormat.CASUAL)
        assertEquals(SynergyEngineState.COMPLETE, casual.engines.first { it.axis == "DEATH" }.state)
    }

    @Test
    fun `printings of one card collapse into a single member with summed copies`() {
        val first = creature("p1", "Outlet", "sac_outlet")
        val second = creature("p2", "Outlet", "sac_outlet")
        val payoff = creature("c2", "Payoff", "death_payoff")

        val result = useCase.discover(listOf(owned(first, 1), owned(second, 2), owned(payoff)), DeckFormat.MODERN)

        val producers = result.engines.first { it.axis == "DEATH" }.producers
        assertEquals(1, producers.size)
        assertEquals(3, producers.single().ownedCopies)
    }

    @Test
    fun `a tribe needs eight own-subtype members and lists its payoffs first`() {
        val elves = (1..8).map { creature("e$it", "Elf $it", typeLine = "Creature — Elf") }
        val lord = creature("lord", "Elf Lord", typeLine = "Creature — Human", oracle = "Other Elves you control get +1/+1.")

        val result = useCase.discover((elves + lord).map { owned(it) }, DeckFormat.MODERN)

        val tribe = assertNotNull(result.tribes.firstOrNull { it.tribeKey == "tribe:elf" })
        assertEquals(8, tribe.memberCount)
        assertEquals(1, tribe.payoffCount)
        assertEquals("Elf Lord", tribe.cards.first().card.name)

        val tooFew = useCase.discover(elves.drop(1).map { owned(it) }, DeckFormat.MODERN)
        assertTrue(tooFew.tribes.none { it.tribeKey == "tribe:elf" })
    }

    @Test
    fun `containing narrows to the synergies a card takes part in`() {
        val outlet = creature("c1", "Outlet", "sac_outlet")
        val payoff = creature("c2", "Payoff", "death_payoff")
        val lifeSource = creature("c3", "Healer", "lifegain_source")
        val lifePayoff = creature("c4", "Life Payoff", "lifegain_payoff")

        val result = useCase.discover(listOf(owned(outlet), owned(payoff), owned(lifeSource), owned(lifePayoff)), DeckFormat.MODERN)
        val filtered = result.containing("Outlet")

        assertTrue(filtered.engines.isNotEmpty())
        assertTrue(filtered.engines.all { it.contains("Outlet") })
        assertFalse(filtered.engines.any { it.axis == "LIFE" })
    }

    @Test
    fun `complete engines are listed before incomplete ones`() {
        val outlets = (1..4).map { creature("o$it", "Outlet $it", "sac_outlet") }
        val lifeSource = creature("c3", "Healer", "lifegain_source")
        val lifePayoff = creature("c4", "Life Payoff", "lifegain_payoff")

        val result = useCase.discover(outlets.map { owned(it, 4) } + owned(lifeSource) + owned(lifePayoff), DeckFormat.MODERN)

        val states = result.engines.map { it.state }
        val firstIncomplete = states.indexOfFirst { it != SynergyEngineState.COMPLETE }
        assertTrue(firstIncomplete == -1 || states.drop(firstIncomplete).none { it == SynergyEngineState.COMPLETE })
    }

    @Test
    fun `every listed card matches the Browse membership predicate of its section`() {
        val mismatches = mutableListOf<String>()
        AnalysisV3Fixtures.ALL.forEach { fixture ->
            val collection = fixture.mainboard.map { owned(it.card, it.quantity) }
            val format = if (fixture.format.isSixtyCardConstructed) fixture.format else DeckFormat.CASUAL
            val result = useCase.discover(collection, format)
            val context = SectionQueryContext(colorIdentity = emptySet(), format = format, dominantTribe = null)
            fun check(sectionId: String, cards: List<SynergyMember>) {
                val predicate = SectionMembership.predicate(sectionId, context) ?: return
                cards.filterNot { predicate(it.card) }.forEach { mismatches += "fixture=${fixture.id} section=$sectionId card=${it.card.name}" }
            }
            result.engines.forEach { engine ->
                check(engine.producersSectionId, engine.producers)
                if (!engine.isProducerOnly) check(engine.payoffsSectionId, engine.payoffs)
            }
            result.tribes.forEach { tribe -> check(tribe.sectionId, tribe.cards) }
        }
        assertTrue(mismatches.isEmpty(), "Discovery disagreed with SectionMembership:\n${mismatches.joinToString("\n")}")
    }
}
