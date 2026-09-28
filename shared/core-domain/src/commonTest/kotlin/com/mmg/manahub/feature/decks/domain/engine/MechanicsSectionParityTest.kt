package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.SuggestedTag
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture12Atraxa
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MechanicsSectionParityTest {
    private val context = SectionQueryContext(emptySet(), DeckFormat.COMMANDER_CASUAL, null)

    private fun tagged(id: String, name: String, key: String, confidence: Float = 0.8f) =
        card(id = id, name = name, typeLine = "Creature", suggestedTags = listOf(
            SuggestedTag(CardTag(key, TagCategory.ROLE), confidence),
        ))

    @Test
    fun `graveyard exit producer and payoff attribution matches Collection membership`() {
        val spiritMascot = tagged("mascot", "Spirit Mascot", "leave_graveyard_payoff", 0.88f)
        val recursion = tagged("recursion", "Recursion Spell", "graveyard_exit_source")
        val eligible = listOf(spiritMascot, recursion)
        val producers = SectionMembership.predicate("engine:GRAVEYARD_EXIT:producers", context)!!
        val payoffs = SectionMembership.predicate("engine:GRAVEYARD_EXIT:payoffs", context)!!

        assertEquals(listOf("recursion"), eligible.filter(producers).map { it.scryfallId })
        assertEquals(listOf("mascot"), eligible.filter(payoffs).map { it.scryfallId })
        assertTrue((SynergyGraph.cardAxisProfile(recursion, ArchetypeFormat.COMMANDER).produces["GRAVEYARD_EXIT"] ?: 0f) > 0f)
        assertTrue((SynergyGraph.cardAxisProfile(spiritMascot, ArchetypeFormat.COMMANDER).consumes["GRAVEYARD_EXIT"] ?: 0f) > 0f)
    }

    @Test
    fun `counter source does not become a payoff and pseudo evasion is capped`() {
        val source = tagged("source", "Counter Maker", "counters_source")
        val payoff = tagged("payoff", "Counter Reward", "counters_payoff", 0.85f)
        val trample = tagged("trample", "Trampling Creature", "pseudo_evasion")

        assertTrue(SectionMembership.predicate("engine:COUNTERS:producers", context)!!(source))
        assertFalse(SectionMembership.predicate("engine:COUNTERS:payoffs", context)!!(source))
        assertTrue(SectionMembership.predicate("engine:COUNTERS:payoffs", context)!!(payoff))
        assertEquals(0.5f, SynergyGraph.cardAxisProfile(trample, ArchetypeFormat.COMMANDER).produces["ATTACK"])
        assertEquals(0.5f, SynergyGraph.cardAxisProfile(trample, ArchetypeFormat.COMMANDER).consumes["ATTACHED"])
    }

    @Test
    fun `Mana Base attribution and Collection browse include a producing land`() {
        val island = card(
            id = "island", name = "Island", typeLine = "Basic Land — Island",
            colorIdentity = emptyList(), producedMana = "U",
        )
        val sectionId = "produces:U"

        assertTrue(SectionMembership.predicate(sectionId, context)!!(island))
        assertTrue(SectionSearchQuery.buildFor(sectionId, context)!!.contains("t:land produces:U"))
        assertTrue(SectionSearchQuery.toAdvancedQuery(sectionId, context)!!.criteria.isNotEmpty())
    }

    @Test
    fun `Plan Role attribution and Wizard browse agree for a real counter producer`() {
        val source = fixture12Atraxa().mainboard.first { it.card.name == "Cathars' Crusade" }.card
        val sectionId = "role:counters_source"
        val whiteContext = context.copy(colorIdentity = setOf(ManaColor.W))
        val member = SectionMembership.predicate(sectionId, whiteContext)!!
        val eligible = SectionSearchQuery.localStructuralGate(whiteContext)

        assertTrue((ArchetypeRoleClassifier.classify(source)["counters_source"] ?: 0f) > 0f)
        assertTrue(member(source) && eligible(source))
        assertTrue(SectionSearchQuery.buildFor(sectionId, whiteContext)!!.contains("+1/+1"))
        assertTrue(SectionSearchQuery.toAdvancedQuery(sectionId, whiteContext)!!.criteria.isNotEmpty())
    }

    @Test
    fun `graveyard exit ideals retain Commander six four and scaled Sixty values`() {
        val commander = SynergyGraph.axisIdeals(ArchetypeFormat.COMMANDER).getValue("GRAVEYARD_EXIT")
        val sixty = SynergyGraph.axisIdeals(ArchetypeFormat.SIXTY).getValue("GRAVEYARD_EXIT")

        assertEquals(6, commander.producerIdeal)
        assertEquals(4, commander.payoffIdeal)
        assertTrue(sixty.producerIdeal in 1..6)
        assertTrue(sixty.payoffIdeal in 1..4)
    }
}
