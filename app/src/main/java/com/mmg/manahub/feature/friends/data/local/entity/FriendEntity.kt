package com.mmg.manahub.feature.friends.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("friend_user_id") val friendUserId: String,
    @ColumnInfo("friend_nickname") val friendNickname: String,
    @ColumnInfo("friend_game_tag") val friendGameTag: String,
    @ColumnInfo("friend_avatar_url") val friendAvatarUrl: String?,
    @ColumnInfo("cached_at") val cachedAt: Long = System.currentTimeMillis(),
)
