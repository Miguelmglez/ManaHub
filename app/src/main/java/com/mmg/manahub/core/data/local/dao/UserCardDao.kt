package com.mmg.manahub.core.data.local.dao

import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.local.entity.SyncStatus
import com.mmg.manahub.core.data.local.entity.UserCardEntity
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class UserCardDao {

    // Returns new row id, or -1 if (scryfall_id, is_foil, condition, language, ...) already exists.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(userCard: UserCardEntity): Long

    @Update
    abstract suspend fun update(userCard: UserCardEntity)

    @Delete
    abstract suspend fun delete(userCard: UserCardEntity)

    @Query("DELETE FROM user_cards WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("UPDATE user_cards SET quantity = quantity + 1 WHERE id = :id")
    abstract suspend fun incrementQuantity(id: Long)

    @Query("UPDATE user_cards SET quantity = :qty WHERE id = :id")
    abstract suspend fun updateQuantity(id: Long, qty: Int)

    // Private helper: increments the existing matching row by unique key.
    // is_in_wishlist is part of the unique key so collection and wishlist entries stay separate.
    @Query("""
        UPDATE user_cards SET quantity = quantity + 1
        WHERE scryfall_id        = :scryfallId
        AND   is_foil            = :isFoil
        AND   is_alternative_art = :isAlternativeArt
        AND   condition          = :condition
        AND   language           = :language
        AND   is_in_wishlist     = :isInWishlist
    """)
    abstract suspend fun incrementQuantityByUniqueKey(
        scryfallId:       String,
        isFoil:           Boolean,
        isAlternativeArt: Boolean,
        condition:        String,
        language:         String,
        isInWishlist:     Boolean,
    )

    /**
     * Atomically inserts the entry or increments its quantity if it already exists.
     * Either path also marks the row as PENDING_UPLOAD so the sync system picks it up.
     *
     * The @Transaction ensures the INSERT and conditional UPDATE are executed as a
     * single SQLite transaction, eliminating the race condition where two concurrent
     * callers (e.g. double-tap, WorkManager sync) both receive -1L from insert()
     * and then both fire the increment, resulting in a quantity inflated by 2.
     */
    @Transaction
    open suspend fun insertOrIncrement(userCard: UserCardEntity) {
        val insertedId = insert(userCard)
        if (insertedId == -1L) {
            incrementQuantityByUniqueKey(
                scryfallId       = userCard.scryfallId,
                isFoil           = userCard.isFoil,
                isAlternativeArt = userCard.isAlternativeArt,
                condition        = userCard.condition,
                language         = userCard.language,
                isInWishlist     = userCard.isInWishlist,
            )
            markPendingUploadByUniqueKey(
                scryfallId       = userCard.scryfallId,
                isFoil           = userCard.isFoil,
                isAlternativeArt = userCard.isAlternativeArt,
                condition        = userCard.condition,
                language         = userCard.language,
                isInWishlist     = userCard.isInWishlist,
            )
        }
    }

    @Query("""
        UPDATE user_cards SET sync_status = ${SyncStatus.PENDING_UPLOAD}
        WHERE scryfall_id        = :scryfallId
        AND   is_foil            = :isFoil
        AND   is_alternative_art = :isAlternativeArt
        AND   condition          = :condition
        AND   language           = :language
        AND   is_in_wishlist     = :isInWishlist
    """)
    abstract suspend fun markPendingUploadByUniqueKey(
        scryfallId:       String,
        isFoil:           Boolean,
        isAlternativeArt: Boolean,
        condition:        String,
        language:         String,
        isInWishlist:     Boolean,
    )

    @Query("UPDATE user_cards SET sync_status = ${SyncStatus.PENDING_UPLOAD} WHERE id = :id")
    abstract suspend fun markPendingUpload(id: Long)

    @Query("UPDATE user_cards SET sync_status = ${SyncStatus.SYNCED}, remote_id = :remoteId WHERE id = :id")
    abstract suspend fun markAsSynced(id: Long, remoteId: String)

    @Query("SELECT * FROM user_cards WHERE sync_status = ${SyncStatus.PENDING_UPLOAD}")
    abstract suspend fun getPendingUpload(): List<UserCardEntity>

    @Query("SELECT * FROM user_cards WHERE remote_id = :remoteId LIMIT 1")
    abstract suspend fun getByRemoteId(remoteId: String): UserCardEntity?

    @Query("SELECT COUNT(*) FROM user_cards WHERE sync_status = ${SyncStatus.PENDING_UPLOAD}")
    abstract fun observePendingCount(): Flow<Int>

    /** Used during pull-sync: fully overwrites a local row with remote state. Safe because
     *  no other table has a FK pointing at user_cards rows. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertOrReplace(entity: UserCardEntity): Long

    // Plain query (no @Relation) so Room returns one row per UserCardEntity,
    // avoiding the collapse that @Transaction/@Relation causes when multiple rows
    // share the same scryfall_id.
    @Query("SELECT * FROM user_cards WHERE scryfall_id = :scryfallId ORDER BY added_at DESC")
    abstract fun observeByScryfallId(scryfallId: String): Flow<List<UserCardEntity>>

    @Transaction
    @Query("SELECT * FROM user_cards ORDER BY added_at DESC")
    abstract fun observeCollection(): Flow<List<UserCardWithCard>>

    @Transaction
    @Query("""
        SELECT uc.* FROM user_cards uc
        INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE c.color_identity LIKE '%' || :color || '%'
        ORDER BY c.name ASC
    """)
    abstract fun observeByColor(color: String): Flow<List<UserCardWithCard>>

    @Transaction
    @Query("""
        SELECT uc.* FROM user_cards uc
        INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE c.rarity = :rarity ORDER BY c.name ASC
    """)
    abstract fun observeByRarity(rarity: String): Flow<List<UserCardWithCard>>

    @Transaction
    @Query("""
        SELECT uc.* FROM user_cards uc
        INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE c.name LIKE '%' || :query || '%' ORDER BY c.name ASC
    """)
    abstract fun searchInCollection(query: String): Flow<List<UserCardWithCard>>

    @Query("SELECT * FROM user_cards WHERE id = :id")
    abstract suspend fun getById(id: Long): UserCardEntity?

    @Query("SELECT DISTINCT scryfall_id FROM user_cards")
    abstract suspend fun getAllScryfallIds(): List<String>

    @Query("SELECT COUNT(*) FROM user_cards")
    abstract fun observeCount(): Flow<Int>
}

/** Room relation: user card entry joined with its full card metadata. */
data class UserCardWithCard(
    @Embedded val userCard: UserCardEntity,
    @Relation(parentColumn = "scryfall_id", entityColumn = "scryfall_id")
    val card: CardEntity,
)
