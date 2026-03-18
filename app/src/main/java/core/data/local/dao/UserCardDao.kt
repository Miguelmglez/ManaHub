package core.data.local.dao

import core.data.local.entity.CardEntity
import core.data.local.entity.UserCardEntity
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserCardDao {

    // Returns new row id, or -1 if (scryfall_id, is_foil, condition, language) already exists.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(userCard: UserCardEntity): Long

    @Update
    suspend fun update(userCard: UserCardEntity)

    @Delete
    suspend fun delete(userCard: UserCardEntity)

    @Query("DELETE FROM user_cards WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE user_cards SET quantity = quantity + 1 WHERE id = :id")
    suspend fun incrementQuantity(id: Long)

    @Query("UPDATE user_cards SET quantity = :qty WHERE id = :id")
    suspend fun updateQuantity(id: Long, qty: Int)

    // Called when insert() returns -1 — increments the existing matching row.
    @Query("""
        UPDATE user_cards SET quantity = quantity + 1
        WHERE scryfall_id = :scryfallId
        AND   is_foil     = :isFoil
        AND   condition   = :condition
        AND   language    = :language
    """)
    suspend fun incrementQuantityByUniqueKey(
        scryfallId: String,
        isFoil:     Boolean,
        condition:  String,
        language:   String,
    )

    @Transaction
    @Query("SELECT * FROM user_cards ORDER BY added_at DESC")
    fun observeCollection(): Flow<List<UserCardWithCard>>

    @Transaction
    @Query("""
        SELECT uc.* FROM user_cards uc
        INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE c.color_identity LIKE '%' || :color || '%'
        ORDER BY c.name ASC
    """)
    fun observeByColor(color: String): Flow<List<UserCardWithCard>>

    @Transaction
    @Query("""
        SELECT uc.* FROM user_cards uc
        INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE c.rarity = :rarity ORDER BY c.name ASC
    """)
    fun observeByRarity(rarity: String): Flow<List<UserCardWithCard>>

    @Transaction
    @Query("""
        SELECT uc.* FROM user_cards uc
        INNER JOIN cards c ON uc.scryfall_id = c.scryfall_id
        WHERE c.name LIKE '%' || :query || '%' ORDER BY c.name ASC
    """)
    fun searchInCollection(query: String): Flow<List<UserCardWithCard>>

    @Query("SELECT * FROM user_cards WHERE id = :id")
    suspend fun getById(id: Long): UserCardEntity?

    @Query("SELECT DISTINCT scryfall_id FROM user_cards")
    suspend fun getAllScryfallIds(): List<String>

    @Query("SELECT COUNT(*) FROM user_cards")
    fun observeCount(): Flow<Int>
}

/** Room relation: user card entry joined with its full card metadata. */
data class UserCardWithCard(
    @Embedded val userCard: UserCardEntity,
    @Relation(parentColumn = "scryfall_id", entityColumn = "scryfall_id")
    val card: CardEntity,
)
