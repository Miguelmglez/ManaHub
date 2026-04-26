package com.mmg.manahub.core.domain.usecase.decks

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DeckCard
import com.mmg.manahub.core.domain.model.DeckFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class BasicLandCalculatorTest {

    private fun createCard(scryfallId: String, name: String, manaCost: String?, colorIdentity: List<String>, typeLine: String = "Creature"): Card {
        return Card(
            scryfallId = scryfallId,
            name = name,
            printedName = null,
            manaCost = manaCost,
            cmc = 0.0,
            colors = emptyList(),
            colorIdentity = colorIdentity,
            typeLine = typeLine,
            printedTypeLine = null,
            oracleText = null,
            printedText = null,
            keywords = emptyList(),
            power = null,
            toughness = null,
            loyalty = null,
            setCode = "SET",
            setName = "Set Name",
            collectorNumber = "1",
            rarity = "common",
            releasedAt = "2023-01-01",
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
            scryfallUri = "uri",
            cachedAt = 0,
            isStale = false,
            staleReason = null,
            tags = emptyList(),
            userTags = emptyList(),
            suggestedTags = emptyList(),
            relatedUris = emptyMap(),
            purchaseUris = emptyMap(),
            gameChanger = false
        )
    }

    @Test
    fun `standard calculation works correctly`() {
        val cards = listOf(
            DeckCard(createCard("1", "White Card", "{W}", listOf("W")), quantity = 10),
            DeckCard(createCard("2", "Blue Card", "{U}", listOf("U")), quantity = 10)
        )
        val result = BasicLandCalculator.calculate(cards, emptyList(), DeckFormat.STANDARD)
        
        assertEquals(12, result.plains)
        assertEquals(12, result.islands)
        assertEquals(0, result.swamps)
        assertEquals(0, result.mountains)
        assertEquals(0, result.forests)
    }

    @Test
    fun `commander calculation filters off-color cards`() {
        val commanderIdentity = setOf("W", "U")
        val cards = listOf(
            DeckCard(createCard("1", "White Card", "{W}", listOf("W")), quantity = 10),
            DeckCard(createCard("2", "Blue Card", "{U}", listOf("U")), quantity = 10),
            DeckCard(createCard("3", "Red Card", "{R}", listOf("R")), quantity = 10) // Should be ignored
        )
        // Commander target land count is 37
        val result = BasicLandCalculator.calculate(cards, emptyList(), DeckFormat.COMMANDER, commanderIdentity)
        
        // Weights: W=10, U=10. Total Weight=20.
        // Slots: 37.
        // Plains: (10/20)*37 = 18.5 -> 19
        // Islands: 37 - 19 = 18
        assertEquals(19, result.plains)
        assertEquals(18, result.islands)
        assertEquals(0, result.mountains) // Red card was ignored
    }

    @Test
    fun `fallback to equal distribution when no weights but identity exists`() {
        val commanderIdentity = setOf("W", "G")
        val cards = emptyList<DeckCard>()
        val result = BasicLandCalculator.calculate(cards, emptyList(), DeckFormat.COMMANDER, commanderIdentity)
        
        // 37 slots / 2 colors = 18 each + 1 remainder
        assertEquals(19, result.plains)
        assertEquals(18, result.forests)
    }
    
    @Test
    fun `calculation with multi-mana cards`() {
        val cards = listOf(
            DeckCard(createCard("1", "UW Card", "{W}{U}", listOf("W", "U")), quantity = 10)
        )
        val result = BasicLandCalculator.calculate(cards, emptyList(), DeckFormat.STANDARD)
        
        // 24 slots, weights W=10, U=10.
        assertEquals(12, result.plains)
        assertEquals(12, result.islands)
    }
}
