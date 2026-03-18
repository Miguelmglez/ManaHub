package core.data.local.entity

import androidx.room.*

@Entity(
    tableName = "user_cards",
    foreignKeys = [ForeignKey(
        entity        = CardEntity::class,
        parentColumns = ["scryfall_id"],
        childColumns  = ["scryfall_id"],
        onDelete      = ForeignKey.CASCADE,
    )],
    indices = [
        Index("scryfall_id"),
        Index("is_for_trade"),
        Index("is_in_wishlist"),
        // Prevents duplicate logical entries for the same physical card variant
        Index(value = ["scryfall_id", "is_foil", "condition", "language"], unique = true),
    ],
)
data class UserCardEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    @ColumnInfo(name = "scryfall_id")  val scryfallId:   String,
    @ColumnInfo(name = "quantity")     val quantity:     Int     = 1,
    @ColumnInfo(name = "is_foil")      val isFoil:       Boolean = false,
    // Condition: NM | LP | MP | HP | DMG
    @ColumnInfo(name = "condition")    val condition:    String  = "NM",
    // Language ISO: en | ja | de | fr | es | pt | it | ko | ru | zhs | zht
    @ColumnInfo(name = "language")     val language:     String  = "en",

    @ColumnInfo(name = "is_for_trade")    val isForTrade:    Boolean = false,
    @ColumnInfo(name = "is_in_wishlist")  val isInWishlist:  Boolean = false,
    @ColumnInfo(name = "min_trade_value") val minTradeValue: Double? = null,

    @ColumnInfo(name = "notes")       val notes:      String? = null,
    @ColumnInfo(name = "acquired_at") val acquiredAt: Long?   = null,
    @ColumnInfo(name = "added_at")    val addedAt:    Long    = System.currentTimeMillis(),
)
