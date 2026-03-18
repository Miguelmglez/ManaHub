package com.mmg.magicfolder.core.data.local.mapper

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mmg.magicfolder.core.data.local.entity.CardEntity
import com.mmg.magicfolder.core.domain.model.Card

private val gson = Gson()
private val listType = object : TypeToken<List<String>>() {}.type

internal fun String.toStringList(): List<String> = gson.fromJson(this, listType) ?: emptyList()
internal fun List<String>.toJsonString(): String = gson.toJson(this)

fun CardEntity.toDomainCard(): Card = Card(
    scryfallId        = scryfallId,
    name              = name,
    manaCost          = manaCost,
    cmc               = cmc,
    colors            = colors.toStringList(),
    colorIdentity     = colorIdentity.toStringList(),
    typeLine          = typeLine,
    oracleText        = oracleText,
    keywords          = keywords.toStringList(),
    power             = power,
    toughness         = toughness,
    loyalty           = loyalty,
    setCode           = setCode,
    setName           = setName,
    collectorNumber   = collectorNumber,
    rarity            = rarity,
    releasedAt        = releasedAt,
    frameEffects      = frameEffects.toStringList(),
    promoTypes        = promoTypes.toStringList(),
    lang              = lang,
    imageNormal       = imageNormal,
    imageArtCrop      = imageArtCrop,
    imageBackNormal   = imageBackNormal,
    priceUsd          = priceUsd,
    priceUsdFoil      = priceUsdFoil,
    priceEur          = priceEur,
    priceEurFoil      = priceEurFoil,
    legalityStandard  = legalityStandard,
    legalityPioneer   = legalityPioneer,
    legalityModern    = legalityModern,
    legalityCommander = legalityCommander,
    flavorText        = flavorText,
    artist            = artist,
    scryfallUri       = scryfallUri,
    isStale           = isStale,
    staleReason       = staleReason,
    cachedAt          = cachedAt,
)

fun Card.toEntityCard(): CardEntity = CardEntity(
    scryfallId        = scryfallId,
    name              = name,
    lang              = lang,
    manaCost          = manaCost,
    cmc               = cmc,
    colors            = colors.toJsonString(),
    colorIdentity     = colorIdentity.toJsonString(),
    typeLine          = typeLine,
    oracleText        = oracleText,
    keywords          = keywords.toJsonString(),
    power             = power,
    toughness         = toughness,
    loyalty           = loyalty,
    setCode           = setCode,
    setName           = setName,
    collectorNumber   = collectorNumber,
    rarity            = rarity,
    releasedAt        = releasedAt,
    frameEffects      = frameEffects.toJsonString(),
    promoTypes        = promoTypes.toJsonString(),
    imageNormal       = imageNormal,
    imageArtCrop      = imageArtCrop,
    imageBackNormal   = imageBackNormal,
    priceUsd          = priceUsd,
    priceUsdFoil      = priceUsdFoil,
    priceEur          = priceEur,
    priceEurFoil      = priceEurFoil,
    legalityStandard  = legalityStandard,
    legalityPioneer   = legalityPioneer,
    legalityModern    = legalityModern,
    legalityCommander = legalityCommander,
    flavorText        = flavorText,
    artist            = artist,
    scryfallUri       = scryfallUri,
    isStale           = isStale,
    staleReason       = staleReason,
    cachedAt          = cachedAt,
)

fun List<CardEntity>.toDomainCardList(): List<Card> = map { it.toDomainCard() }
fun List<Card>.toEntityCardList(): List<CardEntity> = map { it.toEntityCard() }
