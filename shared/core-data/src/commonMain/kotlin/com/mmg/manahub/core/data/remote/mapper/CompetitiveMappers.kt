package com.mmg.manahub.core.data.remote.mapper

import com.mmg.manahub.core.data.remote.dto.ArchetypeShareDto
import com.mmg.manahub.core.data.remote.dto.DecklistCardDto
import com.mmg.manahub.core.data.remote.dto.LimitedCardRatingDto
import com.mmg.manahub.core.data.remote.dto.LimitedRatingsSnapshotDto
import com.mmg.manahub.core.data.remote.dto.MetaSnapshotDto
import com.mmg.manahub.core.data.remote.dto.NormalizedDecklistDto
import com.mmg.manahub.core.data.remote.dto.SourceStatusDto
import com.mmg.manahub.core.data.remote.dto.TrendingCardDto
import com.mmg.manahub.core.model.ArchetypeShare
import com.mmg.manahub.core.model.DecklistCard
import com.mmg.manahub.core.model.LimitedCardRating
import com.mmg.manahub.core.model.LimitedRatingsSnapshot
import com.mmg.manahub.core.model.MetaSnapshot
import com.mmg.manahub.core.model.RepresentativeDeck
import com.mmg.manahub.core.model.SourceStatus
import com.mmg.manahub.core.model.TrendingCard

fun SourceStatusDto.toDomain(): SourceStatus = SourceStatus(
    name = name,
    active = active,
    reason = reason,
)

fun DecklistCardDto.toDomain(): DecklistCard = DecklistCard(name = name, quantity = quantity)

fun NormalizedDecklistDto.toDomain(): RepresentativeDeck = RepresentativeDeck(
    source = source,
    eventName = eventName,
    eventUrl = eventUrl,
    player = player,
    format = format,
    result = result,
    colors = colors,
    cards = cards.map { it.toDomain() },
)

fun ArchetypeShareDto.toDomain(): ArchetypeShare = ArchetypeShare(
    key = key,
    label = label,
    colors = colors,
    keyCards = keyCards,
    deckCount = deckCount,
    metaSharePct = metaSharePct,
    deltaPct = deltaPct,
    representativeDeck = representativeDeck?.toDomain(),
)

fun TrendingCardDto.toDomain(): TrendingCard = TrendingCard(
    name = name,
    playRatePct = playRatePct,
    deltaPct = deltaPct,
)

/**
 * Maps the raw Worker response to the domain snapshot. An empty [MetaSnapshotDto.archetypes]
 * list (the expected v1 state while MTGO/TopDeck/Spicerack sources are in skip-mode) maps
 * straight through to an empty domain list — this is intentionally NOT special-cased as an
 * error anywhere in this mapping.
 */
fun MetaSnapshotDto.toDomain(): MetaSnapshot = MetaSnapshot(
    format = format,
    week = week,
    numDecksSampled = numDecksSampled,
    archetypes = archetypes.map { it.toDomain() },
    trendingCards = trendingCards.map { it.toDomain() },
    sources = sources.map { it.toDomain() },
    attribution = attribution,
    cachedAt = cachedAt,
)

fun LimitedCardRatingDto.toDomain(): LimitedCardRating = LimitedCardRating(
    name = name,
    color = color,
    rarity = rarity,
    avgSeen = avgSeen,
    avgPick = avgPick,
    playRatePct = playRatePct,
    gpWinRatePct = gpWinRatePct,
    ohWinRatePct = ohWinRatePct,
    gdWinRatePct = gdWinRatePct,
    gihWinRatePct = gihWinRatePct,
    iwdPct = iwdPct,
    sampleSize = sampleSize,
    imageUrl = imageUrl,
)

fun LimitedRatingsSnapshotDto.toDomain(): LimitedRatingsSnapshot = LimitedRatingsSnapshot(
    expansion = expansion,
    format = format,
    cards = cards.map { it.toDomain() },
    attribution = attribution,
    cachedAt = cachedAt,
)
