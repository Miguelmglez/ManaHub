package com.mmg.manahub.core.domain.model

data class CollectionStats(
    val totalCards:        Int,
    val uniqueCards:       Int,
    val totalDecks:        Int,
    val totalValueUsd:     Double,
    val totalValueEur:     Double,
    val mostValuableCards: List<CardValue>,
    val byColor:           Map<MtgColor, Int>,
    val byRarity:          Map<Rarity, Int>,
    val byType:            Map<CardType, Int>,
    val cmcDistribution:   Map<Int, Int>,
    val bySet:             Map<String, Int>,
    // Innovative Stats
    val totalFoil:         Int     = 0,
    val totalFullArt:      Int     = 0,
    val topArtist:         String? = null,
    val topArtistCount:    Int     = 0,
    val avgManaValue:      Double  = 0.0,
    val avgPower:          Double? = null,
    val avgToughness:      Double? = null,
    val oldestCard:        CardValue? = null,
    val newestCard:        CardValue? = null,
    // Set Stats
    val topSetByCount:     Pair<String, Int>?    = null,
    val topSetByValue:     Pair<String, Double>? = null,
    // AutoTags Stats
    val autoTagDistribution: Map<String, Int> = emptyMap(),
)

data class CardValue(
    val scryfallId:    String,
    val name:          String,
    val priceUsd:      Double,
    val priceEur:      Double,
    val isFoil:        Boolean,
    val imageArtCrop:  String?,
    val colorIdentity: String = "",
    val setCode:       String = "",
    val setName:       String = "",
    val rarity:        String = "",
)

enum class MtgColor { W, U, B, R, G, COLORLESS }
enum class Rarity   { COMMON, UNCOMMON, RARE, MYTHIC, SPECIAL }
enum class CardType { CREATURE, INSTANT, SORCERY, ENCHANTMENT, ARTIFACT, PLANESWALKER, LAND, BATTLE, OTHER }