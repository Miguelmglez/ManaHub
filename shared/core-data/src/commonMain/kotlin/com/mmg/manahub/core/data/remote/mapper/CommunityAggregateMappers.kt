package com.mmg.manahub.core.data.remote.mapper

import com.mmg.manahub.core.data.remote.dto.AggregateCardEntryDto
import com.mmg.manahub.core.data.remote.dto.CommanderAggregateResponseDto
import com.mmg.manahub.core.data.remote.dto.NamedCountDto
import com.mmg.manahub.core.data.remote.dto.SixtyAggregateResponseDto
import com.mmg.manahub.core.data.remote.dto.TrendingResponseDto
import com.mmg.manahub.core.model.AggregateCardEntry
import com.mmg.manahub.core.model.AggregateSource
import com.mmg.manahub.core.model.AvgTypeDistribution
import com.mmg.manahub.core.model.CommunityAggregate
import com.mmg.manahub.core.model.NamedCount
import com.mmg.manahub.core.model.SixtyDeckSummary
import com.mmg.manahub.core.model.TrendingSnapshot

fun AggregateCardEntryDto.toDomain(): AggregateCardEntry = AggregateCardEntry(
    name = name,
    scryfallUid = scryfallUid,
    inclusionPct = inclusionPct,
    synergy = synergy,
    category = category,
    numDecks = numDecks,
)

fun NamedCountDto.toDomain(): NamedCount = NamedCount(name, count)

fun CommanderAggregateResponseDto.toDomain(source: AggregateSource): CommunityAggregate.Commander =
    CommunityAggregate.Commander(
        commander = commander,
        numDecksSampled = numDecksSampled,
        avgTypeDistribution = AvgTypeDistribution(
            creature = avgTypeDistribution.creature,
            instant = avgTypeDistribution.instant,
            sorcery = avgTypeDistribution.sorcery,
            artifact = avgTypeDistribution.artifact,
            enchantment = avgTypeDistribution.enchantment,
            battle = avgTypeDistribution.battle,
            planeswalker = avgTypeDistribution.planeswalker,
            land = avgTypeDistribution.land,
        ),
        manaCurve = manaCurve,
        themeTags = themeTags.map { it.toDomain() },
        similarCommanders = similarCommanders,
        gameChangersCount = gameChangersCount,
        cards = cards.map { it.toDomain() },
        cachedAt = cachedAt,
        source = source,
    )

fun SixtyAggregateResponseDto.toDomain(source: AggregateSource): CommunityAggregate.Sixty =
    if (status == "building") {
        CommunityAggregate.Sixty.Building(
            canonicalKey = canonicalKey,
            collected = progress?.collected ?: 0,
            target = progress?.target ?: 0,
        )
    } else {
        CommunityAggregate.Sixty.Materialized(
            canonicalKey = canonicalKey,
            format = format,
            numDecksSampled = numDecksSampled,
            deckSummaries = deckSummaries.map {
                SixtyDeckSummary(
                    archidektId = it.id,
                    name = it.name,
                    owner = it.owner,
                    viewCount = it.viewCount,
                    colors = it.colors,
                    edhBracket = it.edhBracket,
                )
            },
            colorProfile = colorProfile,
            cards = cards.map { it.toDomain() },
            cachedAt = cachedAt,
            source = source,
        )
    }

fun TrendingResponseDto.toDomain(): TrendingSnapshot = TrendingSnapshot(
    week = week,
    topCommanders = topCommanders.map { it.toDomain() },
    topCards = topCards.map { it.toDomain() },
)
