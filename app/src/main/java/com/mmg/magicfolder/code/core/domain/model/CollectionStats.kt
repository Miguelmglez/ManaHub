package com.mmg.magicfolder.code.core.domain.model

data class CollectionStats(
    val totalCards:        Int,
    val uniqueCards:       Int,
    val totalDecks:        Int,
    val totalValueUsd:     Double,
    val totalValueEur:     Double,
    val mostValuableCards: List,
    val byColor:           Map,
    val byRarity:          Map,
    val byType:            Map,
    val cmcDistribution:   Map,
    val bySet:             Map,
)

data class CardValue(
    val scryfallId:   String,
    val name:         String,
    val priceUsd:     Double,
    val isFoil:       Boolean,
    val imageArtCrop: String?,
)

enum class MtgColor { W, U, B, R, G, COLORLESS, MULTICOLOR }
enum class Rarity   { COMMON, UNCOMMON, RARE, MYTHIC, SPECIAL }
enum class CardType { CREATURE, INSTANT, SORCERY, ENCHANTMENT, ARTIFACT, PLANESWALKER, LAND, BATTLE, OTHER }