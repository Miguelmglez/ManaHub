package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class CardEntity(

    @PrimaryKey
    @ColumnInfo(name = "scryfall_id")
    val scryfallId: String,

    @ColumnInfo(name = "name")           val name:           String,
    @ColumnInfo(name = "printed_name")   val printedName:    String?,
    @ColumnInfo(name = "lang")           val lang:           String,
    @ColumnInfo(name = "mana_cost")      val manaCost:       String?,
    @ColumnInfo(name = "cmc")            val cmc:            Double,
    @ColumnInfo(name = "colors")         val colors:         String,  // JSON: ["W","U"]
    @ColumnInfo(name = "color_identity") val colorIdentity:  String,  // JSON: ["W","U"]
    @ColumnInfo(name = "type_line")      val typeLine:       String,
    @ColumnInfo(name = "printed_type_line")val printedTypeLine:String?,
    @ColumnInfo(name = "oracle_text")    val oracleText:     String?,
    @ColumnInfo(name = "printed_text")   val printedText:    String?,
    @ColumnInfo(name = "keywords")       val keywords:       String,  // JSON list
    @ColumnInfo(name = "power")          val power:          String?,
    @ColumnInfo(name = "toughness")      val toughness:      String?,
    @ColumnInfo(name = "loyalty")        val loyalty:        String?,

    @ColumnInfo(name = "set_code")          val setCode:         String,
    @ColumnInfo(name = "set_name")          val setName:         String,
    @ColumnInfo(name = "collector_number")  val collectorNumber: String,
    @ColumnInfo(name = "rarity")            val rarity:          String,
    @ColumnInfo(name = "released_at")       val releasedAt:      String,

    // Alternate art treatment — e.g. ["showcase"], ["extendedart"], ["borderless"]
    @ColumnInfo(name = "frame_effects")  val frameEffects: String = "[]",
    // Promo markers — e.g. ["boosterfun"], ["stamped"]
    @ColumnInfo(name = "promo_types")    val promoTypes:   String = "[]",

    @ColumnInfo(name = "image_normal")      val imageNormal:     String?,
    @ColumnInfo(name = "image_art_crop")    val imageArtCrop:    String?,
    @ColumnInfo(name = "image_back_normal") val imageBackNormal: String?,  // DFC back face

    @ColumnInfo(name = "price_usd")      val priceUsd:     Double?,
    @ColumnInfo(name = "price_usd_foil") val priceUsdFoil: Double?,
    @ColumnInfo(name = "price_eur")      val priceEur:     Double?,
    @ColumnInfo(name = "price_eur_foil") val priceEurFoil: Double?,

    @ColumnInfo(name = "legality_standard")  val legalityStandard:  String,
    @ColumnInfo(name = "legality_pioneer")   val legalityPioneer:   String,
    @ColumnInfo(name = "legality_modern")    val legalityModern:    String,
    @ColumnInfo(name = "legality_commander") val legalityCommander: String,

    // Added in Deck Doctor Phase 4 (DB v40, D2). NOT NULL with a DEFAULT so existing
    // rows backfill to 'not_legal' on the additive ADD COLUMN migration; correct values
    // arrive on the next Scryfall refresh.
    @ColumnInfo(name = "legality_legacy",  defaultValue = "not_legal") val legalityLegacy:  String = "not_legal",
    @ColumnInfo(name = "legality_vintage", defaultValue = "not_legal") val legalityVintage: String = "not_legal",
    @ColumnInfo(name = "legality_pauper",  defaultValue = "not_legal") val legalityPauper:  String = "not_legal",

    @ColumnInfo(name = "flavor_text")  val flavorText:  String?,
    @ColumnInfo(name = "artist")       val artist:      String?,
    @ColumnInfo(name = "scryfall_uri") val scryfallUri: String,
    @ColumnInfo(name = "cached_at")    val cachedAt:    Long    = System.currentTimeMillis(),

    // True when cachedAt > CachePolicy.STALE_MS AND last refresh failed.
    // Reset to false on every successful Scryfall refresh.
    @ColumnInfo(name = "is_stale")     val isStale:     Boolean = false,
    @ColumnInfo(name = "stale_reason") val staleReason: String? = null,

    /** JSON array of `{key, category}` objects — auto-confirmed by the tagging engine. */
    @ColumnInfo(name = "tags", defaultValue = "[]")
    val tags: String = "[]",

    /** JSON array of `{key, category}` objects — manually added by the user. */
    @ColumnInfo(name = "user_tags", defaultValue = "[]")
    val userTags: String = "[]",

    /** JSON array of `{key, category, confidence, source}` objects. */
    @ColumnInfo(name = "suggested_tags", defaultValue = "[]")
    val suggestedTags: String = "[]",

    /** JSON object mapping site name → URL (Gatherer, EDHREC, TCGPlayer articles…). */
    @ColumnInfo(name = "related_uris", defaultValue = "{}")
    val relatedUris: String = "{}",

    /** JSON object mapping site name → purchase URL (TCGPlayer, Cardmarket…). */
    @ColumnInfo(name = "purchase_uris", defaultValue = "{}")
    val purchaseUris: String = "{}",

    /** True when Scryfall marks this card as a Game Changer in its format. */
    @ColumnInfo(name = "game_changer", defaultValue = "0")
    val gameChanger: Boolean = false,

    /**
     * EDHREC popularity rank (lower = more played). Null when Scryfall does not
     * provide a rank for this card. Nullable with NO defaultValue — absence is
     * meaningful and must not be coerced to a sentinel value.
     */
    @ColumnInfo(name = "edhrec_rank")
    val edhrecRank: Int? = null,

    /**
     * Penny Dreadful popularity rank (lower = more played). Null when Scryfall
     * does not provide a rank for this card.
     */
    @ColumnInfo(name = "penny_rank")
    val pennyRank: Int? = null,

    /**
     * JSON array of CardFace objects for double-faced / multi-face cards.
     * Null for single-faced cards.
     * Schema: [{"name":"...","manaCost":"...","typeLine":"...","oracleText":"...",
     *           "power":null,"toughness":null,"loyalty":null,"defense":null,
     *           "flavorText":null,"imageNormal":"...","imageArtCrop":"..."}]
     */
    @ColumnInfo(name = "card_faces")
    val cardFaces: String? = null,
)