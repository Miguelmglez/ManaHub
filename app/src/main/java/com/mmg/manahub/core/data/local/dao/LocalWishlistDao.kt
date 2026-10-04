package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.local.entity.LocalWishlistEntity
import com.mmg.manahub.core.data.local.entity.CollectionTransferWishlistDirtyEntity
import com.mmg.manahub.core.data.local.TradeListOwner
import kotlinx.coroutines.flow.Flow

data class LocalWishlistWithCard(
    @Embedded val entity: LocalWishlistEntity,
    @Relation(
        parentColumn = "scryfall_id",
        entityColumn = "scryfall_id"
    )
    val card: CardEntity?
)

@Dao
abstract class LocalWishlistDao {

    @Transaction
    @Query("SELECT * FROM local_wishlists WHERE owner_user_id = :ownerUserId ORDER BY created_at DESC")
    abstract fun observeAllWithCard(ownerUserId: String): Flow<List<LocalWishlistWithCard>>

    @Transaction
    @Query("SELECT w.* FROM local_wishlists w JOIN collection_transfer_wishlist_dirty d ON d.wishlist_id=w.id WHERE w.owner_user_id='local_guest' AND d.owner_key=:guestOwner AND d.deleted=0 ORDER BY w.created_at DESC")
    abstract fun observeProvenLocalWithCard(guestOwner: String): Flow<List<LocalWishlistWithCard>>

    @Transaction
    @Query("SELECT w.* FROM local_wishlists w JOIN collection_transfer_wishlist_dirty d ON d.wishlist_id=w.id WHERE w.owner_user_id='local_guest' AND d.owner_key=:guestOwner AND d.deleted=0 AND w.scryfall_id=:printing ORDER BY w.created_at DESC")
    abstract fun observeProvenLocalPrintingWithCard(guestOwner: String,printing: String): Flow<List<LocalWishlistWithCard>>

    @Transaction
    @Query("SELECT w.* FROM local_wishlists w JOIN collection_transfer_wishlist_dirty d ON d.wishlist_id=w.id WHERE w.owner_user_id='local_guest' AND d.owner_key=:guestOwner AND d.deleted=0 AND w.scryfall_id IN (SELECT scryfall_id FROM cards WHERE (:oracleId!='' AND oracle_id=:oracleId) OR (oracle_id='' AND name=:name)) ORDER BY w.created_at DESC")
    abstract fun observeProvenLocalVersionsWithCard(guestOwner: String,oracleId: String,name: String): Flow<List<LocalWishlistWithCard>>

    @Query("SELECT COUNT(*) FROM local_wishlists w JOIN collection_transfer_wishlist_dirty d ON d.wishlist_id=w.id WHERE w.owner_user_id='local_guest' AND d.owner_key=:guestOwner AND d.deleted=0 AND w.synced=0")
    abstract fun observeProvenLocalUnsyncedCount(guestOwner: String): Flow<Int>

    @Query("SELECT * FROM local_wishlists WHERE owner_user_id = :ownerUserId ORDER BY created_at DESC")
    abstract fun observeAll(ownerUserId: String): Flow<List<LocalWishlistEntity>>

    @Transaction
    @Query("SELECT * FROM local_wishlists WHERE scryfall_id = :scryfallId AND owner_user_id = :ownerUserId ORDER BY created_at DESC")
    abstract fun observeByScryfallIdWithCard(scryfallId: String, ownerUserId: String): Flow<List<LocalWishlistWithCard>>

    @Query("SELECT * FROM local_wishlists WHERE scryfall_id = :scryfallId AND owner_user_id = :ownerUserId ORDER BY created_at DESC")
    abstract fun observeByScryfallId(scryfallId: String, ownerUserId: String): Flow<List<LocalWishlistEntity>>

    @Query("SELECT * FROM local_wishlists WHERE scryfall_id = :scryfallId AND owner_user_id = :ownerUserId")
    abstract suspend fun getByScryfallId(scryfallId: String, ownerUserId: String): List<LocalWishlistEntity>

