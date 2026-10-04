package com.mmg.manahub.core.data.local.dao

import androidx.room.*
import com.mmg.manahub.core.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CollectionExportDao {
    @Insert(onConflict=OnConflictStrategy.ABORT)
    abstract suspend fun insert(job: CollectionExportEntity)
    @Update
    abstract suspend fun update(job: CollectionExportEntity)
    @Query("SELECT * FROM collection_export_jobs WHERE id=:id AND owner_key=:owner")
    abstract suspend fun get(id: String,owner: String): CollectionExportEntity?
    @Query("SELECT * FROM collection_export_jobs WHERE id=:id AND owner_key=:owner")
    abstract fun observe(id: String,owner: String): Flow<CollectionExportEntity?>
    @Query("SELECT id FROM collection_export_jobs WHERE owner_key=:owner ORDER BY created_at DESC,id DESC LIMIT 1")
    abstract suspend fun latest(owner: String): String?
    @Query("SELECT * FROM collection_selection_rows WHERE query_id=:id AND export_ordinal>:ordinal ORDER BY export_ordinal LIMIT 50")
    abstract suspend fun page(id: String,ordinal: Long): List<CollectionSelectionRowEntity>
}
