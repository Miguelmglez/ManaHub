package com.mmg.manahub.core.data.remote.mapper

import com.mmg.manahub.core.data.remote.dto.CardDto
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardFace
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * D14: canonical WUBRG letter order used everywhere a compact colour-subset string is
 * persisted (`Card.producedMana`, and mirrored by `Card.colors`/`colorIdentity` call sites
 * that want a stable display order).
 */
private val WUBRG_ORDER = listOf("W", "U", "B", "R", "G")

/**
 * Collapses a Scryfall colour-letter list (e.g. `["U", "W"]`) into a compact, canonically
 * ordered subset string (`"WU"`) — NOT a JSON blob. Unknown/malformed letters are dropped
 * defensively rather than propagated, since this string is later re-parsed by pip/production
 * matching (Deck Doctor mana-base analysis, Phase 1).
 */
private fun List<String>?.toCompactWubrg(): String {
    if (this.isNullOrEmpty()) return ""
    val present = this.map { it.uppercase() }.toSet()
    return WUBRG_ORDER.filter { it in present }.joinToString("")
}

@OptIn(ExperimentalTime::class)
fun CardDto.toDomain(): Card {
    val front = cardFaces?.firstOrNull()
    val back  = cardFaces?.getOrNull(1)
    return Card(
        scryfallId = id,
        name = name,
        manaCost = manaCost ?: front?.manaCost,
        cmc = cmc ?: 0.0,
        colors = colors ?: emptyList(),
        colorIdentity = colorIdentity,
        typeLine = typeLine ?: front?.typeLine ?: "",
        oracleText = oracleText ?: front?.oracleText,
        keywords = keywords,
        power = power ?: front?.power,
        toughness = toughness ?: front?.toughness,
        loyalty = loyalty,
        setCode = setCode,
        setName = setName,
        collectorNumber = collectorNumber,
        rarity = rarity,
        releasedAt = releasedAt,
        frameEffects = frameEffects ?: emptyList(),
        promoTypes = promoTypes ?: emptyList(),
        lang = lang,
        imageNormal = imageUris?.normal ?: front?.imageUris?.normal,
        imageArtCrop = imageUris?.artCrop ?: front?.imageUris?.artCrop,
        imageBackNormal = back?.imageUris?.normal,
        priceUsd = prices.usd?.toDoubleOrNull(),
        priceUsdFoil = prices.usdFoil?.toDoubleOrNull(),
        priceEur = prices.eur?.toDoubleOrNull(),
        priceEurFoil = prices.eurFoil?.toDoubleOrNull(),
        legalityStandard = legalities.standard,
        legalityPioneer = legalities.pioneer,
        legalityModern = legalities.modern,
        legalityCommander = legalities.commander,
        legalityLegacy = legalities.legacy,
        legalityVintage = legalities.vintage,
        legalityPauper = legalities.pauper,
        flavorText = flavorText,
        artist = artist,
        scryfallUri = scryfallUri,
        isStale = false,
        staleReason = null,
        cachedAt = Clock.System.now().toEpochMilliseconds(),
        printedName = printedName,
        printedText = printedText,
        printedTypeLine = printedTypeLine,
        relatedUris = relatedUris ?: emptyMap(),
        purchaseUris = purchaseUris ?: emptyMap(),
        gameChanger = gameChanger ?: false,
        edhrecRank = edhrecRank,
        pennyRank = pennyRank,
        cardFaces = cardFaces?.map { face ->
            CardFace(
                name = face.name,
                printedName = face.printedName,
                manaCost = face.manaCost,
                typeLine = face.typeLine,
                oracleText = face.oracleText,
                power = face.power,
                toughness = face.toughness,
                loyalty = face.loyalty,
                defense = face.defense,
                flavorText = face.flavorText,
                imageNormal = face.imageUris?.normal,
                imageArtCrop = face.imageUris?.artCrop,
            )
        },
        producedMana = producedMana.toCompactWubrg(),
        // Card Versions & Languages, Phase 1A: root oracle_id first, falling back to the front
        // face's (reversible cards sometimes carry it only per-face), else "" (pre-v46 fallback
        // is an exact match on `name`, which is always the English oracle name — see Card.oracleId).
        oracleId = oracleId ?: front?.oracleId ?: "",
    )
}

fun List<CardDto>.toDomain(): List<Card> = map { it.toDomain() }
