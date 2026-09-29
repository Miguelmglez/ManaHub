package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mmg.manahub.core.data.local.entity.FriendEntity
import com.mmg.manahub.core.data.local.entity.FriendRequestEntity
import com.mmg.manahub.core.data.local.entity.OutgoingFriendRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FriendDao {

    @Query("SELECT * FROM friends ORDER BY friend_nickname ASC")
    abstract fun observeFriends(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friend_requests ORDER BY created_at DESC")
    abstract fun observePendingRequests(): Flow<List<FriendRequestEntity>>

    @Query("SELECT COUNT(*) FROM friend_requests")
    abstract fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM friends")
    abstract fun observeFriendCount(): Flow<Int>

    /** User ids of the cached friends (snapshot read, used to detect newly accepted friendships). */
    @Query("SELECT friend_user_id FROM friends")
    abstract suspend fun getFriendUserIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertFriends(friends: List<FriendEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertRequests(requests: List<FriendRequestEntity>)

    @Query("DELETE FROM friends")
    abstract suspend fun clearFriends()

    @Query("DELETE FROM friends WHERE id = :id")
    abstract suspend fun deleteFriend(id: String)

    @Query("DELETE FROM friend_requests WHERE id = :id")
    abstract suspend fun deleteRequest(id: String)

    @Query("DELETE FROM friend_requests")
    abstract suspend fun clearRequests()

    // ── Outgoing friend requests ──────────────────────────────────────────────

    /** Emits the list of requests the current user has sent that are still PENDING. */
    @Query("SELECT * FROM outgoing_friend_requests ORDER BY created_at DESC")
    abstract fun observeOutgoingRequests(): Flow<List<OutgoingFriendRequestEntity>>

    /** Supabase row ids are stable primary keys, so REPLACE only overwrites the same request. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertOutgoingRequests(requests: List<OutgoingFriendRequestEntity>)

    /** Removes a single outgoing request by its Supabase friendship id. */
    @Query("DELETE FROM outgoing_friend_requests WHERE id = :id")
    abstract suspend fun deleteOutgoingRequest(id: String)

    @Query("DELETE FROM outgoing_friend_requests")
    abstract suspend fun clearOutgoingRequests()

    // A request whose row id or counterpart is already a friend is stale: that id or pair was accepted.
    @Query(
        "DELETE FROM friend_requests WHERE id IN (SELECT id FROM friends) " +
            "OR from_user_id IN (SELECT friend_user_id FROM friends)"
    )
    abstract suspend fun deleteRequestsShadowedByFriends()

    @Query(
        "DELETE FROM outgoing_friend_requests WHERE id IN (SELECT id FROM friends) " +
            "OR to_user_id IN (SELECT friend_user_id FROM friends)"
    )
    abstract suspend fun deleteOutgoingShadowedByFriends()

    /**
     * Replaces whichever lists are non-null in one transaction; a null list keeps its cached rows.
     * Requests shadowed by a friend are dropped, so no id is ever in two lists.
     */
    @Transaction
    open suspend fun replaceAll(
        friends: List<FriendEntity>?,
        incoming: List<FriendRequestEntity>?,
        outgoing: List<OutgoingFriendRequestEntity>?,
    ) {
        if (friends != null) {
            clearFriends()
            upsertFriends(friends)
        }
        if (incoming != null) {
            clearRequests()
            upsertRequests(incoming)
        }
        if (outgoing != null) {
            clearOutgoingRequests()
            upsertOutgoingRequests(outgoing)
        }
        deleteRequestsShadowedByFriends()
        deleteOutgoingShadowedByFriends()
    }

    /** Drops every friends-feature row (sign-out, account switch, account deletion). */
    @Transaction
    open suspend fun clearAll() {
        clearFriends()
        clearRequests()
        clearOutgoingRequests()
    }
}
