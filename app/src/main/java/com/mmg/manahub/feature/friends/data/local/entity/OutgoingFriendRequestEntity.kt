package com.mmg.manahub.feature.friends.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Caches outgoing friend requests sent by the current user that are still PENDING.
 *
 * Kept as a separate table from [FriendRequestEntity] (which stores incoming requests)
 * because the column semantics differ: incoming uses `from_*` columns, outgoing uses
 * `to_*` columns. Mixing both directions into one table would require nullable columns
 * and a direction flag, adding unnecessary complexity to every reactive query.
 *
 * This table is the local mirror of:
 *   SELECT * FROM friendships WHERE user_id_1 = me AND status = 'PENDING'
 */
@Entity(tableName = "outgoing_friend_requests")
data class OutgoingFriendRequestEntity(
    /** Supabase `friendships.id` — used as the stable primary key. */
    @PrimaryKey val id: String,
    /** The user ID of the recipient (friendships.user_id_2). */
    @ColumnInfo("to_user_id") val toUserId: String,
    /** Display name of the recipient at the time the request was sent. */
    @ColumnInfo("to_nickname") val toNickname: String,
    /** Game tag of the recipient (e.g. "#1234"). */
    @ColumnInfo("to_game_tag") val toGameTag: String,
    /** Avatar URL of the recipient — nullable, may not be set yet. */
    @ColumnInfo("to_avatar_url") val toAvatarUrl: String?,
    /** Epoch millis when the friendship row was created in Supabase. */
    @ColumnInfo("created_at") val createdAt: Long,
    /** Epoch millis when this row was last written to Room. */
    @ColumnInfo("cached_at") val cachedAt: Long = System.currentTimeMillis(),
)
