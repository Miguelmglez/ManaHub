package com.mmg.manahub.core.data.local.paging

import androidx.room.*

@Entity(tableName = "remote_keys")
data class RemoteKeyEntity(
    @PrimaryKey
    @ColumnInfo(name = "label") val label: String,
    // Null means there is no next page (end of the list has been reached).
    @ColumnInfo(name = "next_offset") val nextOffset: Int?,
)