    // Card Versions & Languages, Phase 1A. Every wishlist entry for ANY printing/language sharing
    // the same oracle identity — see UserCardCollectionDao.observeVersionsByOracle for the exact
    // matching semantics (oracle_id when known, exact `name` match as the pre-oracle_id fallback).
    @Transaction
    @Query("""
        SELECT * FROM local_wishlists
        WHERE owner_user_id = :ownerUserId AND scryfall_id IN (
            SELECT scryfall_id FROM cards
            WHERE (:oracleId != '' AND oracle_id = :oracleId)
               OR (oracle_id = '' AND name = :name)
        )
        ORDER BY created_at DESC
    """)
    abstract fun observeVersionsByOracle(oracleId: String, name: String, ownerUserId: String): Flow<List<LocalWishlistWithCard>>

    // Used to check `synced` before mutating a row, so local edits to an already-synced entry
    // can be paired with the matching remote call (trades audit §2.3, 2026-07-10).
    @Query("SELECT * FROM local_wishlists WHERE id = :id AND owner_user_id = :ownerUserId")
    abstract suspend fun getById(id: String, ownerUserId: String): LocalWishlistEntity?

    @Query("SELECT * FROM local_wishlists WHERE synced = 0 AND owner_user_id IN (:ownerUserId, 'local_guest') AND id NOT IN (SELECT wishlist_id FROM collection_transfer_wishlist_dirty)")
    abstract suspend fun getUnsynced(ownerUserId: String): List<LocalWishlistEntity>

    @Query("SELECT wishlist_id FROM collection_transfer_wishlist_dirty WHERE owner_key='account:' || :ownerUserId AND wishlist_id IN (:ids) AND (pending=1 OR deleted=1)")
    abstract suspend fun getTransferDirtyIds(ownerUserId: String, ids: List<String>): List<String>

