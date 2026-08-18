package com.mmg.manahub.core.data.remote.mapper

import com.mmg.manahub.core.data.remote.dto.ArchetypeShareDto
import com.mmg.manahub.core.data.remote.dto.DecklistCardDto
import com.mmg.manahub.core.data.remote.dto.LimitedCardRatingDto
import com.mmg.manahub.core.data.remote.dto.LimitedRatingsSnapshotDto
import com.mmg.manahub.core.data.remote.dto.MetaSnapshotDto
import com.mmg.manahub.core.data.remote.dto.NormalizedDecklistDto
import com.mmg.manahub.core.data.remote.dto.SourceStatusDto
import com.mmg.manahub.core.data.remote.dto.TrendingCardDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage for the `manahub-competitive` Worker-response-DTO -> domain-model mapping
 * (Competitive feature, Phase 3). `commonMain`/`commonTest` — no Android dependency, runs on
 * every KMP target.
 */
class CompetitiveMappersTest {

    @Test
    fun `meta snapshot dto maps every field into the domain model`() {
        val dto = MetaSnapshotDto(
            format = "modern",
            week = "2026-W32",
            numDecksSampled = 250,
            archetypes = listOf(
                ArchetypeShareDto(
                    key = "izzet-murktide",
                    label = "Izzet Murktide",
                    colors = listOf("U", "R"),
                    keyCards = listOf("Murktide Regent"),
                    deckCount = 30,
                    metaSharePct = 12.0,
                    deltaPct = 1.5,
                    representativeDeck = NormalizedDecklistDto(
                        source = "mtgo",
                        eventName = "Modern Challenge",
                        eventUrl = "https://mtgo.com/example",
                        player = "Player1",
                        format = "modern",
                        result = "5-0",
                        colors = listOf("U", "R"),
                        cards = listOf(DecklistCardDto("Murktide Regent", 4)),
                    ),
                ),
            ),
            trendingCards = listOf(TrendingCardDto("Ragavan, Nimble Pilferer", 45.0, -2.0)),
            sources = listOf(SourceStatusDto("mtgo", true, null), SourceStatusDto("topdeck", false, "TOPDECK_API_KEY not configured")),
            attribution = listOf("Data: MTGO"),
            cachedAt = 12345L,
        )

        val domain = dto.toDomain()

        assertEquals("modern", domain.format)
        assertEquals("2026-W32", domain.week)
        assertEquals(250, domain.numDecksSampled)
        assertEquals(1, domain.archetypes.size)
        val archetype = domain.archetypes.first()
        assertEquals("izzet-murktide", archetype.key)
        assertEquals(12.0, archetype.metaSharePct)
        assertEquals(1.5, archetype.deltaPct)
        assertEquals("Murktide Regent", archetype.representativeDeck?.cards?.first()?.name)
        assertEquals(4, archetype.representativeDeck?.cards?.first()?.quantity)
        assertEquals("Ragavan, Nimble Pilferer", domain.trendingCards.first().name)
        assertEquals(2, domain.sources.size)
        assertTrue(domain.sources.first().active)
        assertEquals("TOPDECK_API_KEY not configured", domain.sources[1].reason)
        assertEquals(12345L, domain.cachedAt)
    }

    @Test
    fun `meta snapshot dto with empty archetypes maps to an empty list, not an error`() {
        val dto = MetaSnapshotDto(
            format = "vintage",
            week = "2026-W32",
            archetypes = emptyList(),
            sources = listOf(SourceStatusDto("mtgo", false, "DAYBREAK_SERVICE_ID not configured")),
        )

        val domain = dto.toDomain()

        assertTrue(domain.archetypes.isEmpty())
        assertEquals(1, domain.sources.size)
    }

    @Test
    fun `archetype dto with null representativeDeck and null deltaPct maps to null domain fields`() {
        val dto = ArchetypeShareDto(key = "k", label = "L", deckCount = 1, metaSharePct = 5.0)
        val domain = dto.toDomain()
        assertNull(domain.deltaPct)
        assertNull(domain.representativeDeck)
    }

    @Test
    fun `limited ratings snapshot dto maps every field into the domain model`() {
        val dto = LimitedRatingsSnapshotDto(
            attribution = "Data: 17Lands.com",
            expansion = "BLB",
            format = "PremierDraft",
            cards = listOf(
                LimitedCardRatingDto(
                    name = "Sample Card",
                    color = "G",
                    rarity = "rare",
                    avgSeen = 3.2,
                    avgPick = 2.1,
                    playRatePct = 55.0,
                    gpWinRatePct = 58.3,
                    ohWinRatePct = 60.1,
                    gdWinRatePct = 59.0,
                    gihWinRatePct = 61.2,
                    iwdPct = 3.5,
                    sampleSize = 4200,
                    imageUrl = "https://cards.scryfall.io/example.jpg",
                ),
            ),
            cachedAt = 999L,
        )

        val domain = dto.toDomain()

        assertEquals("BLB", domain.expansion)
        assertEquals("PremierDraft", domain.format)
        assertEquals("Data: 17Lands.com", domain.attribution)
        assertEquals(999L, domain.cachedAt)
        val card = domain.cards.first()
        assertEquals("Sample Card", card.name)
        assertEquals(3.2, card.avgSeen)
        assertEquals(4200, card.sampleSize)
        assertEquals("https://cards.scryfall.io/example.jpg", card.imageUrl)
    }

    @Test
    fun `limited card rating dto with all-null optional stats maps to all-null domain fields`() {
        val dto = LimitedCardRatingDto(name = "Land", color = "", rarity = "common")
        val domain = dto.toDomain()

        assertNull(domain.avgSeen)
        assertNull(domain.avgPick)
        assertNull(domain.playRatePct)
        assertNull(domain.gpWinRatePct)
        assertNull(domain.sampleSize)
        assertNull(domain.imageUrl)
    }
}
