package com.mmg.manahub.core.data.remote.mapper

import com.mmg.manahub.core.data.remote.dto.CardDto
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardFace
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
    )
}

fun List<CardDto>.toDomain(): List<Card> = map { it.toDomain() }
