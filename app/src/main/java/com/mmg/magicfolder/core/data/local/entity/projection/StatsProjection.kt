package com.mmg.magicfolder.core.data.local.entity.projection


data class TotalsProjection(val totalCards: Int, val uniqueCards: Int)
data class CardValueProjection(val scryfallId: String, val name: String, val priceUsd: Double, val priceEur: Double, val isFoil: Boolean, val imageArtCrop: String?, val colorIdentity: String = "")
data class ColorCountProjection(val colorIdentity: String, val count: Int)
data class RarityCountProjection(val rarity: String, val count: Int)
data class TypeCountProjection(val typeLine: String, val count: Int)
data class CmcCountProjection(val cmc: Int, val count: Int)
data class SetCountProjection(val setCode: String, val count: Int)