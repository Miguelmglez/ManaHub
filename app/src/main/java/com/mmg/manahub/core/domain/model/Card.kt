package com.mmg.manahub.core.domain.model

data class Card(
    val scryfallId: String,
    val name: String,
    val printedName: String?,
    val manaCost: String?,
    val cmc: Double,
    val colors: List<String>,
    val colorIdentity: List<String>,
    val typeLine: String,
    val printedTypeLine: String?,
    val oracleText: String?,
    val printedText: String?,
    val keywords: List<String>,
    val power: String?,
    val toughness: String?,
    val loyalty: String?,
    val setCode: String,
    val setName: String,
    val collectorNumber: String,
    val rarity: String,
    val releasedAt: String,
    val frameEffects: List<String>,  // e.g. ["showcase"], ["extendedart"]
    val promoTypes: List<String>,  // e.g. ["boosterfun"]
    val lang: String,
    val imageNormal: String?,
    val imageArtCrop: String?,
    val imageBackNormal: String?,       // non-null only for double-faced cards
    val priceUsd: Double?,
    val priceUsdFoil: Double?,
    val priceEur: Double?,
    val priceEurFoil: Double?,
    val legalityStandard: String,
    val legalityPioneer: String,
    val legalityModern: String,
    val legalityCommander: String,
    val flavorText: String?,
    val artist: String?,
    val scryfallUri: String,
    val isStale: Boolean = false,
    val staleReason: String? = null,
    val cachedAt: Long = 0L,
    /** Auto-confirmed tags from the tagging engine. Persisted in Room as JSON. */
    val tags: List<CardTag> = emptyList(),
    /** Tags added manually by the user from the tag picker or custom input. */
    val userTags: List<CardTag> = emptyList(),
    /**
     * Tags the auto-tagging engine *suggests* for this card but whose confidence
     * was below the auto-add threshold. Surfaced in CardDetail for the user to
     * confirm or dismiss; never auto-applied to [tags].
     */
    val suggestedTags: List<SuggestedTag> = emptyList(),
)

data class UserCard(
    val id:               String,                              // UUID, client-generated
    val scryfallId:       String,
    val quantity:         Int     = 1,
    val isFoil:           Boolean = false,
    val isAlternativeArt: Boolean = false,
    val condition:        String  = "NM",
    val language:         String  = "en",
    val isForTrade:       Boolean = false,
    val isInWishlist:     Boolean = false,
    val updatedAt:        Long    = System.currentTimeMillis(),
    val createdAt:        Long    = System.currentTimeMillis(),
)

data class UserCardWithCard(val userCard: UserCard, val card: Card)
