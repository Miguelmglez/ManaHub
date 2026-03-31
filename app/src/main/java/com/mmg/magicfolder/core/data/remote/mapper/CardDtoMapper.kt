package com.mmg.magicfolder.core.data.remote.mapper

import com.mmg.magicfolder.core.data.local.entity.CardEntity
import com.mmg.magicfolder.core.data.remote.dto.CardDto
import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.data.local.mapper.toEntityCard

fun CardDto.toDomain(): Card {
    val front = cardFaces?.firstOrNull()
    val back  = cardFaces?.getOrNull(1)
    return Card(
        scryfallId = id,
        name = name,
        manaCost = manaCost ?: front?.manaCost,
        cmc = cmc,
        colors = colors ?: emptyList(),
        colorIdentity = colorIdentity,
        typeLine = typeLine,
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
        flavorText = flavorText,
        artist = artist,
        scryfallUri = scryfallUri,
        isStale = false,
        staleReason = null,
        cachedAt = System.currentTimeMillis(),
        printedName = printedName?:"",
        printedText = printedText?:"",
        printedTypeLine = printedTypeLine?:""
    )
}

fun CardDto.toEntity(): CardEntity         = toDomain().toEntityCard()
fun List<CardDto>.toDomain(): List<Card>   = map { it.toDomain() }
fun List<CardDto>.toEntity(): List<CardEntity> = map { it.toEntity() }
