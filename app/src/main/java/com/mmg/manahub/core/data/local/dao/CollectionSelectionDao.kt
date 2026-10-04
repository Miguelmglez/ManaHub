package com.mmg.manahub.core.data.local.dao

import androidx.room.*
import com.mmg.manahub.core.data.local.entity.*
import com.mmg.manahub.core.domain.collection.transfer.CollectionSelectionSection

/** Only frozen tag fields are needed when the selection has no filters. */
data class CollectionSelectionTags(
    @ColumnInfo(name="source_id") val sourceId: String,
    @ColumnInfo(name="card_tags") val tags: String,
    @ColumnInfo(name="card_user_tags") val userTags: String,
)

@Dao
abstract class CollectionSelectionDao {
    @Insert(onConflict=OnConflictStrategy.ABORT)
    abstract suspend fun insertQuery(query: CollectionSelectionQueryEntity)
    @Update
    abstract suspend fun updateQuery(query: CollectionSelectionQueryEntity)
    @Query("SELECT * FROM collection_selection_queries WHERE id=:id AND owner_key=:ownerKey")
    abstract suspend fun query(id: String,ownerKey: String): CollectionSelectionQueryEntity?
    @Query("SELECT * FROM collection_selection_rows WHERE query_id=:id AND source_id>:after ORDER BY source_id LIMIT 200")
    abstract suspend fun scan(id: String,after: String): List<CollectionSelectionRowEntity>
    @Query("UPDATE collection_selection_rows SET matched=1 WHERE query_id=:id AND source_id IN (:ids)")
    abstract suspend fun markMatched(id: String,ids: List<String>)
    @Query("UPDATE collection_selection_rows SET matched=1 WHERE query_id=:id AND (:requireMetadata=0 OR card_stale_reason IS NULL OR card_stale_reason!='pending_hydration')")
    abstract suspend fun markUnfiltered(id: String,requireMetadata: Boolean)
    @Query("SELECT source_id,card_tags,card_user_tags FROM collection_selection_rows WHERE query_id=:id AND source_id>:after AND (card_tags NOT IN ('','[]') OR card_user_tags NOT IN ('','[]')) ORDER BY source_id LIMIT 200")
    abstract suspend fun scanTags(id: String,after: String): List<CollectionSelectionTags>
    @Query("UPDATE collection_selection_rows SET matched=0 WHERE query_id=:id AND matched=1")
    protected abstract suspend fun clearMatched(id: String)
    @Transaction
    open suspend fun resetEvaluation(id: String) { clearMatched(id);deleteGroups(id) }
    @Update
    abstract suspend fun updateRows(rows: List<CollectionSelectionRowEntity>)
    @Query("SELECT * FROM collection_selection_groups WHERE query_id=:id AND group_key>:after ORDER BY group_key LIMIT 200")
    abstract suspend fun scanGroups(id: String,after: String): List<CollectionSelectionGroupEntity>
    @Update
    abstract suspend fun updateGroups(groups: List<CollectionSelectionGroupEntity>)
    @Query("SELECT * FROM collection_selection_rows WHERE query_id=:id AND source_id IN (:ids)")
    abstract suspend fun rows(id: String,ids: List<String>): List<CollectionSelectionRowEntity>
    @Query("SELECT * FROM collection_selection_groups WHERE query_id=:id AND ordinal>:after ORDER BY ordinal LIMIT 50")
    abstract suspend fun page(id: String,after: Long): List<CollectionSelectionGroupEntity>
    @Query("SELECT COUNT(*) FROM collection_selection_groups WHERE query_id=:id")
    abstract suspend fun groupCount(id: String): Long
    @Query("SELECT COALESCE(SUM(quantity),0) FROM collection_selection_groups WHERE query_id=:id")
    abstract suspend fun copyCount(id: String): Long
    @Query("SELECT section AS token,COUNT(*) AS `groups`,COALESCE(SUM(quantity),0) AS copies FROM collection_selection_groups WHERE query_id=:id GROUP BY section ORDER BY MIN(ordinal)")
    abstract suspend fun sections(id: String): List<CollectionSelectionSection>
    @Query("DELETE FROM collection_selection_rows WHERE query_id=:id")
    protected abstract suspend fun deleteRows(id: String)
    @Query("DELETE FROM collection_selection_groups WHERE query_id=:id")
    protected abstract suspend fun deleteGroups(id: String)
    @Query("DELETE FROM collection_selection_queries WHERE id=:id AND owner_key=:ownerKey")
    protected abstract suspend fun deleteQuery(id: String,ownerKey: String)
    @Transaction
    open suspend fun discard(id: String,ownerKey: String) {
        if(query(id,ownerKey)==null)return
        deleteRows(id);deleteGroups(id);deleteQuery(id,ownerKey)
    }
}
