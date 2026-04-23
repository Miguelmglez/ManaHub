package com.mmg.manahub.feature.deckmagic.engine

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DeckFormat
import com.mmg.manahub.feature.decks.DeckSlotEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckMagicEngineTest {

    private val engine = DeckMagicEngine()

    @Test
    fun `test analyzeDeck with low land count returns weakness`() = runBlocking {
        // Standard deck usually wants ~24 lands. Let's give it 18.
        val cards = List(18) { createLandSlot() } + List(42) { createNonLandSlot() }
        
        val report = engine.analyzeDeck(cards, emptyList(), DeckFormat.STANDARD)
        
        assertTrue(report.weaknesses.any { it.labelResId == com.mmg.manahub.R.string.deck_improve_lands_few })
    }

    @Test
    fun `test analyzeDeck with high mana curve returns weakness`() = runBlocking {
        // High CMC cards
        val cards = List(24) { createLandSlot() } + List(36) { createNonLandSlot(cmc = 5.0) }
        
        val report = engine.analyzeDeck(cards, emptyList(), DeckFormat.STANDARD)
        
        assertTrue(report.weaknesses.any { it.labelResId == com.mmg.manahub.R.string.deck_improve_mana_curve_high })
    }

    @Test
    fun `test analyzeDeck with good interaction returns strength`() = runBlocking {
        // Many removal cards
        val cards = List(24) { createLandSlot() } + 
                    List(10) { createNonLandSlot(tags = listOf("removal")) } +
                    List(26) { createNonLandSlot() }
        
        val report = engine.analyzeDeck(cards, emptyList(), DeckFormat.STANDARD)
        
        assertTrue(report.strengths.any { it.labelResId == com.mmg.manahub.R.string.deck_improve_interaction_good })
    }

    private fun createLandSlot() = DeckSlotEntry(
        scryfallId = "land",
        quantity = 1,
        isSideboard = false,
        card = createCard(typeLine = "Basic Land", cmc = 0.0)
    )

    private fun createNonLandSlot(cmc: Double = 2.0, tags: List<String> = emptyList()) = DeckSlotEntry(
        scryfallId = "card_${cmc}_${tags.joinToString()}",
        quantity = 1,
        isSideboard = false,
        card = createCard(typeLine = "Creature", cmc = cmc, tags = tags)
    )

    private fun createCard(typeLine: String, cmc: Double, tags: List<String> = emptyList()) = Card(
        scryfallId = "id",
        name = "Name",
        printedName = null,
        manaCost = null,
        cmc = cmc,
        colors = emptyList(),
        colorIdentity = emptyList(),
        typeLine = typeLine,
        printedTypeLine = null,
        oracleText = null,
        printedText = null,
        keywords = emptyList(),
        power = null,
        toughness = null,
        loyalty = null,
        setCode = "set",
        setName = "Set",
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
        scryfallUri = "",
        gameChanger = false,
        isStale = false,
        staleReason = null,
        cachedAt = 0,
        tags = tags.map { com.mmg.manahub.core.domain.model.CardTag(it, com.mmg.manahub.core.domain.model.TagCategory.ROLE) },
        userTags = emptyList(),
        suggestedTags = emptyList()
    )
}
