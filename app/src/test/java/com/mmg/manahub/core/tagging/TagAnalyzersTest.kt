package com.mmg.manahub.core.tagging

import com.mmg.manahub.core.domain.model.Card
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagAnalyzersTest {

    private fun createCard(typeLine: String, oracleText: String? = null): Card {
        return Card(
            scryfallId = "test",
            name = "Test Card",
            printedName = null,
            manaCost = null,
            cmc = 0.0,
            colors = emptyList(),
            colorIdentity = emptyList(),
            typeLine = typeLine,
            printedTypeLine = null,
            oracleText = oracleText,
            printedText = null,
            keywords = emptyList(),
            power = null,
            toughness = null,
            loyalty = null,
            setCode = "TST",
            setName = "Test Set",
            collectorNumber = "1",
            rarity = "common",
            releasedAt = "2024-01-01",
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
            scryfallUri = "https://test.com"
        )
    }

    @Test
    fun `TypeLineAnalyzer combines Basic and Land into basic_land`() {
        val forest = createCard("Basic Land — Forest")
        val tags = TypeLineAnalyzer.analyze(forest)
        
        // Should have basic_land and forest
        assertTrue(tags.any { it.tag.key == "basic_land" })
        assertTrue(tags.any { it.tag.key == "forest" })
        
        // Should NOT have basic or land as separate tags
        assertFalse(tags.any { it.tag.key == "basic" })
        assertFalse(tags.any { it.tag.key == "land" })
    }

    @Test
    fun `StrategyAnalyzer excludes ramp tag for Basic Lands`() {
        // Basic lands often have oracle text that triggers "ramp" pattern "add {"
        val forest = createCard("Basic Land — Forest", "({T}: Add {G}.)")
        val tags = StrategyAnalyzer.analyze(forest)
        
        assertFalse("Basic Lands should not be tagged as ramp", tags.any { it.tag.key == "ramp" })
    }

    @Test
    fun `StrategyAnalyzer still tags ramp for non-Basic Lands`() {
        val rampantGrowth = createCard("Sorcery", "Search your library for a basic land card...")
        val tags = StrategyAnalyzer.analyze(rampantGrowth)
        
        assertTrue("Non-basic land with ramp text should be tagged as ramp", tags.any { it.tag.key == "ramp" })
    }
}
