package com.mmg.magicfolder.code.core.data.remote.mapper


import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mmg.magicfolder.code.core.data.local.entity.CardEntity
import com.mmg.magicfolder.code.core.domain.model.Card

private val gson = Gson()
private val listType = object : TypeToken>() {}.type

internal fun String.toStringList(): List = gson.fromJson(this, listType) ?: emptyList()
internal fun List.toJson(): String = gson.toJson(this)

fun CardEntity.toDomain(): Card = Card(
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

fun Card.toEntity(): CardEntity = CardEntity(
    scryfallId        = scryfallId,
    name              = name,
    lang              = lang,
    manaCost          = manaCost,
    cmc               = cmc,
    colors            = colors.toJson(),
    colorIdentity     = colorIdentity.toJson(),
    typeLine          = typeLine,
    oracleText        = oracleText,
    keywords          = keywords.toJson(),
    power             = power,
    toughness         = toughness,
    loyalty           = loyalty,
    setCode           = setCode,
    setName           = setName,
    collectorNumber   = collectorNumber,
    rarity            = rarity,
    releasedAt        = releasedAt,
    frameEffects      = frameEffects.toJson(),
    promoTypes        = promoTypes.toJson(),
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

fun List.toDomain(): List       = map { it.toDomain() }
fun List.toEntity():       List  = map { it.toEntity() }