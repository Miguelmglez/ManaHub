package com.mmg.manahub.core.data.local.entity.projection


data class TotalsProjection(val totalCards: Int, val uniqueCards: Int)
data class CardValueProjection(
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

data class ArtistCountProjection(val artist: String?, val count: Int)
data class SetValueProjection(val setCode: String, val totalValue: Double)
data class TagProjection(val tags: String?)
data class ColorCountProjection(val colorIdentity: String, val count: Int)
data class RarityCountProjection(val rarity: String, val count: Int)
data class TypeCountProjection(val typeLine: String, val count: Int)
data class CmcCountProjection(val cmc: Int, val count: Int)
data class SetCountProjection(val setCode: String, val count: Int)