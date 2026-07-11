package com.mmg.manahub.core.data.local.mapper

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mmg.manahub.core.data.local.entity.DeckEntity
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckSlot
import com.mmg.manahub.core.model.DeckWithCards
import com.mmg.manahub.core.data.local.dao.DeckWithCards as DeckWithCardsEntity

/** Shared JSON codec for the `themes_override` column (Deck Doctor Phase 1.5, D2). */
private val themesOverrideGson = Gson()
private val themesOverrideListType = object : TypeToken<List<String>>() {}.type

/** `["TRIBAL","ARISTOCRATS"]` (or null/blank) -> a parsed list; never throws on malformed JSON. */
private fun decodeThemesOverride(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        themesOverrideGson.fromJson<List<String>>(json, themesOverrideListType) ?: emptyList()
    }.getOrDefault(emptyList())
}

/** An empty list encodes to `null` (matches the "no pin" nullable-column convention). */
private fun encodeThemesOverride(themes: List<String>): String? =
    if (themes.isEmpty()) null else themesOverrideGson.toJson(themes)

fun DeckEntity.toDomainDeck(): Deck = Deck(
    id = id,
    userId = userId,
    name = name,
    description = description,
    format = format,
    coverCardId = coverCardId,
    commanderCardId = commanderCardId,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sourceUrl = sourceUrl,
    sourceAuthor = sourceAuthor,
    sourceService = sourceService,
    importedAt = importedAt,
    archetypeOverride = archetypeOverride,
    themesOverride = decodeThemesOverride(themesOverride),
)

fun DeckWithCardsEntity.toDomainDeckWithCards(): DeckWithCards = DeckWithCards(
    deck = deck.toDomainDeck(),
    mainboard = cards.filter { !it.isSideboard }.map { DeckSlot(it.scryfallId, it.quantity) },
    sideboard = cards.filter { it.isSideboard }.map { DeckSlot(it.scryfallId, it.quantity) },
)

fun Deck.toEntity(): DeckEntity = DeckEntity(
    id = id,
    userId = userId,
    name = name,
    description = description,
    format = format,
    coverCardId = coverCardId,
    commanderCardId = commanderCardId,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sourceUrl = sourceUrl,
    sourceAuthor = sourceAuthor,
    sourceService = sourceService,
    importedAt = importedAt,
    archetypeOverride = archetypeOverride,
    themesOverride = encodeThemesOverride(themesOverride),
)
