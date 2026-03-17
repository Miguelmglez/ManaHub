package com.mmg.magicfolder.code.core.domain.model

data class Card(
    val scryfallId:       String,
    val name:             String,
    val manaCost:         String?,
    val cmc:              Double,
    val colors:           List<String>,
    val colorIdentity:    List<String>,
    val typeLine:         String,
    val oracleText:       String?,
    val keywords:         List<String>,
    val power:            String?,
    val toughness:        String?,
    val loyalty:          String?,
    val setCode:          String,
    val setName:          String,
    val collectorNumber:  String,
    val rarity:           String,
    val releasedAt:       String,
    val frameEffects:     List<String>,  // e.g. ["showcase"], ["extendedart"]
    val promoTypes:       List<String>,  // e.g. ["boosterfun"]
    val lang:             String,
    val imageNormal:      String?,
    val imageArtCrop:     String?,
    val imageBackNormal:  String?,       // non-null only for double-faced cards
    val priceUsd:         Double?,
    val priceUsdFoil:     Double?,
    val priceEur:         Double?,
    val priceEurFoil:     Double?,
    val legalityStandard:  String,
    val legalityPioneer:   String,
    val legalityModern:    String,
    val legalityCommander: String,
    val flavorText:       String?,
    val artist:           String?,
    val scryfallUri:      String,
    val isStale:          Boolean = false,
    val staleReason:      String? = null,
    val cachedAt:         Long    = 0L,
)

data class UserCard(
    val id:            Long    = 0,
    val scryfallId:    String,
    val quantity:      Int     = 1,
    val isFoil:        Boolean = false,
    val condition:     String  = "NM",
    val language:      String  = "en",
    val isForTrade:    Boolean = false,
    val isInWishlist:  Boolean = false,
    val minTradeValue: Double? = null,
    val notes:         String? = null,
    val acquiredAt:    Long?   = null,
    val addedAt:       Long    = System.currentTimeMillis(),
)

data class UserCardWithCard(val userCard: UserCard, val card: Card)

data class Deck(
    val id:          Long    = 0,
    val name:        String,
    val description: String? = null,
    val format:      String  = "casual",
    val coverCardId: String? = null,
    val createdAt:   Long    = System.currentTimeMillis(),
    val updatedAt:   Long    = System.currentTimeMillis(),
)

data class DeckWithCards(val deck: Deck, val mainboard: List<DeckSlot>, val sideboard: List<DeckSlot>)
data class DeckSlot(val scryfallId: String, val quantity: Int)