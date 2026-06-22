package com.mmg.manahub.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CardDto(
    @SerialName("id")               val id:              String,
    @SerialName("name")             val name:            String,
    @SerialName("printed_name")     val printedName:     String?      = null,
    @SerialName("lang")             val lang:            String,
    @SerialName("mana_cost")        val manaCost:        String?      = null,
    @SerialName("cmc")              val cmc:             Double?      = null,
    @SerialName("colors")           val colors:          List<String>? = null,
    @SerialName("color_identity")   val colorIdentity:   List<String>,
    @SerialName("type_line")        val typeLine:        String?      = null,
    @SerialName("printed_type_line")val printedTypeLine: String?      = null,
    @SerialName("oracle_text")      val oracleText:      String?      = null,
    @SerialName("printed_text")     val printedText:     String?      = null,
    @SerialName("keywords")         val keywords:        List<String>,
    @SerialName("power")            val power:           String?      = null,
    @SerialName("toughness")        val toughness:       String?      = null,
    @SerialName("loyalty")          val loyalty:         String?      = null,
    @SerialName("set")              val setCode:         String,
    @SerialName("set_name")         val setName:         String,
    @SerialName("collector_number") val collectorNumber: String,
    @SerialName("rarity")           val rarity:          String,
    @SerialName("released_at")      val releasedAt:      String,
    @SerialName("frame_effects")    val frameEffects:    List<String>? = null,
    @SerialName("promo_types")      val promoTypes:      List<String>? = null,
    @SerialName("image_uris")       val imageUris:       ImageUrisDto? = null,
    @SerialName("card_faces")       val cardFaces:       List<CardFaceDto>? = null,
    @SerialName("prices")           val prices:          PricesDto,
    @SerialName("legalities")       val legalities:      LegalitiesDto,
    @SerialName("scryfall_uri")     val scryfallUri:     String,
    @SerialName("flavor_text")      val flavorText:      String?      = null,
    @SerialName("artist")           val artist:          String?      = null,
    @SerialName("related_uris")     val relatedUris:     Map<String, String>? = null,
    @SerialName("purchase_uris")    val purchaseUris:    Map<String, String>? = null,
    @SerialName("game_changer")     val gameChanger:     Boolean?     = null,
    @SerialName("edhrec_rank")      val edhrecRank:      Int?         = null,
    @SerialName("penny_rank")       val pennyRank:       Int?         = null,
)

@Serializable
data class ImageUrisDto(
    @SerialName("small")    val small:   String? = null,
    @SerialName("normal")   val normal:  String? = null,
    @SerialName("large")    val large:   String? = null,
    @SerialName("png")      val png:     String? = null,
    @SerialName("art_crop") val artCrop: String? = null,
)

@Serializable
data class CardFaceDto(
    @SerialName("name")        val name:       String,
    @SerialName("mana_cost")   val manaCost:   String?  = null,
    @SerialName("type_line")   val typeLine:   String?  = null,
    @SerialName("oracle_text") val oracleText: String?  = null,
    @SerialName("power")       val power:      String?  = null,
    @SerialName("toughness")   val toughness:  String?  = null,
    @SerialName("loyalty")     val loyalty:    String?  = null,
    @SerialName("defense")     val defense:    String?  = null,
    @SerialName("flavor_text") val flavorText: String?  = null,
    @SerialName("image_uris")  val imageUris:  ImageUrisDto? = null,
)

@Serializable
data class PricesDto(
    @SerialName("usd")      val usd:     String? = null,
    @SerialName("usd_foil") val usdFoil: String? = null,
    @SerialName("eur")      val eur:     String? = null,
    @SerialName("eur_foil") val eurFoil: String? = null,
    @SerialName("tix")      val tix:     String? = null,
)

@Serializable
data class LegalitiesDto(
    @SerialName("standard")  val standard:  String,
    @SerialName("pioneer")   val pioneer:   String,
    @SerialName("modern")    val modern:    String,
    @SerialName("legacy")    val legacy:    String,
    @SerialName("vintage")   val vintage:   String,
    @SerialName("commander") val commander: String,
    @SerialName("pauper")    val pauper:    String,
)

@Serializable
data class SearchResultDto(
    @SerialName("total_cards") val totalCards: Int,
    @SerialName("has_more")    val hasMore:    Boolean,
    @SerialName("next_page")   val nextPage:   String? = null,
    @SerialName("data")        val data:       List<CardDto>,
)

@Serializable
data class CardCollectionRequestDto(
    @SerialName("identifiers") val identifiers: List<CardIdentifierDto>
)

@Serializable
data class CardIdentifierDto(
    @SerialName("id")               val id:              String? = null,
    @SerialName("name")             val name:            String? = null,
    @SerialName("set")              val set:             String? = null,
    @SerialName("collector_number") val collectorNumber: String? = null,
)

@Serializable
data class CardCollectionResponseDto(
    @SerialName("data")      val data:     List<CardDto>,
    @SerialName("not_found") val notFound: List<CardIdentifierDto>,
)
