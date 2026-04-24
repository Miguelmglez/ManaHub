package com.mmg.manahub.core.data.local.paging

import androidx.room.*

@Dao
interface RemoteKeyDao {

    @Upsert
    fun upsert(key: RemoteKeyEntity)

    @Query("SELECT * FROM remote_keys WHERE label = :label")
    fun getByLabel(label: String): RemoteKeyEntity?

    @Query("DELETE FROM remote_keys WHERE label = :label")
    fun delete(label: String)
}
