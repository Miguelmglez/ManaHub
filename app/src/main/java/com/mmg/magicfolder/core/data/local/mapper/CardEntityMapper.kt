package com.mmg.magicfolder.core.data.local.mapper

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mmg.magicfolder.core.data.local.entity.CardEntity
import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.CardTag
import com.mmg.magicfolder.core.domain.model.SuggestedTag
import com.mmg.magicfolder.core.domain.model.TagCategory

private val gson = Gson()
private val listType = object : TypeToken<List<String>>() {}.type

internal fun String.toStringList(): List<String> = gson.fromJson(this, listType) ?: emptyList()
internal fun List<String>.toJsonString(): String  = gson.toJson(this)

// ── Tag persistence ───────────────────────────────────────────────────────────
//
// Stored as a JSON array of `{k, c}` records:  [{"k":"flying","c":"KEYWORD"}]
// Suggested tags add a confidence and source: [{"k":"ramp","c":"ARCHETYPE","p":0.85,"s":"strategy"}]

private data class TagRecord(val k: String, val c: String)
private data class SuggestionRecord(val k: String, val c: String, val p: Float, val s: String)

private val tagListType        = object : TypeToken<List<TagRecord>>() {}.type
private val suggestionListType = object : TypeToken<List<SuggestionRecord>>() {}.type

fun String.toTagList(): List<CardTag> = runCatching {
    val records: List<TagRecord> = gson.fromJson(this, tagListType) ?: emptyList()
    records.mapNotNull { rec ->
        val cat = runCatching { TagCategory.valueOf(rec.c) }.getOrNull() ?: return@mapNotNull null
        CardTag(rec.k, cat)
    }
}.getOrDefault(emptyList())

fun List<CardTag>.toTagsJson(): String =
    gson.toJson(map { TagRecord(it.key, it.category.name) })

fun String.toSuggestedTagList(): List<SuggestedTag> = runCatching {
    val records: List<SuggestionRecord> = gson.fromJson(this, suggestionListType) ?: emptyList()
    records.mapNotNull { rec ->
        val cat = runCatching { TagCategory.valueOf(rec.c) }.getOrNull() ?: return@mapNotNull null
        SuggestedTag(
            tag        = CardTag(rec.k, cat),
            confidence = rec.p,
            source     = rec.s,
        )
    }
}.getOrDefault(emptyList())

fun List<SuggestedTag>.toSuggestedTagsJson(): String =
    gson.toJson(map { SuggestionRecord(it.tag.key, it.tag.category.name, it.confidence, it.source) })

// ── Entity ↔ Domain ───────────────────────────────────────────────────────────

fun CardEntity.toDomainCard(): Card = Card(
    scryfallId = scryfallId,
    name = name,
    manaCost = manaCost,
    cmc = cmc,
    colors = colors.toStringList(),
    colorIdentity = colorIdentity.toStringList(),
    typeLine = typeLine,
    oracleText = oracleText,
    keywords = keywords.toStringList(),
    power = power,
    toughness = toughness,
    loyalty = loyalty,
    setCode = setCode,
    setName = setName,
    collectorNumber = collectorNumber,
    rarity = rarity,
    releasedAt = releasedAt,
    frameEffects = frameEffects.toStringList(),
    promoTypes = promoTypes.toStringList(),
    lang = lang,
    imageNormal = imageNormal,
    imageArtCrop = imageArtCrop,
    imageBackNormal = imageBackNormal,
    priceUsd = priceUsd,
    priceUsdFoil = priceUsdFoil,
    priceEur = priceEur,
    priceEurFoil = priceEurFoil,
    legalityStandard = legalityStandard,
    legalityPioneer = legalityPioneer,
    legalityModern = legalityModern,
    legalityCommander = legalityCommander,
    flavorText = flavorText,
    artist = artist,
    scryfallUri = scryfallUri,
    isStale = isStale,
    staleReason = staleReason,
    cachedAt = cachedAt,
    tags = tags.toTagList(),
    userTags = userTags.toTagList(),
    suggestedTags = suggestedTags.toSuggestedTagList(),
    printedName = printedName,
    printedText = printedText,
    printedTypeLine = printedTypeLine
)

fun Card.toEntityCard(): CardEntity = CardEntity(
    scryfallId = scryfallId,
    name = name,
    lang = lang,
    manaCost = manaCost,
    cmc = cmc,
    colors = colors.toJsonString(),
    colorIdentity = colorIdentity.toJsonString(),
    typeLine = typeLine,
    oracleText = oracleText,
    keywords = keywords.toJsonString(),
    power = power,
    toughness = toughness,
    loyalty = loyalty,
    setCode = setCode,
    setName = setName,
    collectorNumber = collectorNumber,
    rarity = rarity,
    releasedAt = releasedAt,
    frameEffects = frameEffects.toJsonString(),
    promoTypes = promoTypes.toJsonString(),
    imageNormal = imageNormal,
    imageArtCrop = imageArtCrop,
    imageBackNormal = imageBackNormal,
    priceUsd = priceUsd,
    priceUsdFoil = priceUsdFoil,
    priceEur = priceEur,
    priceEurFoil = priceEurFoil,
    legalityStandard = legalityStandard,
    legalityPioneer = legalityPioneer,
    legalityModern = legalityModern,
    legalityCommander = legalityCommander,
    flavorText = flavorText,
    artist = artist,
    scryfallUri = scryfallUri,
    isStale = isStale,
    staleReason = staleReason,
    cachedAt = cachedAt,
    tags = tags.toTagsJson(),
    userTags = userTags.toTagsJson(),
    suggestedTags = suggestedTags.toSuggestedTagsJson(),
    printedName = printedName,
    printedText = printedText,
    printedTypeLine =  printedTypeLine
)

fun List<CardEntity>.toDomainCardList(): List<Card> = map { it.toDomainCard() }
fun List<Card>.toEntityCardList(): List<CardEntity> = map { it.toEntityCard() }