    /** Dirty protection and pull writes share a transaction with transfer execution. */
    @Transaction
    open suspend fun upsertRemoteProtected(entries: List<LocalWishlistEntity>, ownerUserId: String) {
        require(entries.all { it.ownerUserId==ownerUserId })
        entries.chunked(200).forEach { page ->
            val dirty=getTransferDirtyIds(ownerUserId,page.map { it.id }).toSet()
            reactivateSeenTombstones(ownerUserId,page.map { it.id })
            val ready=page.filterNot { it.id in dirty }
            if(ready.isNotEmpty())upsertRemoteRows(ready)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertRow(entry: LocalWishlistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertRows(entries: List<LocalWishlistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertRemoteRows(entries: List<LocalWishlistEntity>)

    @Update
    protected abstract suspend fun updateRow(entry: LocalWishlistEntity)

    @Query("UPDATE local_wishlists SET quantity = :quantity WHERE id = :id AND owner_user_id = :ownerUserId")
    protected abstract suspend fun updateQuantityRow(id: String, quantity: Int, ownerUserId: String)

    @Query("""
        SELECT * FROM local_wishlists
        WHERE scryfall_id = :scryfallId
          AND owner_user_id = :ownerUserId
          AND match_any_variant = :matchAnyVariant
          AND (is_foil = :isFoil OR (is_foil IS NULL AND :isFoil IS NULL))
          AND (condition = :condition OR (condition IS NULL AND :condition IS NULL))
          AND (language = :language OR (language IS NULL AND :language IS NULL))
        LIMIT 1
    """)
    abstract suspend fun getByAttributes(
        scryfallId: String,
        matchAnyVariant: Boolean,
        isFoil: Boolean?,
        condition: String?,
        language: String?,
        ownerUserId: String,
    ): LocalWishlistEntity?

    @Query("""
        SELECT w.* FROM local_wishlists w JOIN collection_transfer_wishlist_dirty d ON d.wishlist_id=w.id
        WHERE w.owner_user_id='local_guest' AND d.owner_key=:guestOwner AND d.deleted=0
          AND w.scryfall_id=:printing AND w.match_any_variant=:matchAny
          AND (w.is_foil=:foil OR (w.is_foil IS NULL AND :foil IS NULL))
          AND (w.condition=:condition OR (w.condition IS NULL AND :condition IS NULL))
          AND (w.language=:language OR (w.language IS NULL AND :language IS NULL))
        LIMIT 1
    """)
    abstract suspend fun provenLocalByAttributes(guestOwner: String,printing: String,matchAny: Boolean,foil: Boolean?,condition: String?,language: String?): LocalWishlistEntity?

    @Query("SELECT w.* FROM local_wishlists w JOIN collection_transfer_wishlist_dirty d ON d.wishlist_id=w.id WHERE w.id=:id AND w.owner_user_id='local_guest' AND d.owner_key=:guestOwner AND d.deleted=0")
    abstract suspend fun provenLocalById(guestOwner: String,id: String): LocalWishlistEntity?

    @Query("SELECT w.* FROM local_wishlists w JOIN collection_transfer_wishlist_dirty d ON d.wishlist_id=w.id WHERE w.scryfall_id=:printing AND w.owner_user_id='local_guest' AND d.owner_key=:guestOwner AND d.deleted=0")
    abstract suspend fun provenLocalByPrinting(guestOwner: String,printing: String): List<LocalWishlistEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM local_wishlists WHERE id=:id)")
    protected abstract suspend fun containsId(id: String): Boolean

    /** Provenance precedes a fresh row in the same transaction; existing rows cannot acquire it. */
    @Transaction
    open suspend fun insertProvenLocal(entry: LocalWishlistEntity, guestOwner: String) {
        require(entry.ownerUserId==TradeListOwner.GUEST && guestOwner.startsWith("guest:") && !containsId(entry.id) && managed(entry.id)==null)
        stageManaged(entry,capturedOwner=guestOwner)
        insertRow(entry)
    }

    /** Inserts or merges every entry in one transaction; merged rows go back to unsynced. */
    @Transaction
    open suspend fun addOrMergeAll(entries: List<LocalWishlistEntity>) {
        entries.forEach { entry ->
            val existing = getByAttributes(
                scryfallId = entry.scryfallId,
                matchAnyVariant = entry.matchAnyVariant,
                isFoil = entry.isFoil,
                condition = entry.condition,
                language = entry.language,
                ownerUserId = entry.ownerUserId ?: return@forEach,
            )
            if (existing != null) {
                // Summed as Long first, like the collection path: an Int overflow here would
                // write a negative wanted quantity.
                val merged = (existing.quantity.toLong() + entry.quantity)
                    .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                update(existing.copy(quantity = merged, synced = false))
            } else {
                insert(entry)
            }
        }
    }

    @Delete
    protected abstract suspend fun deleteRow(entry: LocalWishlistEntity)

    @Query("DELETE FROM local_wishlists WHERE id = :id AND owner_user_id = :ownerUserId")
    protected abstract suspend fun deleteOwnedRow(id: String, ownerUserId: String)

    @Query("SELECT * FROM collection_transfer_wishlist_dirty WHERE wishlist_id=:id")
    abstract suspend fun managed(id: String): CollectionTransferWishlistDirtyEntity?
    @Insert(onConflict=OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertManaged(row: CollectionTransferWishlistDirtyEntity)
    @Update
    protected abstract suspend fun updateManaged(row: CollectionTransferWishlistDirtyEntity)
    @Query("UPDATE collection_transfer_wishlist_dirty SET pending=1,next_repair_at=0,revision=revision+1 WHERE owner_key='account:' || :ownerUserId AND wishlist_id IN (:ids) AND deleted=1 AND pending=0 AND revision<9223372036854775807")
    protected abstract suspend fun reactivateSeenTombstones(ownerUserId: String, ids: List<String>)
    @Query("SELECT * FROM local_wishlists WHERE synced=0 AND owner_user_id=:ownerUserId AND id NOT IN (SELECT wishlist_id FROM collection_transfer_wishlist_dirty) ORDER BY id LIMIT 200")
    abstract suspend fun unmanagedUnsyncedPage(ownerUserId: String): List<LocalWishlistEntity>
    @Query("UPDATE local_wishlists SET owner_user_id=:ownerUserId WHERE id=:id AND owner_user_id='local_guest' AND NOT EXISTS(SELECT 1 FROM collection_transfer_wishlist_dirty WHERE wishlist_id=:id)")
    abstract suspend fun claimUnmanagedGuest(id: String, ownerUserId: String)

    @Query("SELECT w.* FROM local_wishlists w JOIN collection_transfer_wishlist_dirty d ON d.wishlist_id=w.id WHERE w.owner_user_id='local_guest' AND d.owner_key=:guestOwner AND d.deleted=0 ORDER BY w.id LIMIT 200")
    protected abstract suspend fun provenLocalPage(guestOwner: String): List<LocalWishlistEntity>

    /** Login adopts only this installation's proven local payload, leaving transfer snapshots intact. */
    @Transaction
    open suspend fun adoptProvenLocalPage(guestOwner: String, accountId: String): List<String> {
        require(guestOwner.startsWith("guest:") && accountId.isNotBlank() && accountId!=TradeListOwner.GUEST)
        return provenLocalPage(guestOwner).map { local ->
            val proof=requireNotNull(managed(local.id))
            require(proof.ownerKey==guestOwner && !proof.deleted && proof.revision<Long.MAX_VALUE)
            val existing=getByAttributes(local.scryfallId,local.matchAnyVariant,local.isFoil,local.condition,local.language,accountId)
            if(existing==null) {
                updateRow(local.copy(ownerUserId=accountId,synced=false))
                updateManaged(proof.copy(ownerKey="account:$accountId",revision=proof.revision+1L,pending=true,nextRepairAt=0L))
                local.id
            } else {
                val copies=com.mmg.manahub.core.domain.collection.transfer.transferCollectionQuantity(existing.quantity,false,local.quantity.toLong())
                val merged=existing.copy(quantity=copies,synced=false)
                update(merged)
                deleteRow(local)
                updateManaged(proof.copy(revision=proof.revision+1L,deleted=true,pending=false,nextRepairAt=0L,quantity=0))
                existing.id
            }
        }.distinct()
    }

    /** Every local writer retains versioned intent; explicit guests keep their captured provenance. */
    @Transaction
    open suspend fun stageManaged(entry: LocalWishlistEntity, deleted: Boolean=false, capturedOwner: String?=null) {
        val previous=managed(entry.id)
        val user=entry.ownerUserId ?: return
        val owner=capturedOwner ?: previous?.ownerKey ?: if(user!=TradeListOwner.GUEST)"account:$user" else return
        require(if(user==TradeListOwner.GUEST)owner.startsWith("guest:") else owner=="account:$user")
        require(previous==null || previous.ownerKey==owner)
        require(user!=TradeListOwner.GUEST || previous!=null || !deleted && !containsId(entry.id))
        val row=CollectionTransferWishlistDirtyEntity(entry.id,owner,previous?.revision ?: 1L,deleted,true,0L,entry.scryfallId,if(deleted)0 else entry.quantity,entry.isFoil,entry.condition,entry.language,entry.matchAnyVariant,entry.createdAt)
        if(previous==null)insertManaged(row)
        else if(previous.copy(revision=row.revision,pending=true,nextRepairAt=0L)!=row || !previous.pending) {
            require(previous.revision<Long.MAX_VALUE); updateManaged(row.copy(revision=previous.revision+1L))
        }
    }
    @Transaction
    open suspend fun insert(entry: LocalWishlistEntity) {
        val previous=managed(entry.id)
        require(previous?.deleted!=true)
        insertRow(entry)
        if(entry.ownerUserId!=null)stageManaged(requireNotNull(getById(entry.id,entry.ownerUserId)))
    }
    @Transaction
    open suspend fun insertAll(entries: List<LocalWishlistEntity>) { entries.forEach { insert(it) } }
    @Transaction
    open suspend fun update(entry: LocalWishlistEntity) {
        val owner=entry.ownerUserId ?: error("Unverified wishlist owner")
        require(getById(entry.id,owner)!=null && managed(entry.id)?.deleted!=true)
        updateRow(entry.copy(synced=false)); stageManaged(entry)
    }
    @Transaction
    open suspend fun updateQuantity(id: String, quantity: Int, ownerUserId: String) {
        val row=getById(id,ownerUserId) ?: return
        if(quantity<=0)deleteById(id,ownerUserId) else update(row.copy(quantity=quantity))
    }
    @Transaction
    open suspend fun deleteById(id: String, ownerUserId: String) {
        val row=getById(id,ownerUserId) ?: return
        stageManaged(row,deleted=true); deleteOwnedRow(id,ownerUserId)
    }
    @Transaction
    open suspend fun delete(entry: LocalWishlistEntity) { deleteById(entry.id,entry.ownerUserId ?: error("Unverified wishlist owner")) }
    @Transaction
    open suspend fun stageMissingDeletion(id: String, ownerKey: String) {
        require(ownerKey.startsWith("account:"))
        val previous=managed(id)
        require(previous==null || previous.ownerKey==ownerKey)
        if(previous==null)insertManaged(CollectionTransferWishlistDirtyEntity(id,ownerKey,1L,deleted=true))
        else if(!previous.deleted || !previous.pending) { require(previous.revision<Long.MAX_VALUE); updateManaged(previous.copy(revision=previous.revision+1L,deleted=true,pending=true,nextRepairAt=0L,quantity=0)) }
    }

    @Query("UPDATE local_wishlists SET synced = 1 WHERE id IN (:ids) AND owner_user_id IN (:ownerUserId, 'local_guest') AND id NOT IN (SELECT wishlist_id FROM collection_transfer_wishlist_dirty)")
    abstract suspend fun markSynced(ids: List<String>, ownerUserId: String)

    @Query("DELETE FROM local_wishlists WHERE synced = 1 AND owner_user_id = :ownerUserId AND id NOT IN (SELECT wishlist_id FROM collection_transfer_wishlist_dirty WHERE pending=1)")
    abstract suspend fun clearSynced(ownerUserId: String)

    /** Removes ambiguous legacy rows while retaining verified rows for every account. */
    @Query("""
        DELETE FROM local_wishlists
        WHERE owner_user_id IS NULL
           OR (synced = 1 AND owner_user_id = 'local_guest')
    """)
    abstract suspend fun deleteAmbiguousRows()

    /** Claims verified guest rows [ids] for [ownerUserId] after migration. */
    @Query("UPDATE local_wishlists SET owner_user_id = :ownerUserId WHERE id IN (:ids) AND owner_user_id = 'local_guest' AND id NOT IN (SELECT wishlist_id FROM collection_transfer_wishlist_dirty)")
    abstract suspend fun stampOwner(ids: List<String>, ownerUserId: String)

    @Query("SELECT id FROM local_wishlists WHERE synced = 1 AND owner_user_id = :ownerUserId AND id NOT IN (SELECT wishlist_id FROM collection_transfer_wishlist_dirty WHERE pending=1)")
    abstract suspend fun getSyncedIds(ownerUserId: String): List<String>

    // Callers chunk [ids]: API 29's SQLite caps a statement at 999 bind variables.
    @Query("DELETE FROM local_wishlists WHERE synced = 1 AND owner_user_id = :ownerUserId AND id IN (:ids) AND id NOT IN (SELECT wishlist_id FROM collection_transfer_wishlist_dirty WHERE pending=1)")
    abstract suspend fun deleteSyncedByIds(ids: List<String>, ownerUserId: String)

    @Query("DELETE FROM local_wishlists WHERE synced = 1 AND owner_user_id = :ownerUserId AND id NOT IN (:ids) AND id NOT IN (SELECT wishlist_id FROM collection_transfer_wishlist_dirty WHERE pending=1)")
    abstract suspend fun deleteSyncedNotIn(ids: List<String>, ownerUserId: String)

    @Query("SELECT COUNT(*) FROM local_wishlists WHERE synced = 0 AND owner_user_id = :ownerUserId")
    abstract fun observeUnsyncedCount(ownerUserId: String): Flow<Int>
}
