package com.mmg.manahub.core.data.local.entity

import androidx.room.*

/** Values for [UserCardEntity.syncStatus]. */
object SyncStatus {
    /** Row is in sync with Supabase (or user is not logged in — no action needed). */
    const val SYNCED = 0
    /** Local change not yet pushed to Supabase. */
    const val PENDING_UPLOAD = 1
}

@Entity(
    tableName = "user_cards",
    foreignKeys = [ForeignKey(
        entity        = CardEntity::class,
        parentColumns = ["scryfall_id"],
        childColumns  = ["scryfall_id"],
        // RESTRICT: any attempt to DELETE a card that is still referenced in the
        // user's collection will throw a SQLiteConstraintException immediately,
        // making data loss loud and explicit instead of silent (CASCADE would
        // delete the user's collection entries without warning).
        onDelete      = ForeignKey.RESTRICT,
    )],
    indices = [
        Index("scryfall_id"),
        Index("is_for_trade"),
        Index("is_in_wishlist"),
        Index("sync_status"),
        // Prevents duplicate logical entries for the same physical card variant.
        // is_in_wishlist is included so the same copy can exist in both collection
        // (is_in_wishlist = 0) and wishlist (is_in_wishlist = 1) simultaneously.
        Index(value = ["scryfall_id", "is_foil", "condition", "language", "is_alternative_art", "is_in_wishlist"], unique = true),
    ],
)
data class UserCardEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    @ColumnInfo(name = "scryfall_id")       val scryfallId:      String,
    @ColumnInfo(name = "quantity")          val quantity:        Int     = 1,
    @ColumnInfo(name = "is_foil")           val isFoil:          Boolean = false,
    @ColumnInfo(name = "is_alternative_art") val isAlternativeArt: Boolean = false,
    // Condition: NM | LP | MP | HP | DMG
    @ColumnInfo(name = "condition")         val condition:       String  = "NM",
    // Language ISO: en | ja | de | fr | es | pt | it | ko | ru | zhs | zht
    @ColumnInfo(name = "language")          val language:        String  = "en",

    @ColumnInfo(name = "is_for_trade")    val isForTrade:    Boolean = false,
    @ColumnInfo(name = "is_in_wishlist")  val isInWishlist:  Boolean = false,
    @ColumnInfo(name = "min_trade_value") val minTradeValue: Double? = null,

    @ColumnInfo(name = "notes")       val notes:      String? = null,
    @ColumnInfo(name = "acquired_at") val acquiredAt: Long?   = null,
    @ColumnInfo(name = "added_at")    val addedAt:    Long    = System.currentTimeMillis(),

    // ── Sync metadata ─────────────────────────────────────────────────────────
    // Defaults to PENDING_UPLOAD so all new/existing rows are queued for the
    // first sync push after the user logs in.
    @ColumnInfo(name = "sync_status") val syncStatus: Int    = SyncStatus.PENDING_UPLOAD,
    // UUID assigned by Supabase after the row is successfully uploaded.
    @ColumnInfo(name = "remote_id")   val remoteId:   String? = null,
)
