package com.mmg.magicfolder.core.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CardDto(
    @SerializedName("id")               val id:              String,
    @SerializedName("name")             val name:            String,
    @SerializedName("lang")             val lang:            String,
    @SerializedName("mana_cost")        val manaCost:        String?,
    @SerializedName("cmc")              val cmc:             Double,
    @SerializedName("colors")           val colors:          List<String>?,
    @SerializedName("color_identity")   val colorIdentity:   List<String>,
    @SerializedName("type_line")        val typeLine:        String,
    @SerializedName("oracle_text")      val oracleText:      String?,
    @SerializedName("keywords")         val keywords:        List<String>,
    @SerializedName("power")            val power:           String?,
    @SerializedName("toughness")        val toughness:       String?,
    @SerializedName("loyalty")          val loyalty:         String?,
    @SerializedName("set")              val setCode:         String,
    @SerializedName("set_name")         val setName:         String,
    @SerializedName("collector_number") val collectorNumber: String,
    @SerializedName("rarity")           val rarity:          String,
    @SerializedName("released_at")      val releasedAt:      String,
    @SerializedName("frame_effects")    val frameEffects:    List<String>? = null,
    @SerializedName("promo_types")      val promoTypes:      List<String>? = null,
    @SerializedName("image_uris")       val imageUris:       ImageUrisDto?,
    @SerializedName("card_faces")       val cardFaces:       List<CardFaceDto>?,
    @SerializedName("prices")           val prices:          PricesDto,
    @SerializedName("legalities")       val legalities:      LegalitiesDto,
    @SerializedName("scryfall_uri")     val scryfallUri:     String,
    @SerializedName("flavor_text")      val flavorText:      String?,
    @SerializedName("artist")           val artist:          String?,
)

data class ImageUrisDto(
    @SerializedName("small")    val small:   String?,
    @SerializedName("normal")   val normal:  String?,
    @SerializedName("large")    val large:   String?,
    @SerializedName("png")      val png:     String?,
    @SerializedName("art_crop") val artCrop: String?,
)

data class CardFaceDto(
    @SerializedName("name")        val name:       String,
    @SerializedName("mana_cost")   val manaCost:   String?,
    @SerializedName("type_line")   val typeLine:   String?,
    @SerializedName("oracle_text") val oracleText: String?,
    @SerializedName("power")       val power:      String?,
    @SerializedName("toughness")   val toughness:  String?,
    @SerializedName("image_uris")  val imageUris:  ImageUrisDto?,
)

data class PricesDto(
    @SerializedName("usd")      val usd:     String?,
    @SerializedName("usd_foil") val usdFoil: String?,
    @SerializedName("eur")      val eur:     String?,
    @SerializedName("eur_foil") val eurFoil: String?,
    @SerializedName("tix")      val tix:     String?,
)

data class LegalitiesDto(
    @SerializedName("standard")  val standard:  String,
    @SerializedName("pioneer")   val pioneer:   String,
    @SerializedName("modern")    val modern:    String,
    @SerializedName("legacy")    val legacy:    String,
    @SerializedName("vintage")   val vintage:   String,
    @SerializedName("commander") val commander: String,
    @SerializedName("pauper")    val pauper:    String,
)

data class SearchResultDto(
    @SerializedName("total_cards") val totalCards: Int,
    @SerializedName("has_more")    val hasMore:    Boolean,
    @SerializedName("next_page")   val nextPage:   String?,
    @SerializedName("data")        val data:       List<CardDto>,
)

data class CardCollectionRequestDto(
    @SerializedName("identifiers") val identifiers: List<CardIdentifierDto>
)

data class CardIdentifierDto(
    @SerializedName("id")               val id:              String? = null,
    @SerializedName("name")             val name:            String? = null,
    @SerializedName("set")              val set:             String? = null,
    @SerializedName("collector_number") val collectorNumber: String? = null,
)

data class CardCollectionResponseDto(
    @SerializedName("data")      val data:     List<CardDto>,
    @SerializedName("not_found") val notFound: List<CardIdentifierDto>,
)
