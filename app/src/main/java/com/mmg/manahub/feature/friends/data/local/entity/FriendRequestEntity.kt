package com.mmg.manahub.feature.friends.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friend_requests")
data class FriendRequestEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("from_user_id") val fromUserId: String,
    @ColumnInfo("from_nickname") val fromNickname: String,
    @ColumnInfo("from_game_tag") val fromGameTag: String,
    @ColumnInfo("from_avatar_url") val fromAvatarUrl: String?,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("cached_at") val cachedAt: Long = System.currentTimeMillis(),
)
