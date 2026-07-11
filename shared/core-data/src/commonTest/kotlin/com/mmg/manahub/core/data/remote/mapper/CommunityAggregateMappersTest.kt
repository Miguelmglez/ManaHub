package com.mmg.manahub.core.data.remote.mapper

import com.mmg.manahub.core.data.remote.dto.AggregateCardEntryDto
import com.mmg.manahub.core.data.remote.dto.AvgTypeDistributionDto
import com.mmg.manahub.core.data.remote.dto.BuildProgressDto
import com.mmg.manahub.core.data.remote.dto.CommanderAggregateResponseDto
import com.mmg.manahub.core.data.remote.dto.NamedCountDto
import com.mmg.manahub.core.data.remote.dto.SixtyAggregateResponseDto
import com.mmg.manahub.core.data.remote.dto.SixtyDeckSummaryDto
import com.mmg.manahub.core.data.remote.dto.TrendingResponseDto
import com.mmg.manahub.core.model.AggregateSource
import com.mmg.manahub.core.model.CommunityAggregate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Coverage for the Worker-response-DTO -> domain-model mapping (Phase 3.3). `commonMain`/
 * `commonTest` — no Android dependency, runs on every KMP target.
 */
class CommunityAggregateMappersTest {

    @Test
    fun `commander dto maps every field into the domain model, tagging the given source`() {
        val dto = CommanderAggregateResponseDto(
            status = "materialized",
            commander = "Atraxa, Praetors' Voice",
            numDecksSampled = 42455,
            avgTypeDistribution = AvgTypeDistributionDto(land = 35, creature = 24),
            manaCurve = mapOf("3" to 16),
            themeTags = listOf(NamedCountDto("Infect", 4864)),
            similarCommanders = listOf("Atraxa, Grand Unifier"),
            gameChangersCount = 1,
            cards = listOf(AggregateCardEntryDto("Sol Ring", "sol-ring-uid", 0.94f, -0.14f, "gamechangers", 40000)),
            cachedAt = 12345L,
        )

        val domain = dto.toDomain(AggregateSource.WORKER)

        assertEquals("Atraxa, Praetors' Voice", domain.commander)
        assertEquals(42455, domain.numDecksSampled)
        assertEquals(35, domain.avgTypeDistribution.land)
        assertEquals(16, domain.manaCurve["3"])
        assertEquals("Infect", domain.themeTags.first().name)
        assertEquals(listOf("Atraxa, Grand Unifier"), domain.similarCommanders)
        assertEquals(1, domain.gameChangersCount)
        assertEquals("Sol Ring", domain.cards.first().name)
        assertEquals(-0.14f, domain.cards.first().synergy)
        assertEquals(AggregateSource.WORKER, domain.source)
    }

    @Test
    fun `sixty dto with status materialized maps to Sixty Materialized`() {
        val dto = SixtyAggregateResponseDto(
            status = "materialized",
            canonicalKey = "krenko-mob-boss",
            format = 3,
            numDecksSampled = 20,
            deckSummaries = listOf(SixtyDeckSummaryDto(id = 1, name = "Deck", owner = "u", viewCount = 5)),
            colorProfile = mapOf("R" to 20),
            cards = listOf(AggregateCardEntryDto("Krenko, Mob Boss", null, 1f, 0.5f, "mainboard", 20)),
            cachedAt = 999L,
        )

        val domain = dto.toDomain(AggregateSource.CACHE)

        val materialized = assertIs<CommunityAggregate.Sixty.Materialized>(domain)
        assertEquals("krenko-mob-boss", materialized.canonicalKey)
        assertEquals(3, materialized.format)
        assertEquals(20, materialized.numDecksSampled)
        assertEquals(1, materialized.deckSummaries.size)
        assertEquals(AggregateSource.CACHE, materialized.source)
    }

    @Test
    fun `sixty dto with status building maps to Sixty Building, ignoring materialized-only fields`() {
        val dto = SixtyAggregateResponseDto(
            status = "building",
            canonicalKey = "krenko-mob-boss",
            progress = BuildProgressDto(collected = 5, target = 20),
        )

        val domain = dto.toDomain(AggregateSource.WORKER)

        val building = assertIs<CommunityAggregate.Sixty.Building>(domain)
        assertEquals("krenko-mob-boss", building.canonicalKey)
        assertEquals(5, building.collected)
        assertEquals(20, building.target)
    }

    @Test
    fun `sixty dto building with null progress defaults to zero, zero`() {
        val dto = SixtyAggregateResponseDto(status = "building", canonicalKey = "k", progress = null)
        val building = assertIs<CommunityAggregate.Sixty.Building>(dto.toDomain(AggregateSource.WORKER))
        assertEquals(0, building.collected)
        assertEquals(0, building.target)
    }

    @Test
    fun `trending dto maps top commanders and top cards`() {
        val dto = TrendingResponseDto(
            week = "2026-W28",
            topCommanders = listOf(NamedCountDto("Atraxa, Praetors' Voice", 12)),
            topCards = listOf(NamedCountDto("Sol Ring", 30)),
        )
        val domain = dto.toDomain()
        assertEquals("2026-W28", domain.week)
        assertEquals("Atraxa, Praetors' Voice", domain.topCommanders.first().name)
        assertEquals(30, domain.topCards.first().count)
    }
}
